# Big-Market 简历代码走读与面试追问报告

> 对应简历：**Big-Market 大营销抽奖平台｜后端开发工程师｜2024.10 – 2025.05**
>
> 本文比 [18-resume-project-deep-dive.md](18-resume-project-deep-dive.md) 更偏「对着代码讲」和「面试官追问」。建议先读 18 建立口述框架，再用本文做走读与答疑。

---

## 使用方式

| 场景 | 怎么用 |
|------|--------|
| 面试前 1 天 | 按「主链路走读」从 Controller 跟到 Outbox，能口述每一步类名 |
| 面试中被追问 | 直接跳到对应章节的「可能问的问题」 |
| 对简历措辞纠偏 | 看每节末尾的「表述陷阱」，避免说错 |

**简历四点 ↔ 本文章节：**

1. DDD 架构 → §1
2. O(1) Redis 抽奖算法 → §2
3. 责任链 + 决策树规则引擎 → §3
4. Outbox + Redis 预扣库存 → §4

---

## 0. 端到端主链路（先背这条）

```text
POST /api/v1/raffle/activity/draw_by_token
  → RaffleActivityController
  → RaffleDrawApplicationService.draw()          # trigger 薄封装
  → RaffleApplicationService.executeDraw()       # domain 编排
      ① createOrder          # 活动域：额度 + 抽奖单
      ② performRaffle        # 策略域：责任链 →（可选）规则树
      ③ saveUserAwardRecord  # 奖品域：中奖记录 + Outbox task → MQ
  catch → 补偿额度（本地 CAS / 远程 rollback）
异步：
  SendMessageTaskJob         # MQ 补偿
  UpdateAwardStockJob        # Redis 库存回写 MySQL
  SendAwardConsumer          # 真正发奖（可能进积分域）
```

**编排入口（必看）：**

`big-market-domain/.../activity/application/RaffleApplicationService.java` → `executeDraw()`

**走读顺序建议（约 30 分钟）：**

1. `RaffleApplicationService.executeDraw`
2. `AbstractRaffleActivityPartake.createOrder` → `ActivityPartakeOrderSupport.saveCreatePartakeOrderAggregate`
3. `LocalStrategyDecisionPort` → `AbstractRaffleStrategy.performRaffle`
4. `DefaultChainFactory` + 三个 `*LogicChain`
5. `DecisionTreeEngine` + `RuleLock` / `RuleStock` / `RuleLuckAward`
6. `AwardService.saveUserAwardRecord` → `AwardDispatchSupport`
7. `SendMessageTaskJob` + `UpdateAwardStockJob`

---

## 1. 架构设计（DDD：策略 / 活动 / 奖品 / 积分）

### 1.1 简历在说什么

> 基于 DDD 划分策略、活动、奖品、积分等领域，业务与基础设施解耦，提升可维护性。

### 1.2 代码走读：四层与四个限界上下文

#### 四层（模块级）

| 层 | 模块 | 抽奖相关入口 |
|----|------|--------------|
| trigger | `big-market-trigger` | `RaffleActivityController`、`RaffleDrawApplicationService`、`SendAwardConsumer`、`SendMessageTaskJob` |
| application | `domain/.../application` | `RaffleApplicationService` |
| domain | `big-market-domain` | `AbstractRaffleStrategy`、`AwardService`、`CreditAdjustService` |
| infrastructure | `big-market-infrastructure` | `*Repository`、`*Support`、`Local*Port` |

**原则：** domain 只依赖自己定义的 `adapter/port` 与 `adapter/repository` 接口，不 import infrastructure。

#### 四个领域包（命名对照）

| 简历 | 包路径 | 核心职责 |
|------|--------|----------|
| 策略 | `domain/strategy` | 概率表、责任链/规则树、奖品库存调度 |
| 活动 | `domain/activity` | 参与下单、总/月/日额度、活动 SKU |
| 奖品 | `domain/award` | 中奖记录、发奖 Outbox、分发策略 |
| 积分 | `domain/credit` | 积分账户、交易流水、积分 Outbox |

