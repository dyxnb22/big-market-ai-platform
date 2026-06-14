# 12 抽奖策略算法详解

## 整体两阶段设计

抽奖执行分为两个阶段，入口在 `AbstractRaffleStrategy.performRaffle()`：

```
阶段一：责任链 → 决定"抽哪个奖品 ID"
阶段二：规则树 → 对抽出的奖品 ID 做"能不能给"的过滤
```

```java
// AbstractRaffleStrategy.performRaffle()
// 阶段一：责任链
DefaultChainFactory.StrategyAwardVO chainResult = raffleLogicChain(userId, strategyId);

// 如果责任链被黑名单或权重接管，直接返回（跳过规则树）
if (!RULE_DEFAULT.equals(chainResult.getLogicModel())) {
    return buildRaffleAwardEntity(strategyId, chainResult.getAwardId(), ...);
}

// 阶段二：规则树（仅在责任链走到默认节点时才进入）
DefaultTreeFactory.StrategyAwardVO treeResult =
    raffleLogicTree(userId, strategyId, chainResult.getAwardId(), endDateTime);
return buildRaffleAwardEntity(strategyId, treeResult.getAwardId(), ...);
```

---

## 阶段一：责任链（抽奖前规则）

### 装配时机

在调用 `armory` 接口时，`StrategyArmoryDispatch` 从 `strategy_award` 表读取奖品及概率，
生成一个长度为 10000 的随机概率表存入 Redis（Key: `strategy_award_count_{strategyId}`）。
每个位置存储一个奖品 ID，按概率填充。

### 责任链节点（从左到右依次执行）

```
rule_blacklist → rule_weight → rule_default（必须存在）
```

**节点 1：BlackListLogicChain（黑名单）**

- 查询 Redis 中的黑名单用户列表
- 命中黑名单 → 直接返回配置的"兜底奖品"（通常是积分），不再往下
- 未命中 → 调用 `next().logic()` 传给下一节点

**节点 2：RuleWeightLogicChain（积分权重）**

- 权重规则格式：`4000:102,103,104,105 5000:102,103,104,105,106,107`
  含义：累计消耗 4000 积分的用户，只在 102/103/104/105 号奖品中抽
- 查询用户累计消耗积分（`queryActivityAccountTotalUseCount`）
- 默认使用 `AnalyticalEqual`：精确匹配积分值（必须等于 4000 才触发 4000 权重）
- 命中对应积分段 → 在该子集概率表中抽取，返回奖品 ID
- 未命中 → 放行给下一节点

**节点 3：DefaultLogicChain（默认随机抽奖）**

- 从 Redis 中 10000 格概率表随机取一个位置
- 返回该位置对应的奖品 ID，`logicModel = "rule_default"`

### 关键：原型模式（Prototype）

`DefaultChainFactory.buildChain()` 通过 `applicationContext.getBean()` 获取链节点 Bean。
链节点类上标注了 `@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)`，
每次 `getBean` 都返回新实例，确保不同策略的责任链 next 引用互不干扰。

---

## 阶段二：规则树（抽奖后过滤）

### 数据来源

规则树从三张表加载并缓存：
- `rule_tree`：树的基本信息（树名、根节点 Key）
- `rule_tree_node`：每个节点的 key、ruleValue
- `rule_tree_line`：节点间的边（from → to，条件 EQUAL/ALLOW/TAKE_OVER）

### 三个树节点

**节点 A：RuleLockLogicTreeNode（次数锁）**

- 检查用户今日抽奖次数（`queryTodayUserRaffleCount`）是否 ≥ 配置的解锁门槛
- **≥ 门槛（ALLOW）** → 奖品解锁，进入下一节点（通常是 rule_stock）
- **< 门槛（TAKE_OVER）** → 奖品未解锁，走向兜底节点（rule_luck_award）

**节点 B：RuleStockLogicTreeNode（库存扣减）**

- 调用 `strategyDispatch.subtractionAwardStock()` 对 Redis 中的奖品库存做原子 decrement
- **库存 > 0（TAKE_OVER）** → 扣减成功，写入延迟队列（异步同步到 MySQL），返回该奖品
- **库存 = 0（ALLOW）** → 库存不足，走向兜底节点（rule_luck_award）

**节点 C：RuleLuckAwardLogicTreeNode（兜底奖品）**

- 返回配置好的兜底奖品 ID（通常是随机积分）
- 树的叶子节点，无后续边

### 规则树遍历示意

```
                 [rule_lock]
                /           \
         ALLOW/             \TAKE_OVER（未解锁）
              /               \
        [rule_stock]       [rule_luck_award]
        /           \
  TAKE_OVER/        \ALLOW（无库存）
  （扣减成功）         \
  返回原奖品       [rule_luck_award]
```

---

## 概率表的数学原理

装配时将奖品按概率展开成一个 10000 格的数组并乱序（Fisher-Yates shuffle）：

| 奖品 ID | 概率 | 占格数 |
|---------|------|--------|
| 101（一等奖） | 0.01 | 100 格 |
| 102（二等奖） | 0.05 | 500 格 |
| 103（三等奖） | 0.94 | 9400 格 |

抽奖时：`RandomUtil.nextInt(10000)` 随机取下标，O(1) 复杂度命中奖品，
避免了每次抽奖都做概率计算的 CPU 开销。

---

## 库存的"两级"设计

奖品库存分两级：

| 级别 | 存储 | 操作 | 时机 |
|------|------|------|------|
| Redis | `strategy_award_count:{strategyId}_{awardId}` | 原子 DECR | 每次抽奖实时扣减 |
| MySQL | `strategy_award.stock_count_surplus` | 批量 UPDATE | `UpdateAwardStockJob` 异步从 Redis 延迟队列读取后更新 |

这样设计的原因：Redis 操作是原子的，高并发下不会超卖；MySQL 批量更新降低写压力。
真实库存以 Redis 为准，MySQL 是最终落库记录。
