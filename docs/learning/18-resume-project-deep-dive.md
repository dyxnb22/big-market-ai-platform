# Big-Market 简历项目详解（结合代码）

> 对应简历条目：**Big-Market 大营销抽奖平台｜后端开发工程师｜2024.10 – 2025.05**
>
> 本文按简历四点展开，并映射到本仓库真实代码与文档，便于复习与面试口述。

---

## 0. 项目一句话与端到端主链路

**一句话：** 基于 DDD 的营销抽奖平台，用 Redis 预装配概率表做 O(1) 抽奖，用责任链 + 决策树编排规则，用 Outbox + Redis 预扣库存保障高并发下的最终一致性。

**一次抽奖主链路（代码入口）：**

```text
POST /api/v1/raffle/activity/draw_by_token
  → RaffleActivityController.draw()
  → RaffleActivityFacade.draw()
  → RaffleDrawApplicationService.draw()
  → RaffleApplicationService.executeDraw()
      ① Activity：createOrder（扣额度 + 建抽奖单）
      ② Strategy：performRaffle（责任链选奖 → 规则树过滤/扣库存）
      ③ Award：saveUserAwardRecord（中奖记录 + Outbox task → MQ）
      ④ MQ Consumer：SendAwardConsumer → distributeAward（发奖）
      ⑤ Credit：积分奖品直接入账，或写 credit_award_task 后由 message-job 派发到账户服务
  → SendMessageTaskJob / UpdateAwardStockJob / DispatchCreditAwardTaskJob
     （MQ 补偿 / 奖品库存回写 / 积分发奖任务派发）
```

编排代码：

```51:87:big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java
    public ActivityDrawResponseEntity executeDraw(ActivityDrawRequestEntity request) {
        // ...
        // 2. 参与活动 - 创建参与记录订单（含额度扣减）
        UserRaffleOrderEntity orderEntity = raffleActivityPartakeService.createOrder(userId, activityId);

        try {
            // 3. 抽奖策略 - 执行抽奖
            RaffleAwardEntity raffleAwardEntity = strategyDecisionPort.performRaffle(...);

            // 4. 存放结果 - 写入中奖记录
            awardFulfillmentPort.saveUserAwardRecord(userAwardRecord);
            // ...
```

当前实现还有一段容易被问到的失败补偿：如果策略抽奖或发奖记录写入阶段异常，`RaffleApplicationService` 会回退额度。本地模式下通过 `compensatePartakeQuota()` 做条件更新；远程额度服务开启时，先把抽奖单从 `create` CAS 到失败态，再调用账户端口回滚，避免重复补偿。

HTTP 侧薄封装：

```30:39:big-market-trigger/src/main/java/com/dyx/market/trigger/application/RaffleDrawApplicationService.java
    public ActivityDrawResponseDTO draw(ActivityDrawRequestDTO request) {
        // Nacos runtime switch degradeSwitch 降级校验
        ActivityDrawResponseEntity result = raffleApplicationService.executeDraw(
                ActivityDrawRequestEntity.builder()
                        .userId(request.getUserId())
                        .activityId(request.getActivityId())
                        .build());
```

**命名对照（简历 ↔ 代码）：**

| 简历用语 | 代码 Bounded Context |
|---------|----------------------|
| 策略 | `domain/strategy` |
| 活动 | `domain/activity` |
| 奖品 | `domain/award`（不是 prize） |
| 积分 | `domain/credit`（不是 points） |

**当前服务模块对照：**

最终 Docker 学习栈运行 gateway、auth、admin、market、chatbot、message-job、account 七个服务；策略与返利在 market 内部实现，积分奖在 message-job 内通过本地 outbox 派发。

| 服务模块 | 主要职责 |
|----------|----------|
| `big-market-market-service` | 主市场/抽奖服务启动模块，承载活动抽奖、策略、发奖本地路径 |
| `big-market-trigger` | HTTP、MQ Consumer、XXL-Job、应用层适配器 |
| `big-market-account-service` | 账户额度、积分交易 RPC Provider |
| `big-market-message-job-service` | 拆分后的消息/Outbox Job，如积分发奖任务派发 |

---

## 1. 架构设计：基于 DDD，拆分策略 / 活动 / 奖品 / 积分

### 1.1 简历原文在说什么

