# 03 核心业务流程

## 流程一：参与活动与抽奖

这是整个平台最核心的流程，涉及 Activity + Strategy + Award 三个领域。

### 入口

```
POST /api/v1/raffle/activity/raffle
  → RaffleActivityController.raffle()
  → RaffleApplicationService.executeDraw(ActivityDrawRequestEntity)
```

### 完整流程

```
┌──────────────────────────────────────────────────────────────┐
│ Step 1  参数校验                                               │
│   userId 非空 && activityId 非空                               │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│ Step 2  幂等查询                                               │
│   activityRepository.queryNoUsedRaffleOrder()                │
│   → 若存在 create 态旧订单，直接复用（不重复扣配额）               │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│ Step 3  创建参与订单（配额扣减）                                  │
│   IRaffleActivityPartakeService.createOrder()                │
│                                                              │
│   3.1 活动规则链校验（Chain of Responsibility）               │
│       ActivityBaseActionChain  → 活动状态 / 日期是否合法        │
│       ActivitySkuStockActionChain → SKU 库存是否充足           │
│                                                              │
│   3.2 doFilterAccount() — 配额预检查（事务外，体验优化）          │
│       查询总账户 → monthCountSurplus > 0 → dayCountSurplus > 0 │
│       ⚠ 真正的一致性边界在 Step 3.3 的 DB WHERE surplus > 0    │
│                                                              │
│   3.3 saveCreatePartakeOrderAggregate()（事务内）             │
│       ┌─────────────────────────────────────┐               │
│       │ raffle_activity_account  surplus - 1 │               │
│       │ raffle_activity_account_month        │ 原子写入        │
│       │   surplus - 1（懒创建或更新）          │               │
│       │ raffle_activity_account_day          │               │
│       │   surplus - 1（懒创建或更新）          │               │
│       │ user_raffle_order  INSERT (create态) │               │
│       └─────────────────────────────────────┘               │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│ Step 4  执行抽奖策略                                            │
│   IStrategyDecisionPort.performRaffle(RaffleFactorEntity)    │
│                                                              │
│   4.1 责任链过滤（pre-raffle）                                 │
│       BlackListLogicChain  → 黑名单用户强制返回保底奖              │
│       RuleWeightLogicChain → 积分达阈值进权重奖池               │
│       DefaultLogicChain    → 默认概率表随机（O(1)/O(logN)）     │
│                                                              │
│   4.2 决策树过滤（post-raffle）                                │
│       RuleStockLogicTreeNode   → Redis 扣减奖品库存            │
│       RuleLockLogicTreeNode    → 解锁次数不足时换奖              │
│       RuleLuckAwardLogicTreeNode → 幸运奖替换                 │
│                                                              │
│   返回 RaffleAwardEntity (awardId, awardTitle, awardConfig)  │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│ Step 5  保存中奖记录（触发异步发奖）                               │
│   IAwardFulfillmentPort.saveUserAwardRecord()                │
│   ┌─────────────────────────────────────────┐               │
│   │ user_award_record  INSERT (create态)     │ 原子写入        │
│   │ task               INSERT (待发 MQ 消息) │               │
│   └─────────────────────────────────────────┘               │
│   → SendMessageTaskJob 轮询发出 MQ                            │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
                   返回抽奖结果给用户

════════════════════ 异常补偿路径 ════════════════════

    Step 4 或 Step 5 任意抛出异常：
      compensatePartakeQuota(userId, activityId, orderId, orderTime)
        → DB CAS: user_raffle_order state: create → failed
        → 若成功: 恢复 total/month/day surplus（使用 orderTime 定位正确周期账户）
        → 若失败（订单已非 create 态）: 跳过，幂等
```

### 配额层级设计

```
每个用户对每个活动有三层独立配额：

  总配额     raffle_activity_account.total_count_surplus
    │
    ├── 月配额  raffle_activity_account_month.month_count_surplus
    │
    └── 日配额  raffle_activity_account_day.day_count_surplus

扣减时三层同时 -1（同一事务）
校验时任一层不足即拒绝
补偿时三层同时 +1（但只还创建该订单当时所在的月/日账户）
```

---

## 流程二：发奖履约

发奖是异步的，通过 Outbox + MQ + Consumer 三段完成。