每个域内部结构大致相同：

```text
domain/<bc>/
├── adapter/port/          # 出站端口（防腐层）
├── adapter/repository/    # 仓储接口
├── model/{aggregate,entity,valobj}/
├── service/               # 领域服务
└── application/           # 用例编排（activity 最明显）
```

#### Port 解耦（面试必讲）

活动域编排时**不直接依赖**策略/奖品实现类：

| Port（domain 定义） | 本地实现（infrastructure） | 实际委托 |
|---------------------|----------------------------|----------|
| `IStrategyDecisionPort` | `LocalStrategyDecisionPort` | `IRaffleStrategy.performRaffle` |
| `IAwardFulfillmentPort` | `LocalAwardFulfillmentPort` | `AwardService.saveUserAwardRecord` |
| `IActivityAccountPort` | `LocalActivityAccountPort` / 远程 Account 适配 | 远程额度扣减（开关控制） |

走读文件：

- `domain/activity/adapter/port/IStrategyDecisionPort.java`
- `infrastructure/adapter/port/LocalStrategyDecisionPort.java`
- `domain/activity/application/RaffleApplicationService.java`

#### 活动域：额度扣减不在编排类表面

`RaffleApplicationService` 第 62 行只调 `createOrder`，**本行没有扣额度代码**。扣减在 `createOrder` 内部：

```text
AbstractRaffleActivityPartake.createOrder
  → 查未使用订单（state=create）→ 有则直接返回（不扣额度）
  → doFilterAccount（预检查总/月/日）
  → buildUserRaffleOrder
  → doSavePartakeOrder
       默认：saveCreatePartakeOrderAggregate
            （同一本地事务：扣总/月/日 + insert user_raffle_order）
```

关键文件：

- `domain/activity/service/partake/AbstractRaffleActivityPartake.java`
- `domain/activity/service/partake/RaffleActivityPartakeService.java`
- `infrastructure/.../ActivityPartakeOrderSupport.java`

### 1.3 可能问的问题

**Q1：你们 DDD 四层各自干什么？domain 为什么不能直接调 Redis？**

> trigger 接请求/MQ/Job；application 编排用例；domain 放业务规则与聚合；infrastructure 实现仓储与中间件。domain 只依赖 Port/Repository 接口，这样换 Redis 客户端或拆微服务时，业务规则不用跟着改。

**Q2：一次抽奖四个域怎么协作？**

> 活动域 `executeDraw` 编排：先 `createOrder` 扣额度建单，再经 `IStrategyDecisionPort` 出奖，再经 `IAwardFulfillmentPort` 落中奖记录与 Outbox；积分域在发奖消费或积分交易路径里入账。

**Q3：聚合根举一个例子？**

> `CreatePartakeOrderAggregate`：总/月/日额度与 `user_raffle_order` 必须同事务；`UserAwardRecordAggregate`：中奖记录与 `task` 同事务。

**Q4：`createOrder` 那一行有没有扣额度？**

> 编排类表面没有；新建订单时在 `doSavePartakeOrder` → `saveCreatePartakeOrderAggregate` 里扣；若复用未使用的 `create` 订单则不再扣。

**Q5：远程额度开关是干什么的？**

> `account.service.remote-quota-decrement.enabled`（默认 false）。false：额度与订单同本地事务；true：先 `IActivityAccountPort.decrementQuota`，再只插订单，失败则 `rollbackQuota`（Saga）。

### 1.4 表述陷阱

| 容易说错 | 更准确 |
|----------|--------|
| 「RaffleApplicationService 里扣了额度」 | 「它调用 createOrder，扣减在 partake 保存聚合时完成」 |
| 「奖品域叫 prize」 | 代码是 `award`；积分是 `credit` |
| 「DDD 就是分包」 | 要讲 Port 倒置依赖 + 聚合事务边界 |

---

## 2. 高性能算法（Redis O(1) 空间换时间）

