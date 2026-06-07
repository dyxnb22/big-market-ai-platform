# big-market Microservices Evolution Roadmap

## 1. Current State (as of 2026-06-07)

The project has completed Phase 1: a modular monolith split into 5 independently deployable Spring Boot services behind an API gateway. All 5 services run healthy; the 14-check smoke test passes end-to-end.

**Running services:**

| Service | Port | Responsibility |
|---------|------|---------------|
| `big-market-gateway` | 8080 | Spring Cloud Gateway — routes all external traffic |
| `big-market-auth-service` | 8081 | Stateless JWT login + token verify |
| `big-market-admin-service` | 8082 | Platform runtime config (file-backed) |
| `big-market-market-service` | 8083 | Core raffle / activity / credit / rebate / award / MQ / jobs / Dubbo RPC |
| `big-market-chatbot-service` | 8084 | AI chatbot (DeepSeek or local rule engine) |

**Known Phase 1 residual issues:**
- `PlatformConfigService` is isolated per-process (admin-service and chatbot-service each hold independent state)
- No dead-letter queue: MQ consumer failures requeue immediately → potential infinite retry loops
- No circuit breaker between gateway and downstream services
- Static gateway routing (no service-discovery integration)
- All market logic (HTTP controllers, MQ listeners, XXL-Job handlers, Dubbo RPC) is packed into one `market-service` JVM
- All market-service tables share one MySQL instance with no per-service schema isolation

---

## 2. Design Principles for Future Splitting

These principles govern every future extract decision. Violating them creates more problems than the split solves.

### 2.1 Split by business capability, not by layer

Wrong split: "all MQ listeners go into a listener-service." This creates a cross-cutting service with no cohesive domain ownership — every business change touches it.

Right split: "credit domain logic + credit MQ consumers + credit tables go into account-service." The service owns one vertical slice.

### 2.2 Follow the data, not the code

A service boundary is only real when the service owns its tables exclusively. Until then, the split is a facade — two JVMs sharing one schema are still a distributed monolith.

For each candidate service extract, map: which tables does this domain write to? Those tables must eventually follow the service into its own schema.

### 2.3 Prefer strangler-fig extraction over big-bang rewrites

Extract one service at a time. Each extract must:
1. Keep all smoke tests passing (the 14-check script is the minimum bar)
2. Keep `big-market-app` buildable and runnable as a regression baseline
3. Not require changes to any other service's code

### 2.4 Infrastructure complexity must be justified

Adding a new service adds: a new Dockerfile, a new Spring Boot app module, a new health endpoint, a new entry in docker-compose, and one more service to debug when things go wrong. Only extract when the benefit (independent deploy cadence, different scaling requirements, different team ownership, or a known bottleneck) clearly outweighs the cost.

### 2.5 Do not split until the interface is stable

If the domain API (`IBehaviorRebateService`, `IAwardService`, etc.) is still changing, splitting it out creates churn: every interface change becomes a cross-service protocol change. Stabilize the interface first, then extract.

---

## 3. Phase 1 Completed — Runtime Split

**Status: Done and validated.**

