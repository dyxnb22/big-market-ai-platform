# 04 设计模式解析

## 设计模式总览

| 模式 | 用在哪 | 解决什么问题 |
|------|--------|------------|
| 模板方法 | 抽奖流程骨架 | 固定抽奖步骤顺序，子类扩展具体算法 |
| 责任链 | 策略规则过滤 / 活动配额校验 | 规则可插拔，动态组合，避免 if-else 堆砌 |
| 决策树 | 出奖后过滤 | 复杂多分支条件结构化表达 |
| 策略模式 | 概率算法选择 / 发奖类型路由 | 算法可替换，对扩展开放 |
| 工厂模式 | 规则链 / 决策树构建 | 隐藏构建细节，统一入口 |
| 观察者 / 事件 | 领域事件发布 | 解耦领域逻辑和副作用 |
| Outbox 模式 | 可靠事件发布 | 避免分布式事务，保证至少一次投递 |
| 聚合模式 | 事务边界 | 原子写多表，保证一致性 |
| 端口与适配器 | 微服务解耦 | 领域层不依赖具体实现，支持本地/远程切换 |
| 特性开关 | 流量切换 | 零停机发布，灰度路由 |
| 分片路由 | DB 路由中间件 | 透明的分库分表 |
| 幂等设计 | 所有写入操作 | 防止重复请求造成数据不一致 |

---

## 模板方法

**位置**：`AbstractRaffleStrategy.performRaffle()`

抽奖流程分三步，顺序固定，但每步的具体实现可被子类定制：

```java
// 模板方法（final，不可覆盖顺序）
public RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity) {
    // Step 1: 责任链过滤（可被子类替换实现）
    DefaultChainFactory.StrategyAwardVO chainAwardVO = raffleLogicChain(userId, strategyId);

    // Step 2: 如果责任链已决出结果（非默认），直接返回
    if (!DefaultLogicChain.RULE_DEFAULT.equals(chainAwardVO.getLogicModel())) {
        return buildRaffleAwardEntity(strategyId, chainAwardVO.getAwardId(), ...);
    }

    // Step 3: 决策树过滤（可被子类替换实现）
    DefaultTreeFactory.StrategyAwardVO treeAwardVO = raffleLogicTree(userId, strategyId, chainAwardVO.getAwardId(), ...);
    return buildRaffleAwardEntity(strategyId, treeAwardVO.getAwardId(), ...);
}

// 抽象方法，子类实现
protected abstract DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId);
protected abstract DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId, ...);
```

**学习要点**：模板方法定义算法骨架，开闭原则体现在"对步骤扩展开放，对流程修改封闭"。

---

## 责任链

### 策略规则链（抽奖前拦截）

**位置**：`domain.strategy.service.rule.chain.*`

```
链条构成（由 DefaultChainFactory 按策略 ruleModels 配置动态组装）：

BlackListLogicChain
  → 条件：userId 在黑名单中
  → 动作：返回黑名单奖品（保底），logicModel = "rule_blacklist"
  → 否则：next.logic()

RuleWeightLogicChain
  → 条件：用户积分 >= 某权重阈值
  → 动作：从权重奖池随机，logicModel = "rule_weight"
  → 否则：next.logic()

DefaultLogicChain（必须在链尾）
  → 动作：走默认概率表随机，logicModel = "rule_default"
```

**关键代码结构**：

```java
// 抽象基类管理 next 指针
public abstract class AbstractLogicChain implements ILogicChain {
    private ILogicChain next;

    @Override
    public ILogicChain appendNext(ILogicChain next) {
        this.next = next;
        return next;
    }

    protected ILogicChain next() { return next; }
}

// 链节点实现
public class BlackListLogicChain extends AbstractLogicChain {
    public StrategyAwardVO logic(String userId, Long strategyId) {
        if (isBlacklisted(userId)) {
            return StrategyAwardVO.builder().awardId(blacklistAward).logicModel(RULE_BLACKLIST).build();
        }
        return next().logic(userId, strategyId);
    }
}
```

**工厂构建链**：

```java
// DefaultChainFactory.openLogicChain()
String ruleModels = strategyEntity.getRuleModels(); // "rule_blacklist,rule_weight"
ILogicChain chain = defaultLogicChain; // 从链尾开始
if (ruleModels.contains("rule_weight")) chain = ruleWeightLogicChain.appendNext(chain);
if (ruleModels.contains("rule_blacklist")) chain = blackListLogicChain.appendNext(chain);
return chain; // 返回链头
```

### 活动配额规则链

**位置**：`domain.activity.service.chain.*`

```
ActivityBaseActionChain    → 校验活动状态（是否 open）和日期
  → ActivitySkuStockActionChain → 校验 SKU 库存是否充足
```

---

## 决策树

**位置**：`domain.strategy.service.rule.tree.*`

决策树解决的问题：根据出奖结果，通过树形结构判断是否满足领奖条件，可能换奖或不换。

