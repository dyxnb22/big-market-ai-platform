# big-market Microservices Evolution Roadmap

## 1. Current State (as of 2026-06-09, Phase 2.2-B9 award credit outbox E2E rehearsal gate added)

The project has completed Phase 1 (runtime split), Phase 2.1 (message-job extraction), Phase 2.2-A (account-service dark launch), Phase 2.2-B1 (read-only adapter), Phase 2.2-B remote-read validation, Phase 2.2-B2 (MQ write adapters), Phase 2.2-B3 (HTTP credit exchange write adapter), Phase 2.2-B4 (award credit path audit), Phase 2.2-B5 (award credit outbox scaffold — design and DDL only), Phase 2.2-B6 (outbox producer/consumer scaffold — disabled by default), Phase 2.2-B7 (integration validation scaffold), Phase 2.2-B8 (staging idempotency validation), and Phase 2.2-B9 (controlled E2E rehearsal + production promotion gate). Seven independently deployable Spring Boot launchers run behind an API gateway. Re-run the smoke test in the current local environment before treating the runtime state as current.

**Running services:**

| Service | Port | Responsibility |
|---------|------|---------------|
| `big-market-gateway` | 8080 | Spring Cloud Gateway — routes all external traffic |
| `big-market-auth-service` | 8081 | Stateless JWT login + token verify |
| `big-market-admin-service` | 8082 | Platform runtime config (file-backed) |
| `big-market-market-service` | 8083 | HTTP APIs + Dubbo RPC (raffle / activity / strategy / rebate / credit / ERP) |
| `big-market-chatbot-service` | 8084 | AI chatbot (DeepSeek or local rule engine) |
| `big-market-message-job-service` | 8085 | MQ consumers + XXL-Job handlers |
| `big-market-account-service` | 8086 | **Dark launch** — Dubbo provider for credit + quota. Remote-read validated. MQ write consumers now route through adapters; write flags still default false. |

**Phase 1.2 changes completed (2026-06-09):**
- `spring.rabbitmq.listener.simple.default-requeue-rejected=false` in market-service (now message-job-service)
- DLX exchange + DLQ queues + bindings declared via `RabbitMQDlqConfig`
- All 4 MQ consumer queues now declare `x-dead-letter-exchange=dlx`

**Phase 2.1 changes completed (2026-06-09):**
- `big-market-message-job-service` created as new Spring Boot launcher (port 8085)
- MQ consumers (`ActivitySkuStockZeroConsumer`, `CreditAdjustSuccessConsumer`, `RebateMessageConsumer`, `SendAwardConsumer`) now run only in message-job-service
- XXL-Job handlers (`SendMessageTaskJob`, `UpdateActivitySkuStockJob`, `UpdateAwardStockJob`) now run only in message-job-service
- market-service scan narrowed to exclude `trigger.job` and `trigger.listener`
- `XxlJobAutoConfig` and `RabbitMQDlqConfig` removed from market-service
- XXL-Job executor appname: `big-market-message-job` (port 9998)

**Phase 1.2 Task 2 completed (2026-06-09):**
- `NacosConfigSyncService` in `big-market-management` (conditional on `nacos.config.sync.enabled=true`)
- Admin-service publishes full platform config to Nacos `big-market-platform-config` on every save/delete
- Chatbot-service subscribes: fetches on startup + listener for live pushes
- `PlatformConfigService` isolation issue resolved

**Phase 1.3 stability batch completed (2026-06-09):**
- `SendAwardConsumer` now propagates unexpected failures — DLQ will trigger for award processing errors; INDEX_DUP (duplicate message) is still swallowed gracefully
- Gateway circuit breakers added (Resilience4J) for all four downstream routes with stable JSON fallback responses (`code=0007`)
- Trace ID propagation: gateway injects `X-Trace-Id` header (generating one if absent); all servlet services read it into MDC for structured logging
- Smoke test extended to 16/16 (added fallback endpoint check)

