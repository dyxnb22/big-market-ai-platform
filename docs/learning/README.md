# Big Market Final-State Learning Notes

This directory explains the finished local microservices learning project. The
notes use the current repository as evidence and avoid migration diary wording.

## Reading Order

1. [03-architecture-overview.md](03-architecture-overview.md) - 总体架构
2. [01-url-request-flows.md](01-url-request-flows.md) - 请求链路
3. [02-business-flows-and-diagrams.md](02-business-flows-and-diagrams.md) - 领域模型
4. [04-module-or-service-boundaries.md](04-module-or-service-boundaries.md) - 服务边界
5. [00-learning-guide.md](00-learning-guide.md) - 数据与任务学习路线
6. [08-technical-stack.md](08-technical-stack.md) - MQ/XXL-Job 和技术栈
7. [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md) - 幂等与一致性
8. [07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md) - 降级与回滚
9. [10-problems-and-fixes.md](10-problems-and-fixes.md) 与 [11-review-rounds-and-final-check.md](11-review-rounds-and-final-check.md) - 监控、排查和验收
10. [09-code-map.md](09-code-map.md) - 代码地图

## Core Conclusion

The project is a Java 8 + Spring Boot 2.7.12 multi-module microservices learning
system. Runtime services include gateway, auth, admin, market, chatbot,
message-job, account, fulfillment, rebate, and strategy. Shared modules provide
domain models, repository adapters, API contracts, common types, DB routing,
DCC, and rate limiting.

Important code paths:

- `pom.xml`
- `docker-compose.yml`
- `big-market-gateway/src/main/resources/application.yml`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http`
- `big-market-domain/src/main/java/com/dyx/market/domain`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job`

Local completion is verified by Maven build, smoke tests, runtime guardrail
scripts, and the ability to explain raffle, credit, rebate, award, strategy,
message, and rollback flows from code.
