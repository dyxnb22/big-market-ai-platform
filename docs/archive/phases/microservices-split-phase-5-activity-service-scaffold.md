> **Archived (2026-06-12):** Phase 1-7 historical implementation record. See `docs/MICROSERVICES.md` for current status.

# Phase 5-F — Activity-Service Dark-Launch Scaffold

> This document is the Phase 5-F deliverable: a minimal Maven module scaffold
> for the future `big-market-activity-service`, with strict boundary guardrails
> and a deterministic validator.
>
> No draw execution moves. No traffic is enabled. No remote flag is introduced.
> This batch establishes the structural boundary for the activity bounded context
> so that Phase 5-G (saga/outbox design) and Phase 7 (table ownership) can be
> planned and validated against a concrete module target.
>
> Last revised: 2026-06-11.

---

## 1. Why Activity-Service is the Next Bounded Context

`RaffleApplicationService` is the draw orchestrator. It fans out across three
in-process steps that each touch separate tables and separate domain objects:

1. `IRaffleActivityPartakeService.createOrder` — activity/account tables.
2. `IStrategyDecisionPort.performRaffle` — strategy/rule tables (Phase 5-D port).
3. `IAwardFulfillmentPort.saveUserAwardRecord` — award/task tables (Phase 5-E port).

The activity bounded context owns the largest share of these tables:
`raffle_activity`, `raffle_activity_count`, `raffle_activity_sku`,
`raffle_activity_account*`, `user_raffle_order`. It is the natural owner of
the draw orchestration lifecycle.

Establishing a dedicated service launcher now:
- Proves the scan boundary compiles cleanly without pulling trigger packages.
- Creates a target module for Phase 5-G to attach a saga/outbox design.
- Gives Phase 7 a concrete module to migrate activity table credentials into.
- Allows CI to guard that no forbidden dependencies or providers are added incrementally.

---

## 2. What Is Scaffolded in This Batch

| Artifact | Path | Purpose |
|----------|------|---------|
| Module POM | `big-market-activity-service/pom.xml` | Maven module declaration; no `big-market-trigger` dependency |
| Application class | `...activity/ActivityServiceApplication.java` | Spring Boot launcher; conservative scan scope |
| `application.yml` | `.../resources/application.yml` | Port 8090; Dubbo app `big-market-activity`; no provider scan; no remote draw/award flags |
| `logback-spring.xml` | `.../resources/logback-spring.xml` | Standard log config (matches other service modules) |
| `spring-config.xml` | `.../resources/spring-config.xml` | Spring XML import shim (matches other service modules) |
| `spring-config-token.xml` | `.../resources/spring/spring-config-token.xml` | Token bean (matches other service modules) |
| `mybatis-config.xml` | `.../resources/mybatis/config/mybatis-config.xml` | Empty type-alias config (matches other service modules) |
| Root POM entry | `pom.xml` `<modules>` | Registers `big-market-activity-service` in reactor |
| Validator | `scripts/validate-microservices-phase-5-activity-service-scaffold.sh` | Deterministic repo-only checks |
| This doc | `docs/microservices-split-phase-5-activity-service-scaffold.md` | Phase 5-F boundary record |

---

## 3. What Is Explicitly NOT Moved in This Batch

| Item | Current location | Moves when |
|------|-----------------|-----------|
| `RaffleApplicationService` | `big-market-domain/.../activity/application/` | Phase 5-G saga design approved + Phase 7 table ownership resolved |
| `RaffleActivityController` | `big-market-trigger/.../trigger/http/` | Controller/API migration plan (Phase 5-G follow-on) |
| `IRaffleActivityPartakeService` and impl | `big-market-domain/.../activity/service/partake/` | Same as above |
| Activity table mapper XMLs | `big-market-infrastructure/.../resources/mybatis/mapper/` | Phase 7 datasource isolation |
| `task` outbox table / `SendMessageTaskJob` | `big-market-message-job-service` | Outbox ownership requires saga design (Phase 5-G) |
| Any MQ consumer or XXL-Job handler | — | Not in scope; activity-service has none in this batch |
| Any `@DubboService` provider | — | Not in scope; no provider package registered |
| Any HTTP `@RestController` | — | Not in scope; no HTTP route in this batch |
| Draw execution in `market-service` | `big-market-market-service` | Remains until Phase 5-G + Phase 7 |

---

## 4. Scan / Package Boundary

`ActivityServiceApplication` uses:

```java
@SpringBootApplication(scanBasePackages = {
    "com.dyx.market.activity",
    "com.dyx.market.infrastructure"
})
```

**Included:**
- `com.dyx.market.activity` — this module's own code (currently empty beyond the launcher).
- `com.dyx.market.infrastructure` — shared infrastructure beans required for DB/Redis wiring.

**Explicitly excluded:**
- `com.dyx.market.trigger.*` — HTTP controllers, job handlers, MQ listeners.
- `com.dyx.market.domain.strategy` — strategy rule-chain/tree evaluation.
- `com.dyx.market.domain.award` — award record persistence.
- `com.dyx.market.domain.rebate` — rebate order creation.