### 2.1 简历在说什么

> 基于 Redis 设计 O(1) 空间换时间抽奖算法，结合多线程预装配，提升高并发抽奖响应。

### 2.2 代码走读：装配 → 选算法 → 抽奖

#### 装配入口

`AbstractStrategyAlgorithm.assembleLotteryStrategy(strategyId)`：

1. 查 `strategy_award` 列表  
2. 每个奖品库存写入 Redis：`strategy_award_count_key_{strategyId}_{awardId}`  
3. 装配默认概率表：`key = strategyId`  
4. 若有 `rule_weight`，为每个积分段再装配子集表：`key = strategyId_4000` 等  

文件：`domain/strategy/service/armory/AbstractStrategyAlgorithm.java`

#### 算法选择（阈值 10000）

`StrategyArmoryDispatch.armoryAlgorithm`：

```text
minAwardRate → convert 成整数 rateRange
rateRange <= 10000 → O1Algorithm
rateRange >  10000 → OLogNAlgorithm
并把算法 bean 名缓存到 Redis，抽奖时按 key 取回
```

文件：`domain/strategy/service/armory/StrategyArmoryDispatch.java`  
常量：`ALGORITHM_THRESHOLD_VALUE = 10000`

#### O(1) 算法

`O1Algorithm`：

| 阶段 | 做什么 |
|------|--------|
| armory | 按概率把 awardId 填进 list → `Collections.shuffle` → `Map<index, awardId>` → Redis Hash |
| dispatch | `secureRandom.nextInt(rateRange)` → `getStrategyAwardAssemble`（一次 Hash GET） |

文件：`domain/strategy/service/armory/algorithm/impl/O1Algorithm.java`

#### Redis Key（走读时能报出来）

定义在 `big-market-types/.../Constants.RedisKey`：

| 用途 | Key 前缀 |
|------|----------|
| 概率表 | `big_market_strategy_rate_table_key_{key}` |
| 随机范围 | `big_market_strategy_rate_range_key_{key}` |
| 选用算法 | `strategy_armory_algorithm_key_{key}` |
| 奖品库存 | `strategy_award_count_key_{strategyId}_{awardId}` |
| 库存延迟队列 | `strategy_award_count_query_key...` |

实现：`infrastructure/.../StrategyAwardCacheSupport.java`

#### 「多线程」实际在哪

| 场景 | 是否多线程 | 说明 |
|------|------------|------|
| O1 概率表装配 | 基本否 | 单线程 expand + shuffle |
| OLogN `threadSearch` | 是 | 分段数 >16 时 `CompletableFuture` + 线程池并行扫段 |
| `UpdateAwardStockJob` | 是 | 按奖品维度线程池回写 DB |

**面试建议说法：** 预装配把计算前置到活动上线；抽奖 O(1) 读 Redis。多线程用在大范围 O(log n) 查找与库存异步回写，而不是「多线程并行建概率表」。

### 2.3 可能问的问题

**Q1：为什么叫空间换时间？**

> 把概率展开成查找表存 Redis，用内存换掉每次抽奖的加权扫描；抽奖只做随机下标 + 一次 GET。

**Q2：为什么要预热（armory）？**

> 概率计算、洗牌、写 Redis 放在活动装配阶段；抽奖热点路径不做重计算，RT 更稳。

**Q3：什么时候不用 O(1)？**

> `rateRange > 10000` 时用 `OLogNAlgorithm`，避免超大 Hash；查找按表大小走线性 / 二分 / 多线程分段。

**Q4：权重分段的概率表怎么来的？**

> 装配时按 `rule_weight` 过滤奖品子集，再对 `strategyId_权重值` 各装一张表；权重链命中后 `getRandomAwardId(strategyId, weightKey)` 抽子集表。

**Q5：随机数用什么？会不会不公平？**

> `SecureRandom`；表先按概率占格再 shuffle，长期频率逼近配置概率。

### 2.4 表述陷阱

