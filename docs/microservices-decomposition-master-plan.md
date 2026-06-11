# big-market Microservices Decomposition Master Plan

> Scope: end-to-end repo plan covering remaining Phase 3 work through final
> production-ready service ownership. This document supersedes the open-ended
> tail of `docs/microservices-roadmap.md` as the single coordinating plan.
> It is planning-only. No Java behavior changes, no DDL, no traffic enablement.

Last revised: 2026-06-11.
Status anchor: Phase 4-D/E/F complete (tag `phase-4-strategy-read-adapter-boundary`). Phase 3 complete. Phase 4 complete. Phase 5-A orchestration map complete (tag `phase-5-activity-draw-orchestration-map`). Phase 5-B draw-command boundary design doc complete. Phase 5-C account/quota port re-verification complete. Phase 5-D local strategy decision port introduced (tag `phase-5-strategy-decision-port-boundary`). Phase 5-E local award fulfillment port introduced (tag `phase-5-award-fulfillment-port-boundary`). Phase 5-F activity-service dark-launch scaffold introduced (tag `phase-5-activity-service-dark-launch-scaffold`): big-market-activity-service module at port 8090; scan boundary enforced; no draw execution moved; no RPC provider, HTTP controller, MQ consumer, or job handler added; no remote flag introduced. Phase 5-G draw saga/outbox design complete (tag `phase-5-activity-draw-saga-outbox-scaffold`): orchestration saga pattern chosen; IDrawOutboxPort + DrawOutboxEvent + LocalDrawOutboxPort scaffold contracts introduced; design doc committed; IDrawOutboxPort NOT wired into draw hot-path (requires Phase 7-D DDL + Phase 8-E approval); no remote flags introduced. Phase 5 complete. Phase 6-A DAO ownership matrix complete (tag `phase-6-dao-ownership-matrix`). Phase 6-B package-ownership boundary validator complete (tag `phase-6-package-ownership-boundaries`). Phase 7-A/7-B predecessor batches complete through tag `phase-7-award-credit-outbox-boundary`. Phase 7-C complete: proposed per-domain task outbox DDL exists for `rebate_task_outbox`, `credit_trade_task_outbox`, and `award_dispatch_task_outbox`; AL-8/AL-9/AL-10 direct repository `ITaskDao` couplings are resolved through task-outbox ports while local adapters preserve legacy `ITaskDao` behavior. Phase 7-E complete: DB users/grants plan is documented. Phase 7-F complete: sharded schema isolation plan is documented. Phase 7 is repo-complete (tag `phase-7-complete-phase-8-readiness`); physical runtime table isolation remains Phase 8 external-gated. Phase 8 status: repo readiness complete / external cutover gated; production cutover is not complete without DBA/Ops/Engineering/Oncall/Product evidence.

---

## 1. Executive Summary

The codebase has finished a runtime split (Phase 1) and the dark-launch /
adapter / outbox / cutover-readiness work for `account-service` and
`fulfillment-service` (Phase 2). Phase 3 has started with the
`big-market-rebate-service` boundary: dark-launch module, write-path adapter
for `calendarSignRebate`, and a `@ConditionalOnProperty` ownership gate on the
legacy `trigger.rpc.RebateServiceRPC` provider.

That is **not** the end of decomposition. The repository still sits on top of
five large coupling points: a shared `big-market-domain` jar, a shared
`big-market-infrastructure` jar, a shared `big-market-trigger` module that
mixes HTTP / RPC / listener / job / adapter code, a shared generic `task`
outbox table, and a `RaffleApplicationService` orchestration that fans into
activity, strategy, and award in-process.

"Done" for this program is the conjunction of five orthogonal completions.
This plan separates them deliberately because conflating them is how
microservice migrations slip:

| Dimension | What "done" means | Current state |
|-----------|-------------------|---------------|
| Runtime split | Each service is its own Spring Boot launcher with bounded `scanBasePackages` | Done for the 8 services that exist; 4 service launchers still scan the full `domain` / `infrastructure` |
| Code ownership split | Each bounded context lives in (or is exclusively scanned by) the owning service module | Partial. Rebate, account, fulfillment have provider modules but still share `big-market-domain` / `big-market-infrastructure` jars |
| Data ownership split | Tables, outbox tables, and DB credentials are owned by exactly one service | Not started. All services share `big_market_01` / `big_market_02` and the generic `task` table |
| Traffic cutover | Remote flags flipped on; legacy providers disabled; new service takes production traffic | Blocked. All remote flags default false; B17 / B18 / fulfillment B23-C staging steps are still pending in [[project_microservices_phase1]] |
| Production readiness | Per-service rate limits, secrets, resource limits, distributed tracing, alerting, runbooks | Partial. Tracing header in place; resource limits and per-service rate limits not done |

A green Maven build, green smoke test, and a green validator script prove
only the **runtime split** and the **code ownership split** layers. Data
ownership and traffic cutover require staging windows that this batch
explicitly does not perform.

---

## 2. Current State Inventory

### 2.1 Service launchers (8 + gateway)

| Module | Port | Phase | State |
|--------|------|-------|-------|
| `big-market-gateway` | 8080 | 1 | Stable. Circuit breakers + traceId in place |
| `big-market-auth-service` | 8081 | 1 | Stable |
| `big-market-admin-service` | 8082 | 1 | Stable |
| `big-market-chatbot-service` | 8084 | 1 | Stable |
| `big-market-market-service` | 8083 | 1 | HTTP API + legacy Dubbo providers (raffle / activity / strategy / rebate / credit / ERP) |
| `big-market-message-job-service` | 8085 | 2.1 | Owns MQ consumers + XXL-Job handlers |
| `big-market-account-service` | 8086 | 2.2 | Dark launch. Read remote flag validated; write flags default false |
| `big-market-fulfillment-service` | 8087 | 2.3 | Dark launch. No callers wired |
| `big-market-rebate-service` | 8088 | 3 | Dark launch. Local rebate adapter wired in market-service controller; remote flag default false; legacy provider ownership gated |

### 2.2 Shared modules (still monolithic in shape)

