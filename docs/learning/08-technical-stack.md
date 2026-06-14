# 08 Technical Stack

| Technology | Evidence | Purpose |
| --- | --- | --- |
| Java 8 | `pom.xml` | Main language |
| Spring Boot 2.7.12 | `pom.xml` | Service runtime |
| Spring Web MVC | service controllers | HTTP APIs |
| Spring Cloud Gateway | `big-market-gateway/pom.xml` | API gateway |
| Resilience4j | gateway config | Circuit breaker responses |
| Dubbo | provider classes and `big-market-api` | RPC contracts |
| Nacos | service configs and dev compose | Registry/config center |
| MyBatis | mapper XML and DAO interfaces | Persistence |
| MySQL | `docs/dev-ops/mysql/sql` | Business storage |
| Redis/Redisson | Redis configs and `RedissonService` | Cache, locks, counters |
| RabbitMQ | listeners and Rabbit configs | Async messages |
| XXL-Job | job config and `@XxlJob` handlers | Scheduled compensation |
| Elasticsearch | ES mappers and dev compose | Operational query model |
| JWT | `AuthService` | User/admin auth |
| Micrometer Prometheus | actuator config | Metrics |
| Grafana | `docs/dev-ops/grafana` | Dashboard learning setup |
| Docker Compose | `docker-compose.yml` | Local runtime |
| Playwright | `tests/e2e` | Frontend/API flow tests |

## MQ And XXL-Job Focus

Read these files together:

- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/CreditAdjustSuccessConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/XxlJobConfig.java`

## Technology Not Used As A Core Mechanism

The codebase does not implement a service mesh, Kafka, OAuth2 authorization
server, or Seata/TCC distributed transaction framework. Hystrix appears as an
old dependency, while the active gateway circuit breaker is Resilience4j.