| 容易说错 | 更准确 |
|----------|--------|
| 「多线程预装配概率表」 | 「预装配 + O(log n)/库存 Job 使用线程池」 |
| 「Key 叫 strategy_award_count_{strategyId}」 | 概率表是 `rate_table`；`award_count` 是库存计数 |
| 「每次抽奖都算概率」 | 抽奖只查预热表 |

---

## 3. 规则引擎（责任链 + 决策树）

### 3.1 简历在说什么

> 责任链 + 决策树双层规则引擎，编排黑名单、权重分段、次数锁定等规则。

### 3.2 代码走读：两阶段入口

`AbstractRaffleStrategy.performRaffle`：

```text
1. raffleLogicChain(userId, strategyId)  → 得到 awardId + logicModel
2. 若 logicModel != rule_default → 直接返回（跳过规则树）
3. raffleLogicTree(...) → 次数锁 / 库存 / 兜底
4. buildRaffleAwardEntity
```

文件：`domain/strategy/service/AbstractRaffleStrategy.java`

#### 责任链装配

`DefaultChainFactory.openLogicChain(strategyId)`：

1. 读策略 `rule_models`（如 `rule_blacklist,rule_weight`）  
2. 按顺序 `getBean` 串成链，**尾部固定接 `rule_default`**  
3. 按 `strategyId` 缓存链实例  

节点（均为 `@Scope(PROTOTYPE)`）：

| Bean | 类 | 行为 |
|------|-----|------|
| `rule_blacklist` | `BlackListLogicChain` | 命中用户 → 固定兜底奖，接管 |
| `rule_weight` | `RuleWeightLogicChain` | 按累计消耗积分匹配分段 → 子集表抽奖 |
| `rule_default` | `DefaultLogicChain` | 主表 O(1)/O(log n) 抽奖 |

**为什么 Prototype：** 链节点有 `next` 指针；若单例，不同策略的链会互相串改。

#### 决策树

`DecisionTreeEngine.process`：从根节点循环，按节点返回的 `ALLOW` / `TAKE_OVER` 选边。

| 节点 | 类 | 典型语义 |
|------|-----|----------|
| `rule_lock` | `RuleLockLogicTreeNode` | 今日抽奖次数未达门槛 → TAKE_OVER 走兜底 |
| `rule_stock` | `RuleStockLogicTreeNode` | Redis DECR 成功 → TAKE_OVER 返回原奖；失败 → ALLOW 走兜底 |
| `rule_luck_award` | `RuleLuckAwardLogicTreeNode` | 返回配置兜底奖 |

数据表：`rule_tree` / `rule_tree_node` / `rule_tree_line`。

#### 何时不进规则树

1. 责任链被黑名单/权重接管（`logicModel != rule_default`）  
2. 该奖品未配置 `ruleModels` / 规则树（`DefaultRaffleStrategy.raffleLogicTree` 直接返回原 awardId）

### 3.3 可能问的问题

**Q1：责任链和决策树区别？为什么两层？**

> 链解决「抽什么」（线性前置分流）；树解决「能不能给」（带分支的后置过滤）。语义不同，拆开后扩展互不影响。

**Q2：黑名单为什么跳过库存树？**

> 黑名单已给出最终兜底奖，业务上不再做次数/库存校验；只有默认随机路径才需要「抽中后过滤」。

**Q3：次数锁 ALLOW / TAKE_OVER 怎么理解？**

> 达到次数门槛 ALLOW 继续往库存走；未达 TAKE_OVER 被拦截，沿边走到幸运奖。库存扣失败则 ALLOW 落到幸运奖（注意：库存成功是 TAKE_OVER 结束）。

**Q4：链节点为什么 Prototype？**

> 避免单例共享 `next`，导致策略 A 的链串到策略 B。

**Q5：规则改了线上不生效可能是什么原因？**

> `DefaultChainFactory` 按 strategyId 缓存链；规则模型若来自缓存，改库后需失效/重装策略，否则仍走旧链。

### 3.4 表述陷阱

