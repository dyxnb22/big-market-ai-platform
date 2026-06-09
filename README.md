# Big Market AI Platform

这是从原始面试复习项目复制出来的产品化版本，原项目仍保留在：

```text
/Users/diaoyuxuan/IdeaProjects/big-market
```

当前版本已经完成第一轮"可学习、可运行、可继续重构"的补全：

- Java 包名统一为 `com.dyx.market`，不再使用原作者命名空间。
- 内置 `big-market-starter-db-router`、`big-market-starter-dcc`、`big-market-starter-ratelimiter`，便于学习 DB 路由、动态配置和限流实现。
- 新增 `big-market-auth-access`，提供登录和 token 校验入口。
- 新增 `big-market-management`，提供本地持久化配置能力。
- 新增 `big-market-admin`，提供管理端配置 API。
- 新增 `big-market-chatbot`，提供规则版 Chatbot，受管理端开关控制，并调用现有抽奖/积分/签到业务接口。
- 新增 `big-market-web/index.html`，提供轻量用户端 + 管理端联调页面。

---

## Phase 2.2-B1 Microservices (Current Active Architecture)

The monolith has been progressively split into 7 independently deployable services behind an API gateway. The original `big-market-app` is preserved untouched as a legacy fallback.

### Services

| Service | Port | Responsibility |
|---------|-----:|----------------|
| big-market-gateway | 8080 | Gateway routing to all backend services |
| big-market-auth-service | 8081 | Login, JWT issuing, token verification |
| big-market-admin-service | 8082 | Admin configuration APIs |
| big-market-market-service | 8083 | Core marketing / raffle / activity APIs + Dubbo RPC |
| big-market-chatbot-service | 8084 | Chatbot APIs |
| big-market-message-job-service | 8085 | MQ consumers + XXL-Job scheduled handlers |
| big-market-account-service | 8086 | Internal Dubbo provider — credit + quota operations. Dark-launched Phase 2.2-A; read adapter wired Phase 2.2-B1 (flag defaults off). Phase 2.2-B scaffolds write adapters/flags, but no write-path cutover is enabled. |

Shared library modules (`big-market-domain`, `big-market-infrastructure`, `big-market-api`, `big-market-types`, `big-market-queries`, starter modules) are reused as JAR dependencies — no code was moved or duplicated.

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

# 2. Start the Phase 1 microservices stack
docker compose up --build -d
```

### Verify

```bash
# Check all containers are running
docker compose -f docs/dev-ops/docker-compose-environment.yml ps
docker compose ps

# Run smoke test (expects 17/17 PASS — 7 health + 9 functional + 1 fallback)
./scripts/smoke-test-phase-1.sh

# Validate account-service remote-read routing and fallback (temporarily recreates market-service only)
./scripts/validate-account-remote-read.sh

# Or run the full orchestrated validation (build → infra → app → smoke test):
./scripts/validate-microservices-stack.sh
```

> **Runtime validation note (Phase 2.2-B):** account-service Dubbo providers are registered.
> A read-only adapter (`IAccountReadAdapter`) is wired into `RaffleActivityController` and
> `RaffleStrategyController`. The adapter is gated behind `account.service.remote-read.enabled`
> (env: `ACCOUNT_SERVICE_REMOTE_READ_ENABLED`, defaults `false`). Validate it with
> `./scripts/validate-account-remote-read.sh`; the script flips only market-service,
> checks read endpoints, confirms fallback while account-service is stopped, and restores
> the default. Write-path adapters and flags now exist, but callers are not cut over and
> `ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=false` /
> `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=false` remain the defaults.

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
5. `account-service` write cutover has not been done yet. Phase 2.2-B2 is the next planned batch after remote-read validation passes.

### Documentation

- [docs/microservices-split-phase-1.md](docs/microservices-split-phase-1.md) — Phase 1 architecture, service responsibilities, env vars, verification checklist
- [docs/microservices-roadmap.md](docs/microservices-roadmap.md) — phase-by-phase evolution plan (Phase 1 through Phase 4)
- [docs/microservices-split-phase-2-2-account-service.md](docs/microservices-split-phase-2-2-account-service.md) — account-service extraction readiness and cutover plan
- [scripts/smoke-test-phase-1.sh](scripts/smoke-test-phase-1.sh) — 17-check smoke test for the current 7-service stack
- [scripts/validate-account-remote-read.sh](scripts/validate-account-remote-read.sh) — Phase 2.2-B remote-read and fallback validation
- [scripts/validate-microservices-stack.sh](scripts/validate-microservices-stack.sh) — orchestrated build + docker + smoke test runner

---

## Legacy Monolith (big-market-app)

The original single-JVM launcher is preserved at port 8098. Use it for local debugging or as a reference only. Do not run it simultaneously with the Phase 1 microservices stack against the same infrastructure unless you intentionally want duplicate MQ consumers, scheduled jobs, shared DB/Redis writes, and registration side effects. The main risk is shared infrastructure behavior, not direct HTTP port conflict.

### Quick Start (monolith)

启动中间件、打包并以前台方式运行应用：

```bash
./scripts/dev-run.sh
```

如果希望后台运行：

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
mvn -DskipTests package
./scripts/app-start.sh
./scripts/app-status.sh
```

应用默认地址：`http://127.0.0.1:8098`

前端页面：

```bash
./scripts/web-start.sh
open http://127.0.0.1:5173
```

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

- [docs/microservices-split-phase-1.md](docs/microservices-split-phase-1.md)
- [docs/product-architecture.md](docs/product-architecture.md)
- [docs/rebuild-roadmap.md](docs/rebuild-roadmap.md)
