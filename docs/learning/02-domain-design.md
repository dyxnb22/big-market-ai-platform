# 02 领域模型设计

## DDD 核心概念速查

| 概念 | 在本项目中的体现 |
|------|---------------|
| **实体 (Entity)** | 有唯一 ID 的对象：`ActivityEntity`、`StrategyAwardEntity`、`UserRaffleOrderEntity` |
| **值对象 (Value Object)** | 无 ID、不可变的描述性概念：`ActivityStateVO`、`TradeTypeVO`、`RuleLogicCheckTypeVO` |
| **聚合 (Aggregate)** | 事务边界单元，一次事务内一起持久化：`CreatePartakeOrderAggregate` 等 |
| **领域服务 (Domain Service)** | 跨实体的业务操作：`BehaviorRebateService`、`CreditAdjustService` |
| **应用服务 (Application Service)** | 编排跨领域用例：`RaffleApplicationService` |
| **仓储 (Repository)** | 聚合的持久化接口：`IActivityRepository`、`IStrategyRepository` |
| **端口 (Port)** | 领域向外依赖的接口：`IStrategyDecisionPort`、`IAwardFulfillmentPort` |
| **适配器 (Adapter)** | 端口的具体实现：`LocalStrategyDecisionPort`（本地）、未来 Remote 适配器 |

---

## 领域一：Activity（活动与配额）

### 核心实体

```
ActivityEntity
  activityId, activityName, strategyId
  state: ActivityStateVO (open / close)
  beginDateTime, endDateTime

UserRaffleOrderEntity
  userId, activityId, strategyId
  orderId (唯一)
  orderState: UserRaffleOrderStateVO (create / used / cancel / failed)
  endDateTime

ActivityAccountEntity        ← 用户活动总配额账户
  userId, activityId
  totalCount, totalCountSurplus
  dayCount, dayCountSurplus
  monthCount, monthCountSurplus
```

### 核心聚合

```
CreatePartakeOrderAggregate
  ├── ActivityAccountEntity    (总账户，更新 surplus)
  ├── ActivityAccountMonthEntity (月账户，懒创建或更新)
  ├── ActivityAccountDayEntity   (日账户，懒创建或更新)
  └── UserRaffleOrderEntity      (新建抽奖订单)
  
  → 一次事务内原子写入以上四张表
```

### 端口

```
IActivityAccountPort
  decrementQuota(userId, activityId, outBusinessNo): boolean
  rollbackQuota(userId, activityId, outBusinessNo)
  
IDrawOutboxPort
  保存 draw 事件到 outbox（Phase 5-G 预留）
```

### 活动配额规则链（Chain of Responsibility）

```
ActivityBaseActionChain       ← 校验活动状态、日期
  → ActivitySkuStockActionChain  ← 校验 SKU 库存
```

---

## 领域二：Strategy（抽奖策略）

### 核心实体

```
StrategyEntity
  strategyId, strategyDesc
  ruleModels: "rule_weight,rule_blacklist"  ← 决定启用哪些规则

StrategyAwardEntity
  strategyId, awardId, awardTitle
  awardRate (概率), awardCount (库存), sort (排序)
  ruleModels: "rule_stock,rule_luck_award"

StrategyRuleEntity
  strategyId, awardId(可空), ruleModel
  ruleValue  ← 规则配置值，例如权重配置 "6000:102,103"

RuleTreeVO
  treeId, treeName, treeRootRuleNode
  treeNodeMap: Map<ruleKey, RuleTreeNodeVO>
```

### 责任链与决策树分工

```
raffleLogicChain()  ← 抽奖前拦截（决定用哪个奖池）
  BlackListLogicChain   → 用户在黑名单，强制返回特定奖品
  RuleWeightLogicChain  → 用户积分满足权重阈值，从权重奖池随机
  DefaultLogicChain     → 兜底，走默认概率表随机
  
raffleLogicTree()  ← 出奖后过滤（决定是否能领这个奖）
  RuleStockLogicTreeNode  → Redis 扣减奖品库存
  RuleLockLogicTreeNode   → 用户抽奖次数不足，锁定本奖
  RuleLuckAwardLogicTreeNode → 幸运奖替换
```

### 概率算法（Armory）

```
StrategyArmoryDispatch
  装配（初始化）：assembleStrategy(strategyId)
  分发（运行时）：getRandomAwardId(strategyId) → awardId

算法选择（基于最小概率粒度）：
  minRate < 0.01%（对应 rateRange > 10000）→ OLogNAlgorithm（二分查找）
  minRate ≥ 0.01%（对应 rateRange ≤ 10000）→ O1Algorithm（平铺 Map）
```

### 端口

```
IStrategyDecisionPort
  performRaffle(RaffleFactorEntity): RaffleAwardEntity
  ← Local 实现：直接调 AbstractRaffleStrategy
  ← Remote 实现：Dubbo 调 strategy-service（flag=false 时不启用）

IStrategyActivityMappingPort
  queryStrategyActivityId(strategyId): Long

IStrategyActivityAccountPort
  queryActivityAccountCount(...): StrategyActivityAccountVO
```

---

## 领域三：Rebate（行为返利）

### 核心实体

```
BehaviorEntity
  userId
  behaviorTypeVO: CALENDAR_SIGN / ACTIVITY_PARTICIPATE
  outBusinessNo  ← 日期字符串（例如"2026-06-13"），用于幂等

BehaviorRebateOrderEntity
  userId, orderId
  behaviorType, rebateType: SKU / INTEGRAL
  rebateConfig  ← SKU 编号 或 积分数量
  bizId         ← 唯一幂等键：userId + rebateType + outBusinessNo
```