| 容易说错 | 更准确 |
|----------|--------|
| 「所有抽奖都走规则树」 | 仅默认链路径且奖品配置了树才走 |
| 「库存节点失败就抛异常」 | 失败走兜底奖，用户仍能拿到结果 |
| 「权重是决策树节点」 | 权重在责任链；次数锁/库存在树 |

---

## 4. 高可用（Outbox + Redis 预扣 + 异步回写）

### 4.1 简历在说什么

> Outbox（Task 表 + 补偿 Job）保证 MQ 最终一致；Redis 预扣库存 + 异步批量回写 DB，减轻数据库压力。

### 4.2 代码走读：Outbox

#### 写路径

```text
AwardService.saveUserAwardRecord
  → 构建 SendAwardMessage + TaskEntity(state=create)
  → UserAwardRecordAggregate
  → AwardRepository / AwardDispatchSupport.saveUserAwardRecord
```

`AwardDispatchSupport` 本地事务内：

1. `insert user_award_record`  
2. `insert task`（Outbox）  
3. CAS 将 `user_raffle_order`：`create → used`（失败则整单回滚）  

事务提交后尽力发 MQ：

- 成功 → task `complete`  
- 失败 → task `fail`，留给 Job  

文件：

- `domain/award/service/AwardService.java`
- `infrastructure/.../AwardDispatchSupport.java`
- `domain/award/model/valobj/TaskStateVO.java`（`create` / `complete` / `fail`）

#### 补偿 Job

`SendMessageTaskJob`（XXL-Job，按库分片 DB1/DB2）：

1. Redisson 锁  
2. 查未发送/失败任务  
3. 重发 MQ，更新状态  

文件：`big-market-trigger/.../job/SendMessageTaskJob.java`

同类 Outbox 还用于积分交易、返利等（`ICreditTradeTaskOutboxPort`、`IRebateTaskOutboxPort`）。

### 4.3 代码走读：库存两级

```text
装配：cacheStrategyAwardCount → Redis
抽中：RuleStockLogicTreeNode → subtractionAwardStock → DECR
成功：写入 Redisson 延迟队列（约 3s）
Job：UpdateAwardStockJob 拉取 → updateStrategyAwardStock（MySQL -1）
```

文件：

- `domain/strategy/service/rule/tree/impl/RuleStockLogicTreeNode.java`
- `infrastructure/.../StrategyAwardCacheSupport.java`（DECR + 队列）
- `big-market-trigger/.../job/UpdateAwardStockJob.java`

**并发正确性以 Redis 为准**；MySQL 是异步投影。DECR 后若 `<0` 会回滚计数；成功路径还有按剩余量的 `SETNX` 锁键，降低库存回补后的超卖风险。

### 4.4 与额度补偿的关系（常被连问）

抽奖失败时 `RaffleApplicationService` catch 会补偿**活动额度**（仅当 `awardSaved=false`）；若规则树已 **Redis 预占奖品库存**（`stockReserved=true`）且中奖记录**未落库**，编排层会调用 `releaseAwardStockReservation` 做 INCR+删 lock 对称回滚。

落库成功（`awardSaved=true`）后调用 `confirmAwardStockReservation` 入延迟队列写 MySQL；若 confirm 失败，**禁止 release**，改为写入 `strategy_award_stock_confirm_task`，由 `StrategyAwardStockConfirmJob` 补偿确认。

> 预占/确认/释放由 `RuleStockLogicTreeNode` → `StrategyAwardCacheSupport.reserveStock/confirm/release` 实现；兜底奖（`RuleLuckAwardLogicTreeNode`）无 DECR，不入队。

订单复用：上次已扣额度、订单仍为 `create`，重试 `createOrder` 直接返回旧单，**不再扣额度**。

### 4.5 可能问的问题

**Q1：为什么用 Outbox 而不是先发 MQ？**

> 先发 MQ 再写库：消息成功、库失败会「空发奖」。Outbox 保证业务行与待发消息同事务落库，发送失败可重试，最终一致。

