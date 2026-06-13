# 08 技术栈

| 技术 | 证据 | 出现位置 | 用途 | 学习重点 |
| --- | --- | --- | --- | --- |
| Java 8 | 父 `pom.xml` `java.version=1.8` | 全仓库 | 主开发语言 | 语法、日期、并发基础 |
| Spring Boot 2.7.12 | 父 `pom.xml` parent | 所有服务 | Web/配置/启动 | 自动配置、profile、actuator |
| Spring Web MVC | 各 service pom | Controller | HTTP API | `@RestController`、`@RequestMapping` |
| Spring Cloud Gateway | `big-market-gateway` | 网关 | 路由、熔断、过滤器 | route predicates/filter |
| Resilience4j CircuitBreaker | gateway yml | 网关 | 下游失败熔断/fallback | 配置参数和 fallback |
| MyBatis | pom + mapper XML | infrastructure/service resources | DAO 持久化 | Mapper 接口/XML |
| MySQL | application yml + SQL | docs/dev-ops/mysql/sql | 业务库、分库 | 表结构、唯一索引 |
| HikariCP | DB Router starter/yml | 数据源 | 连接池 | pool 配置 |
| 自研 mini-db-router | `big-market-starter-db-router` | repository | 分库分表路由 | `dbRouter.doRouter(userId)` |
| Redis/Redisson | pom/yml/RedissonService | 缓存、锁、延迟队列 | 库存、账户锁、任务锁 | 原子扣减、RLock |
| RabbitMQ | pom/yml/listener | MQ | 返利、发奖、积分发货、库存清零 | listener、DLQ |
| XXL-Job | pom/job classes | job | 补偿任务、库存同步 | `@XxlJob` |
| Dubbo | pom/provider classes | RPC | 服务化接口 | `@DubboService`、`@DubboReference` |
| Nacos | pom/yml | 注册/配置同步 | Dubbo registry、配置同步 | registry address |
| Elasticsearch SQL JDBC | yml + ES mapper | 运营查询 | 查询用户抽奖单 | ES SQL mapper |
| JWT jjwt | pom + AuthService | auth | 登录 token | claims、jti、exp |
| Fastjson/Fastjson2 | imports | JSON | 日志/MQ 解析 | DTO 序列化 |
| Guava RateLimiter | starter-ratelimiter | 限流 starter | 注解限流 | 当前使用点有限 |
| Micrometer Prometheus | pom/yml/job `@Timed` | 监控 | 指标暴露 | actuator prometheus |
| Docker Compose | `docker-compose.yml` | 部署 | 本地/容器运行 | 服务端口和依赖 |
| Playwright | `package.json`、`tests/e2e` | E2E | 前端/API 权限流测试 | `npm test` |

## 未发现或不可误写的技术

- 完整服务网格：Not found in current code.
- Kafka：Not found in current code.
- OAuth2 授权服务器：Not found in current code.
- Seata/TCC 分布式事务：Not found in current code.
- 真实 Hystrix 使用：父依赖中有 Hystrix，但未发现 `@HystrixCommand` 使用；当前真实熔断在 gateway Resilience4j。

