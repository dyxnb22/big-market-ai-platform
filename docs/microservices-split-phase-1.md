# big-market Phase 1 Microservices Split

## 1. Background: Current Modular Monolith

Before this split, the project had a single runnable Spring Boot application (`big-market-app`) that packaged all modules together:

```
big-market-app  ← only Spring Boot launcher
  ├── big-market-trigger      (HTTP, MQ, XXL-Job, Dubbo RPC)
  ├── big-market-infrastructure (MySQL, Redis, RabbitMQ, Elasticsearch)
  ├── big-market-domain       (all domain logic)
  ├── big-market-auth-access  (login / JWT controller)
  ├── big-market-admin        (admin config controller)
  ├── big-market-chatbot      (AI assistant controller)
  └── shared starters + types
```

All controllers ran on the same JVM, same port (8098 in dev), and shared one database connection pool, one Redis client, and one RabbitMQ connection.

## 2. Phase 1 Microservice Layout

```
External Traffic
      │
      ▼
┌─────────────────────┐
│  big-market-gateway  │  :8080  Spring Cloud Gateway — routes requests
└──────────┬──────────┘
           │  routes by path prefix
     ┌─────┴─────────────────────┐
     │             │             │             │
     ▼             ▼             ▼             ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐
│ auth     │ │ admin    │ │ market   │ │ chatbot      │
│ service  │ │ service  │ │ service  │ │ service      │
│ :8081    │ │ :8082    │ │ :8083    │ │ :8084        │
└──────────┘ └──────────┘ └──────────┘ └──────────────┘
 stateless     file-based   MySQL+Redis   file-based
 JWT only      config only  RabbitMQ      in-memory
                            Dubbo/Nacos
```

### Shared library modules (unchanged — packaged as JARs)

| Module | Role |
|--------|------|
| `big-market-types` | Common enums, exceptions, annotations |
| `big-market-api` | External DTOs and service interfaces |
| `big-market-queries` | ES/query-side models |
| `big-market-domain` | All domain services (stateless JWT `AuthService` lives here) |
| `big-market-infrastructure` | MySQL, Redis, RabbitMQ, Elasticsearch adapters |
| `big-market-management` | File-backed `PlatformConfigService` |
| `big-market-auth-access` | `AuthAccessController` bean only |
| `big-market-admin` | `AdminConfigController` bean only |
| `big-market-chatbot` | `ChatbotController` bean only |
| `big-market-trigger` | Market HTTP controllers, MQ listeners, XXL-Job, Dubbo RPC |
| `big-market-starter-*` | DB router, DCC, rate limiter starters |

`big-market-app` is preserved as-is for comparison and local dev fallback.

## 3. Service Responsibility Boundaries

### `big-market-gateway` (port 8080)
- Entry point for all external traffic
- Routes by URL path prefix:
  - `/api/*/auth/**` → auth-service:8081
  - `/api/*/admin/**` → admin-service:8082
  - `/api/*/chatbot/**` → chatbot-service:8084
  - `/api/**` (catch-all) → market-service:8083
- No business logic — pure routing
- Spring Cloud Gateway (WebFlux reactive), does **not** use Spring MVC

### `big-market-auth-service` (port 8081)
- Endpoints: `POST /api/v1/auth/login`, `GET /api/v1/auth/verify`
- Issues and validates stateless JWT tokens
- **No database, no Redis, no RabbitMQ** — AuthService is purely JWT signature logic
- Scans: `com.dyx.market.auth.service`, `com.dyx.market.auth`, `com.dyx.market.domain.auth`

### `big-market-admin-service` (port 8082)
- Endpoints: `GET/POST /api/v1/admin/config/**`
- Manages platform runtime configuration (enabled/disabled switches, chatbot settings)
- Backed by `PlatformConfigService` (in-memory + file `data/platform-config.properties`)
- JWT + admin-token interceptor on all `/api/*/admin/**` paths
- **No database, no Redis** — config is file-backed
- Scans: `com.dyx.market.admin.service`, `com.dyx.market.admin`, `com.dyx.market.management`, `com.dyx.market.domain.auth`

### `big-market-market-service` (port 8083)
- All raffle/activity/strategy/rebate/credit/ERP/DCC endpoints
- Dubbo RPC service for external rebate calls (`RebateServiceRPC`)
- Full infrastructure: MySQL (sharded 2 DBs × 4 tables), Redis, Elasticsearch, Nacos
- **No longer owns MQ consumers or XXL-Job handlers** — moved to `big-market-message-job-service`
- Scans: `com.dyx.market.market`, `com.dyx.market.trigger.http`, `com.dyx.market.trigger.rpc`, `com.dyx.market.domain`, `com.dyx.market.infrastructure`