**Phase 2.1 stabilization batch completed (2026-06-09):**
- Logback MDC key mismatch fixed: patterns now use `%X{traceId}` (was `%X{trace-id}`)
- `logback-spring.xml` added to auth-service, admin-service, chatbot-service, gateway
- `TraceIdFilter` added to message-job-service (actuator HTTP endpoints now carry traceId)
- `logging.config: classpath:logback-spring.xml` added to all service application.yml files
- `scripts/validate-microservices-stack.sh` added (orchestrates build + docker + smoke test)
- `scripts/smoke-test-phase-1.sh` comments updated (historically named; validates 6-service stack)
- `docs/microservices-split-phase-2-2-account-service.md` created (design-only readiness doc)

**Phase 2.2-A dark launch batch completed (2026-06-09):**
- `big-market-account-service` created as new Spring Boot launcher (port 8086)
- `IAccountCreditService` and `IAccountQuotaService` Dubbo API contracts added to `big-market-api`
- `CreditTradeRequestDTO` added to `big-market-api`
- `AccountCreditServiceRPC` and `AccountQuotaServiceRPC` Dubbo providers implemented; delegate to existing domain services unchanged
- account-service added to `docker-compose.yml` (port 8086, same infra network)
- Smoke test extended to **17/17** (7 health checks + 9 functional + 1 fallback)
- `scripts/validate-microservices-stack.sh` argument parsing bug fixed (`--skip-docker` no longer breaks HOST detection)
- `docs/microservices-split-phase-2-2-account-service.md` updated with dark-launch status and Phase 2.2-B plan
- **No traffic cutover**: market-service and message-job-service callers still use domain services in-process

**Phase 2.2-B1 read-only adapter batch completed (2026-06-09):**
- `AccountCreditServiceRPC`: null-request guard; invalid tradeName/tradeType now returns `ILLEGAL_PARAMETER` instead of leaking `IllegalArgumentException`
- `IAccountReadAdapter` interface added in `big-market-trigger`; `AccountRemoteReadAdapter` implements it in `big-market-market-service` with `@DubboReference(check=false)` for both account-service APIs
- `LocalAccountReadAdapter` added in `big-market-trigger` as `@ConditionalOnMissingBean` fallback for `big-market-app` and any host without account-service clients
- Feature flag `account.service.remote-read.enabled=false` added to market-service — defaults off; remote reads fall back to local on any failure
- `RaffleActivityController` read-only methods (`queryUserCreditAccount`, `queryUserActivityAccount`) wired to adapter
- `RaffleStrategyController` account count reads (`queryRaffleAwardList`, `queryRaffleStrategyRuleWeight`) wired to adapter
- All write paths (draw, creditPayExchangeSku, calendarSignRebate, MQ consumers) unchanged
- Smoke test remains **17/17** (no behaviour change with flag=false)
- **No traffic flowing to account-service yet** — flag defaults false

**Phase 2.2-B validation/write-scaffold batch completed (2026-06-09):**
- `docker-compose.yml` now wires `ACCOUNT_SERVICE_REMOTE_READ_ENABLED=false` into market-service explicitly
- `scripts/validate-account-remote-read.sh` validates remote-read=true, checks the four read endpoints, proves `AccountRemoteReadAdapter` logs, stops account-service for one fallback check, restarts it, and restores remote-read=false
- `AccountRemoteReadAdapter` logs remote successes as well as fallbacks/non-success responses
- Write-path feature flags added with defaults false:
  - `account.service.remote-credit-write.enabled=false`
  - `account.service.remote-quota-write.enabled=false`
- Trigger-level write adapter interfaces/local defaults and market-service remote-capable adapters were added, but no caller was routed through them yet
- `IAccountQuotaService` now has quota write RPC scaffold methods (`createOrder`, `updateOrder`) with provider implementations in account-service
- **No write traffic is cut over by default**

