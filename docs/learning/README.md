# Big Market 学习文档索引

本目录是 Big Market 微服务项目的学习材料，基于完成态代码编写，可直接对照代码阅读。

## 推荐学习顺序

**第一阶段：建立整体认知**

1. [03-architecture-overview.md](03-architecture-overview.md) - 整体架构与服务拓扑
2. [04-module-or-service-boundaries.md](04-module-or-service-boundaries.md) - 模块职责与服务边界
3. [13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md) - DDD 四层架构与设计模式（重点）

**第二阶段：理解业务流程**

4. [01-url-request-flows.md](01-url-request-flows.md) - 所有 URL 入口与请求流
5. [02-business-flows-and-diagrams.md](02-business-flows-and-diagrams.md) - 7 个核心业务机制
6. [12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md) - 抽奖策略算法详解（重点）

**第三阶段：理解技术实现**

7. [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md) - 高并发与幂等设计
8. [07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md) - 降级与回滚
9. [05-authentication-and-authorization.md](05-authentication-and-authorization.md) - 鉴权体系
10. [08-technical-stack.md](08-technical-stack.md) - 技术栈全览

**第四阶段：面试备考**

11. [11-key-design-decisions.md](11-key-design-decisions.md) - 7 个关键设计决策与理由（面试必读）
12. [14-interview-qa.md](14-interview-qa.md) - 20 道高频面试题 Q&A（面试必读）

**参考工具**

- [09-code-map.md](09-code-map.md) - 代码跳转地图
- [10-troubleshooting.md](10-troubleshooting.md) - 常见问题排查
- [00-learning-guide.md](00-learning-guide.md) - 学习路径总览

## 项目核心信息

| 维度 | 内容 |
|------|------|
| 语言/框架 | Java 8 + Spring Boot 2.7.12 |
| 架构 | DDD 四层 + 10 个微服务 |
| 核心技术 | Dubbo、Nacos、RabbitMQ、Redis/Redisson、MySQL、XXL-Job、ES |
| 关键设计 | 责任链+规则树抽奖、Outbox 消息可靠投递、分库分表、适配器灰度切换 |

关键代码入口：

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/AbstractRaffleStrategy.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`