### `big-market-message-job-service` (port 8085) — Phase 2.1
- MQ listeners: activity SKU stock zero, send award, rebate (send_rebate), credit adjust
- XXL-Job handlers: send message task, update activity SKU stock, update award stock
- DLQ configuration: DLX exchange + 4 `*.dlq` queues (dead-letter routing for all consumers)
- `default-requeue-rejected: false` — failed messages dead-lettered, not requeued
- Full infrastructure: MySQL (sharded), Redis, RabbitMQ, Elasticsearch (for ESUserRaffleOrderRepository), Nacos-configured XXL-Job executor (`big-market-message-job`, port 9998)
- Scans: `com.dyx.market.message.job`, `com.dyx.market.trigger.job`, `com.dyx.market.trigger.listener`, `com.dyx.market.domain`, `com.dyx.market.infrastructure`

### `big-market-chatbot-service` (port 8084)
- Endpoint: `POST /api/v1/chatbot/ask`
- Supports `local` (rule-based fallback) and `deepseek` providers
- Reads chatbot enabled/provider config from `PlatformConfigService`
- **No database, no Redis** — stateless per request
- Scans: `com.dyx.market.chatbot.service`, `com.dyx.market.chatbot`, `com.dyx.market.management`

## 4. Port Reference

| Service | Internal Port | Exposes |
|---------|--------------|---------|
| big-market-gateway | 8080 | `8080` — all external traffic |
| big-market-auth-service | 8081 | `8081` — login + token verify |
| big-market-admin-service | 8082 | `8082` — admin config API |
| big-market-market-service | 8083 | `8083` — core market APIs |
| big-market-chatbot-service | 8084 | `8084` — chatbot API |
| big-market-message-job-service | 8085 | `8085` — actuator health only (no public API) |
| mysql | 3306 | `13306` (host) |
| redis | 6379 | `16379` (host) |
| rabbitmq | 5672/15672 | `5672/15672` |
| nacos | 8848/9848 | `8848/9848` |
| elasticsearch | 9200/9300 | `9200/9300` |

## 5. Build and Startup Steps

```bash
# 1. Build all JARs (skip tests for speed)
mvn clean package -DskipTests

# 2. Start the full infrastructure stack (all services including prometheus, grafana)
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d

# 3. Start the Phase 1 microservices stack
docker compose up --build -d

# 4. Run the smoke test to verify all services
./scripts/smoke-test-phase-1.sh

# 5. Start a single service (after infra is up)
docker compose up big-market-auth-service
```

> **Network note:** The infrastructure compose file defines a shared Docker network `my-network` (Docker Compose names it `dev-ops_my-network`). All infra services including RabbitMQ are on this network. The Phase 1 app stack declares it as `external: true` and attaches all microservices to it. No manual `docker network connect` steps are required.

> **Port reference for infra tools:** prometheus → http://localhost:9091 | redis-admin → http://localhost:18081 | canal-adapter → http://localhost:18082 | xxl-job-admin → http://localhost:9090 | phpmyadmin → http://localhost:8899 | grafana → http://localhost:4000

## 6. Environment Variables Reference

All secrets and host addresses are externalized. Create a `.env` file at the project root to override defaults:

```bash
# .env (do not commit this file)
JWT_SECRET=your-strong-jwt-secret-at-least-32-chars
ADMIN_TOKEN=your-admin-bearer-token
ADMIN_USER_IDS=userId1,userId2
MYSQL_ROOT_PASSWORD=123456
MYSQL_USER=root
RABBITMQ_USER=admin
RABBITMQ_PASS=admin
DEEPSEEK_API_KEY=sk-xxxxxxxxxxxx
CHATBOT_PROVIDER=deepseek
XXL_JOB_TOKEN=default_token
```

## 7. Verification Checklist

After `docker compose up --build`, run these checks:

```bash
# Health checks (all should return {"status":"UP"})
curl http://localhost:8081/actuator/health   # auth-service
curl http://localhost:8082/actuator/health   # admin-service
curl http://localhost:8083/actuator/health   # market-service
curl http://localhost:8084/actuator/health   # chatbot-service
curl http://localhost:8080/actuator/health   # gateway

# Login (via gateway)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userId":"test-user-001"}'

# Verify token (replace TOKEN with value from login response)
curl http://localhost:8080/api/v1/auth/verify \
  -H "Authorization: Bearer TOKEN"

# Admin config list (via gateway — requires admin auth)
curl http://localhost:8080/api/v1/admin/config/list \
  -H "Authorization: Bearer TOKEN"

# Chatbot (via gateway)
curl -X POST http://localhost:8080/api/v1/chatbot/ask \
  -H "Content-Type: application/json" \
  -d '{"message":"你好"}'

# Market: query stage activity
curl "http://localhost:8080/api/v1/raffle/activity/query_stage_activity_id?channel=default&source=web"
```

## 8. Phase 1.3 — Stability Batch (2026-06-09)

This batch hardens Phase 1 before any new service is extracted. No new service modules were added.

### 8.1 SendAwardConsumer DLQ fix

**Problem:** `SendAwardConsumer.listener()` caught all exceptions and swallowed them (commented-out `throw e`). Failed award messages were silently dropped — DLQ was never triggered.

**Fix:** Uncommented exception propagation. Pattern now matches `CreditAdjustSuccessConsumer`:
- `AppException` with `INDEX_DUP` code → `log.warn` + `return` (idempotent duplicate, not an error)
- Other `AppException` → rethrow → Spring AMQP dead-letters to `send_award.dlq`
- Generic `Exception` → `log.error` + rethrow → Spring AMQP dead-letters to `send_award.dlq`
- `listener` method now declares `throws Exception` (valid for Spring AMQP `@RabbitListener`)

**File:** `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`

### 8.2 Gateway circuit breakers

**Problem:** Gateway had no failure isolation. A single downed service caused all in-flight requests to hang until timeout.

**Fix:** Added Spring Cloud CircuitBreaker (Resilience4J reactive) to all four downstream routes:
- `auth-cb` → fallback `/fallback/auth-service`
- `admin-cb` → fallback `/fallback/admin-service`
- `chatbot-cb` → fallback `/fallback/chatbot-service`
- `market-cb` → fallback `/fallback/market-service`

Fallback returns `{"code":"0007","info":"网关接口调用失败","data":null}` — stable JSON, matches `ResponseCode.GATEWAY_ERROR`. Circuit breaker settings: 10-request sliding window, 50% failure threshold, 10s open-state wait.

**New files:**
- `big-market-gateway/src/main/java/com/dyx/market/gateway/fallback/FallbackController.java`
- `big-market-gateway/pom.xml` — added `spring-cloud-starter-circuitbreaker-reactor-resilience4j:2.1.7`

### 8.3 Trace ID propagation

**Problem:** No correlation ID across service hops, making cross-service request tracing impossible.

**Fix:**
- Gateway reads `X-Trace-Id` from incoming request; generates a random UUID if absent. Forwards it to downstream services and echoes it in the response header.
- All four servlet-based services (auth, admin, market, chatbot) install a `TraceIdFilter` (`OncePerRequestFilter`) that reads `X-Trace-Id`, places it in MDC under key `traceId`, and echoes it on the response.
- Trace ID is lightweight (UUID, no collector required). Add `%X{traceId}` to logback patterns to surface it in logs.

**New files:**
- `big-market-gateway/src/main/java/com/dyx/market/gateway/filter/TraceIdGlobalFilter.java`
- `big-market-{auth,admin,chatbot}-service/.../config/TraceIdFilter.java`
- `big-market-market-service/.../config/TraceIdFilter.java`

### 8.4 Smoke test updated

Smoke test extended from 15 to 16 checks. The new check calls `GET /fallback/auth-service` on the gateway and expects `"0007"` in the response, verifying the fallback controller is wired correctly.

**MQ/DLQ behavior is NOT validated by the smoke test.** The DLQ fix (8.1) requires a running RabbitMQ with the DLX policy active to verify end-to-end.

### 8.5 What is still NOT done (account-service extraction is next)

- Account-service has **not** been extracted. That is the next planned phase.
- Database schemas are **not** split. All services still share the same MySQL instance.
- Domain packages have **not** been moved. All domain code is still in `big-market-domain`.
- Service mesh, Zipkin, and OpenTelemetry are **not** in scope.

---

## 9. Known Limitations