All five services deploy from the root `docker-compose.yml`. Startup is two commands:

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
```

Verification: `./scripts/smoke-test-phase-1.sh` expects 14/14 PASS.

Key design decisions locked in Phase 1:
- Shared library modules (`big-market-domain`, `big-market-infrastructure`, etc.) are JARs — no code was moved
- Bean scanning is controlled per service via `scanBasePackages` in each `@SpringBootApplication`
- `big-market-app` is preserved untouched as a fallback and regression reference
- Gateway routes are statically configured in `application.yml` (Nacos-based dynamic routing deferred to Phase 2+)

---

## 4. Phase 1.2 — Stability and Reliability Cleanup

**Goal:** Harden what was built before any new splitting. Phase 1.2 tasks are all non-functional improvements with no new service modules.

### 4.1 Dead-letter queue (DLQ) policy for MQ consumers

**Problem:** Spring AMQP default `default-requeue-rejected=true`. Any exception in a consumer nacks with `requeue=true` → the message goes back to the head of the queue → immediate retry → infinite loop.

**Affected consumers in `big-market-market-service`:**
- `ActivitySkuStockZeroConsumer` (queue: `activity_sku_stock_zero`)
- `CreditAdjustSuccessConsumer` (queue: `credit_adjust_success`) — currently experiencing this
- `RebateMessageConsumer` (queue: `rebate_message`)
- `SendAwardConsumer` (queue: `send_award`)

**Recommended fix:**
1. Set `spring.rabbitmq.listener.simple.default-requeue-rejected=false` in `big-market-market-service/src/main/resources/application.yml`
2. Declare a dead-letter exchange (`dlx`) and per-queue DLQ bindings (`{queue}.dlq`) via `@Bean Queue` configuration
3. Add a `@RabbitListener` on each DLQ for alerting or manual inspection

**Risk:** Low. Changing `default-requeue-rejected` to false means a message that throws an uncaught exception is dead-lettered rather than looped. This is strictly better behavior for all four consumers.

### 4.2 Shared `PlatformConfigService` via Nacos config

**Problem:** `admin-service` and `chatbot-service` each hold an independent copy of `PlatformConfigService`. Config changes via the admin API are invisible to chatbot-service in real time.

**Recommended fix (Phase 1.2):**
1. Add `spring-cloud-starter-alibaba-nacos-config` to both `big-market-admin-service` and `big-market-chatbot-service`
2. Migrate `PlatformConfigService` to push changes to a Nacos config namespace when values are saved
3. `chatbot-service` subscribes to the same Nacos config namespace via `@NacosValue` or `@RefreshScope`

**Alternative (simpler, no Nacos config dependency):** Poll admin-service via REST on a short interval (30s) from chatbot-service. This avoids the Nacos config dependency but introduces coupling.

**Risk:** Medium. Requires Nacos config namespace setup and testing the `@RefreshScope` refresh propagation.

### 4.3 CI smoke test script

Promote `scripts/smoke-test-phase-1.sh` to run on every build:
- Add a GitHub Actions / Jenkins job that: builds JARs, starts the compose stack, runs the smoke test, tears down
- The 14-check test is the regression gate for every future change

### 4.4 Structured logging and correlation ID

Every service should log with a consistent JSON format and include a `traceId` / `correlationId` header for cross-service request tracing.

**Recommended approach:**
- Add `logstash-logback-encoder` dependency to each service's `pom.xml`
- Add a `MDCFilter` (or a Spring Cloud Sleuth `TraceFilter`) that generates a UUID per request and stores it in MDC
- Gateway injects `X-Trace-Id` header downstream; each service reads it and puts it in MDC

This is a prerequisite for Phase 3 distributed tracing.

---

## 5. Phase 2 — Business Service Boundary Split

**Goal:** Decompose `big-market-market-service` into services that each own a coherent business domain.

**Trigger for starting Phase 2:** At least one of these conditions is true:
- market-service startup time exceeds 90s regularly (currently within limit)
- A team ownership boundary emerges (e.g., a separate team owns credit/rebate)
- A scaling requirement diverges (e.g., XXL-Job handlers need separate scaling from HTTP controllers)
- Phase 1.2 stability items are complete

**Proposed Phase 2 service map:**

```
big-market-market-service (current)
  ├── message-job-service    ← Phase 2.1 (safest first)
  ├── account-service        ← Phase 2.2
  ├── fulfillment-service    ← Phase 2.3
  ├── rebate-service         ← Phase 2.4
  └── raffle-service         ← Phase 2.5 (renamed market-service remainder)