**Phase 2.2-B2 write-path adapter scaffold completed (2026-06-09):**
- `CreditAdjustSuccessConsumer` now injects `IAccountQuotaWriteAdapter` (was `IRaffleActivityAccountQuotaService`) and calls `updateOrder` through the adapter
- `RebateMessageConsumer` now injects `IAccountQuotaWriteAdapter` and `IAccountCreditWriteAdapter` (was `IRaffleActivityAccountQuotaService` + `ICreditAdjustService`) and calls `createOrder` through adapters
- `AccountRemoteCreditWriteAdapter` and `AccountRemoteQuotaWriteAdapter` created in `big-market-message-job-service` config — active only when `remote-credit-write.enabled=true` / `remote-quota-write.enabled=true`
- Local adapter fallbacks (`LocalAccountCreditWriteAdapter`, `LocalAccountQuotaWriteAdapter`) registered in message-job-service via `WriteAdapterLocalConfig` (`@Bean @ConditionalOnMissingBean`) — NOT via package scan, to avoid `@Component`/`@ConditionalOnMissingBean` ordering issues
- Dubbo enabled in message-job-service (was `dubbo.enabled: false`); registry `check=false` prevents startup failure when nacos is unavailable and write flags are false
- `docker-compose.yml` now wires `DUBBO_REGISTRY_ADDRESS=nacos://nacos:8848` into message-job-service
- `scripts/validate-account-remote-write-scaffold.sh` added — checks default flags, health, and optional recreation with flags=true; does NOT publish real MQ messages
- **Write flags still default false** — all consumer writes still use local domain services in-process
- **Full production write traffic cutover still requires:** MQ idempotency verification, business-flow validation end-to-end, and deliberate flag enable per service instance

**Phase 2.2-B3 HTTP credit exchange adapter wiring completed (2026-06-09):**
- `RaffleActivityController.creditPayExchangeSku` now routes through adapters:
  - Step 1 (quota order): `raffleActivityAccountQuotaService.createOrder` → `accountQuotaWriteAdapter.createOrder`
  - Step 2 (credit payment): `creditAdjustService.createOrder` → `accountCreditWriteAdapter.createOrder`
- Unused direct `IRaffleActivityAccountQuotaService` and `ICreditAdjustService` fields removed from the controller
- outBusinessNo flow unchanged: quota adapter returns `unpaidActivityOrder.outBusinessNo`, which credit adapter reuses
- Local behavior unchanged when both flags are false (adapters delegate to same domain services)
- Both write adapters are already present in market-service config (`AccountRemoteQuotaWriteAdapter`, `AccountRemoteCreditWriteAdapter`) from Phase 2.2-B validation; no new market-service adapter code needed

**Phase 2.2-B4 award credit path audit completed (2026-06-09):**
- `UserCreditRandomAward` call chain fully mapped: does NOT call `ICreditAdjustService`; calls `IAwardRepository.saveGiveOutPrizesAggregate` which directly writes `user_credit_account` via `IUserCreditAccountDao`
- `AwardRepository.saveGiveOutPrizesAggregate` refactored: `buildCreditAccountReq()` and `updateOrCreateCreditAccount()` private methods extracted; no behaviour change
- Remote adapter wiring intentionally deferred: `user_credit_account` write and `user_award_record` write share one `transactionTemplate.execute()` block — splitting creates partial-success risk without saga/outbox
- Section 4.1 corrected: `UserCreditRandomAward` removed from `ICreditAdjustService` caller list; full analysis added in Phase 2.2-B4 section
- `scripts/validate-award-credit-path.sh` added: 8 static checks asserting call-chain invariants
- Code behaviour unchanged; write flags remain false

**Phase 2.2-B5 award credit outbox scaffold completed (2026-06-09):**
- Outbox strategy designed: dedicated `credit_award_task` table (not reusing generic `task` table — different dispatch semantics; needs Dubbo RPC poller, not MQ publish poller)
- Proposed DDL added: `docs/sql/proposed-credit-award-task-outbox.sql` — sharded 4 tables (`_000.._003`), `UNIQUE KEY uq_award_order_id (user_id, award_order_id)` as idempotency key; **not wired to any production code**
- B5 design note added to `docs/microservices-split-phase-2-2-account-service.md`: transaction boundary, outbox fields, idempotency key, retry model, rollback/compensation, and explicit prohibition on direct adapter wiring before outbox/saga
- `scripts/validate-award-credit-outbox-readiness.sh` added: 8 static checks; updated in B6 to allow expected scaffold class references
- **Runtime behaviour unchanged** — no Java source modified; `AwardRepository` still uses direct `userCreditAccountDao` write