> 基于 DDD 划分策略、活动、奖品、积分等领域，实现业务逻辑与基础设施高度解耦，提升代码可维护性。

### 1.2 四层架构

```text
trigger（HTTP / MQ / Job）
  → application（编排一次完整用例）
    → domain（业务规则、聚合、实体；不依赖具体中间件）
      → infrastructure（实现 Port / Repository：MyBatis、Redis、MQ）
```

| 层 | 模块 | 典型类 |
|----|------|--------|
| trigger | `big-market-trigger`、各 `*-service` | `RaffleActivityController`、`SendAwardConsumer`、`SendMessageTaskJob` |
| application | `big-market-trigger/.../application` + `domain/.../application` | `RaffleDrawApplicationService`、`RaffleApplicationService` |
| domain | `big-market-domain` | `AbstractRaffleStrategy`、`AwardService`、`CreditAdjustService` |
| infrastructure | `big-market-infrastructure` | `StrategyRepository`、`AwardDispatchSupport`、`AwardCreditGrantSupport`、`StrategyAwardCacheSupport` |

详细说明见：[13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md)

### 1.3 各领域职责与关键类

#### 策略域 `domain/strategy`

- **职责：** 抽奖规则、概率表装配、责任链/规则树、奖品库存扣减调度
- **关键类：**
  - `AbstractRaffleStrategy` / `DefaultRaffleStrategy`：抽奖标准流程
  - `StrategyArmoryDispatch` / `O1Algorithm`：装配与调度
  - `DefaultChainFactory` / `DecisionTreeEngine`：规则引擎
  - `IStrategyRepository`：仓储接口（domain 不直接碰 Redis/DB）

#### 活动域 `domain/activity`

- **职责：** 活动参与、额度（总/日/月）扣减、抽奖单、活动 SKU 库存
- **关键类：**
  - `RaffleApplicationService`：跨域编排
  - `AbstractRaffleActivityPartake`：参与下单
  - `CreatePartakeOrderAggregate`：额度 + 订单同一事务
  - Port：`IStrategyDecisionPort`、`IAwardFulfillmentPort`（防腐层，调用策略/发奖）
- **补充：** `RaffleApplicationService` 异常时会补偿额度；本地路径调用 `activityRepository.compensatePartakeQuota()`，远程额度扣减开启时先 CAS 标记抽奖单失败，再调用 `activityAccountPort.rollbackQuota()`。

#### 奖品域 `domain/award`

- **职责：** 中奖记录落库、发奖任务（Outbox）、奖品分发策略
- **关键类：**
  - `AwardService.saveUserAwardRecord`
  - `UserAwardRecordAggregate`（中奖记录 + task）
  - `IDistributeAward` 实现（如积分类奖品）
  - `AwardDispatchSupport`：中奖记录、task、抽奖单状态在本地事务内处理，事务后发送 MQ
  - `AwardCreditGrantSupport`：积分奖品发放；中奖记录与 `credit_award_task` Outbox 同事务落库

#### 积分域 `domain/credit`

- **职责：** 积分账户增减、交易流水、积分相关 Outbox
- **关键类：**
  - `CreditAdjustService`
  - `TradeAggregate`
  - `ICreditRepository`

### 1.4 解耦怎么体现（面试重点）

1. **Domain 只依赖接口：** 如 `IStrategyDecisionPort.performRaffle()`，活动域不直接依赖策略实现类。
2. **聚合保证一致性边界：** 参与抽奖时，额度扣减与订单插入在同一聚合/事务内完成。
3. **基础设施可替换：** Redis/MySQL/MQ 实现都在 infrastructure；换存储或拆微服务时，优先改适配器，而不是改领域规则。
4. **边界可替换：** 例如 `LocalAwardFulfillmentPort` 直接委托本地 `IAwardService`，
   通过 Port 隔离领域规则与基础设施实现，当前最终拓扑固定走本地实现。

### 1.5 面试口述模板

> 我按 DDD 把抽奖拆成策略、活动、奖品、积分四个限界上下文。活动域负责编排：先扣额度建单，再通过 Port 调策略出奖，再调奖品域落库发消息。领域层通过 Repository/Port 倒置依赖，不直接绑定 Redis/MyBatis/RPC；所以从单体本地调用演进到 Dubbo 服务拆分时，主要改适配器和启动模块，核心领域规则基本不用动。