```

Each extract is independent. They can be done in any order, but the sequence 2.1 → 2.2 → 2.3 → 2.4 → 2.5 is recommended because each step reduces the size of market-service without breaking the others.

---

## 6. Phase 2.1 — Extract `message-job-service` (Safest First Split)

**Why first:** No business domain logic is extracted. This service only orchestrates: runs scheduled jobs and consumes MQ messages. It calls domain services but does not own any domain state itself.

### What moves

**From `big-market-trigger/src/main/java/com/dyx/market/trigger/job/`:**
- `SendMessageTaskJob` — XXL-Job handler; queries `task` table and publishes undelivered messages to RabbitMQ
- `UpdateActivitySkuStockJob` — XXL-Job handler; reads activity SKU stock deltas from Redis and writes to MySQL
- `UpdateAwardStockJob` — XXL-Job handler; reads award stock deltas from Redis and writes to MySQL

**From `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/`:**
- `ActivitySkuStockZeroConsumer` — MQ consumer; marks activity SKU as sold out
- `CreditAdjustSuccessConsumer` — MQ consumer; updates user credit account on successful credit adjustment
- `RebateMessageConsumer` — MQ consumer; applies behavior rebate credit
- `SendAwardConsumer` — MQ consumer; dispatches awarded prizes

### Dependencies pulled in (via existing JAR deps — no code moves)
- `big-market-domain` — all domain services via dependency injection
- `big-market-infrastructure` — all DAOs and repository implementations
- `big-market-infrastructure` — RabbitMQ `EventPublisher`, Redis templates
- `big-market-starter-db-router` — DB sharding for sharded tables

### New `message-job-service` module structure
```
big-market-message-job-service/
  pom.xml                         ← dep: big-market-infrastructure, big-market-domain
  src/main/java/.../MessageJobServiceApp.java
    @SpringBootApplication(scanBasePackages = {
        "com.dyx.market.message.job.service",   # this service's beans
        "com.dyx.market.trigger.job",           # XXL-Job handlers
        "com.dyx.market.trigger.listener",      # MQ consumers
        "com.dyx.market.domain",                # domain services
        "com.dyx.market.infrastructure"         # DAOs, repos, Redis, MQ
    })
  src/main/resources/application.yml  ← MySQL, Redis, RabbitMQ, XXL-Job config (same as market-service)
  Dockerfile (or handled by Dockerfile.service ARG MODULE_NAME)
