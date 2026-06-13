# Big Market AI Platform

这是从原始面试复习项目复制出来的产品化版本，原项目仍保留在：

```text
/Users/diaoyuxuan/IdeaProjects/big-market
```

当前版本已经完成第一轮"可学习、可运行、可继续重构"的补全：

- Java 包名统一为 `com.dyx.market`，不再使用原作者命名空间。
- 内置 `big-market-starter-db-router`、`big-market-starter-dcc`、`big-market-starter-ratelimiter`，便于学习 DB 路由、动态配置和限流实现。
- 新增 `big-market-management`，提供本地持久化配置能力。
- 新增 `big-market-web/index.html`，提供轻量用户端 + 管理端联调页面。
- 登录/管理/Chatbot Controller 已分别内聚到 `big-market-auth-service`、`big-market-admin-service`、`big-market-chatbot-service`，不再是独立薄模块。
- ES 读模型接口（`IESUserRaffleOrderRepository`）已迁入 `big-market-domain`。

---

## Microservices Architecture (Current Portfolio State)

The monolith has been progressively split into independently deployable
service modules behind an API gateway. The original `big-market-app` remains
as a local legacy fallback/reference launcher.

This repository is currently in **Phase 8 cutover readiness** for learning and
portfolio purposes: repo-only gates are green, remote/outbox/cutover flags
default to `false`, and real staging/production evidence remains
`EXTERNAL-GATED`.

### Services

| Service | Port | Responsibility |
|---------|-----:|----------------|
| big-market-gateway | 8080 | Gateway routing to all backend services |
| big-market-auth-service | 8081 | Login, JWT issuing, token verification |
| big-market-admin-service | 8082 | Admin configuration APIs |
| big-market-market-service | 8083 | Core marketing / raffle / activity APIs + Dubbo RPC |
| big-market-chatbot-service | 8084 | Chatbot APIs |
| big-market-message-job-service | 8085 | MQ consumers + XXL-Job scheduled handlers |
| big-market-account-service | 8086 | Dark-launch Dubbo provider for credit + quota operations |
| big-market-fulfillment-service | 8087 | Dark-launch Dubbo provider for award fulfillment and credit-award outbox |
| big-market-rebate-service | 8088 | Dark-launch Dubbo provider for rebate read/write paths |
| big-market-strategy-service | 8089 | Dark-launch Dubbo provider for strategy reads |

Shared library modules (`big-market-domain`, `big-market-infrastructure`, `big-market-api`, `big-market-types`, starter modules) are reused as JAR dependencies.

### Infrastructure

| Component | Host Port | Purpose |
|-----------|----------:|---------|
| MySQL | 13306 | Database (root/123456) |
| Redis | 16379 | Cache |
| RabbitMQ | 5672 / 15672 | MQ / Management UI (admin/admin) |
| Nacos | 8848 / 9848 | Service registry / config center |
| Elasticsearch | 9200 / 9300 | Search |
| XXL-Job Admin | 9090 | Job scheduler (admin/123456) |
| Prometheus | 9091 | Metrics scraping |
| Grafana | 4000 | Metrics dashboard |
| phpMyAdmin | 8899 | MySQL UI |
| Redis Admin | 18081 | Redis UI (admin/admin) |
| Canal Adapter | 18082 | MySQL → ES sync |

### Prerequisites

- JDK 8
- Maven 3.x
- Docker + Docker Compose v2

### Build

```bash
mvn clean package -DskipTests
```

### Start Local Environment

```bash
# 1. Start the full infrastructure stack
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d

# 2. Start the current microservices stack
docker compose up --build -d
```

### Verify

```bash
# Check all containers are running
docker compose -f docs/dev-ops/docker-compose-environment.yml ps
docker compose ps

# Run smoke test
./scripts/smoke-test-phase-1.sh

# Run full orchestrated validation (build → infra → app → smoke test)
./scripts/validate-microservices-stack.sh

# Check runtime safety guardrails (default credentials, flag isolation, etc.)
./scripts/validate-microservices-phase-8-runtime-safety.sh
```


### Stop

```bash
docker compose down
docker compose -f docs/dev-ops/docker-compose-environment.yml down
```

### Useful Logs