---

## 2. 高性能算法：Redis O(1) 空间换时间抽奖 + 预装配

### 2.1 简历原文在说什么

> 基于 Redis 设计并实现 O(1) 空间换时间抽奖算法，结合多线程预装配，显著提升高并发下的抽奖响应速度。

### 2.2 为什么需要预装配

若每次抽奖都现场做加权随机（遍历奖品算区间），高并发下 CPU 与逻辑复杂度都会上去。

本项目把「按概率选奖」前置到 **armory（装配）** 阶段，抽奖时只做一次随机下标 + Redis 读取。

### 2.3 装配流程（代码）

入口：`AbstractStrategyAlgorithm.assembleLotteryStrategy(strategyId)`

```38:71:big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/armory/AbstractStrategyAlgorithm.java
    public boolean assembleLotteryStrategy(Long strategyId) {
        // 1. 查询策略配置
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);

        // 2. 缓存奖品库存【用于 decr 扣减】
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            cacheStrategyAwardCount(strategyId, awardId, awardCount);
        }

        // 3.1 默认装配全量概率表
        armoryAlgorithm(String.valueOf(strategyId), strategyAwardEntities);

        // 3.2 按权重分段再装配子集概率表（如 strategyId_4000）
        // ...
```

要点：

1. 把每个奖品剩余库存写入 Redis（供后续 DECR）
2. 装配默认策略概率表：`key = strategyId`
3. 若配置了 `rule_weight`，再为每个积分段装配子集表：`key = strategyId_4000` 等

### 2.4 O(1) 算法实现

类：`O1Algorithm`

```21:58:big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/armory/algorithm/impl/O1Algorithm.java
    public void armoryAlgorithm(...) {
        // 1. 按概率把 awardId 填进 list（占格越多概率越高）
        // 2. Collections.shuffle 乱序
        // 3. 转成 Map<index, awardId>
        // 4. 存入 Redis
    }

    public Integer dispatchAlgorithm(String key) {
        int rateRange = repository.getRateRange(key);
        Integer awardId = repository.getStrategyAwardAssemble(key, secureRandom.nextInt(rateRange));
        return awardId;
    }
```

**数学直觉（假设 rateRange = 10000）：**

| 奖品 | 概率 | 占格数 |
|------|------|--------|
| 101 | 0.01 | 100 |
| 102 | 0.05 | 500 |
| 103 | 0.94 | 9400 |

抽奖：`nextInt(10000)` → Redis `HGET` → O(1) 得到 `awardId`。

这就是简历里的 **「空间换时间」**：用 Redis 存一张展开后的概率查找表，换掉每次抽奖的加权计算。

### 2.5 算法选择与异步处理

`StrategyArmoryDispatch` 会按概率范围阈值选择算法：

- 范围较小 → `O1Algorithm`（查表 O(1)）
- 范围过大 → `OLogNAlgorithm`（分段查找，避免超大 Hash）

当前代码中，概率表装配在活动上线/装配阶段顺序构建；线程池和分库任务主要用在库存回写、消息补偿等异步处理场景：

| 场景 | 实现方式 |
|------|----------|
| 活动上线前概率表预热 | `assembleLotteryStrategy()` 将默认表与权重子表写入 Redis |
| 抽奖实时路径 | 随机下标 + Redis 读取，O(1) 命中奖品 |
| 奖品库存回写 | `UpdateAwardStockJob` 使用 `ThreadPoolExecutor` 消费延迟队列并回写 MySQL |
| 活动 SKU 库存回写 | `UpdateActivitySkuStockJob` 使用 `ThreadPoolExecutor` 异步处理 SKU 队列 |
| MQ / Outbox 补偿 | `SendMessageTaskJob_DB1/DB2`、`DispatchCreditAwardTaskJob_DB1/DB2` 分库扫描 |

**面试建议表述：**

> 我们把抽奖概率表在活动装配阶段预热到 Redis，抽奖时随机下标一次命中，复杂度 O(1)。概率范围过大时切 O(log n) 算法，避免 Redis 大 Hash 过重；库存回写和部分 Outbox 补偿任务通过 XXL-Job 分库扫描、线程池异步处理，把耗时操作移出抽奖主链路，保证高并发下抽奖 RT 稳定。