**Q2：task 有哪些状态？Job 扫什么？**

> `create` 入库；发送成功 `complete`；失败 `fail`。Job 扫未完成任务补偿发送。

**Q3：如何防超卖？**

> Redis DECR 原子扣减；不足则走幸运奖。MySQL 异步更新，不扛热点写。

**Q4：MQ 重复消费怎么办？**

> 业务唯一键 + 捕获 `DuplicateKeyException`；消费者幂等。发奖侧还有订单 `create→used` CAS。

**Q5：Redis 与 MySQL 库存不一致怎么办？**

> 以 Redis 为准防超卖；Job/延迟队列追平 MySQL；极端情况对账补偿。

**Q6：抽奖中途挂了，用户额度会不会丢？**

> 订单停在 `create` 可复用继续抽；`executeDraw` 失败会走额度补偿（CAS，防重复回滚）。

### 4.6 表述陷阱

| 容易说错 | 更准确 |
|----------|--------|
| 「MQ 事务消息」 | 本项目主路径是本地消息表 Outbox + 补偿 Job |
| 「库存扣 MySQL」 | 热点扣 Redis，MySQL 异步回写 |
| 「失败会回滚奖品库存」 | 默认补偿的是活动额度，不是策略奖品 Redis 库存 |

---

## 5. 面试 2 分钟串讲（贴简历）

> 我做的是营销抽奖平台后端。按 DDD 拆了策略、活动、奖品、积分，活动应用服务编排整条链路，跨域通过 Port 解耦。  
> 性能上，活动装配时把概率表预热到 Redis，抽奖 O(1) 查表；概率范围过大切 O(log n)。  
> 规则上，责任链做黑名单和积分权重分流，默认路径再进决策树做次数锁和库存校验。  
> 可用性上，中奖记录和发奖消息同事务写 Outbox，失败由 XXL-Job 补偿；库存 Redis 预扣、Job 异步回写 DB。

---

## 6. 深挖题速查（按简历点）

### DDD

| 追问 | 锚点代码 |
|------|----------|
| 分层 | `docs/learning/13-ddd-and-design-patterns.md` |
| Port | `IStrategyDecisionPort` / `LocalStrategyDecisionPort` |
| 编排 | `RaffleApplicationService.executeDraw` |
| 额度事务 | `ActivityPartakeOrderSupport.saveCreatePartakeOrderAggregate` |

### 算法

| 追问 | 锚点代码 |
|------|----------|
| O(1) | `O1Algorithm` |
| 选算法 | `StrategyArmoryDispatch`（阈值 10000） |
| 装配 | `AbstractStrategyAlgorithm.assembleLotteryStrategy` |
| Redis | `StrategyAwardCacheSupport`、`Constants.RedisKey` |

### 规则引擎

| 追问 | 锚点代码 |
|------|----------|
| 两阶段 | `AbstractRaffleStrategy.performRaffle` |
| 建链 | `DefaultChainFactory` |
| 树引擎 | `DecisionTreeEngine` |
| 库存节点 | `RuleStockLogicTreeNode` |

### 高可用

| 追问 | 锚点代码 |
|------|----------|
| Outbox 写 | `AwardService` + `AwardDispatchSupport` |
| 补偿 | `SendMessageTaskJob` |
| 库存回写 | `UpdateAwardStockJob` |
| 额度补偿 | `RaffleApplicationService` catch + `compensatePartakeQuota` |

---

## 7. 相关文档

| 文档 | 用途 |
|------|------|
| [18-resume-project-deep-dive.md](18-resume-project-deep-dive.md) | 简历四点口述版 |
| [12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md) | 算法与规则引擎细节 |
| [13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md) | DDD 与模式 |
| [14-interview-qa.md](14-interview-qa.md) | 通用高频题 |
| [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md) | 高并发 |
| [../data-and-outbox.md](../data-and-outbox.md) | Outbox / 数据所有权 |
| [09-code-map.md](09-code-map.md) | 跳转地图 |