**Phase 2.2-B6 award credit outbox producer/consumer scaffold completed (2026-06-09):**
- `CreditAwardTask` PO and `ICreditAwardTaskDao` added to `big-market-infrastructure`
- `credit_award_task_mapper.xml` added to big-market-app, message-job-service, and account-service mapper directories
- `AwardRepository.saveGiveOutPrizesAggregate` now carries a flag-guarded outbox branch:
  - `flag=false` (default): Redis lock → dbRouter → transactionTemplate → `updateOrCreateCreditAccount` + `updateAwardRecordCompletedState` (**unchanged**)
  - `flag=true` (disabled): inserts `credit_award_task` outbox row inside same transaction; `updateOrCreateCreditAccount` NOT called
- `DispatchCreditAwardTaskJob` consumer added to message-job-service: `@ConditionalOnProperty(account.award-credit-outbox.enabled)` — NOT instantiated when flag=false; polls pending outbox rows and calls `IAccountCreditWriteAdapter.createOrder(awardOrderId)`
- `TradeNameVO.AWARD_CREDIT` enum value added for consumer dispatch
- Feature flag `account.award-credit-outbox.enabled=false` added to message-job-service and big-market-app configs
- `scripts/validate-award-credit-outbox-b6.sh` added: 17 static checks for B6 scaffold invariants
- **Runtime behaviour unchanged** — all flags default to false; no `credit_award_task` table access at startup or runtime
- DDL is still proposed-only; `activity_id` and `strategy_id` removed from schema (not carried by `GiveOutPrizesAggregate`)

**Phase 2.2-B7 award credit outbox integration validation scaffold completed (2026-06-09):**
- `scripts/validate-award-credit-outbox-integration.sh` added — safe local/staging integration scaffold
- Default mode: 9 static preflight checks + Docker health/flag/table checks (no data modified, no services restarted)
- `APPLY_LOCAL_OUTBOX_DDL=true` branch: applies `docs/sql/proposed-credit-award-task-outbox.sql` to local Docker MySQL only; blocked for non-localhost hosts; verifies three-digit suffix `_000.._003` after creation
- `RUN_FLAG_TRUE_VALIDATION=true` branch: recreates `big-market-message-job-service` with `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true`; verifies clean startup + `DispatchCreditAwardTaskJob` registration; restores `flag=false` via EXIT trap on any exit path
- Manual integration steps documented (Steps A-F): test row insert → XXL-Job trigger → state transition → idempotency re-trigger; not auto-executed (XXL-Job cannot be triggered safely by script)
- **B7 is a validation scaffold only — no production cutover; all flags remain false by default**
- **No Java code changes** — B7 adds scripts/docs and exposes the outbox env var in Docker Compose; B6 scaffold is unchanged

**Phase 2.2-B8 award credit outbox staging idempotency validation completed (2026-06-09):**
- `scripts/validate-award-credit-outbox-staging-idempotency.sh` added — machine-verifiable staging + idempotency checkpoint script
- Static checks (13): state machine mapper correctness (`updateDispatched`/`updateRetryFailed`/`queryPendingTasks`), `outBusinessNo = task.getAwardOrderId()` forwarding, `user_credit_order.out_business_no` UNIQUE KEY, `DuplicateKeyException` handler in `CreditRepository`, `TradeNameVO.AWARD_CREDIT` enum, handler names, shard coverage
- Docker read-only checks (up to 13 if MySQL running): `user_credit_order_000..003` and `credit_award_task_000..003` table presence in both shard DBs
- Write-mode (`STAGING_IDEMPOTENCY_WRITE=true`, localhost only, 4 checks): inserts test outbox row, verifies `state=pending`, confirms duplicate INSERT is blocked by `UNIQUE KEY uq_award_order_id`, verifies no `user_credit_order` row exists for the test `award_order_id`; cleans up test row via EXIT trap
- Bugs found and fixed: check 4 initial grep used literal `< 5` but XML mappers use `&lt; 5`; check 29 initial approach captured stderr via `2>&1` but `mysql_exec` suppresses stderr — both fixed before commit
- Manual staging checklist printed (Steps 1–7): DDL → flag=true → XXL-Job registration → insert test row → trigger handler → verify pending→dispatched → re-trigger idempotency → restore flag=false
- Rollback guidance: restore `flag=false`, re-apply DDL if UNIQUE KEY missing, escalate if double `user_credit_order` row observed
- **B8 is a validation scaffold only — no production cutover; all flags remain false by default**
- **No Java code changes** — B8 adds script and updates docs only

