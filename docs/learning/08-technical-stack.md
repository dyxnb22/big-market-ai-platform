# 08 技术栈

| 技术 | 证据 | 用途 |
| --- | --- | --- |
| Java 8 | `pom.xml` | 主语言 |
| Spring Boot 2.7.12 | `pom.xml` | 服务运行时 |
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
| JWT | `AuthService` | 用户/管理员鉴权 |
| Micrometer Prometheus | actuator 配置 | 指标 |
| Grafana | `docs/dev-ops/grafana` | 仪表盘学习环境 |
| Docker Compose | `docker-compose.yml` | 本地运行时 |
| HTML/CSS/JS | `big-market-web/` | 用户端与管理端前端（原生，非 React） |
| DOMPurify | `big-market-web/index.html` + `app.js` | Chatbot 消息 HTML 净化 |
| localStorage | `big-market-web/app.js` | 本地对话、抽奖记录、积分流水 |
| Playwright | `tests/e2e` | 前端/API 流程测试 |

## MQ 与 XXL-Job 重点阅读

建议一起阅读：

- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/CreditAdjustSuccessConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/XxlJobConfig.java`

## 未作为核心机制使用的技术

代码库未实现 service mesh、Kafka、OAuth2 授权服务器或 Seata/TCC 分布式事务框架。Hystrix 为旧依赖残留，活跃网关熔断使用 Resilience4j。前端未使用 React/Vue 等 SPA 框架；`big-market-web` 为桌面/Web 优先的静态页面，无独立移动端导航。
