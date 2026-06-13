# 05 微服务拆分历程

## 为什么拆分

单体 `big-market-app` 随着业务增长面临的问题：

- **部署耦合**：改一处，整体重新部署
- **扩容粒度粗**：高峰期只有抽奖慢，但整体都得扩
- **团队协作冲突**：多人改同一个仓库，合并频繁冲突
- **技术债累积**：一个模块的技术选型影响所有模块

---

## 拆分原则

**按业务边界划分**，而不是按技术层划分（不能把所有 Controller 拆一个服务、所有 Service 拆一个）。

本项目的边界依据：

1. **数据所有权**：哪些表归哪个服务管（见 `docs/microservices-dao-ownership.md`）
2. **业务内聚**：账户操作（credit/quota）内聚，策略操作内聚，返利操作内聚
3. **变更频率**：策略规则变动频繁，应独立于活动管理
4. **伸缩需求**：抽奖热路径（strategy + market）和发奖冷路径（fulfillment）伸缩需求不同

---

## 八个阶段

### Phase 1：运行时拆分（完成）

目标：让各服务能独立部署、独立启动。

```
做了什么：
  - 创建 11 个独立可部署的 Spring Boot 服务模块
  - 公共库（domain、infrastructure、trigger）以 JAR 依赖方式复用
  - 配置 Dubbo 提供者注册（account/fulfillment/rebate/strategy 暗启动）
  - 配置 Nacos 服务发现
  - 配置 API Gateway 路由规则

没做什么：
  - 流量未切换（全走 market-service 本地路径）
  - 数据库未隔离（所有服务仍共享同一 schema）
```

### Phase 2：账户/履约服务暗启动（Repo 就绪）

目标：准备 account-service 和 fulfillment-service 的适配器，让代码路径就绪但不开流量。

```
account-service 拥有：
  user_credit_account, user_credit_order
  raffle_activity_account, raffle_activity_account_day, raffle_activity_account_month
  raffle_quota_decrement_ledger, credit_award_task

fulfillment-service 拥有：
  award, user_award_record

Flag（全部默认 false）：
  account.service.remote-credit-write.enabled
  account.service.remote-quota-decrement.enabled
  account.fulfillment.remote-award.enabled
```

### Phase 3：返利服务（Repo 就绪）

```
rebate-service 拥有：
  daily_behavior_rebate, user_behavior_rebate_order

适配器：IRebateOrderAdapter, IRebateReadAdapter
  本地默认：LocalRebateOrderAdapter/LocalRebateReadAdapter
  远程暗启动：通过 rebate-service Dubbo 提供者

Flag：
  rebate.service.remote-create-order.enabled
  rebate.service.remote-read.enabled
  rebate.legacy-rpc-provider.enabled  ← 控制旧 RPC 是否还提供服务
```

### Phase 4：策略服务（Repo 就绪）

```
strategy-service 拥有：
  strategy, strategy_award, strategy_rule
  rule_tree, rule_tree_node, rule_tree_node_line

读优先策略：先把读流量切过去，决策流量后续再切

Flag：
  strategy.service.remote-read.enabled
  strategy.legacy-rpc-provider.enabled
```

### Phase 5：活动服务脚手架（设计完成）

```
activity-service 负责：活动参与、配额管理、抽奖编排
  但是：draw 执行逻辑目前仍在 market-service 中

原因：draw 路径依赖 strategy/account/fulfillment 的协同，
      需要等这三个服务的外部证据通过后，才能安全移动。

脚手架做了什么：
  - 定义了 IDrawOutboxPort 端口接口
  - IAwardFulfillmentPort 实现本地适配器
  - RaffleApplicationService 已通过端口解耦，未来可切换
```

### Phase 6：DAO 所有权矩阵（完成）

AL-1 到 AL-11：11 个跨域 DAO 耦合点全部通过端口隔离。

```
AL-1: StrategyRepository → IRaffleActivityDao
      解决：IStrategyActivityMappingPort（strategy 通过端口查 activityId）

AL-2/3: StrategyRepository → IRaffleActivityAccountDao/Day
         解决：IStrategyActivityAccountPort

AL-4: ActivityRepository → IUserCreditAccountDao
      解决：移到 account 域管理

AL-8/9/10: BehaviorRebateRepository/CreditRepository/AwardRepository → ITaskDao
           解决：各自的 per-domain outbox port（IRebateTaskOutboxPort 等）
```

### Phase 7：数据与 Outbox 边界（完成）