| Module | Role | Coupling concern |
|--------|------|------------------|
| `big-market-domain` | All bounded contexts in one jar (`activity`, `strategy`, `award`, `credit`, `rebate`, `task`, `auth`) | Any service that scans `com.dyx.market.domain.*` pulls every context |
| `big-market-infrastructure` | All DAOs, repositories, Redis clients, MQ publisher, ES gateway in one jar | Table ownership is convention only; no Maven boundary |
| `big-market-trigger` | HTTP controllers, Dubbo RPC providers, MQ listeners, XXL-Job handlers, local/remote adapters | Trigger jar is shared even though most services only need a subset |
| `big-market-api` | External Dubbo contracts and DTOs | Contract surface OK; ownership of each `Ixxx` is not aligned to a service module |
| `big-market-types` | Response codes, exceptions, enums | Acceptable shared kernel |
| `big-market-queries` | Elasticsearch query interfaces (raffle SKU) | Only used by market-service today |
| `big-market-starter-*` | DB router, DCC, rate limiter | Cross-cutting library code, fine to keep shared |

### 2.3 Bounded contexts (today)

`big-market-domain/src/main/java/com/dyx/market/domain` currently contains:
`activity`, `strategy`, `award`, `credit`, `rebate`, `task`, `auth`.

### 2.4 Known service boundary intentions

| Bounded context | Target service | Status |
|-----------------|----------------|--------|
| credit | account-service | Dark-launch provider exists, write flags off |
| activity.quota | account-service | Quota-decrement ledger + saga done; flag off |
| award | fulfillment-service | Dark-launch provider exists; saga via credit_award_task outbox; outbox flag off |
| rebate | rebate-service | Dark-launch provider exists; remote-create-order flag off; legacy provider gate added |
| strategy | strategy-service (not created) | No module yet; Phase 4 target |
| activity (draw/partake/orchestration) | activity-service (not created) | Highest risk; Phase 5 target |
| task / outbox | per-domain outbox tables | Phase 7-B decision complete; runtime still uses shared `task` until migration |
| auth | auth-service | Stateless; only JWT verify and login |
| admin / config | admin-service | Stable |
| chatbot | chatbot-service | Stable |
| query/search (ES) | strategy-service or activity-service | Decision deferred to Phase 4/5 |

### 2.5 Coupling hotspots

1. `RaffleApplicationService` directly imports activity, strategy, award and orchestrates the draw flow in-process.
2. `AwardRepository.saveGiveOutPrizesAggregate` writes credit data through `IAwardCreditWritePort` and updates `user_award_record` in one local transaction; runtime credit-award outbox traffic remains flag-gated and default false.
3. `RaffleActivityController` directly injects `IBehaviorRebateService` for the read path (`isCalendarSignRebate`); only the write path is adapter-wired today.
4. `BehaviorRebateRepository.saveUserRebateRecord` writes the rebate order plus a row in the generic `task` outbox shared across domains.
5. `message-job-service` and `account-service` still scan the full `com.dyx.market.domain` and `com.dyx.market.infrastructure` packages.
6. `big-market-market-service` still depends on `big-market-trigger` and scans `trigger.http` + `trigger.rpc`, which keeps the legacy `IRebateService` provider alive (now `@ConditionalOnProperty`-gated, but still part of the jar).

---

## 3. Definition of Done for the Full Decomposition

Tracked as a checklist. Each item is independently verifiable.

1. **Bounded context ownership** — every context in §2.3 has a single owning service or is **explicitly accepted as shared** in this document with a written reason. No "default" owners.
2. **Controller dependency rule** — no HTTP controller in any service module depends directly on a foreign bounded context's domain service. Cross-context calls go through `*Adapter` interfaces with a local fallback and a remote (Dubbo) implementation.
3. **Bounded scan packages** — every service launcher's `scanBasePackages` lists only the contexts it owns (plus shared infrastructure required for DB / MQ / Redis bootstrap).
4. **Remote flags default false** — all `*.remote-*.enabled` flags default false until an explicit cutover batch flips them in staging-evidence-backed Phase 8 sub-batches.
5. **Legacy provider disablement** — every legacy Dubbo provider in `big-market-trigger.rpc` has a `@ConditionalOnProperty` gate, and after cutover the corresponding `*.legacy-rpc-provider.enabled` flag defaults false.
6. **Table ownership documented** — `docs/microservices-data-ownership.md` (Phase 7 deliverable) maps every physical table to exactly one owning service. Sharing is explicit and gated.
7. **Outbox ownership resolved** — the shared `task` table either splits into service-specific outboxes or is explicitly owned by a `message-outbox-service`; rebate and award outbox writes do not silently share the table.
8. **Boundary validators** — for each bounded context with a service module, a `scripts/validate-microservices-phase-N-*.sh` script asserts the module wiring, scan packages, mapper resources, forbidden trigger / domain dependencies, default flags, and provider contract.
9. **Cutover approval gates** — each production traffic flip requires a separate approval batch with its own evidence template; repo-only batches never enable remote traffic.
10. **Regression baseline preserved** — `big-market-app` remains buildable and runnable as a single-process fallback throughout decomposition.

---

## 4. Phase Plan

Phases below cover **remaining** work. Phase 1, Phase 2.1, Phase 2.2 (up to
B21), Phase 2.3 (up to B23-D / E scaffolds), and Phase 3 batches 1–5 are
already done — see `docs/microservices-roadmap.md` for that history.

### 4.1 Phase 3 — Rebate-Service Completion (REPO-READY)

All Phase 3 repo-only sub-batches are complete. Traffic cutover is Phase 8 work.

| Sub-batch | Title | Type | Default flag behavior | Status |
|-----------|-------|------|------------------------|--------|
| 3-A | Rebate read adapter boundary (`isCalendarSignRebate`) | adapter | `rebate.service.remote-read.enabled=false` | **Done** — tag `phase-3-rebate-read-adapter-boundary` |
| 3-B | Rebate read contract in `big-market-api` (`IRebateService.isCalendarSignRebate`) | API | n/a (interface) | **Done** — included in 3-A batch |
| 3-C | Rebate dependency narrowing audit | validator | n/a | **Done** — `scripts/validate-microservices-phase-3-rebate-dependency-narrowing.sh` |
| 3-D | Rebate task/outbox ownership decision document | docs | n/a | **Done** — `docs/microservices-split-phase-3-rebate-outbox-ownership.md` |
| 3-E | Rebate cutover-readiness rehearsal script (dry-run; no DDL, no traffic) | validator | flags remain false | **Done** — `scripts/validate-microservices-phase-3-rebate-cutover-readiness.sh` |

Exit criteria met: read + write adapter contracts complete, both remote flags default false,
legacy provider gate present (`matchIfMissing=true`), all validators green, outbox ownership
decision recorded (Option A for Phase 3; Phase 7-C tracks the `rebate_task_outbox` DDL proposal).