**Phase 2.2-B9 award credit outbox E2E rehearsal gate completed (2026-06-09):**
- `scripts/validate-award-credit-outbox-e2e-rehearsal.sh` added — controlled local/staging E2E rehearsal + production promotion gate
- Default mode: 11 static checks + Docker read-only checks (dry-run; no writes, no flag changes)
- Static checks: promotion-gate invariants covering flag default, `@ConditionalOnProperty` guard, `outBusinessNo = task.getAwardOrderId()` forwarding, success/failure state-machine paths, `queryPendingTasks` filter alignment, retry exhaustion boundary, handler name declarations, shard coverage, `DuplicateKeyException` handler, and `user_credit_order.out_business_no` UNIQUE KEY
- Docker read-only checks: service health for `big-market-message-job-service` and `big-market-account-service`, MySQL reachability, outbox table presence in both shard DBs, account ledger table presence
- `B9_E2E_REHEARSAL=true` mode (localhost only): DDL pre-check → enable `flag=true` → confirm container env → insert test outbox row → try XXL-Job auto-trigger via admin API → poll for `pending→dispatched` → verify exactly 1 `user_credit_order` row → reset to `pending` → re-trigger → verify count still 1 (no double-credit) → restore `flag=false` via EXIT trap + cleanup test row
- XXL-Job auto-trigger: best-effort via `POST /xxl-job-admin/login` + `pageList` (find job ID by handler name) + `triggerJob`; falls back gracefully to PAUSE/MANUAL step with clear instructions when admin unreachable or job not yet registered
- `B9_MANUAL_TRIGGERED=true` skips interactive pauses for CI/scripted runs
- `B9_POST_CHECK=true`: read-only post-manual-trigger check (outbox state, ledger count, idempotency confirmation)
- `B9_CLEANUP=true`: explicit cleanup mode removes only B9 test rows; localhost only
- Production promotion gate checklist documented: B4..B9 automated gates + staging manual steps + blocked items + rollback steps
- **B9 is the final automated gate before production enablement — all flags remain false by default**
- **No Java code changes** — B9 adds script and docs only

**Known residual issues:**
- Docker runtime validation should be re-run before any cutover — run `./scripts/validate-microservices-stack.sh` to confirm 17/17
- Remote-read script depends on local test data for `userId=xiaofuge` and `activityId=100301`; override with `ACCOUNT_REMOTE_READ_USER_ID` / `ACCOUNT_REMOTE_READ_ACTIVITY_ID` if needed
- Static gateway routing (no service-discovery integration; account-service has no gateway route — Dubbo/internal only)
- All market-service tables share one MySQL instance with no per-service schema isolation
- MQ DLQ behavior is not covered by the smoke test (requires real RabbitMQ integration test)
- `credit_award_task_000..003` tables not yet applied to any environment — apply `docs/sql/proposed-credit-award-task-outbox.sql` before enabling `flag=true`; use `APPLY_LOCAL_OUTBOX_DDL=true` for local validation
- XXL-Job handlers `DispatchCreditAwardTaskJob_DB1/_DB2` must be registered in XXL-Job admin before any `flag=true` staging test
- Replay idempotency (Steps C-E in B7 script) must pass in staging before production promotion
- `RaffleActivityPartakeService` quota decrement: deferred, high risk — needs purpose-built decrement RPC before any cutover attempt
- MQ idempotency end-to-end verification and business-flow validation still required before enabling write flags
- See `docs/microservices-split-phase-2-2-account-service.md` Phase 2.2-B9 section for E2E rehearsal flow, promotion gate criteria, rollback steps, and remaining risks

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

**Status: Done. Phase 1.2 reliability cleanup also complete (2026-06-09).**