### 树结构

```
RuleTreeVO
  treeRootRuleNode: "rule_stock"    ← 从库存节点开始
  treeNodeMap:
    "rule_stock":
      ├── TAKE_OVER → "rule_lock"   ← 库存扣减成功，判断是否解锁
      └── ALLOW     → (返回当前奖品)  ← 库存耗尽，放行（降级或兜底）
    "rule_lock":
      ├── TAKE_OVER → "rule_luck"   ← 未解锁，替换幸运奖
      └── ALLOW     → (返回当前奖品)  ← 已解锁，放行
    "rule_luck":
      └── TAKE_OVER → (返回幸运奖)
```

### 节点实现

```java
// RuleStockLogicTreeNode
public TreeActionEntity logic(String userId, Long strategyId, Integer awardId, ...) {
    boolean decremented = strategyDispatch.subtractionAwardStock(strategyId, awardId, endDateTime);
    if (decremented) {
        // 库存扣减成功，交给下一节点判断
        return TreeActionEntity.builder()
            .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
            .strategyAwardVO(StrategyAwardVO.builder().awardId(awardId)...).build();
    }
    // 库存耗尽，允许通过（配合兜底奖）
    return TreeActionEntity.builder()
        .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
        .strategyAwardVO(StrategyAwardVO.builder().awardId(defaultAwardId)...).build();
}
```

**责任链 vs 决策树的区别**：

| | 责任链 | 决策树 |
|--|--------|--------|
| 结构 | 线性（链表） | 非线性（二叉树/多叉树） |
| 执行 | 按顺序每个节点都有机会 | 按分支条件跳转 |
| 用途 | 规则拦截（可以提前终止） | 条件路由（根据结果选路径） |
| 配置方式 | 代码拼接 chain | DB 存储树结构（rule_tree 表） |

---

## 概率算法：O(1) vs O(logN)

**位置**：`domain.strategy.service.armory.*`

### O(1) 算法（O1Algorithm）

适用于奖品概率粒度不太精细（最小概率 ≥ 0.01%，即 rateRange ≤ 10000）的场景。

**原理**：预计算一个平铺索引表，每个 index 直接映射到 awardId。

```
装配（一次性，assembleStrategy 时执行）：
  awardList = [{awardId:101, rate:0.05}, {awardId:102, rate:0.30}, ...]
  rateRange = 1/minRate = 10000
  table = new ArrayList(rateRange)
  for each award:
    count = (int)(rateRange * award.rate)  // 102 号奖品占 3000 个 slot
    for i in [0, count): table.add(awardId)
  Collections.shuffle(table)  // 打乱顺序
  → 存入 Redis: HSET strategy_rate_table_100 0 101  1 102  2 101 ...

抽奖（每次抽奖时）：
  index = Redis INCR seed % rateRange
  awardId = Redis HGET strategy_rate_table_100 index
  // 时间复杂度 O(1)，纯 Redis 读
```

**空间换时间**：rateRange=10000 时，Redis 中存 10000 个槽位。

### O(logN) 算法（OLogNAlgorithm）

适用于概率粒度极细（rateRange > 10000）或奖品很多的场景。

**原理**：构建累积概率数组，二分查找定位。

```
装配：
  sorted awards by awardId
  cumulative[i] = sum(rates[0..i])
  → 存入 Redis 有序数组

抽奖：
  r = random(0, 1)
  binarySearch(cumulative, r) → awardId
  // 时间复杂度 O(logN)，N 为奖品数量
```

**选择标准**（`AbstractStrategyAlgorithm`）：

```java
BigDecimal minAwardRate = awardList.stream().map(StrategyAwardEntity::getAwardRate).min(...);
double rateRange = convert(minAwardRate); // 概率 → 范围
if (rateRange <= 10000) {
    return o1Algorithm;
} else {
    return oLogNAlgorithm;
}
```

---

## Outbox 模式

**位置**：所有带 `TaskEntity` 的聚合 + `SendMessageTaskJob` + 各 MQ Consumer

### 问题背景

抽奖成功后需要发 MQ 触发发奖，如果先落库再发 MQ，数据库成功但 MQ 失败怎么办？如果先发 MQ 再落库，MQ 成功但落库失败怎么办？两种方案都有一致性风险。

### Outbox 解决方案

```
规则：消息先写表（同一事务），异步发送，消费端幂等。

Step 1：领域事务内
  ┌─────────────────────────────────────────┐
  │ INSERT user_award_record (业务数据)      │  ← 同一个 DB 事务
  │ INSERT task (state=create, msg=JSON)    │
  └─────────────────────────────────────────┘
  → 两个写入要么都成功，要么都失败

Step 2：Job 轮询（SendMessageTaskJob，5 秒间隔）
  SELECT * FROM task WHERE state='create' AND timeout
  FOR EACH task:
    MQ.publish(topic, message)  ← 事务外
    UPDATE task SET state='completed'

Step 3：MQ Consumer（SendAwardConsumer）
  消费消息，执行业务
  业务表有唯一索引 → 重复消费 DuplicateKeyException → 幂等忽略

Step 4：边界情况
  publish 成功，markCompleted 失败 → task 还是 create → 下次重发
  → Consumer 幂等处理，不会造成数据问题
```