```

### Market-service after extract
Remove `trigger/job/` and `trigger/listener/` from `market-service`'s scan packages. Market-service becomes HTTP + Dubbo RPC only.

### Port: 8085

### Validation
- Smoke test still passes 14/14 (smoke test does not test MQ consumers or XXL-Job directly)
- Verify XXL-Job admin shows executors from `message-job-service` container name
- Verify `SendMessageTaskJob` picks up and publishes `task` table rows

---

## 7. Phase 2.2 — Extract `account-service`

**Business domain:** User credit account management — earning, spending, and querying credit balances.

### What moves

**Domain layer (via JAR — no code moved, only scan restriction):**
- `com.dyx.market.domain.credit` — `CreditAdjustService`, `ICreditAdjustService`, credit domain model
- `com.dyx.market.domain.activity.service.quota` — activity account quota management (`IActivityAccountQuotaService`, `ActivityAccountQuotaService`)

**Trigger layer:**
- No dedicated HTTP endpoints currently. Account data is accessed via other domain services.
- `CreditAdjustSuccessConsumer` — moves to `message-job-service` (Phase 2.1), which calls `ICreditAdjustService` in `account-service` via Dubbo

**Infrastructure / tables exclusively owned by `account-service`:**
- `user_credit_account` — user credit balance
- `user_credit_order` — credit earn/spend ledger
- `raffle_activity_account` — user activity quota account
- `raffle_activity_account_day` — daily quota
- `raffle_activity_account_month` — monthly quota

### Interface exposure
`account-service` exposes `ICreditAdjustService` as a Dubbo provider. `message-job-service` calls it when `CreditAdjustSuccessConsumer` needs to apply a credit adjustment.

### Port: 8086

### Migration prerequisite
The `raffle_activity_account*` tables are currently accessed by `domain/activity/` services too (activity quota checks during raffle participation). Before account-service can own these tables exclusively, activity domain must call account-service via Dubbo for quota checks rather than hitting the DB directly.

**This is a non-trivial interface change — defer until domain API is stable.**

---

## 8. Phase 2.3 — Extract `fulfillment-service`

**Business domain:** Award fulfillment — dispatching prizes after a raffle win.

### What moves

**Domain layer:**
- `com.dyx.market.domain.award` — `IAwardService`, `AwardService`, award distribution strategies (`UserCreditRandomAward`, `OpenAIAccountAdjustQuotaAward`)

**Trigger layer:**
- `SendAwardConsumer` — moves to `message-job-service` (Phase 2.1), which calls `IAwardService` in `fulfillment-service` via Dubbo

**Infrastructure / tables:**
- `user_award_record` — records what award each user received
- `award` — award catalog (prize types, configs)

### Interface exposure
`fulfillment-service` exposes `IAwardService` as a Dubbo provider. `SendAwardConsumer` (in `message-job-service`) calls it.

### Port: 8087

### Dependencies
`AwardService` calls `ICreditAdjustService` to issue credit awards (`UserCreditRandomAward` adjusts credit). This creates a dependency: `fulfillment-service` → `account-service`. Both must be up for award fulfillment to work. Add Dubbo circuit breaker or fallback here.

---

## 9. Phase 2.4 — Extract `rebate-service`

**Business domain:** Behavior-triggered rebate issuance — issuing credit rewards for user behaviors (sign-in, activity completion, etc.).

### What moves

**Domain layer:**
- `com.dyx.market.domain.rebate` — `IBehaviorRebateService`, `BehaviorRebateService`

**Trigger layer:**
- `RebateMessageConsumer` — moves to `message-job-service` (Phase 2.1), calls `IBehaviorRebateService` via Dubbo
- `RebateServiceRPC` — the existing Dubbo RPC provider (`com.dyx.market.trigger.rpc.RebateServiceRPC`) stays in this service as the Dubbo exposure layer

**Infrastructure / tables:**
- `user_behavior_rebate_order` — tracks rebate orders per user per behavior
- `daily_behavior_rebate` — rebate rule config per behavior type

### Interface exposure
`rebate-service` is already Dubbo-exposed via `RebateServiceRPC` implementing `IRebateService` from `big-market-api`. This is the cleanest extract because the interface already exists.

### Port: 8088

### Dependencies
`BehaviorRebateService` calls `ICreditAdjustService` (account-service) to issue the rebate as credit. Same dependency as fulfillment-service. Coordinate extraction order or accept the cross-service Dubbo call.

---

## 10. Phase 2.5 — Rename and Refine `raffle-service` (market-service remainder)

After extracting message-job, account, fulfillment, and rebate services, what remains in `market-service`:

**HTTP controllers:**
- `RaffleActivityController` — activity participation, stage queries, activity sku queries
- `RaffleStrategyController` — strategy raffle, award listing, strategy assembly
- `ErpOperateController` — admin ERP: activity config, strategy rule config
- `DCCController` — dynamic config control

**Domain:**
- `com.dyx.market.domain.strategy` — full raffle strategy engine (rule chains, decision trees, strategy assembly)
- `com.dyx.market.domain.activity` — raffle participation (`IRaffleActivityPartakeService`), activity armory, stage service
- `com.dyx.market.domain.task` — task service for reliable async publishing

**Elasticsearch queries:**
- `big-market-queries` module — `IRaffleActivitySkuQuery` ES query interface

**Rename to `big-market-raffle-service`** (port remains 8083). This signals that the service now has a focused boundary: raffle strategy + activity participation + ERP config. All user account management, award dispatch, and rebate processing have moved out.

**Tables exclusively owned after Phase 2:**
- `strategy`, `strategy_award`, `strategy_rule`
- `rule_tree`, `rule_tree_node`, `rule_tree_node_line`
- `raffle_activity`, `raffle_activity_count`, `raffle_activity_sku`, `raffle_activity_stage`
- `raffle_activity_order`
- `user_raffle_order`
- `task`

---

## 11. Phase 3 — Data Ownership and Dependency Cleanup

Phase 2 extracts services but they still share one MySQL instance and all tables are visible to all services. Phase 3 makes the separation real.

### 3.1 Per-service schema isolation

**Current state:** All tables live in `big_market_01` and `big_market_02` (sharded). All services connect with the same root credentials.

**Target state:** Each service connects to its own schema (or database user with restricted grants):

| Service | Schema |
|---------|--------|
| raffle-service | `big_market` (existing sharded schemas) |
| account-service | `big_market_account` |
| fulfillment-service | `big_market_fulfillment` |
| rebate-service | `big_market_rebate` |

**Migration approach (Strangler Fig):**
1. Add the new schema alongside the existing ones (no data movement yet)
2. Double-write: the extracting service writes to both the old table and the new schema
3. Backfill historical data to the new schema
4. Verify read consistency
5. Cut over reads to new schema
6. Remove old table access

**This is the riskiest phase.** Do not rush it. Each table migration needs a rollback plan.

### 3.2 Remove cross-service direct DB access

After Phase 3, no service reads another service's tables directly. All cross-domain queries go through Dubbo RPC or a read-only query projection published as an event.

### 3.3 Nacos-based dynamic gateway routing

Replace static gateway routes in `application.yml` with Nacos service discovery:
1. Each service registers with Nacos at startup (Dubbo already does this for RPC; add Spring Cloud Nacos Discovery for HTTP)
2. Gateway uses `lb://big-market-raffle-service` style URIs with `ReactiveLoadBalancerClientFilter`
3. Gateway routes update without restart when services register/deregister