```
┌─────────────────────────────────────────────────────────────┐
│ 阶段 A：保存中奖记录（同步，在主流程 Step 5 中完成）               │
│   user_award_record (state=create)                          │
│   task (state=create, topic=send_award, message=JSON)       │
└────────────────────────┬────────────────────────────────────┘
                         │ 同一事务内完成，保证中奖和待发消息共存
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 阶段 B：Outbox 轮询发送（SendMessageTaskJob，每 5 秒）          │
│   1. 获取分布式锁（防多实例重复发）                              │
│   2. 查 task 表 state=create 且超时的任务（最多 10 条）          │
│   3. eventPublisher.publish(topic, message)                 │
│   4. 更新 task.state = completed                            │
│   （publish 成功后 markCompleted 失败 → 下次重发，消费端幂等）    │
└────────────────────────┬────────────────────────────────────┘
                         ↓ RabbitMQ
┌─────────────────────────────────────────────────────────────┐
│ 阶段 C：消费发奖事件（SendAwardConsumer）                        │
│   1. 解析 SendAwardMessage (userId, orderId, awardId, ...)  │
│   2. IAwardDispatchAdapter.distributeAward()                │
│      → 查 award.awardKey                                    │
│      → 按 awardKey 路由到对应 IDistributeAward Handler:      │
│        "credit"   → 调积分 domain 增加积分                    │
│        "sku"      → 充值 SKU 配额                            │
│        "physical" → 记录发货信息                             │
│   3. 更新 user_award_record.state = used                    │
└─────────────────────────────────────────────────────────────┘

幂等保证：user_award_record 有 uq_order_id 唯一索引
重复消费 → DuplicateKeyException → 忽略（已处理）
```

---

## 流程三：签到返利

```
POST /api/v1/raffle/activity/calendar_sign_rebate_by_token
  → RaffleActivityController.calendarSignRebate(userId)
```

### 流程

```
┌──────────────────────────────────────────────────────────────┐
│ Step 1  幂等预检查（无锁，体验优化）                              │
│   outBusinessNo = 今日日期 "2026-06-13"                       │
│   rebateReadAdapter.isCalendarSignRebate(userId, date)       │
│   → 已签到 → 直接返回已签到响应（不创建订单）                      │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│ Step 2  创建返利订单                                            │
│   IBehaviorRebateService.createOrder(BehaviorEntity)         │
│                                                              │
│   查 daily_behavior_rebate 获取今日签到配置                     │
│   对每条返利配置：                                              │
│     bizId = userId + "_" + rebateType + "_" + outBusinessNo  │
│     ┌─────────────────────────────────────────────┐          │
│     │ user_behavior_rebate_order  INSERT           │ 原子写入  │
│     │ task                        INSERT           │          │
│     └─────────────────────────────────────────────┘          │
│                                                              │
│   并发幂等兜底：uq_biz_id 唯一索引                               │
│   → INDEX_DUP 异常 → 触发层捕获 → 返回已签到响应                 │
└─────────────────────────┬────────────────────────────────────┘
                          ↓ MQ 异步
┌──────────────────────────────────────────────────────────────┐
│ Step 3  消费返利事件（RebateMessageConsumer）                   │
│   RebateMessage.rebateType == "integral"                     │
│     → IAccountCreditWriteAdapter.createOrder(TradeEntity)   │
│     → 积分账户 +N                                             │
│   RebateMessage.rebateType == "sku"                          │
│     → IAccountQuotaWriteAdapter.createOrder(SkuRechargeEntity) │
│     → 活动 SKU 配额 +N                                        │
└──────────────────────────────────────────────────────────────┘
```

### 幂等设计要点

```
bizId 构成：userId + rebateType + outBusinessNo（日期）
  → 同一用户同一天同一类型返利只允许一条记录
  → uq_biz_id 是最终防线
  → isCalendarSignRebate 是体验优化（减少无效写入），不是幂等核心
```

---

## 流程四：积分扣减（SKU 兑换）

```
POST /api/v1/raffle/activity/credit_pay_order
```

### 流程