### 为什么不用分布式事务（2PC / Saga）

- 2PC 同步阻塞，性能差，不适合高并发抽奖
- Saga 实现复杂，补偿逻辑难维护
- Outbox 只需要单数据库事务（已有的能力），MQ 消费端做幂等，实现简单可靠

---

## 端口与适配器

**位置**：`domain.*.adapter.port` 包（端口定义） + `trigger/adapter/*` 包（适配器实现）

### 设计目的

领域层不关心"策略决策是本地执行还是远程调用"——它只调用接口。适配器层负责路由。

```
                  Domain Layer
                 ┌────────────┐
                 │ IStrategy  │
                 │ DecisionPort│
                 └──────┬─────┘
                        │ depends on interface only
                 ┌──────▼─────────────────────────────────────┐
                 │              Adapter Layer                   │
                 │  LocalStrategyDecisionPort  (flag=false)    │
                 │    → 直接调 AbstractRaffleStrategy          │
                 │                                             │
                 │  RemoteStrategyDecisionPort (flag=true)     │
                 │    → Dubbo RPC → strategy-service           │
                 └─────────────────────────────────────────────┘
```

**切换方式**（无代码修改，纯配置）：

```yaml
# application.yml
strategy:
  service:
    remote-read:
      enabled: false  # ← 改为 true 即切换到远程
```

```java
@Resource
@Qualifier("localStrategyDecisionPort")  // 或 "remoteStrategyDecisionPort"
private IStrategyDecisionPort strategyDecisionPort;

// 实际上通过 @ConditionalOnProperty 实现
@Bean
@ConditionalOnProperty(name = "strategy.service.remote-read.enabled", havingValue = "false", matchIfMissing = true)
public IStrategyDecisionPort localStrategyDecisionPort(...) { ... }
```

---

## 特性开关（Feature Flag）

**位置**：`big-market-starter-dcc` + `@DCCValue` 注解 + Nacos

### 作用

- 新服务暗启动：先部署，先注册 Dubbo，但不开流量
- 灰度发布：逐步把流量切到新路径
- 快速回滚：发现问题，修改一个 Nacos 配置，秒级生效

### 本项目的 Flag 清单

```
account.service.remote-quota-decrement.enabled = false
account.service.remote-credit-write.enabled    = false
account.service.remote-quota-write.enabled     = false
account.fulfillment.remote-award.enabled        = false
rebate.service.remote-create-order.enabled      = false
rebate.service.remote-read.enabled              = false
strategy.service.remote-read.enabled            = false
account.award-credit-outbox.enabled             = false
```

全部默认 `false`，生产切换需外部证据（DBA/Ops/Engineering 确认后才改）。

---

## 幂等设计

### 唯一键一览

| 场景 | 幂等键 | 唯一索引 |
|------|--------|---------|
| 抽奖参与订单 | userId + activityId（查现有 create 订单） | — |
| 返利订单 | bizId = userId + rebateType + date | `uq_biz_id` |
| 积分交易 | outBusinessNo | `uq_out_business_no` |
| 中奖记录 | orderId | `uq_order_id` |
| SKU 兑换订单 | outBusinessNo | `uq_out_business_no` |
| 配额扣减账本 | userId + activityId + outBusinessNo | `uq_user_activity_biz` |
| Outbox 任务 | userId + messageId | `uq_user_message_id` |
| 积分发奖任务 | awardOrderId | `uq_award_order_id` |

### 处理重复的通用模式

```java
try {
    dao.insert(entity);
} catch (DuplicateKeyException e) {
    log.warn("幂等拦截，已处理 orderId:{}", entity.getOrderId());
    // 不再抛出，当作成功处理
}
```

---

## 分布式锁

**位置**：`ActivityRepository`、`CreditRepository`、`AwardRepository`

### 使用方式

```java
RLock lock = redisService.getLock(RedisKey.USER_CREDIT_ACCOUNT_LOCK + userId + "_" + outBusinessNo);
try {
    lock.lock();  // 无参：开启 Watchdog 自动续期（默认 30 秒，每 10 秒续一次）
    // ... critical section ...
} finally {
    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

### 为什么用 `lock.lock()` 而不是 `lock.lock(3, TimeUnit.SECONDS)`

带 leaseTime 的版本：锁固定 3 秒后自动释放，不续期。如果慢 SQL 超过 3 秒，锁提前释放，并发请求进入临界区，破坏一致性。

无参版本：Redisson Watchdog 每 10 秒检测一次，如果线程还在执行，就续期到 30 秒，直到线程完成后主动 `unlock()`。
