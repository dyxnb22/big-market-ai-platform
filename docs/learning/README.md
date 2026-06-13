# Big Market 学习手册索引

本目录是基于当前仓库代码、配置、脚本、SQL 和测试整理的学习手册。结论只写已发现证据；未发现的能力会明确写 `Not found in current code.`；仅作为建议的内容会明确写 `No real implementation found. This is only a future improvement recommendation.`

## 最终阅读顺序

1. [00-learning-guide.md](00-learning-guide.md) - 一天学习路线和全局入口
2. [03-architecture-overview.md](03-architecture-overview.md) - 先看运行形态和架构图
3. [04-module-or-service-boundaries.md](04-module-or-service-boundaries.md) - 再看模块/服务边界
4. [01-url-request-flows.md](01-url-request-flows.md) - 按 URL 进入代码
5. [02-business-flows-and-diagrams.md](02-business-flows-and-diagrams.md) - 按业务流程理解系统
6. [05-authentication-and-authorization.md](05-authentication-and-authorization.md) - 学登录、JWT、用户/管理员鉴权
7. [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md) - 学库存、额度、幂等、锁、任务
8. [07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md) - 学失败、补偿、降级、网关熔断
9. [08-technical-stack.md](08-technical-stack.md) - 技术栈和证据
10. [09-code-map.md](09-code-map.md) - 关键代码地图
11. [10-problems-and-fixes.md](10-problems-and-fixes.md) - 问题分类与整改结果
12. [11-review-rounds-and-final-check.md](11-review-rounds-and-final-check.md) - 迭代复核记录
13. [12-risky-changes-and-final-remediation.md](12-risky-changes-and-final-remediation.md) - 高风险整改计划和结论

## 当前仓库的核心结论

- 这是一个 Java 8 + Spring Boot 2.7.12 的多模块 Maven 项目。
- 当前代码同时保留 `big-market-app` 传统一体化启动器，以及 `big-market-gateway`、`big-market-auth-service`、`big-market-admin-service`、`big-market-chatbot-service`、`big-market-market-service`、`big-market-message-job-service`、`big-market-account-service`、`big-market-fulfillment-service`、`big-market-rebate-service`、`big-market-strategy-service` 等服务化启动器。
- 业务主线是营销抽奖活动：活动上架/装配、用户签到返利、积分账户、SKU 兑换抽奖次数、抽奖、中奖记录、发奖、运营查询、AI Chat 消耗积分。
- 代码中存在真实的 JWT 登录校验、用户 Token 拦截、管理员 Token/JWT 校验、默认凭据启动保护。
- 代码中存在 Redis/Redisson 缓存与锁、RabbitMQ 消息、XXL-Job 任务、MyBatis 持久化、分库分表路由、Dubbo/Nacos 服务化接口、Spring Cloud Gateway + Resilience4j 网关熔断。
- 部分服务化能力是暗启动/脚手架状态，默认配置仍走本地进程内适配器；这些能力在文档中标为“部分实现”或“未来启用前需验证”。

## 图表覆盖

- 架构图：[03-architecture-overview.md](03-architecture-overview.md)
- URL 流程图和时序图：[01-url-request-flows.md](01-url-request-flows.md)
- 业务流程图和状态图：[02-business-flows-and-diagrams.md](02-business-flows-and-diagrams.md)
- 高并发图：[06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md)
- 失败/降级图：[07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md)