**Remaining blockers before Phase 8-C rebate traffic cutover:**
1. Staging provider verification (Nacos, external).
2. Legacy provider disablement (`REBATE_LEGACY_RPC_PROVIDER_ENABLED=false` on market-service).
3. Shared `task` outbox replaced by `rebate_task_outbox` (Phase 7-C, DBA window required).
4. `RebateMessageConsumer` ownership decision (Phase 7-B/8-C).
5. Per-service datasource enforcement (Phase 7-E/F).
6. DBA + Ops + Engineering + Oncall sign-off (Phase 8 approval gate).

### 4.2 Phase 4 — Strategy-Service Read-First Extraction

Read-first because the draw path writes are owned by `RaffleApplicationService`
orchestration; pulling the strategy reads out first is safe and de-risks the
later activity/draw split.

| Sub-batch | Title | Type | Status |
|-----------|-------|------|--------|
| 4-A | Strategy boundary assessment doc | docs | **Done** — `docs/microservices-split-phase-4-strategy-service.md` |
| 4-B | Strategy read-only API contract in `big-market-api` | API | **Done** — `IStrategyReadService` with `queryRaffleAwardList` + `queryRaffleStrategyRuleWeight` |
| 4-C | `big-market-strategy-service` dark-launch module | module | **Done** — port 8089, Dubbo port 20884, `StrategyReadServiceRPC` provider; `strategy.service.remote-read.enabled=false` |
| 4-D | Market-service read adapters (`IStrategyReadAdapter` + local + remote) | adapter | **Done** — `strategy.service.remote-read.enabled=false`; tag `phase-4-strategy-read-adapter-boundary` |
| 4-E | Strategy scan / mapper / dependency narrowing validator | validator | **Done** — `scripts/validate-microservices-phase-4-strategy-dependency-narrowing.sh` |
| 4-F | Strategy table ownership mapping (`strategy`, `strategy_award`, `strategy_rule`, `rule_tree*`) | docs | **Done** — `docs/microservices-split-phase-4-strategy-table-ownership.md`; tag `phase-4-strategy-read-adapter-boundary` |

**Non-goal in Phase 4:** moving the draw *decision* call. The draw decision
writes participation orders and triggers award fulfillment; that flow stays
in market-service until Phase 5.

**Phase 4-A/B/C exit criteria met:** read-only API contract and dark-launch module
in place; `strategy.service.remote-read.enabled` defaults false; no trigger dependency;
provider scans only `com.dyx.market.strategy.provider`; validator script green;
boundary assessment documents read-first rationale and explicit non-goals.

### 4.3 Phase 5 — Activity / Draw Orchestration Decomposition

Highest-risk phase. Sequenced so no irreversible move happens until the
saga / idempotency primitives from Phase 2.2 and the strategy read split
from Phase 4 are stable.

| Sub-batch | Title | Type | Notes |
|-----------|-------|------|-------|
| 5-A | Map `RaffleApplicationService` orchestration | docs | **Done** — `docs/microservices-split-phase-5-activity-draw-orchestration.md`; validator `scripts/validate-microservices-phase-5-activity-draw-orchestration.sh`; tag `phase-5-activity-draw-orchestration-map` |
| 5-B | Define draw-command boundary | docs | Decide: orchestration adapter inside market-service vs new `activity-service` application boundary |
| 5-C | Isolate account-quota call behind `IActivityAccountPort` (already done — re-verify post Phase 4) | validator | Confirms B11–B14 still hold |
| 5-D | Isolate strategy-decision call behind `IStrategyDecisionPort` | adapter | **Done** — local delegates to in-process `IRaffleStrategy`; no remote flag |
| 5-E | Isolate award-fulfillment call behind `IAwardFulfillmentPort` | adapter | **Done** — local delegates to in-process `IAwardService`; no remote flag |
| 5-F | Activity-service scaffold decision/prep (dark launch only if approved, no orchestration moved) | module/docs | **Done** — `big-market-activity-service` module at port 8090; scan boundary enforced; no draw execution moved; no RPC provider, HTTP controller, MQ consumer, or job handler; no remote flag introduced. Tag: `phase-5-activity-service-dark-launch-scaffold` |
| 5-G | Activity-service orchestration target and saga/outbox design | docs | **Done** — orchestration saga chosen; `IDrawOutboxPort` + `DrawOutboxEvent` + `LocalDrawOutboxPort` scaffold contracts introduced; design doc `docs/microservices-split-phase-5-activity-draw-saga-outbox.md` committed; port NOT wired into draw hot-path (requires Phase 7-D + Phase 8-E). Tag: `phase-5-activity-draw-saga-outbox-scaffold` |

**Hard rule for Phase 5:** no synchronous write call moves out of
market-service until 5-G is signed off and a saga / idempotency design is
written. Failing this rule is the documented #1 risk in §9.

### 4.4 Phase 6 — Shared Infrastructure and Domain Ownership Split

Goal: turn shared-jar coupling from a convention into a Maven boundary, but
**without massive package renames**.

| Sub-batch | Title | Type | Notes |
|-----------|-------|------|-------|
| 6-A | DAO ownership mapping (`docs/microservices-dao-ownership.md`) | docs | Maps each `IXxxDao` to its owning service |
| 6-B | Per-context package-ownership validator | validator | Static check: each service launcher's scan packages must match an allow-list; new DAOs added without owner = CI fail |
| 6-C | Optional `big-market-infrastructure-<context>` modules where justified | module | Only for contexts that already have a dark-launch service AND ≥ 4 owned DAOs |
| 6-D | Domain jar split decision (`docs/microservices-domain-split-decision.md`) | docs | Default: **do not split** `big-market-domain` (see §10 non-goals); document conditions under which a split is justified |
| 6-E | Trigger module decomposition decision | docs | Most trigger code now lives in service modules; document keeping `big-market-trigger` as legacy-only with `@ConditionalOnProperty` gates |

### 4.5 Phase 7 — Data Ownership and Outbox Boundary