```bash
docker compose logs big-market-gateway
docker compose logs big-market-auth-service
docker compose logs big-market-admin-service
docker compose logs big-market-market-service
docker compose logs big-market-chatbot-service
docker compose logs big-market-message-job-service
docker compose -f docs/dev-ops/docker-compose-environment.yml logs rabbitmq
docker compose -f docs/dev-ops/docker-compose-environment.yml logs nacos
```

### Known Limitations

1. `PlatformConfigService` syncs via Nacos: admin-service publishes to `big-market-platform-config` on save; chatbot-service fetches on startup and receives live push updates. Requires Nacos to be running (it is in the default stack).
2. Pre-existing RabbitMQ test messages for `userId: xiaofuge` produce noisy consumer error logs due to a DB sharding mismatch. This does not affect service health or smoke test results.
3. Gateway circuit breakers (Resilience4J) are active on all four downstream routes. If a service is down, the gateway returns `{"code":"0007","info":"网关接口调用失败","data":null}` instead of hanging.
4. All services propagate `X-Trace-Id` — gateway generates one if absent; downstream services put it in MDC as `traceId`.
5. Phase 8 cutover paths are repo-ready but not real-production-proven: proposed DDL is not applied, service-provider registration is external-gated, and all cutover flags default false.

### Documentation

- [docs/MICROSERVICES.md](docs/MICROSERVICES.md) — authoritative entry point: current service inventory, bounded-context cutover status, completed phases, active Phase 8, documentation index, archive map
- [docs/microservices-dao-ownership.md](docs/microservices-dao-ownership.md) — AL-1..AL-11 cross-boundary DAO ownership matrix
- [docs/microservices-legacy-cleanup-inventory.md](docs/microservices-legacy-cleanup-inventory.md) — post-cutover legacy removal inventory
- [docs/microservices-phase-8.md](docs/microservices-phase-8.md) — Phase 8 consolidated doc: cutover runbook, external evidence readiness pack, and staging evidence
- [docs/sql/](docs/sql/) — proposed DDL (`proposed-*.sql`, never applied from this repo)
- [docs/archive/](docs/archive/) — validator-backed historical phase records and superseded summary docs
- [scripts/smoke-test-phase-1.sh](scripts/smoke-test-phase-1.sh) — smoke test for the multi-service stack
- [scripts/validate-microservices-split-all-gates.sh](scripts/validate-microservices-split-all-gates.sh) — aggregate repo-only gate runner
- [scripts/validate-microservices-stack.sh](scripts/validate-microservices-stack.sh) — orchestrated build + docker + smoke test runner

---

## Local Development

```bash
# Start middleware, build modules, and launch the microservices stack
./scripts/dev-run.sh
```

If you prefer manual steps:

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
mvn -DskipTests package
docker compose up -d --build
```

应用默认地址（gateway）：`http://127.0.0.1:8080`

### 启动开发环境

```bash
# 1. 启动中间件
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch

# 2. 打包
mvn -DskipTests package

# 3. 启动微服务栈（gateway + 后端服务）
docker compose up -d --build

# 4. 启动前端（开发模式，端口 5173）
./scripts/web-start.sh
open http://127.0.0.1:5173/login.html
```

前端 API 默认走 `http://127.0.0.1:8080/api/v1`（gateway），Docker 部署时自动切换同源 `/api/v1`。

### API 验证

```bash
# Gateway health
curl -s http://127.0.0.1:8080/actuator/health

# 登录
curl -s http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}'

# 完整 smoke test
./scripts/smoke-api.sh
```

---

接口冒烟：

```bash
./scripts/smoke-api.sh
```

### Core API (via gateway at :8080)

```text
POST /api/v1/auth/login
GET  /api/v1/auth/verify

GET  /api/v1/admin/config/list
GET  /api/v1/admin/config/get
POST /api/v1/admin/config/save
POST /api/v1/admin/config/delete

POST /api/v1/chatbot/ask
```

---

## Further Reading

- [docs/MICROSERVICES.md](docs/MICROSERVICES.md) — authoritative microservices decomposition status
- [docs/archive/phases.md](docs/archive/phases.md) — Phase 1–7 historical records

This repository is a personal learning / portfolio project. Repo-only
validators are exercised in this codebase; all production/cutover feature
flags default to `false` and external evidence remains EXTERNAL-GATED.
