# 00 学习指南

## 这个项目解决什么问题

当前仓库实现的是“大营销抽奖平台”。从代码证据看，核心业务是用户围绕营销活动获取/消耗积分和抽奖次数，然后参与抽奖、生成中奖记录并触发发奖。辅助能力包括后台配置、运营查询、动态配置、AI Chat 积分消费、消息补偿、库存异步落库和服务化拆分暗启动。

证据：

- 业务入口：`RaffleActivityController`、`RaffleStrategyController`、`ErpOperateController`、`AdminConfigController`、`ChatbotController`、`AuthAccessController`
- 领域包：`big-market-domain/src/main/java/com/dyx/market/domain/activity|strategy|award|credit|rebate|auth|task`
- 持久化包：`big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository`
- 服务启动器：各 `*ServiceApplication.java` 和 `Application.java`

## 一天学习计划

### 上午：先建立骨架

1. 读 `pom.xml`，理解父工程和模块列表。
2. 读 [03-architecture-overview.md](03-architecture-overview.md)，画出脑内运行图。
3. 读 [04-module-or-service-boundaries.md](04-module-or-service-boundaries.md)，区分“共享 jar 模块”和“可独立启动服务”。
4. 打开 `docker-compose.yml` 和 `big-market-gateway/src/main/resources/application.yml`，理解端口和路由。

### 中午：按 URL 进入主链路

1. 从 `POST /api/v1/auth/login` 看 JWT 生成。
2. 从 `POST /api/v1/raffle/activity/draw_by_token` 进入抽奖主链路。
3. 从 `POST /api/v1/raffle/activity/calendar_sign_rebate_by_token` 看签到返利。
4. 从 `POST /api/v1/raffle/activity/credit_pay_exchange_sku_by_token` 看积分兑换次数。
5. 从 `POST /api/v1/chatbot/ask` 看 AI Chat 和积分扣减/退还。

### 下午：读业务和技术机制

1. 读 [02-business-flows-and-diagrams.md](02-business-flows-and-diagrams.md)，把业务状态和数据写入串起来。
2. 读 [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md)，重点理解 Redis 原子扣减、Redisson 锁、唯一索引幂等、分库路由。
3. 读 [07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md)，理解补偿、任务表、DLQ、网关熔断。
4. 读 [09-code-map.md](09-code-map.md)，按清单跳到关键类。

### 晚上：看问题和整改

1. 读 [10-problems-and-fixes.md](10-problems-and-fixes.md)，知道哪些是已整改、哪些是保留风险。
2. 读 [11-review-rounds-and-final-check.md](11-review-rounds-and-final-check.md)，理解本次文档稳定性。
3. 读 [12-risky-changes-and-final-remediation.md](12-risky-changes-and-final-remediation.md)，知道哪些高风险改动不能贸然开启。

## 学习时不要误解的点

- 不要把 `big-market-domain` 当成独立服务；它是共享领域 jar。
- 不要把所有 `*-service` 都理解为已经承接真实流量；部分 Dubbo 服务和远程适配器默认 feature flag 是 false。
- 不要把 Hystrix 依赖当成真实熔断实现；当前真实网关熔断证据来自 `big-market-gateway` 的 Spring Cloud Gateway CircuitBreaker/Resilience4j 配置。
- 不要把 `@Scheduled` 注释当成正在运行的本地定时任务；实际启用注解是 `@XxlJob`。