| Sub-batch | Title | Type | Notes |
|-----------|-------|------|-------|
| 7-A prep (AL-4) | ActivityRepository credit-account boundary | refactor | **Done** — tag `phase-7-account-boundary-prep-activity-credit-port`; `IActivityAccountPort.queryUserCreditAccountAmount` routes credit reads; `ActivityRepository` no longer imports `IUserCreditAccountDao` |
| 7-A prep (AL-2/AL-3) | StrategyRepository account DAO removal | refactor | **Done** — tag `phase-7-account-boundary-prep-strategy-account-port`; `IStrategyActivityAccountPort` routes quota reads; `StrategyRepository` no longer imports `IRaffleActivityAccountDao` or `IRaffleActivityAccountDayDao` |
| 7-A (AL-1) | StrategyRepository activity mapping boundary | refactor | **Done** — tag `phase-7-strategy-activity-mapping-port`; `IStrategyActivityMappingPort` routes activityId ↔ strategyId reads; `StrategyRepository` no longer imports `IRaffleActivityDao`; all StrategyRepository cross-boundary couplings resolved |
| 7-A prep (AL-5) | AwardRepository activity-order boundary | refactor | **Done** — tag `phase-7-award-activity-order-boundary`; `IAwardActivityOrderPort` routes user_raffle_order create→used transition; `AwardRepository` no longer imports `IUserRaffleOrderDao` |
| 7-A prep (AL-7) | DispatchCreditAwardTaskJob credit-award task boundary | refactor | **Done** — tag `phase-7-credit-award-task-job-boundary`; `ICreditAwardTaskDispatchPort` routes `credit_award_task` reads/state transitions; `DispatchCreditAwardTaskJob` no longer imports `ICreditAwardTaskDao`; flag remains default false |
| 7-A prep (AL-6/AL-11) | AwardRepository credit outbox/write boundary | refactor | **Done** — tag `phase-7-award-credit-outbox-boundary`; `IAwardCreditWritePort` routes default credit-account writes and flag-gated `credit_award_task` inserts; `AwardRepository` no longer imports `IUserCreditAccountDao` or `ICreditAwardTaskDao`; flag remains default false |
| 7-A | Per-service table ownership matrix (extends §6 Boundary Matrix) | docs | Authoritative mapping; includes shard suffix coverage; requires all cross-boundary DAO couplings removed first |
| 7-B | Generic `task` table strategy decision | docs | **Done** — `docs/microservices-split-phase-7-task-outbox-ownership.md`; per-domain outbox/task tables chosen for AL-8/AL-9/AL-10; runtime coupling still allowlisted |
| 7-C | Rebate-specific outbox proposed DDL | docs | `rebate_task_outbox_{000..003}`; proposed-only, no DDL execution |
| 7-D | Activity-specific outbox proposed DDL | docs | Only if Phase 5 commits to async draw orchestration |
| 7-E | DB user / schema isolation plan | docs | Per-service MySQL user with grants restricted to owned tables |
| 7-F | Sharded schema isolation plan | docs | Whether per-service schemas (`big_market_account`, `big_market_rebate`, etc.) follow this milestone or wait until physical scaling pressure |

**Hard rule for Phase 7:** no DDL is executed from any repo-only batch. All
proposed DDL files live in `docs/sql/proposed-*.sql` and are applied by the
DBA in an explicit Phase 8 staging window.

**Current repo-only posture after Phase 7:** Phase 7-C/7-E/7-F are complete,
AL-1 through AL-11 direct repository DAO couplings are resolved, and runtime
physical isolation remains Phase 8 external-gated. The next repo-only work is
regression hardening: keep the completion index and aggregate validators green
until external cutover evidence exists. All runtime flags remain default false.

### 4.6 Phase 8 — Production Cutover and Legacy Cleanup

Each cutover is **one service at a time** with its own approval gate.
Sequence chosen to minimize blast radius and dependency cascade.

| Order | Service | Pre-requisite | Approval gate |
|-------|---------|---------------|---------------|
| 1 | account-service (writes) | B17 staging + B18 production templates complete | DBA + Ops + Engineering + Oncall sign-off (per [[project_microservices_phase1]]) |
| 2 | fulfillment-service | B23-C staging evidence complete; credit-award outbox DDL applied | DBA + Ops + Engineering + Oncall |
| 3 | rebate-service | Phase 3-E rehearsal complete; legacy provider gate verified | DBA + Ops + Engineering + Oncall |
| 4 | strategy-service (reads) | Phase 4-F table ownership confirmed | Engineering + Oncall (lower risk; read-only) |
| 5 | activity-service (only if Phase 5-G approved) | Saga design approved; per-step rollback runbook | DBA + Ops + Engineering + Oncall + Product |

For each cutover:

1. Apply proposed DDL (DBA window).
2. Verify with `validate-production-ddl.sh CONNECT_REMOTE=true`.
3. Register any XXL-Job handlers (Ops window).
4. Enable remote flag on a single canary instance (~15 min).
5. Validate evidence template against acceptance criteria.
6. Full rollout, or `flag=false` rollback at any anomaly.
7. After 7 days stable, set `*.legacy-rpc-provider.enabled=false` on legacy services.
8. After 30 days stable, remove legacy provider class and obsolete local paths in a follow-up batch.

**Rollback principle:** every cutover is reversible by flipping one flag. No
cutover requires DB rollback in the happy path. Quota-leak compensating SQL
is documented per service in its B-series scripts.

**Documentation cleanup principle:** when a legacy path is removed, archive
the historical phase doc under `docs/archive/` instead of editing it. Do not
expand `docs/evidence/` automation — that work was already deliberately
capped at Phase 2.2 / 2.3.

---

## 5. Batch Backlog

Each batch is described with: id, title, objective, files/modules, type,
default flag, validation, risk, dependencies, completion criteria.

### 5.1 Phase 3 — Rebate-Service Completion

**3-A — Rebate read adapter boundary**
- Objective: route `RaffleActivityController.isCalendarSignRebate` through `IRebateReadAdapter` (local default + remote stub) so the read path mirrors the write path adapter pattern.
- Files: `big-market-trigger/.../adapter/IRebateReadAdapter.java`, `LocalRebateReadAdapter.java`, `big-market-market-service/.../config/RebateRemoteReadAdapter.java`, `RaffleActivityController.java`.
- Type: adapter.
- Default flag: `rebate.service.remote-read.enabled=false`; falls back to local on remote failure.
- Validation: extend `scripts/validate-microservices-phase-3-rebate-adapter.sh` (or new `*-rebate-read-adapter.sh`) — interface present, local fallback present, controller wired, remote flag default false, fallback path covered.
- Risk: Low.
- Dependencies: none (Batch 2 / 3 already merged).
- Completion: validator passes; `mvn compile` green; no behavior change at runtime.

**3-B — Rebate read contract on `IRebateService`**
- Objective: add `queryOrderByOutBusinessNo(userId, outBusinessNo)` to `IRebateService` so the read path can flow through the existing Dubbo contract instead of a new interface.
- Files: `big-market-api/.../IRebateService.java`, `big-market-rebate-service/.../provider/RebateServiceRPC.java`, legacy `big-market-trigger/.../rpc/RebateServiceRPC.java` (mirror impl for compatibility while legacy provider remains gated true).
- Type: API.
- Default flag: n/a.
- Validation: contract surface check + compile.
- Risk: Low.
- Dependencies: 3-A.
- Completion: both providers implement the method; consumer call is feature-flag-gated by 3-A; validator extended to assert symmetric implementation.