```
Per-domain outbox 表（替代共享 task 表）：
  rebate_task_outbox      （归 rebate-service）
  credit_trade_task_outbox（归 account-service）
  award_dispatch_task_outbox（归 fulfillment-service）

DDL 脚本在 docs/sql/proposed-*.sql（未执行，EXTERNAL-GATED）

DB 用户规划（最小权限）：
  big_market_account_rw      → account 相关表
  big_market_fulfillment_rw  → award 相关表
  big_market_rebate_rw       → rebate 相关表
  big_market_strategy_ro     → strategy 表（只读）
  big_market_market_compat_rw → 兼容期只读
```

### Phase 8：切换就绪包（Repo 就绪，外部门控）

```
就绪内容：
  - 切换冲突矩阵（防止双路径同时开）
  - 幂等与回滚矩阵（每个操作的唯一键和回滚手段）
  - 切换运行手册（切换顺序、检查点、回滚步骤）

外部门控（学习项目中无法模拟的真实环境步骤）：
  DBA: DDL 验证、DB 授权、分片检查
  Ops: Dubbo 注册、Nacos 配置推送、MQ Topic 创建
  Engineering: Staging Canary 验证、回滚演练
  Oncall: 监控大盘、告警规则设置
  Product: GO/NO-GO 决策
```

---

## 服务间通信

### 同步通信（Dubbo RPC）

```
调用方（market-service）
  ↓ @Reference（Dubbo Consumer）
  ↓ Nacos 服务发现
  ↓ Dubbo 协议
  ↓ @Service（Dubbo Provider）
被调用方（account-service / rebate-service / strategy-service 等）

特点：请求-响应，同步等待，有超时设置
用于：配额查询、策略决策等需要立即返回结果的操作
```

### 异步通信（RabbitMQ）

```
发布方（market-service 或 message-job-service）
  ↓ eventPublisher.publish(topic, message)
  ↓ RabbitMQ Exchange → Queue
  ↓
消费方（message-job-service 中的 Consumer）
  ├── SendAwardConsumer      发奖
  ├── RebateMessageConsumer  返利结算
  └── CreditAdjustSuccessConsumer 积分确认

特点：解耦，消费方故障不影响发布方
用于：发奖、返利等对延迟不敏感的副作用操作
```

### 消息主题（Topic）配置

```yaml
spring:
  rabbitmq:
    topic:
      send_award:  send_award       # 发奖 topic
      send_rebate: send_rebate      # 返利 topic
      credit_adjust_success: credit_adjust_success  # 积分调整确认
      activity_sku_stock_zero: activity_sku_stock_zero  # SKU 库存耗尽
```

---

## 暗启动（Dark Launch）模式

所有新服务的切换都遵循同一模式：

```
Stage 1: Deploy（部署但不开流量）
  新服务部署上线
  Dubbo 提供者注册到 Nacos
  但消费者 flag=false，流量仍走本地

Stage 2: Shadow（影子流量验证）
  选部分请求同时打本地和远程
  比较结果一致性

Stage 3: Canary（金丝雀，1% 流量）
  flag=true for 1% users
  观察错误率、延迟、业务指标

Stage 4: Ramp（逐步放量）
  10% → 30% → 100%
  每步观察 24h 稳定后才放量

Stage 5: Legacy Disable（关闭旧路径）
  7 天稳定后关闭 legacy provider
  30 天后删除兼容代码
```

---

## 面试怎么讲这部分

**问：你们微服务是怎么拆的？**

> 我们按业务边界拆，以数据所有权为主要依据。比如积分账户的所有读写都归 account-service，发奖记录的所有读写都归 fulfillment-service，通过端口接口隔离跨域访问。每个新服务都是先暗启动——代码路径就绪但 flag 默认关闭，等 DBA 做了 schema 隔离、Ops 验证了 Dubbo 注册、Engineering 跑了 staging canary 之后，才逐步切流量。

**问：拆分过程中怎么保证不影响线上？**

> 用端口+适配器模式，所有新服务路径都有本地兜底适配器。flag=false 时行为和之前完全一样，改变只是一行配置，无需重新部署。切换冲突矩阵记录了哪些 flag 不能同时为 true（防止双路径），幂等矩阵记录了每个操作的回滚手段。

**问：数据库怎么隔离的？**

> 目前是逻辑隔离（端口层面），物理隔离需要 DBA 建独立 DB 用户、最小权限授权，并把提议的 outbox 表（rebate_task_outbox 等）建好，这部分在我的 DDL 脚本和运行手册里有记录，但没有执行，因为学习环境没有独立的 staging 数据库。
