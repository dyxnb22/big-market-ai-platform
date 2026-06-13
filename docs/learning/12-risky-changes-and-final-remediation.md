# 12 高风险整改计划与最终整改

## 原则

本轮没有直接启用高风险代码路径。原因是当前仓库显示多个高风险能力处于暗启动/脚手架阶段，且需要数据库 DDL、完整基础设施和端到端压测验证。贸然改动会改变核心交易、额度、积分和发奖链路。

## Risky Change 1：启用远程额度扣减

- Issue: `account.service.remote-quota-decrement.enabled` 默认 false。
- Why high-risk: 抽奖主链路依赖额度扣减；远程化后涉及跨服务一致性和回滚。
- Evidence: `RaffleApplicationService.executeDraw` 中根据 flag 选择本地补偿或 `activityAccountPort.rollbackQuota`。
- Impacted files: `RaffleApplicationService`、account-service provider、activity account port、proposed quota ledger SQL。
- Expected before: 本地 `ActivityRepository.saveCreatePartakeOrderAggregate` 在同一事务内扣额度和写抽奖单。
- Expected after: account-service 负责幂等扣减/回滚，market-service 写抽奖单。
- Rollback plan: flag 设回 false，回到本地事务路径。
- Verification plan: 应用 ledger DDL，跑并发抽奖、失败补偿、重复请求、服务异常回滚。
- Implementation decision: Not implemented due to verification risk.

## Risky Change 2：启用 award credit outbox

- Issue: `account.award-credit-outbox.enabled` 默认 false。
- Why high-risk: 发奖积分入账从同步事务改成异步 outbox。
- Evidence: `AwardRepository.saveGiveOutPrizesAggregate` flag 分支，`DispatchCreditAwardTaskJob` 条件启用，`docs/sql/proposed-credit-award-task-outbox.sql`。
- Impacted business flows: 发奖、积分账户、中奖记录完成态。
- Expected before: 发奖积分和中奖记录状态在本地事务路径处理。
- Expected after: 写 `credit_award_task`，XXL job 异步派发。
- Rollback plan: flag false，停止 job，回到 direct local credit write。
- Verification plan: DDL、重复消息、job 重试、积分幂等、中奖记录状态。
- Implementation decision: Not implemented due to missing applied DDL/runtime verification.

## Risky Change 3：服务数据所有权拆分

- Issue: 多个服务共享 `big-market-infrastructure` DAO/repository。
- Why high-risk: 需要迁移依赖方向、拆 mapper、拆 SQL、调整事务边界。
- Evidence: account/rebate/strategy/fulfillment/message-job service POM 都依赖 infrastructure。
- Expected before: 多服务共享同一仓储实现。
- Expected after: 每个服务只访问自己拥有的数据。
- Rollback plan: 保留共享 jar 路径直到每个服务单独验收。
- Verification plan: 编译、契约测试、迁移脚本、回归主业务。
- Implementation decision: Not implemented; future architecture work.

## Risky Change 4：DLQ 自动重放

- Issue: DLQ consumer 只 log，不重放。
- Why high-risk: 自动重放可能造成重复发奖/重复入账，必须先证明所有消费者幂等。
- Evidence: `RabbitMQDlqConfig` 的 `on*Dlq` 只记录日志。
- Implementation decision: Not implemented; future improvement only.

## Risky Change 5：真实用户体系

- Issue: 登录凭据来自 `app.auth.dev-users`。
- Why high-risk: 需要新增用户表、密码哈希、注册/登录策略、迁移前端和测试。
- Evidence: `AuthAccessController.isValidCredential` 解析配置字符串。
- Implementation decision: Not implemented; product/安全需求未在当前代码中定义。

## Final remediation result

- Safe fixes completed: 文档结构和内容整改，错误/过时学习结论清理。
- High-risk fixes completed: None.
- High-risk fixes intentionally not applied: 以上 5 项。
- Current stable state: 学习文档如实标注当前代码已经实现、部分实现、未实现和仅建议的内容。