### Phase 1 Limitations
1. **Shared database**: all services that need MySQL still share the same schemas. No per-service DB isolation yet.
2. **Shared Redis**: market-service and any other service that might need caching all share one Redis instance.
3. **`PlatformConfigService` now syncs via Nacos**: admin-service publishes config changes to Nacos dataId `big-market-platform-config`; chatbot-service subscribes and refreshes in real time. Requires `nacos.config.sync.enabled=true` and `NACOS_HOST=nacos` (both set in docker-compose). Falls back to local defaults if Nacos is unavailable at startup.
4. **Circuit breakers now active at gateway**: all four downstream routes have Resilience4J circuit breakers and stable fallback responses (added in Phase 1.3).
5. **No service discovery at gateway**: gateway routes are statically configured. In Phase 2, integrate Spring Cloud LoadBalancer with Nacos discovery.
6. **big-market-app preserved**: the original monolith launcher still exists. It uses port 8098, so it does not directly collide with the Phase 1 service ports (8080-8084). Do not run it simultaneously with the Phase 1 stack against the same infrastructure unless you intentionally want duplicate consumers, duplicate scheduled jobs, shared DB/Redis writes, and duplicate Dubbo/XXL-Job registration behavior. It is kept as a fallback and reference only.

### Runtime Prerequisites
- Nacos requires MySQL to be fully initialized (including `nacos_config` schema from `nacos.sql`)
- market-service Dubbo registration requires Nacos to be reachable on port 8848
- XXL-Job admin (`xxl-job-admin` container) must be on `dev-ops_my-network`. The executor on port 9090 is reachable inside Docker by container name.
- Elasticsearch X-Pack SQL JDBC requires appropriate licensing. The `xpack.security.enabled=false` flag is set in the compose file to allow unauthenticated access in dev.
- **RabbitMQ network**: the `rabbitmq` service in `docs/dev-ops/docker-compose-environment.yml` is explicitly on `my-network` (which Docker Compose names `dev-ops_my-network`). No manual `docker network connect` step is required.
- **gateway.config properties**: market-service's `application.yml` must include `gateway.config.big-market-appId` and `gateway.config.big-market-appToken` (env vars `GATEWAY_APP_ID` / `GATEWAY_APP_TOKEN`). Missing these causes `awardPort` bean creation failure at startup.

### Auth `/api/v1/auth/verify` Usage Note
The `verify` endpoint reads the JWT from `@RequestHeader("Authorization")` directly — it expects the **raw JWT string** (no `Bearer ` prefix). The gateway and any client calling verify must pass the token without the `Bearer ` prefix.

### RabbitMQ Noisy Consumer Logs (Troubleshooting)
If market-service logs show repeated `CreditAdjustSuccessConsumer` SQL errors for `userId: xiaofuge`, this is caused by a stuck non-persistent test message in the `credit_adjust_success` queue that routes to the un-sharded `big_market` schema (which lacks the sharded `raffle_activity_order` table).

**Root cause:** Spring AMQP's default `default-requeue-rejected=true` causes any failed message to be nacked and requeued immediately, creating an infinite retry loop. The RabbitMQ data directory is not volume-mounted, so all messages are lost on `docker compose down` — a fresh restart clears the stuck message.

**Safe cleanup without restart:**
```bash
# Confirm the queue contains only the stuck test message
docker exec rabbitmq rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers

# Purge only the affected queue (safe — the message is bad local test data, not production data)
docker exec rabbitmq rabbitmqctl purge_queue credit_adjust_success

# Restart market-service so the consumer re-subscribes to a clean queue
docker compose restart big-market-market-service
```

**Do not purge all queues blindly.** Inspect queue state first and only purge `credit_adjust_success` (or whichever queue contains the stuck message).

## 9. Phase 2 Split Suggestions

The following additional services were deferred from Phase 1:

| Service | Source | Trigger |
|---------|--------|---------|
| `account-service` | credit + rebate domain logic from trigger | Extract when credit/rebate team needs separate deploy cadence |
| `fulfillment-service` | award dispatch domain (IAwardService) | Extract when award delivery becomes a bottleneck |
| `rebate-service` | `RebateServiceRPC` Dubbo + `IBehaviorRebateService` | Already Dubbo-exposed; extract as Dubbo provider |
| `message-job-service` | XXL-Job handlers + MQ listeners from trigger | Extract when job scheduling needs independent scaling |
| `config-service` | Upgrade `PlatformConfigService` to shared Nacos config | Required to fix Phase 1 admin/chatbot config isolation issue |

Phase 2 also recommends:
- Per-service database schemas (Strangler Fig migration)
- Nacos-based dynamic gateway routing
- Distributed tracing (Sleuth + Zipkin)
- Per-service circuit breakers (Resilience4J)