---

## 12. Phase 4 — Production Readiness

These items are not prerequisites for Phase 2/3 but must be done before any service goes to a shared or production environment.

### 4.1 Circuit breakers at the gateway

Add Spring Cloud CircuitBreaker (Resilience4J) to the gateway for each downstream route:
```yaml
spring.cloud.gateway.routes:
  - id: market-route
    filters:
      - name: CircuitBreaker
        args:
          name: market-cb
          fallbackUri: forward:/fallback/market
```

Tuning: 50% failure rate threshold, 10-second window, 30-second open state.

### 4.2 Distributed tracing

Add Micrometer Tracing + Zipkin (or OpenTelemetry) to all services:
- Gateway injects trace context into downstream requests
- Each service propagates trace context through MDC and MQ message headers
- Zipkin UI shows end-to-end latency breakdown per request

Prerequisite: Phase 1.2 structured logging must be in place first.

### 4.3 Per-service rate limiting at the gateway

Currently there is no rate limiting at the gateway layer. The `big-market-starter-ratelimiter` (Redis-backed sliding window) is used inside market-service but is invisible to the gateway.

Add `RequestRateLimiter` filter to the gateway for public endpoints:
- `/api/*/auth/login`: 10 req/s per IP
- `/api/*/chatbot/ask`: 5 req/s per userId

### 4.4 Secrets management

Currently `JWT_SECRET`, `ADMIN_TOKEN`, `MYSQL_ROOT_PASSWORD`, `DEEPSEEK_API_KEY` are passed via Docker Compose environment variables or `.env` files. In production:
- Use Docker Swarm secrets or Kubernetes Secrets
- Rotate `JWT_SECRET` without service restart (requires dual-validation during rotation window)

### 4.5 Resource limits

Add `deploy.resources.limits` (CPU, memory) to each service in `docker-compose.yml`:

| Service | Memory limit | CPU limit |
|---------|-------------|-----------|
| gateway | 256m | 0.5 |
| auth-service | 256m | 0.25 |
| admin-service | 256m | 0.25 |
| chatbot-service | 512m | 0.5 |
| market-service | 1g | 1.0 |

---

## 13. Anti-Goals

The following will **not** be done regardless of architectural pressure. These are explicitly out of scope to prevent over-engineering.

