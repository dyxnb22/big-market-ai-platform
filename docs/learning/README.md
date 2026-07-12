# Big Market 学习文档索引

本目录是 Big Market 微服务项目的学习材料，基于 2026-07-11 的**有条件冻结**工作树编写。先看 [`../LEARNING-FREEZE.md`](../LEARNING-FREEZE.md) 了解已验证与未验证边界。

## 权威源（避免重复维护）

| 主题 | 以何为准 |
| --- | --- |
| 就绪 / 验收证据 | [`../LEARNING-FREEZE.md`](../LEARNING-FREEZE.md) |
| 服务端口、流程、部署默认 | [`../MICROSERVICES.md`](../MICROSERVICES.md) |
| Outbox / 幂等 / 积分发奖 | [`../data-and-outbox.md`](../data-and-outbox.md) |
| 代码跳转 | [09-code-map.md](09-code-map.md) |
| 十步路径 + 完成标准 | [00-learning-guide.md](00-learning-guide.md) |
| 历史 BM 整改 | [`../audit-remediation-plan.md`](../audit-remediation-plan.md)（**非**当前状态） |

**本目录文档均已译为中文。** 与代码冲突时以代码 / compose / SQL 为准。归档英文维护文档见 `archive/`。

## 推荐学习顺序

### 第零阶段：动手启动

1. [16-local-setup.md](16-local-setup.md)

### 第一阶段：整体认知

1. [03-architecture-overview.md](03-architecture-overview.md)（图 + 前端；服务表链到 MICROSERVICES）
2. [04-module-or-service-boundaries.md](04-module-or-service-boundaries.md)
3. [13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md)

### 第二阶段：业务流程

1. [01-url-request-flows.md](01-url-request-flows.md)
2. [02-business-flows-and-diagrams.md](02-business-flows-and-diagrams.md)
3. [12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md)
4. [17-canal-es-sync.md](17-canal-es-sync.md)（可选，配合运营查询）

### 第三阶段：技术实现

1. [15-data-model.md](15-data-model.md) → 再读 `data-and-outbox.md`
2. [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md)
3. [07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md)
4. [05-authentication-and-authorization.md](05-authentication-and-authorization.md)
5. [08-technical-stack.md](08-technical-stack.md)

### 第四阶段：面试备考

1. [18-resume-project-deep-dive.md](18-resume-project-deep-dive.md) — 口述框架（含默认 Docker 二级 outbox）
2. [11-key-design-decisions.md](11-key-design-decisions.md)
3. [14-interview-qa.md](14-interview-qa.md)
4. [19-resume-code-walkthrough-and-interview.md](19-resume-code-walkthrough-and-interview.md) — 走读 / 追问（不重复 18 的长叙述）

### 参考工具

- [00-learning-guide.md](00-learning-guide.md)
- [09-code-map.md](09-code-map.md)
- [10-troubleshooting.md](10-troubleshooting.md)
- [archive/](archive/)

## 项目核心信息

| 维度 | 内容 |
| --- | --- |
| 语言/框架 | Java 8 + Spring Boot 2.7.12 |
| 前端 | `big-market-web`：原生 HTML/CSS/JS（非 React） |
| 架构 | DDD 分层 + 微服务启动器；rebate/strategy 默认 embedded |
| 核心技术 | Dubbo、Nacos、RabbitMQ、Redis/Redisson、MySQL、XXL-Job、ES（可选） |
| 关键设计 | 责任链+规则树抽奖、Outbox、分库分表、适配器切换 |

关键代码入口：`big-market-web/app.js`、`RaffleActivityController`、`RaffleApplicationService`、`DispatchCreditAwardTaskJob`（完整列表见 09）。