All five services deploy from the root `docker-compose.yml`. Startup is two commands:

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
```

Verification: `./scripts/smoke-test-phase-1.sh` expects 16/16 PASS (6 health checks + 9 functional checks + 1 fallback endpoint check). Use `./scripts/validate-microservices-stack.sh` to orchestrate build + docker + smoke in one command. This is the acceptance gate for current runtime validation, not a guarantee that the local Docker stack is running right now.

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
Phase 2.2-B4 audit found that `UserCreditRandomAward` does **not** call `ICreditAdjustService`; it builds a `GiveOutPrizesAggregate`, and `AwardRepository.saveGiveOutPrizesAggregate` directly updates `user_credit_account` and `user_award_record` inside one local transaction. Extracting fulfillment-service therefore requires a saga or transactional outbox before credit-award writes can move to account-service safely.

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

### ~~4.1 Circuit breakers at the gateway~~ DONE (Phase 1.3, 2026-06-09)

Resilience4J circuit breakers are active on all four gateway routes (`auth-cb`, `admin-cb`, `chatbot-cb`, `market-cb`). Fallback returns `{"code":"0007","info":"网关接口调用失败","data":null}`. Tuning: 50% failure rate, 10-request sliding window, 10s open-state wait.

### 4.2 Distributed tracing

Lightweight trace ID propagation complete (Phase 1.3 + Phase 2.1 stabilization batch):
- `X-Trace-Id` injected by gateway, forwarded to all downstream services
- All servlet services (auth, admin, market, chatbot, message-job) read it into MDC as `traceId`
- All logback-spring.xml files now use `%X{traceId}` pattern (MDC key mismatch fixed in stabilization batch)
- Gateway (WebFlux) logs do not carry `traceId` in MDC — Reactor context bridging required for that; deferred

Full distributed tracing (Micrometer Tracing + Zipkin/OpenTelemetry) is still pending:
- End-to-end latency breakdown in a tracing UI
- MQ message header trace propagation
- Prerequisite: structured JSON log format per service

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

### ~~Task 1: Implement DLQ policy (Phase 1.2)~~ DONE (2026-06-09)

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

### ~~Task 2: Shared config via Nacos (Phase 1.2)~~ DONE (2026-06-09)

`NacosConfigSyncService` added to `big-market-management` (conditional on `nacos.config.sync.enabled=true`).
Admin-service publishes the full config to Nacos dataId `big-market-platform-config` on every save/delete.
Chatbot-service fetches current config from Nacos on startup and registers a listener for live updates.
Both services connect to `nacos:8848` in Docker via `NACOS_HOST` env var.

### ~~Task 3: Extract `message-job-service` (Phase 2.1)~~ DONE (2026-06-09)

`big-market-message-job-service` created on port 8085. MQ consumers and XXL-Job handlers moved from market-service scan scope. Market-service is now HTTP + Dubbo only.

### Task 4: Confirm Docker runtime and extract account-service (Phase 2.2)

**Next up.** Gate: `./scripts/validate-microservices-stack.sh` returns 16/16 PASS.

Design doc: [docs/microservices-split-phase-2-2-account-service.md](microservices-split-phase-2-2-account-service.md)

Proposed: `big-market-account-service` on port 8086, owning `domain.credit` + `domain.activity.service.quota` + five account tables. Adapter wiring is already in place for MQ consumer writes and `creditPayExchangeSku`; `UserCreditRandomAward` requires saga/outbox design before remote credit writes, and `RaffleActivityPartakeService` still needs a purpose-built quota decrement RPC before cut-over.

---

## 15. Validation Strategy for Every Future Phase

Every future phase change must pass all three layers of validation before it is considered complete.

### Layer 1: Build validation
```bash
mvn clean package -DskipTests
```
Must complete with 0 errors and 0 compilation warnings for all Maven modules. As of Phase 1, the root `pom.xml` declares 19 modules.

### Layer 2: Smoke test (regression gate)
```bash
# Quick (smoke only):
./scripts/smoke-test-phase-1.sh

# Full orchestration (build + docker + smoke):
./scripts/validate-microservices-stack.sh
```
Must return 16/16 PASS (as of Phase 2.1 stabilization). This test must remain green after every change.

As new services are added in Phase 2, extend the smoke test script with new checks for the new service's health endpoint and at least one functional endpoint. The script is historically named `smoke-test-phase-1` for backwards compatibility.

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