相关文档：[12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md)

---

## 3. 规则引擎：责任链 + 决策树双层设计

### 3.1 简历原文在说什么

> 采用责任链 + 决策树组合构建双层规则引擎，实现黑名单过滤、权重分段、次数锁定等规则的灵活编排与解耦。

### 3.2 为什么要「双层」

| 层 | 模式 | 解决的问题 | 时机 |
|----|------|------------|------|
| 第一层 | 责任链 | **抽哪个奖**（抽前分流） | 先执行 |
| 第二层 | 决策树 | **能不能发这个奖**（抽后过滤） | 责任链的候选奖均进入（该奖配置了规则树时） |

入口：`AbstractRaffleStrategy.performRaffle()`

```52:64:big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/AbstractRaffleStrategy.java
        // 2. 责任链：拿到初步 awardId
        DefaultChainFactory.StrategyAwardVO chainStrategyAwardVO = raffleLogicChain(userId, strategyId);

        // 3. 规则树：接管候选奖也要做库存校验
        DefaultTreeFactory.StrategyAwardVO treeStrategyAwardVO =
            raffleLogicTree(userId, strategyId, chainStrategyAwardVO.getAwardId(), endDateTime, orderId);
        return buildRaffleAwardEntity(...);
```

### 3.3 责任链节点（抽前）

配置顺序通常来自策略 `rule_models`，例如：

```text
rule_blacklist → rule_weight → rule_default
```

| 节点 | 类 | 行为 |
|------|-----|------|
| 黑名单 | `BlackListLogicChain` | 命中 → 固定兜底奖，接管链路 |
| 权重 | `RuleWeightLogicChain` | 按用户积分段，在子集概率表抽奖 |
| 默认 | `DefaultLogicChain` | 走主概率表 `getRandomAwardId(strategyId)` |

权重规则示例：

```text
4000:102,103,104,105 5000:102,103,104,105,106,107
```

含义：累计消耗达到对应分段时，只在该奖品子集中抽（装配阶段已为 `strategyId_4000` 建好表）。

**原型模式：** 链节点 `@Scope(PROTOTYPE)`，每次 `buildChain` 拿新实例，避免不同策略的 `next` 互相污染。

### 3.4 决策树节点（抽后）

数据来自 `rule_tree` / `rule_tree_node` / `rule_tree_line`，由 `DecisionTreeEngine` 按边条件（ALLOW / TAKE_OVER）遍历。

典型结构：

```text
                 [rule_lock 次数锁]
                /                 \
           ALLOW                   TAKE_OVER（未达次数）
              /                       \
      [rule_stock 库存]            [rule_luck_award 兜底]
      /            \
 TAKE_OVER        ALLOW（无库存）
 （扣减成功）         \
 返回原奖品        [rule_luck_award]
```

| 节点 | 类 | 行为 |
|------|-----|------|
| 次数锁 | `RuleLockLogicTreeNode` | 今日抽奖次数未达门槛 → 走兜底 |
| 库存 | `RuleStockLogicTreeNode` | Redis DECR；成功返回原奖，失败走兜底 |
| 兜底 | `RuleLuckAwardLogicTreeNode` | 返回配置的幸运奖（常为积分） |

库存扣减触发点：

```text
RuleStockLogicTreeNode
  → strategyDispatch.subtractionAwardStock(strategyId, awardId, endDateTime)
  → Redis 原子 DECR + 延迟队列（供 Job 回写 MySQL）
```

### 3.5 面试口述模板

> 抽奖规则分两层：责任链做抽前分流，处理黑名单和积分权重；责任链给出候选奖后，若该奖配置了规则树，会继续做次数锁和库存校验，避免接管路径绕过库存。两边都是可配置节点，新增规则主要加节点和配置，不用改 `performRaffle` 主流程。

---

## 4. 高可用保障：Outbox + Redis 预扣库存 + 异步回写

### 4.1 简历原文在说什么

> 采用 Outbox（Task 表 + 补偿 Job）保证 MQ 最终一致；通过 Redis 预扣库存与异步批量回写 DB，缓解数据库压力。

这里其实是 **两条互补机制**：消息可靠投递 + 库存高并发。

### 4.2 Outbox：Task 表 + 补偿 Job

#### 问题

