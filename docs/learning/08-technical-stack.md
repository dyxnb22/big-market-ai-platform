# 08 技术栈

| 技术 | 证据 | 用途 |
| --- | --- | --- |
| Java 17 | `pom.xml` (`java.version`) | 主语言（当前基线；历史为 Java 8） |
| Spring Boot 3.5.16 | `pom.xml` | 服务运行时（当前基线；历史为 2.7.12） |
| Spring Web MVC | service controllers | HTTP API |
| Spring Cloud Gateway | `big-market-gateway/pom.xml` | API 网关 |
| Resilience4j | gateway config | 熔断响应 |
| Dubbo | provider 类与 `big-market-api` | RPC 契约 |
| Nacos | 服务配置与 dev compose | 注册/配置中心 |
| MyBatis | mapper XML 与 DAO 接口 | 持久化 |
| MySQL | `docs/dev-ops/mysql/sql` | 业务存储 |
| Redis/Redisson | Redis 配置与 `RedissonService` | 缓存、锁、计数器 |
| RabbitMQ | listeners 与 Rabbit 配置 | 异步消息 |
| XXL-Job | job 配置与 `@XxlJob` 处理器 | 定时补偿 |
| Elasticsearch | ES mapper 与 dev compose | 运营查询模型 |
| JWT | `AuthService` / jjwt 0.11.x | 用户/管理员鉴权 |
| big-market-starter-web | `TraceIdFilter`、`CorsAutoConfiguration`（基于 `CorsSettings`）、`RedissonSingleServerSupport`、线程池优雅关闭 | 全链路 trace、统一 CORS、Redis 连接复用 |
| `CorsSettings`（types） | 网关 `CorsConfig` 与 starter-web 共用来源解析 | 避免 CORS 规则漂移 |
| big-market-starter-data | `ThreadPoolAutoConfiguration` | 各服务共用异步线程池配置 |
| 网关限流 | `RateLimiterConfig`（内存令牌桶） | IP+路径前缀限流 |
| Micrometer Prometheus | actuator 配置（`show-details: when_authorized`） | 指标 |
| Grafana | `docs/dev-ops/grafana` | 仪表盘学习环境 |
| Docker Compose | `docker-compose.yml` | 本地运行时 |
| HTML/CSS/JS | `big-market-web/` | 用户端与管理端前端（原生，非 React） |
| DOMPurify | `big-market-web/index.html` + `app.js` | Chatbot 消息 HTML 净化（CDN 不可用时回退纯文本） |
| localStorage | `big-market-web/app.js` | 本地对话、抽奖记录、积分流水 |
| Playwright | `tests/e2e` | 前端/API 流程测试 |

## MQ 与 XXL-Job 重点阅读

源码在 `big-market-trigger`，**Bean 仅在 message-job-service 激活**。完整跳转表见 [09-code-map.md](09-code-map.md)「Tasks, Outbox, And Operations」；运维检查见 [`../operations-checklist.md`](../operations-checklist.md)；handler 目录见 [`../xxl-job-handlers.md`](../xxl-job-handlers.md)。

核心入口：`SendAwardConsumer`、`SendMessageTaskJob`、`DispatchCreditAwardTaskJob`、`XxlJobConfig`。

## 未作为核心机制使用的技术

代码库未实现 service mesh、Kafka、OAuth2 授权服务器或 Seata/TCC 分布式事务框架。Hystrix 为旧依赖残留，活跃网关熔断使用 Resilience4j。前端未使用 React/Vue 等 SPA 框架；`big-market-web` 为桌面/Web 优先的静态页面，无独立移动端导航。