---

## 5. Forbidden Dependencies

`big-market-activity-service` must never directly depend on:

| Forbidden module | Reason |
|-----------------|--------|
| `big-market-trigger` | Contains HTTP controllers, job handlers, MQ listeners that do not belong to the activity boundary |

The validator (`validate-microservices-phase-5-activity-service-scaffold.sh`) enforces this statically.

---

## 6. Service Port Assignment

| Service | Port |
|---------|------|
| `big-market-gateway` | 8080 |
| `big-market-auth-service` | 8081 |
| `big-market-admin-service` | 8082 |
| `big-market-market-service` | 8083 |
| `big-market-chatbot-service` | 8084 |
| `big-market-message-job-service` | 8085 |
| `big-market-account-service` | 8086 |
| `big-market-fulfillment-service` | 8087 |
| `big-market-rebate-service` | 8088 |
| `big-market-strategy-service` | 8089 |
| **`big-market-activity-service`** | **8090** |

Dubbo protocol port: `20885` (sequential after rebate=20883, strategy=20884).

---

## 7. Table Ownership — Future Phase 7 Work

All activity and account tables (`raffle_activity*`, `raffle_activity_account*`,
`user_raffle_order`, `raffle_activity_count`, `raffle_activity_sku`) remain
owned by the shared `big_market` / `big_market_01` / `big_market_02` schema.

Datasource isolation for activity-service is Phase 7 work. It requires:
- A dedicated `big_market_activity` schema or credentials.
- Migration of mapper XMLs into this module's resources.
- DB router reconfiguration for activity-specific routing keys.

No DDL or schema changes are made in this batch.

---

## 8. No RPC Provider, No HTTP Route, No MQ Consumer, No XXL-Job

In this batch:
- `dubbo.scan.base-packages` is absent from `application.yml` — no `@DubboService` is registered.
- No `@RestController` is added.
- No `@RabbitListener` or RabbitMQ consumer is added.
- No `@XxlJob` handler is added.

The only Dubbo wiring present is the registry connection (disabled by `check: false`)
and the protocol declaration, matching the convention of the other dark-launch modules.

---

## 9. Remaining Blockers Before Moving Draw Execution

### Phase 5-G — Saga / Outbox Design (NEXT)

The three-step draw orchestration (`createOrder → performRaffle → saveUserAwardRecord`)
shares a local ACID transaction boundary between steps 1 and 3. Moving any step
out of process requires:

- Choosing a saga pattern (choreography vs. orchestration).
- Designing an `IDrawOutboxPort` to replace the shared `task` table write.
- Defining rollback/compensation for partial failures (especially stock decrement vs. award persist).
- Idempotency key propagation (`user_raffle_order.order_id`) through remote steps.

No saga design is approved yet. This is the primary blocker.

### Draw Idempotency and Rollback/Compensation

Current: `subtractionAwardStock` (Redis) fires before `saveUserAwardRecord` (DB).
If the DB step fails, Redis stock is already decremented. The async DB sync
(`UpdateAwardStockJob`) would eventually over-count available stock.

Before moving draw execution cross-service, the compensation path must be designed
and validated under concurrent write load.

### Activity Table Ownership (Phase 7)

Activity-service cannot own its write path without exclusive DB credentials and
mapper XML migration. This is Phase 7 datasource isolation work.

### Task / Outbox Ownership

The shared `task` table is written inside `AwardRepository.saveUserAwardRecord`
as part of the same local transaction. Moving award persistence out of process
requires the outbox row to move too. Outbox ownership is Phase 5-G work.

### Controller / API Migration Plan

`RaffleActivityController` is both an HTTP `@RestController` and a
`@DubboService(version = "1.0")`. Migrating it to activity-service requires:
- A plan for HTTP ingress routing (gateway rule update).
- A plan for Dubbo provider cutover from `market-service` to `activity-service`.
- Staged traffic cutover gates (staging evidence, oncall approval).

### Production Cutover Gates

- Phase 5-G saga design approved.
- Activity table datasource isolation complete (Phase 7).
- Staging rehearsal with real traffic on activity-service.
- DBA approval for schema/credential changes.
- Oncall runbook for activity-service.
- All dangerous remote flags remain false until per-gate approval.

---

## 10. Explicit Non-Goals for This Batch (Phase 5-F)

1. No draw execution migration.
2. No `strategy.service.remote-decision.enabled` flag introduced.
3. No `award.service.remote-fulfillment.enabled` flag introduced.
4. No remote draw command or remote award fulfillment implementation.
5. No mapper XML migration (activity tables remain in shared infra).
6. No `RaffleApplicationService` move.
7. No `RaffleActivityController` move.
8. No MQ consumer or XXL-Job handler added to activity-service.
9. No `@DubboService` provider added to activity-service.
10. No changes to `docs/evidence/generated`.
11. No dangerous flag defaults changed.