若「先发 MQ 再写库」：MQ 成功、DB 失败 → 用户收到奖但无记录。

若「先写库再发 MQ」且不落任务：进程宕机 → 有记录但消息丢失，奖发不出去。

#### 做法

在 **同一本地事务** 中：

1. 写 `user_award_record`
2. 写 `task`（`state = create`，消息体已序列化）
3. 更新抽奖单状态等

事务提交后再尝试发 MQ：

- 成功 → task 标 `completed`
- 失败 → task 标 `fail`
- 补偿 → `SendMessageTaskJob` 扫描 `fail`，以及超过 6 秒仍为 `create` 的任务重试

领域侧构建聚合：

```37:63:big-market-domain/src/main/java/com/dyx/market/domain/award/service/AwardService.java
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        // 构建发奖消息 + TaskEntity(state=create)
        UserAwardRecordAggregate aggregate = UserAwardRecordAggregate.builder()
                .taskEntity(taskEntity)
                .userAwardRecordEntity(userAwardRecordEntity)
                .build();
        // 同一事务存储聚合
        awardRepository.saveUserAwardRecord(aggregate);
    }
```

事务与事务后发送位置：

```62:97:big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardDispatchSupport.java
        transactionTemplate.execute(status -> {
            // 写 user_award_record
            userAwardRecordDao.insert(userAwardRecord);
            // 写 task
            awardDispatchTaskOutboxPort.insert(taskEntity);
            // 标记抽奖单已使用
            awardActivityOrderPort.markUserRaffleOrderUsed(...);
        });

        // 事务外发 MQ，失败由 task 补偿
        eventPublisher.publish(taskEntity.getTopic(), taskEntity.getMessage());
```

同类模式还用于：

- 积分交易：`ICreditTradeTaskOutboxPort`
- 行为返利：`IRebateTaskOutboxPort`
- 积分奖品发放：`credit_award_task` + `DispatchCreditAwardTaskJob`（固定 Outbox 链路）

相关文档：[../data-and-outbox.md](../data-and-outbox.md)、[15-data-model.md](15-data-model.md)（task 表）

#### 积分奖品的二级 Outbox

`SendAwardConsumer` 收到发奖消息后调用 `AwardService.distributeAward()`。若奖品类型是 `user_credit_random`，`UserCreditRandomAward` 会生成随机积分并调用 `saveGiveOutPrizesAggregate()`：

```76:90:big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardCreditGrantSupport.java
    private void saveWithCreditOutbox(...) {
        transactionTemplate.execute(status -> {
            int updateAwardCount = userAwardRecordDao.updateAwardRecordCompletedState(userAwardRecordReq);
            // 同事务写入 credit_award_task，后续由 message-job 派发到账户服务
            awardCreditWritePort.insertCreditAwardTask(...);
        });
    }
```

代码固定写 `credit_award_task(state=pending)`，由 `big-market-message-job-service` 的 `DispatchCreditAwardTaskJob_DB1/DB2` 扫描派发到账户服务，成功后标记 `dispatched`，失败累计 `retry_count`，达到阈值转 `failed`。本地与 Docker 只通过 Profile 选择账户写适配器，不改变 Outbox 语义。

#### 面试口述

> 我们用经典 Outbox：业务数据与待发送消息同事务落库，事务提交后再发 MQ，发送成功改 completed，失败或超时 create 由 XXL-Job 补偿扫描，保证发奖消息最终投递。积分奖品还支持二级 Outbox：发奖完成记录和 credit_award_task 同事务落库，再由 message-job 调账户服务，避免积分发放跨服务时丢任务或重复入账。

### 4.3 Redis 预扣库存 + 异步批量回写

#### 问题

高并发下每次扣库存都打 MySQL，容易成为瓶颈，也难做原子扣减。

#### 做法（两级库存）

| 级别 | 存储 | 操作 | 时机 |
|------|------|------|------|
| 热数据 | Redis `strategy_award_count:{strategyId}_{awardId}` | 原子 DECR | 抽奖实时 |
| 冷数据 | MySQL `strategy_award.stock_count_surplus` | 批量 UPDATE | Job 异步 |

流程：

```text
装配时：cacheStrategyAwardCount → Redis
抽中时：RuleStockLogicTreeNode → subtractionAwardStock → DECR
成功后：写入 Redisson 延迟队列
定时：UpdateAwardStockJob 拉取并 updateStrategyAwardStock
```