**3-C — Rebate-service scan / dependency narrowing audit**
- Objective: confirm `RebateServiceApplication` scans only `com.dyx.market.rebate`, `com.dyx.market.domain.rebate`, and a minimal infrastructure subset; mapper XMLs limited to `daily_behavior_rebate`, `user_behavior_rebate_order`, `task`.
- Files: `big-market-rebate-service/.../RebateServiceApplication.java`, `src/main/resources/mybatis/mapper/*.xml`.
- Type: validator.
- Default flag: n/a.
- Validation: extend `validate-microservices-phase-3-next-extraction.sh`.
- Risk: Low.
- Dependencies: none.
- Completion: validator asserts allow-listed packages and mapper files.

**3-D — Rebate task / outbox ownership decision doc**
- Objective: document the decision to keep `task` table shared in Phase 3 and to introduce a dedicated `rebate_task_outbox_{000..003}` in Phase 7-C; explain why no DDL is in scope now.
- Files: `docs/microservices-split-phase-3-next-extraction.md` (append section 13) or new `docs/microservices-rebate-outbox-decision.md`.
- Type: docs.
- Default flag: n/a.
- Validation: presence check.
- Risk: Low.
- Dependencies: none.
- Completion: doc committed; referenced from master plan §7.

**3-E — Rebate cutover-readiness rehearsal script**
- Objective: dry-run-only operator script that prints the ordered cutover steps for rebate (deploy rebate-service → verify Nacos providers → set `REBATE_LEGACY_RPC_PROVIDER_ENABLED=false` on market-service → set `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true` → set `REBATE_SERVICE_REMOTE_READ_ENABLED=true`) without enabling anything.
- Files: `scripts/validate-microservices-phase-3-rebate-cutover-readiness.sh`.
- Type: validator.
- Default flag: n/a; refuses to run with any rebate remote flag = true.
- Validation: the script itself.
- Risk: Low.
- Dependencies: 3-A, 3-B, 3-C, 3-D.
- Completion: script committed; prints plan; exits 0 with all rebate remote flags false; would exit 1 if any flag flipped.

### 5.2 Phase 4 — Strategy-Service

**4-A — Strategy boundary assessment doc**
- Objective: enumerate every `domain.strategy` service and every external caller (HTTP, RPC, MQ, in-process), categorise read vs decision-write, list strategy tables, list dependencies on `domain.activity` (account count).
- Files: `docs/microservices-split-phase-4-strategy-service.md`.
- Type: docs.
- Default flag: n/a.
- Validation: presence + cross-reference check.
- Risk: Low.
- Dependencies: Phase 3 complete.
- Completion: doc committed; cross-context calls listed with line:file references.

**4-B — Strategy read-only API contract**
- Objective: define `IStrategyReadService` in `big-market-api` covering the four read endpoints used by `RaffleStrategyController` (award list, rule weight, armory, strategy award entity).
- Files: `big-market-api/.../IStrategyReadService.java`, DTOs.
- Type: API.
- Default flag: n/a.
- Validation: interface presence + DTO immutability check.
- Risk: Low.
- Dependencies: 4-A.

**4-C — `big-market-strategy-service` dark-launch module**
- Objective: new Spring Boot launcher on port `8089` exporting only the read provider. No HTTP, no MQ, no jobs.
- Files: new module `big-market-strategy-service/`, `pom.xml`, application class, application.yml, Dockerfile entry, `docker-compose.yml`.
- Type: module.
- Default flag: not registered in gateway; not wired by callers.
- Validation: new `scripts/validate-microservices-phase-4-strategy-service.sh`.
- Risk: Low.
- Dependencies: 4-B.

**4-D — Market-service strategy read adapters**
- Objective: `IStrategyReadAdapter` interface in `big-market-trigger`; `LocalStrategyReadAdapter` (default); `StrategyRemoteReadAdapter` in `big-market-market-service` with `strategy.service.remote-read.enabled=false`. Wire `RaffleStrategyController` reads.
- Files: trigger adapter package; market-service config package; controller.
- Type: adapter.
- Default flag: `strategy.service.remote-read.enabled=false`.
- Validation: validator script.
- Risk: Low.
- Dependencies: 4-C.

**4-E — Strategy scan / mapper narrowing validator**
- Objective: assert `StrategyServiceApplication` scans only `com.dyx.market.strategy`, `com.dyx.market.domain.strategy`, and a minimal infrastructure subset; mapper XMLs limited to `strategy*`, `rule_tree*`, `strategy_rule*`.
- Type: validator.
- Risk: Low.

**4-F — Strategy table ownership mapping**
- Objective: document `strategy`, `strategy_award`, `strategy_rule`, `rule_tree`, `rule_tree_node`, `rule_tree_node_line` as strategy-owned; feeds Phase 7.
- Type: docs.
- Risk: Low.

### 5.3 Phase 5 — Activity / Draw Orchestration

**5-A** Orchestration map doc — `docs/microservices-split-phase-5-activity-draw-orchestration.md`. Type: docs. Risk: Medium. **Done** — draw call graph, domain dependencies, MQ/job touchpoints, candidate adapters, non-goals. Tag: `phase-5-activity-draw-orchestration-map`.

**5-B** Draw-command boundary design doc — `docs/microservices-split-phase-5-draw-command-boundary.md`. Type: docs. Risk: Medium. **Done** — two orchestration options assessed; Option A (keep orchestration in market-service, isolate adapters) recommended; DrawCommand/DrawResult contract drafted; idempotency, rollback, and preconditions documented. Tag: `phase-5-strategy-decision-port-boundary`.

**5-C** Re-verify `IActivityAccountPort` (B11–B14 invariants) under Phase 4 ordering — `docs/microservices-split-phase-5-account-quota-port-reverification.md`. Type: validator/docs. Risk: Low. **Done** — all B11–B14 invariants confirmed intact after Phase 4; local default active; remote decrement disabled; blockers documented. Tag: `phase-5-strategy-decision-port-boundary`.

**5-D** Strategy decision port (`IStrategyDecisionPort`, local default `LocalStrategyDecisionPort`; `RaffleApplicationService` updated). Type: adapter. Risk: Medium. **Done** — local port introduced; all draw execution in-process; no remote-decision flag introduced. Remote strategy decision is future Phase 5-G work. Tag: `phase-5-strategy-decision-port-boundary`.