### 核心聚合

```
BehaviorRebateAggregate
  ├── BehaviorRebateOrderEntity  (返利订单)
  └── TaskEntity                 (Outbox 任务，含 MQ 消息体)
  
  → 一次事务内原子写入，保证返利订单和待发消息同时存在
```

### 消息结构

```
SendRebateMessageEvent.RebateMessage
  userId, rebateType, rebateConfig, bizId
  
MQ 消费后：
  rebateType == "sku"      → IAccountQuotaWriteAdapter.createOrder(SkuRechargeEntity)
  rebateType == "integral" → IAccountCreditWriteAdapter.createOrder(TradeEntity)
```

---

## 领域四：Credit（积分账户）

### 核心实体

```
CreditAccountEntity
  userId
  adjustAmount  ← 可用积分余额（= totalAmount - freezeAmount）

CreditOrderEntity
  userId, orderId
  tradeName: TradeNameVO (RAFFLE_REBATE / SKU_DEDUCT 等)
  tradeType: TradeTypeVO (FORWARD=增加 / REVERSE=扣减)
  tradeAmount
  outBusinessNo  ← 幂等键（uq_out_business_no）
```

### 核心聚合

```
TradeAggregate
  ├── CreditAccountEntity  (账户状态)
  ├── CreditOrderEntity    (交易流水)
  └── TaskEntity           (Outbox，通知下游)
```

### 一致性设计

```
扣减逻辑（REVERSE 交易）：
  1. Domain 层预校验：adjustAmount >= amount
  2. 仓储层 DB 条件更新：WHERE available_amount >= amount
     → updateSubtractionAmount() 返回 0 行 → 抛 INSUFFICIENT_BALANCE
  3. 分布式锁 lock.lock() 包裹整个 read-check-write，防并发超扣
```

---

## 领域五：Award（奖品履约）

### 核心实体

```
UserAwardRecordEntity
  userId, activityId, strategyId
  orderId, awardId, awardTitle
  awardState: AwardStateVO (create → used)
  awardConfig  ← 传给具体发奖 Handler 的配置

DistributeAwardEntity
  userId, orderId, awardId, awardConfig
```

### 核心聚合

```
UserAwardRecordAggregate
  ├── UserAwardRecordEntity  (中奖记录，初始态 create)
  └── TaskEntity             (Outbox 任务，含 SendAwardMessageEvent)
```

### 发奖策略（Strategy Pattern）

```
AwardService.distributeAward(DistributeAwardEntity)
  → 查询 award.awardKey
  → Map<awardKey, IDistributeAward> 查找对应 Handler
  → handler.giveOutPrizes(GiveOutPrizesAggregate)
  
awardKey 示例：
  "credit"   → CreditAwardDistributeHandler（增加积分）
  "sku"      → SkuAwardDistributeHandler（充值 SKU）
  "physical" → PhysicalAwardDistributeHandler（快递发货）
```

---

## Task 领域（Outbox 基础设施）

虽然叫"领域"，但 Task 更偏基础设施角色，是其他所有领域实现可靠事件发布的基础。

```
TaskEntity
  userId, topic, messageId, message (JSON)
  state: create → completed / failed

ITaskService
  queryNoSendMessageTaskList()  ← 查找未发送或超时任务（每次最多10条）
  sendMessage(TaskEntity)       ← publish 到 RabbitMQ
  updateTaskSendMessageCompleted()
  updateTaskSendMessageFail()
```

**写入时机**：所有带 Outbox 的聚合在落库时同步写 `task` 表（同一事务）。
**发送时机**：`SendMessageTaskJob`（XXL-Job，每5秒）扫描 task 表，批量发送。

---

## 聚合事务边界总结

| 聚合 | 同一事务内写入的表 | 目的 |
|------|-----------------|------|
| `CreatePartakeOrderAggregate` | raffle_activity_account + month + day + user_raffle_order | 配额与订单原子扣减 |
| `UserAwardRecordAggregate` | user_award_record + task | 中奖记录与发奖消息原子 |
| `BehaviorRebateAggregate` | user_behavior_rebate_order + task | 返利订单与消息原子 |
| `TradeAggregate` | user_credit_account + user_credit_order + task | 积分变更与消息原子 |

---

## 端口与适配器全览

```
领域端口（接口，定义在 domain 包）        本地适配器（默认）
─────────────────────────────────────────────────────────────
IStrategyDecisionPort            ←  LocalStrategyDecisionPort
IAwardFulfillmentPort            ←  LocalAwardFulfillmentPort
IActivityAccountPort             ←  LocalActivityAccountPort
IStrategyActivityAccountPort     ←  LocalStrategyActivityAccountPort
IStrategyActivityMappingPort     ←  LocalStrategyActivityMappingPort
IAccountQuotaWriteAdapter        ←  LocalAccountQuotaWriteAdapter
IAccountCreditWriteAdapter       ←  LocalAccountCreditWriteAdapter
IRebateOrderAdapter              ←  LocalRebateOrderAdapter
IRebateReadAdapter               ←  LocalRebateReadAdapter
IAwardDispatchAdapter            ←  LocalAwardDispatchAdapter
IRebateTaskOutboxPort            ←  LocalRebateTaskOutboxPort
ICreditTradeTaskOutboxPort       ←  LocalCreditTradeTaskOutboxPort
IAwardDispatchTaskOutboxPort     ←  LocalAwardDispatchTaskOutboxPort
```

所有本地适配器直接调用 infrastructure 层的 Repository / DAO。
微服务切换时，用 Remote 适配器（Dubbo）替换，领域层代码不动。