```
┌──────────────────────────────────────────────────────────────┐
│ Step 1  查询 SKU 信息，计算积分价格                              │
│   查 raffle_activity_sku 获取 productAmount（积分单价）         │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│ Step 2  创建积分交易订单                                         │
│   ICreditAdjustService.createOrder(TradeEntity)              │
│                                                              │
│   Domain 层预检查：adjustAmount >= tradeAmount               │
│                                                              │
│   仓储层（持分布式锁 lock.lock()）                              │
│   ┌─────────────────────────────────────────────┐           │
│   │ user_credit_account  UPDATE (available -= N) │           │
│   │   WHERE available_amount >= N               │ 原子写入   │
│   │   返回 0 行 → 抛 INSUFFICIENT_BALANCE        │           │
│   │ user_credit_order  INSERT                   │           │
│   │ task               INSERT（Outbox）          │           │
│   └─────────────────────────────────────────────┘           │
└─────────────────────────┬────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────┐
│ Step 3  更新 SKU 兑换订单状态（触发发奖）                         │
│   ActivityRepository.updateOrder(DeliveryOrderEntity)        │
│   → raffle_activity_order.state = COMPLETED                 │
│   → 同时追加活动账户配额（SKU 兑换等于充值配额）                   │
└──────────────────────────────────────────────────────────────┘
```

---

## 流程五：策略装配（初始化）

抽奖依赖提前装配好的概率表，通常在活动上线时或首次访问时触发。

```
POST /api/v1/raffle/strategy/strategy_armory?strategyId=xxx
  → RaffleStrategyController.strategyArmory()
  → IStrategyArmoryDispatch.assembleStrategy(strategyId)
```

### 装配过程

```
1. 查 strategy_award，获取所有奖品的概率列表

2. 计算 minAwardRate = min(所有奖品概率)
   rateRange = round(1 / minAwardRate)

3. 选择算法：
   rateRange ≤ 10000 → O1Algorithm
   rateRange > 10000 → OLogNAlgorithm

4. O1Algorithm 装配：
   for each award:
     for i in [0, rateRange * awardRate):
       table[i] = awardId
   Collections.shuffle(table)
   → Redis HSET strategy_rate_table_{strategyId} {index} {awardId}

5. 初始化奖品库存到 Redis：
   → Redis SET strategy_award_count_{strategyId}_{awardId} = awardCount

6. 如果策略有 rule_weight，对每个权重阈值分别装配子概率表
```

### 运行时抽奖（O(1)）

```
1. Redis INCRBY strategy_rate_range_seed {strategyId}
   → 每次调用自增，取模得到 index
2. Redis HGET strategy_rate_table_{strategyId} {index}
   → 直接返回 awardId，O(1)
```

---

## 流程六：奖品库存同步

奖品库存先扣 Redis，异步同步到 DB，防止高并发下 DB 成为瓶颈。

```
┌──────────────────────────────────────────────────────────────┐
│ 扣减（实时，在 Step 4.2 决策树内）                               │
│   Redis DECR strategy_award_count_{strategyId}_{awardId}     │
│   > 0 → 成功，放入延迟队列 RDelayedQueue                        │
│   = 0 → 库存耗尽，返回 ALLOW（不走后续树节点）                   │
└─────────────────────────┬────────────────────────────────────┘
                          ↓ 延迟队列
┌──────────────────────────────────────────────────────────────┐
│ 同步（异步，UpdateAwardStockJob，XXL-Job 定时）                  │
│   消费 RDelayedQueue，批量更新 strategy_award.award_count     │
└──────────────────────────────────────────────────────────────┘

好处：抽奖热路径只打 Redis，不打 DB
代价：短暂不一致（Redis 已扣，DB 还未更新），但最终一致
```

---

## 数据流与状态机总结

### UserRaffleOrder 状态机

```
create ──→ used    （抽奖成功，奖品已记录）
       ──→ failed  （抽奖失败，compensatePartakeQuota 补偿后）
       ──→ cancel  （活动取消）

queryNoUsedRaffleOrder 只返回 create 态订单
updateUserRaffleOrderStateFailed CAS: create → failed
```

### UserAwardRecord 状态机

```
create ──→ used   （IDistributeAward.giveOutPrizes 执行后）

DuplicateKeyException on INSERT → 幂等，忽略
```

### Task 状态机

```
create ──→ completed  （MQ 发送成功）
       ──→ failed     （MQ 发送多次失败后标记）

failed 状态的 task 会被 Job 重试
```