**5-E** Award fulfillment port (`IAwardFulfillmentPort`, local default `LocalAwardFulfillmentPort`; `RaffleApplicationService` updated). Type: adapter. Risk: Medium. **Done** — local port introduced; award persistence and task outbox remain in-process; no remote award fulfillment flag introduced.

**5-F** Activity-service scaffold decision/prep (NO orchestration moved). Type: module/docs. Risk: Medium. **Done** — `big-market-activity-service` module created at port 8090; scan restricted to `com.dyx.market.activity` + `com.dyx.market.infrastructure`; no trigger package scanned; no `@DubboService` provider, no HTTP controller, no MQ listener, no XXL-Job, no mapper XML; `RaffleApplicationService` and `RaffleActivityController` remain in place; no remote draw/award flag introduced. Tag: `phase-5-activity-service-dark-launch-scaffold`.

**5-G** Activity orchestration target and saga/outbox design. Type: docs/scaffold. Risk: High — gates any synchronous write move and any remote award fulfillment write path. **Done** — orchestration saga pattern chosen; `IDrawOutboxPort` + `DrawOutboxEvent` + `LocalDrawOutboxPort` introduced (scaffold only; not wired into draw hot-path); design doc at `docs/microservices-split-phase-5-activity-draw-saga-outbox.md`; validator at `scripts/validate-microservices-phase-5-activity-draw-saga-outbox.sh`. Tag: `phase-5-activity-draw-saga-outbox-scaffold`.

### 5.4 Phase 6 — Shared Infrastructure / Domain Split

**6-A** DAO ownership matrix — `docs/microservices-dao-ownership.md`. Type: docs. Risk: Low. **Done** — full inventory of 24 DAO interfaces, 23 physical tables, 7 repositories; 6 cross-boundary access violations documented (highest risk: `StrategyRepository` reads activity + quota tables); validator at `scripts/validate-microservices-phase-6-dao-ownership-matrix.sh`. Tag: `phase-6-dao-ownership-matrix`.

**6-B** Package-ownership validator — `scripts/validate-microservices-phase-6-package-ownership-boundaries.sh`. Type: validator. Risk: Low. **Done** — 11 cross-boundary violations explicitly allowlisted; new coupling outside allowlist fails CI; activity-service scope constraints asserted; Phase 5-D/E/F/G port boundaries re-verified; all remote flags confirmed default false. Tag: `phase-6-package-ownership-boundaries`.

**6-C** Optional per-context infrastructure submodules (only when justified). Type: module. Risk: Medium.

**6-D** Domain jar split decision doc — default **do not split**. Type: docs. Risk: Low.

**6-E** Trigger decomposition decision doc — default **freeze trigger** and migrate forward through adapters. Type: docs. Risk: Low.

### 5.5 Phase 7 — Data Ownership / Outbox

**7-A** Per-service table ownership matrix — extends §6 Boundary Matrix. Type: docs. Risk: Low.

**7-B** Generic `task` table strategy decision doc. Type: docs. Risk: Medium. **Done** — per-domain outbox/task tables chosen; no runtime behavior changed.

**7-C** Per-domain task outbox proposed DDL and AL-8/AL-9/AL-10 task-outbox ports — `docs/sql/proposed-rebate-task-outbox.sql`, `docs/sql/proposed-credit-trade-task-outbox.sql`, `docs/sql/proposed-award-dispatch-task-outbox.sql`. Type: docs/refactor. Risk: Low (proposed-only; local adapters preserve shared `task` fallback). **Done** — direct repository `ITaskDao` couplings resolved.

**7-D** Activity outbox proposed DDL (only if 5-G commits to async). Type: docs. Risk: Medium. **Deferred to Phase 8-E external approval**; no draw hot-path wiring exists.

**7-E** DB user / schema isolation plan doc. Type: docs. Risk: Low. **Done** — `docs/microservices-phase-7-db-users-grants-plan.md`; external DBA execution gated.

**7-F** Per-service schema decision doc. Type: docs. Risk: Low. **Done** — `docs/microservices-phase-7-sharded-schema-isolation-plan.md`; external DBA execution gated.

### 5.6 Phase 8 — Cutover

**8-A** Account-service cutover (B17 + B18 templates already exist). Type: ops batch. Risk: High.

**8-B** Fulfillment-service cutover (B23-D + B23-E templates already exist). Type: ops batch. Risk: High.

**8-C** Rebate-service cutover (rehearsal script from 3-E). Type: ops batch. Risk: Medium.

**8-D** Strategy-service read cutover. Type: ops batch. Risk: Low.

**8-E** Activity-service cutover (only if 5-G + 7-D approved). Type: ops batch. Risk: High.

**8-F** Legacy provider cleanup (set `*.legacy-rpc-provider.enabled=false` defaults after 7-day stability). Type: refactor. Risk: Low.

**8-G** Obsolete local path quarantine (after 30-day stability). Type: refactor. Risk: Low.

---

## 6. Boundary Matrix