| Anti-goal | Reason |
|-----------|--------|
| Event sourcing or CQRS rewrites | The existing DB-first domain model works; CQRS adds complexity without a read-scale problem to solve |
| Service mesh (Istio / Linkerd) | Overkill for a 5-8 service fleet on a single machine; add only if mTLS or canary traffic splitting is required |
| Polyglot persistence (MongoDB, Cassandra) | MySQL with DB sharding covers all current use cases; additional stores add ops burden |
| GraphQL federation | REST + Dubbo is sufficient for this use case |
| Serverless functions for MQ consumers | The Spring AMQP consumer model already provides the right abstraction |
| Splitting `big-market-domain` into per-service domain JARs | Shared domain JAR is correct for this architecture phase; split only if two services genuinely need conflicting domain models |
| Kubernetes migration before Phase 3 | Resolve data ownership first; K8s does not fix shared-DB problems |

---

## 14. Recommended Next 3 Tasks

These are the three highest-value tasks to do immediately after Phase 1 stabilization, in order.

### Task 1: Implement DLQ policy (Phase 1.2)

**File:** `big-market-market-service/src/main/resources/application.yml`

Add:
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
```

And add `RabbitMQConfig` class declaring DLX and DLQ bindings for the 4 consumer queues.

**Why first:** Eliminates the infinite-retry risk from Phase 1. Low risk, high reliability benefit, no new services.

### Task 2: Shared config via Nacos (Phase 1.2)

**Files:** `big-market-admin-service`, `big-market-chatbot-service`

Migrate `PlatformConfigService` to write to Nacos config namespace on save; chatbot-service subscribes. This fixes the most visible Phase 1 limitation: chatbot enable/disable toggle does not propagate in real time.

**Why second:** High visibility fix. Admin can toggle chatbot in the admin API and chatbot-service respects it immediately.

### Task 3: Extract `message-job-service` (Phase 2.1)

Create `big-market-message-job-service` module (port 8085). Move XXL-Job handlers and MQ consumers from market-service scan scope into the new service.

**Why third:** Smallest, cleanest extract. No domain ownership transfer, no schema migration needed. Reduces market-service startup time and scope. Validates the extraction pattern before the harder domain-ownership splits.

---

## 15. Validation Strategy for Every Future Phase

Every future phase change must pass all three layers of validation before it is considered complete.

### Layer 1: Build validation
```bash
mvn clean package -DskipTests
```
Must complete with 0 errors and 0 compilation warnings for all 20+ modules.

### Layer 2: Smoke test (regression gate)
```bash
./scripts/smoke-test-phase-1.sh
```
Must return 14/14 PASS. This test must remain green after every change, whether it's an infra config change, a new service module, or a compose file update.

As new services are added in Phase 2, extend the smoke test script with new checks for the new service's health endpoint and at least one functional endpoint.

### Layer 3: Integration validation (manual)

After each Phase 2 service extract, verify these cross-service flows end-to-end:

| Flow | Steps to verify |
|------|----------------|
| Raffle → award dispatch | POST `/api/v1/raffle/activity/draw`, confirm award record created via DB or `/api/v1/raffle/activity/query_user_activity_account` |
| Behavior → rebate credit | Trigger a sign-in rebate, confirm `user_credit_account` balance increases |
| Admin config → chatbot | POST `/api/v1/admin/config/save` to disable chatbot, POST `/api/v1/chatbot/ask`, confirm disabled response |
| Market-service MQ recovery | Restart `message-job-service`, confirm in-flight XXL-Job tasks resume without duplicate processing |

### Rollback criteria

Any phase change that causes any of the following must be reverted immediately (not patched forward):
- Smoke test drops below 14/14
- Market-service takes >90s to reach healthy state
- Any service that was healthy before the change reports unhealthy after

### Regression baseline
`big-market-app` (the original monolith, port 8098) must remain buildable at all times. Before any Phase 3 schema migration, run `big-market-app` against the same MySQL instance to confirm all original endpoints still work. This is the final regression baseline.