活动 SKU 库存同理：`ActivitySkuStockActionChain` + `UpdateActivitySkuStockJob`。

`UpdateAwardStockJob` 当前实现会查询开放活动的 `(strategyId, awardId)` 列表，并把每个队列消费和 MySQL 回写提交到 `ThreadPoolExecutor`。`UpdateActivitySkuStockJob` 对 SKU 库存也采用类似处理。

并发相关说明：[06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md)

#### 面试口述

> 库存以 Redis 为准做原子预扣，避免超卖；MySQL 通过延迟队列 + Job 异步批量回写，把写压力从抽奖热点路径挪走，形成最终一致。

---

## 5. 四点如何串成「项目亮点」回答（2 分钟版）

> 这个项目是营销抽奖平台。我按 DDD 拆了策略、活动、奖品、积分，活动域编排整条链路，领域通过 Port 解耦基础设施。
> 性能上，抽奖前把概率表预装配到 Redis，抽奖 O(1) 查表；规则上用责任链做黑名单/权重分流，用决策树做次数锁和库存校验。
> 可用性上，中奖记录和发奖消息走 Outbox + 补偿 Job 保证最终投递；积分奖品支持 credit_award_task 二级 Outbox；库存 Redis 预扣、Job 异步回写 DB，扛住高并发并减轻数据库压力。

---

## 6. 代码与文档索引（复习用）

| 主题 | 优先阅读 |
|------|----------|
| DDD 分层与模式 | [13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md) |
| 抽奖算法与规则引擎 | [12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md) |
| 高并发 | [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md) |
| Outbox / 数据 | [../data-and-outbox.md](../data-and-outbox.md)、[15-data-model.md](15-data-model.md) |
| 面试问答 | [14-interview-qa.md](14-interview-qa.md) |
| 代码地图 | [09-code-map.md](09-code-map.md) |

| 主题 | 关键代码 |
|------|----------|
| HTTP 抽奖入口 | `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java` |
| Trigger 应用服务 | `big-market-trigger/src/main/java/com/dyx/market/trigger/application/RaffleDrawApplicationService.java` |
| 抽奖编排 | `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java` |
| 双层规则入口 | `big-market-domain/.../strategy/service/AbstractRaffleStrategy.java` |
| O(1) 算法 | `big-market-domain/.../armory/algorithm/impl/O1Algorithm.java` |
| 装配与库存缓存 | `big-market-domain/.../armory/AbstractStrategyAlgorithm.java` |
| 责任链 | `big-market-domain/.../rule/chain/impl/*LogicChain.java` |
| 决策树 | `big-market-domain/.../rule/tree/factory/engine/impl/DecisionTreeEngine.java` |
| 库存树节点 | `big-market-domain/.../rule/tree/impl/RuleStockLogicTreeNode.java` |
| Outbox 构建 | `big-market-domain/.../award/service/AwardService.java` |
| Outbox 事务与发送 | `big-market-infrastructure/.../adapter/repository/AwardDispatchSupport.java` |
| 积分奖品二级 Outbox | `big-market-infrastructure/.../adapter/repository/AwardCreditGrantSupport.java` |
| MQ 补偿 Job | `big-market-trigger/.../job/SendMessageTaskJob.java` |
| 库存回写 Job | `big-market-trigger/.../job/UpdateAwardStockJob.java` |
| 积分发奖派发 Job | `big-market-message-job-service/.../config/DispatchCreditAwardTaskJob.java` |

---

## 7. 常见追问（简答）

**Q：黑名单/权重接管后为什么还要走规则树？**
A：责任链接管只确定候选奖，不等于库存已预占。候选奖配置规则树时仍需继续执行库存节点，避免接管路径超卖。

**Q：Redis 库存和 MySQL 不一致怎么办？**
A：并发正确性以 Redis 为准；MySQL 是异步投影。Job/延迟队列负责追平；极端情况可对账补偿。

**Q：Outbox 和本地消息表是不是一回事？**
A：本质相同：先落库再发消息，用状态机 + 定时任务保证最终发送成功。

**Q：O(1) 会不会浪费内存？**
A：会，这是空间换时间。概率范围过大时切 `OLogNAlgorithm`，在内存与速度间折中。