| Bounded context | Target service | Current module / package | Target module / package | Current consumers | Table ownership | Event/MQ ownership | Next safe step | Final state |
|-----------------|----------------|--------------------------|--------------------------|-------------------|-----------------|---------------------|----------------|-------------|
| account / credit | account-service | `big-market-domain.credit`, `big-market-infrastructure.dao.IUserCreditAccountDao`, `IUserCreditOrderDao` | `big-market-account-service` provider + owned infra | `RaffleActivityController.creditPayExchangeSku` (adapter), `RebateMessageConsumer` (adapter), `AwardRepository.saveGiveOutPrizesAggregate` (outbox path) | `user_credit_account`, `user_credit_order` | `credit_adjust_success` MQ; `credit_award_task` outbox | Cutover gate B18 (Phase 8-A) | account-service owns all credit writes; legacy provider off |
| account / quota | account-service | `big-market-domain.activity.service.quota`, `IRaffleActivityAccountDao` family | `big-market-account-service` provider | `RaffleActivityPartakeService` (port, flag false), `RebateMessageConsumer` (adapter) | `raffle_activity_account`, `raffle_activity_account_day`, `raffle_activity_account_month`, `raffle_quota_decrement_ledger` | none direct | Cutover gate B18 (Phase 8-A) | account-service owns quota; saga via `IActivityAccountPort` |
| fulfillment / award | fulfillment-service | `big-market-domain.award`, `IAwardDao`, `IUserAwardRecordDao` | `big-market-fulfillment-service` provider | `SendAwardConsumer` (in message-job-service) | `award`, `user_award_record` | `send_award` MQ; `credit_award_task` outbox | Cutover gate B23-E (Phase 8-B) | fulfillment owns award writes; outbox-mediated credit |
| rebate | rebate-service | `big-market-domain.rebate`, `IDailyBehaviorRebateDao`, `IUserBehaviorRebateOrderDao` | `big-market-rebate-service` provider | `RaffleActivityController.calendarSignRebate` and `isCalendarSignRebate` (write/read adapters complete; remote flags default false) | `daily_behavior_rebate`, `user_behavior_rebate_order` | `rebate_message` MQ (consumed by message-job) | Phase 8-C cutover after staging provider verification and legacy provider disablement | rebate-service owns rebate domain; legacy provider off |
| strategy | strategy-service (not created) | `big-market-domain.strategy`, `IStrategyDao`, `IStrategyAwardDao`, `IStrategyRuleDao`, `IRuleTreeDao` family | `big-market-strategy-service` (Phase 4-C) | `RaffleStrategyController` (HTTP), `RaffleApplicationService` (draw decision) | `strategy`, `strategy_award`, `strategy_rule`, `rule_tree`, `rule_tree_node`, `rule_tree_node_line` | none | Phase 4-A boundary assessment | strategy-service owns rule + strategy reads; decision moves only if Phase 5-D wires the adapter |
| activity / draw | activity-service (scaffold only — Phase 5-F) | `big-market-domain.activity` (partake, armory, stage, application orchestration), `IRaffleActivityDao` family, `IUserRaffleOrderDao` | `big-market-activity-service` (scaffold at port 8090; draw execution remains in market-service) | `RaffleActivityController` (HTTP), `ActivitySkuStockZeroConsumer` (message-job) | `raffle_activity`, `raffle_activity_count`, `raffle_activity_sku`, `raffle_activity_stage`, `raffle_activity_order`, `user_raffle_order` | `activity_sku_stock_zero` MQ; XXL-Job stock sync | Phase 5-A orchestration map (NO move yet) | activity-service owns participation + orchestration via saga |
| task / outbox | shared today; Phase 7-C direct DAO cleanup complete | task-outbox ports with local `ITaskDao` fallback | per-domain outbox tables: `rebate_task_outbox`, `credit_trade_task_outbox`, `award_dispatch_task_outbox` | `BehaviorRebateRepository`, `CreditRepository`, `AwardRepository`, MQ publish path through ports | `task` (shared until migration) | `SendMessageTaskJob` XXL-Job (in message-job-service) | Phase 8 external-gated cutover | each domain owns its `*_task_outbox`; legacy `task` retained for back-compat until migrated |
| auth | auth-service | `big-market-auth-access`, `big-market-domain.auth` | `big-market-auth-service` (already done) | gateway routing | none owned in shared DB (JWT stateless) | none | n/a | stable |
| admin / config | admin-service | `big-market-admin`, `big-market-management` | `big-market-admin-service` (already done) | platform config consumers | `platform_config` (Nacos-synced) | none | n/a | stable |
| chatbot | chatbot-service | `big-market-chatbot` | `big-market-chatbot-service` (already done) | end-user `/chatbot/ask` | none owned | none | n/a | stable |
| query / search | strategy-service or activity-service (decide in Phase 4 / 5) | `big-market-queries` (ES) | folds into owning service | `RaffleActivityController` SKU stage queries | Elasticsearch index `raffle_activity_sku` | none | Phase 4-A includes a decision sub-section | owned by whichever service emits the projected events |

---

## 7. Dependency Rules for Future Validators

These rules become assertions in `scripts/validate-microservices-*` and the
Phase 6-B package-ownership validator. They are intentionally narrow so they
do not block legitimate work.

1. **Service launcher scan rule** — a service launcher's `@SpringBootApplication.scanBasePackages` may include only: `com.dyx.market.<servicename>`, the bounded contexts it owns under `com.dyx.market.domain.*`, and infrastructure subpackages required for DB / MQ / Redis bootstrap. It must not scan `com.dyx.market.trigger.http`, `trigger.listener`, `trigger.job`, or `trigger.rpc` unless it explicitly owns that role (e.g., message-job-service owns `trigger.listener` + `trigger.job`).
2. **Trigger module dependency rule** — new service modules must not depend on `big-market-trigger` unless the dependency is justified in the module's `README` or top-of-pom comment.
3. **Controller adapter rule** — HTTP controllers in any service module must not directly inject a domain service from a **foreign** bounded context. Cross-context calls go through an `*Adapter` interface with a local fallback bean.
4. **Provider ownership rule** — Dubbo `@DubboService` providers must live in the owning service module. Any provider remaining in `big-market-trigger.rpc` must carry `@ConditionalOnProperty(name = "<context>.legacy-rpc-provider.enabled", havingValue = "true", matchIfMissing = true)`. After cutover, the corresponding flag defaults false.
5. **Remote flag default rule** — every `*.remote-*.enabled` flag defaults `false` in code, `application.yml`, and `docker-compose.yml` unless changed by an explicit cutover batch backed by staging evidence.
6. **Legacy provider default rule** — `*.legacy-rpc-provider.enabled` defaults `true` (`matchIfMissing = true`) until the corresponding cutover batch flips it to `false`.
7. **Evidence rule** — `docs/evidence/generated/` remains untracked (`.gitignore`). Repo-only batches never expand evidence automation beyond what was capped at Phase 2.2 / 2.3.
8. **No new direct cross-domain injection** — a `@Resource` or `@Autowired` of a foreign-context domain service from a service module is a validator failure. Use an adapter.
9. **Infrastructure DAO ownership rule** — before any DAO migrates to a context-specific infrastructure submodule, its owner must be recorded in `docs/microservices-dao-ownership.md` (Phase 6-A). New DAOs added to `big-market-infrastructure` without an owner entry fail the Phase 6-B validator.
10. **DDL execution rule** — proposed DDL lives in `docs/sql/proposed-*.sql`. Repo-only batches never execute DDL. The Phase 8 cutover scripts only verify DDL presence with read-only queries (`CONNECT_DOCKER` / `CONNECT_REMOTE` modes already do this).

---

## 8. Recommended Execution Order

Phase 3 through Phase 7 are repo-complete. Phase 8 repo readiness is complete,
but actual cutover remains EXTERNAL-GATED. The next executable repo-only work
is hardening and consistency, not staging or production traffic enablement.

| # | Batch | Why this order |
|---|-------|----------------|
| 1 | Keep `scripts/validate-microservices-split-all-gates.sh` green in CI | Aggregates the master plan, Phase 6, Phase 7, Phase 8, service module ownership, and production flag gates without Docker/DB/MQ access. |
| 2 | Extend service ownership validators only when an approved service gains a runtime surface | Prevents accidental provider/controller/listener/job/mapper drift in dark-launch modules. |
| 3 | Prepare external evidence files only after a real staging window exists | Avoids marking Phase 8 production complete from repo-only work. |
| 4 | After external 7-day stability gates, disable legacy providers in a dedicated cleanup batch | Requires cutover evidence and Oncall approval. |
| 5 | After external 30-day stability gates, remove obsolete local paths in a dedicated cleanup batch | Requires evidence that rollback paths are no longer needed. |

---

## 9. Risk Register

| # | Risk | Trigger | Mitigation | Owner |
|---|------|---------|------------|-------|
| 1 | Duplicate Dubbo provider (legacy + new register the same `Ixxx` v1.0 in Nacos) | Cutover starts before legacy provider gate flipped | All legacy providers carry `@ConditionalOnProperty` (already done for rebate; replicate for strategy / activity); Phase 8-F verifies before traffic |
| 2 | Distributed transaction loss | A write moves across services without saga / outbox | Rule §10 forbids synchronous write moves before saga design; Phase 5-G gates activity moves; credit-award outbox already mediates account ↔ fulfillment |
| 3 | Shared `task` outbox runtime coupling | A second domain inserts into `task` after rebate moves out | Phase 7-B/C decision and ports complete; per-domain outbox tables follow `credit_award_task` precedent; physical table cutover still requires DBA-applied DDL and Phase 8 evidence |
| 4 | Activity draw latency or idempotency regression | Phase 5 wires a remote strategy decision call without measuring the in-process baseline | Phase 5-A includes a latency baseline; remote-decision flag defaults false; P99 < +20% is a Phase 8 NO-GO criterion |
| 5 | Shared DAO / table ownership drift | New DAO added to `big-market-infrastructure` without an owner | Phase 6-B validator fails CI for unowned DAOs |
| 6 | Validator false confidence | Repo-only validators pass while staging gate is still pending | Every cutover Phase 8 batch requires an evidence template; validator green ≠ cutover green; documented in §3 item 9 |
| 7 | Production cutover risk | Cutover proceeds without B17 / B23-C / equivalent staging evidence | DBA + Ops + Engineering + Oncall sign-off required per [[project_microservices_phase1]]; B18 script asserts B17 evidence file via `validate-b17-evidence-consistency.sh` |
| 8 | Hidden direct cross-domain injection | Future PR re-introduces `@Resource IFooService` from a foreign context | Phase 6-B validator + grep-based assertion in each phase validator |
| 9 | Scope creep on documentation evidence automation | New service spawns its own B-series evidence templates | Anti-goal in §10: do not extend evidence automation beyond Phase 2.2 / 2.3 |
| 10 | Big-bang temptation | Pressure to "just split everything now" | §10 non-goals + 10-batch execution order keep cadence small |

---

## 10. Non-Goals

These are explicitly out of scope for this decomposition program. They are
not deferred — they are decided **against** unless a concrete trigger
documented below justifies revisiting.

1. **No big-bang rewrite.** Strangler-fig extraction only. Each batch keeps `big-market-app` buildable.
2. **No immediate activity-service extraction before strategy and rebate boundaries are complete.** Activity owns the draw orchestration; pulling it before strategy reads and rebate cutover stabilises will create cascading cross-service partial failures.
3. **No production traffic enablement from repo-only batches.** Repo-only validators prove module wiring and code ownership. Traffic is a Phase 8 DBA + Ops + Engineering + Oncall sign-off gate.
4. **No expansion of generated evidence automation.** The Phase 2.2 (B17 / B18) and Phase 2.3 (B23-D / B23-E) evidence template + intake automation is the cap. Phase 4 / 5 / 6 / 7 / 8 batches reuse the same pattern manually if needed; no new template-generation scripts.
5. **No large-scale package renames without strong reason.** Splitting `big-market-domain` into per-service domain jars (Phase 6-D) is **not** the default plan; the default is to enforce ownership via scan-package validators (Phase 6-B), not Maven boundaries. Move only when a context has ≥ 4 owned services consuming it AND a documented friction case.
6. **No service mesh, no event sourcing rewrite, no polyglot persistence, no Kubernetes migration before Phase 7 data ownership is documented.** These were already declared anti-goals in `docs/microservices-roadmap.md` §13; restated here so this master plan is self-contained.
7. **No DDL execution from this repo.** All DDL is proposed-only under `docs/sql/proposed-*.sql`. The DBA executes DDL in explicit Phase 8 windows; cutover scripts only verify presence read-only.
8. **No setting any remote or dangerous flag default to true from a planning batch.** Defaults flip only in explicit cutover batches backed by staging evidence.

---

## 11. Safety Rules (Recap)

Repo-only batches following this plan must respect every one of the
following hard rules. Violating any is a revert criterion.

- No connection to staging, production, DBs, XXL-Job, Nacos, Redis, MQ, Docker, or external services.
- No `mysql`, `docker`, `curl`, `wget`, or other external mutating commands invoked from batch scripts.
- No traffic enablement.
- No modification of `docs/evidence/generated/`.
- No Java behavior change unless the batch explicitly states it.
- No remote or dangerous flag default flipped to true.
- No expansion of Phase 2 release evidence automation.

---

## 12. Cross-References

- `docs/microservices-roadmap.md` — historical phase log; consult for Phase 1, 2.1, 2.2, 2.3, and Phase 3 batches 1–3 details.
- `docs/microservices-split-phase-2-2-account-service.md` — account-service B-series detail (B1 through B21).
- `docs/microservices-split-phase-2-3-fulfillment-service.md` — fulfillment-service B23 series.
- `docs/microservices-split-phase-3-next-extraction.md` — Phase 3 batches 1–3 implementation detail; future Phase 3-D / 3-E updates land here.
- `docs/microservices-split-completion-index.md` — current completion, AL-1 through AL-11 status, external gates, and validator index.
- `scripts/validate-microservices-phase-3-*.sh` — existing Phase 3 validators.
- `scripts/validate-microservices-master-plan.sh` — this plan's structural validator (added by this batch).
- `docs/microservices-dao-ownership.md` — Phase 6-A DAO ownership matrix: full DAO/table/repository inventory and cross-boundary access violations.
- `scripts/validate-microservices-phase-6-dao-ownership-matrix.sh` — Phase 6-A validator.
