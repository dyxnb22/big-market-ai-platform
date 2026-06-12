> **Archived (2026-06-12):** Phase 1-7 historical implementation record. See `docs/MICROSERVICES.md` for current status.

# Phase 2.3: big-market-fulfillment-service Extraction

## 1. What moves

**Domain code:** `com.dyx.market.domain.award`
- `AwardService` — orchestrates award dispatch (credits, OpenAI quota)
- `UserCreditRandomAward` — awards random credit amounts
- `OpenAIAccountAdjustQuotaAward` — adjusts OpenAI quota for a user
- `IAwardService` — domain service interface

**Tables eventually owned by fulfillment-service:**
- `user_award_record` (sharded: `user_award_record_000..003`)
- `award`

**MQ trigger:** `send_award` RabbitMQ topic — currently consumed by `SendAwardConsumer` in message-job-service. In a future batch, this consumer will call `FulfillmentAwardServiceRPC` via Dubbo instead of calling `IAwardService` in-process.

## 2. The outbox dependency — why Phase 2.3-B must come before traffic cutover

`UserCreditRandomAward.distributeAward` calls `AwardRepository.saveGiveOutPrizesAggregate`, which (when `account.award-credit-outbox.enabled=false`, the current default) writes **both** `user_credit_account` and `user_award_record` inside a single local `transactionTemplate.execute()` block.

This is an intentional design (documented in Phase 2.2-B4 audit): the two writes share a local transaction to prevent partial-credit scenarios. Splitting them across service boundaries requires a distributed transaction strategy.

The credit-award outbox (`credit_award_task` tables + `DispatchCreditAwardTaskJob`) was built in Phase 2.2-B5/B6 precisely to break this boundary safely:
1. `AwardRepository` inserts an outbox row (atomically with `user_award_record`) when `award-credit-outbox.enabled=true`
2. `DispatchCreditAwardTaskJob` polls the outbox and dispatches credit to account-service via `IAccountCreditWriteAdapter.createOrder()`
3. Account-service deduplicates using `outBusinessNo=awardOrderId`

**Consequence:** The fulfillment-service MUST NOT receive live `send_award` traffic until:
- `credit_award_task` DDL is applied to staging (see `docs/sql/proposed-credit-award-task-outbox.sql`)
- `award-credit-outbox.enabled=true` is validated in staging
- The outbox poller runs successfully in message-job-service (or fulfillment-service)
- Evidence is filed and the staging GO decision is made

Enabling fulfillment-service traffic cutover while `award-credit-outbox.enabled=false` would mean `user_credit_account` writes happen in fulfillment-service's DB shard instead of account-service's — breaking the isolation boundary.

## 3. Dubbo interface plan

**Current (Phase 2.3-A — dark launch):**
- `FulfillmentAwardServiceRPC implements IAwardService` registers as Dubbo provider (version 1.0) on port 20882
- No consumer wired to it; `SendAwardConsumer` in message-job-service still calls `IAwardService` in-process

**Phase 2.3-B (planned — after outbox staging validation):**
- Wire `SendAwardConsumer` to call `FulfillmentAwardServiceRPC` via `@DubboReference` instead of in-process
- Requires `account.award-credit-outbox.enabled=true` in fulfillment-service config
- Flag flip in message-job-service: keep `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` (outbox owned by fulfillment-service now)
- Validate end-to-end: raffle win → MQ → SendAwardConsumer → fulfillment-service RPC → outbox → DispatchCreditAwardTaskJob → account-service credit

**Phase 2.3-C (planned — production cutover):**
- Production DDL for outbox tables applied
- Traffic cutover flag enabled after staging evidence complete
- `user_award_record` and `award` tables logically owned by fulfillment-service

## 4. Phase 2.3-B: Award dispatch adapter scaffold (completed 2026-06-10)

**What was wired:**
- `IAwardDispatchAdapter` interface added to `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/` — seam between `SendAwardConsumer` and fulfillment-service Dubbo provider
- `LocalAwardDispatchAdapter` — `@Service @ConditionalOnMissingBean`; delegates to existing `IAwardService` bean (unchanged behavior when flag=false)
- `RemoteAwardDispatchAdapter` — registered via `@Bean @ConditionalOnProperty(name="account.fulfillment.remote-award.enabled", havingValue="true")` in `WriteAdapterLocalConfig`; carries `@DubboReference(interfaceClass=IAwardService.class, version="1.0", check=false)` pointing at `FulfillmentAwardServiceRPC`; re-throws `RpcException` (no silent swallow, no local fallback)
- `SendAwardConsumer` now injects `IAwardDispatchAdapter` (was `IAwardService`)

**Flag state:**
- `account.fulfillment.remote-award.enabled=false` in all configs (message-job-service, big-market-app)
- `ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=${ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:-false}` in docker-compose.yml

**Enabling the remote path requires (non-negotiable):**
1. Phase 2.2 staging GO (B17 evidence filed and Phase K decision = GO)
2. `credit_award_task` outbox DDL applied to staging and `award-credit-outbox.enabled=true` staging-validated

**Validation gate:** `validate-fulfillment-service-b23-b.sh` 16/16 PASS

## 5. Remaining batches before production cutover

| Batch | Description | Blocked on |
|-------|-------------|-----------|
| **B23-C** | Staging validation: outbox DDL applied, E2E award flow through fulfillment-service, evidence | B23-B + Phase 2.2 staging GO + staging DB access |
| **B23-D** | Production promotion gate: static checks + evidence template + post-window checklist | B23-C evidence GO |
| **B23-E** | Production cutover: flag flip, traffic redirect, post-cutover verification | B23-D sign-off |

## 10. Final Readiness Index (2026-06-10)

All Phase 2.3 repo work (B23-A through B23-E) is complete. A compact readiness index and one-command validator suite have been added for future handoffs.

- **Index:** [`docs/evidence/phase-2-3-fulfillment-final-readiness-index.md`](evidence/phase-2-3-fulfillment-final-readiness-index.md) — batch summary (B23-A through B23-E), commit/tag references, artifact links, safe defaults, job ownership decision, blocked items, and exact next real-world action sequence.
- **One-command validator:** `bash scripts/validate-fulfillment-service-phase-2-3.sh` — runs B23-B/C/D/E validators in order, performs a final dangerous-flag scan over all config files, and verifies all five Phase 2.3 git tags exist locally. No network, Docker, DB, staging, or production access required.

**No config changes are included in this batch. All three dangerous flags remain `false` by default.**

Tag: `phase-2.3-final-readiness-index`

---

## 9. Phase 2.3-E: Cutover Execution Pack (2026-06-10)

**This batch does NOT enable production or staging traffic.** All three dangerous flags remain `false` by default.

### What was added

- `docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md` — strict execution worksheet for the actual remote-award cutover. Includes: preconditions inherited from B23-C (SE1–SE11) and B23-D (D1–D8), exact staging cutover steps (S1–S8), exact production cutover steps (P1–P8), flag matrix, canary plan (≥15 min staging, ≥30 min production), rollback commands for all failure modes, observability checklist, evidence attachment table (E1–E12), and a final five-phase GO/NO-GO decision table.
- `scripts/validate-fulfillment-service-b23-e-cutover-execution.sh` — deterministic local validator (no network, Docker, DB, staging, or production access). Verifies B23-E doc completeness, config safety (all three flags false), adapter wiring (B23-B/C/D re-check), job ownership, provider integrity, and all required prior docs/scripts.

### Flag state (unchanged — all false)

| Flag | Default |
|------|---------|
| `account.award-credit-outbox.enabled` | `false` |
| `account.fulfillment.remote-award.enabled` | `false` |
| `account.service.remote-quota-decrement.enabled` | `false` |

### Job ownership (unchanged)

`DispatchCreditAwardTaskJob` remains in `big-market-message-job-service`. Any future move requires a dedicated batch.

### What remains blocked

| Blocker | Gate |
|---------|------|
| B23-C staging evidence (SE1–SE11) completed and signed by oncall lead | Required before staging cutover steps |
| B23-D evidence file completed and signed | Required before production cutover steps |
| DBA applies `credit_award_task` DDL to production shard DBs | Required before outbox flag enable |
| Ops registers `DispatchCreditAwardTaskJob_DB1/_DB2` in production XXL-Job | Required before outbox flag enable |
| Oncall lead issues written approval for production cutover window | Hard gate before step P5 |

**Validation gate:** `bash scripts/validate-fulfillment-service-b23-e-cutover-execution.sh` — all checks PASS (local/static).

**Evidence doc:** `docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md`

---

## 8. Phase 2.3-D: Production Promotion Gate (this batch — 2026-06-10)

**This batch does NOT enable production traffic.** Remote-award cutover remains blocked until B23-C staging evidence is attached and approved.

### What was added

- `docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md` — strict GO/NO-GO checklist for promoting fulfillment-service award dispatch to production after B23-C staging evidence is complete. Includes: staging evidence dependency table (SE1–SE11), production prerequisites, DBA/ops sign-off table, deployment order (9 steps), flag matrix, rollback plan, observability checks, and explicit NO-GO triggers.
- `scripts/validate-fulfillment-service-b23-d-production-gate.sh` — deterministic local validator (no network, no Docker, no DB). Verifies B23-D doc completeness, config safety (all three dangerous flags false), adapter wiring (B23-B/C re-check), job ownership (DispatchCreditAwardTaskJob in message-job-service), provider integrity, and required docs/scripts.

### Flag state (unchanged — all false)

| Flag | Default |
|------|---------|
| `account.award-credit-outbox.enabled` | `false` |
| `account.fulfillment.remote-award.enabled` | `false` |
| `account.service.remote-quota-decrement.enabled` | `false` |

### Job ownership (unchanged)

`DispatchCreditAwardTaskJob` remains in `big-market-message-job-service` through Phase 2.3-D. Any future move to fulfillment-service requires a dedicated batch.

### What remains blocked

| Blocker | Gate |
|---------|------|
| B23-C staging evidence (SE1–SE11) completed and signed | Required before any production action |
| DBA applies `credit_award_task` DDL to production shard DBs | Required before outbox flag enable |
| Ops registers `DispatchCreditAwardTaskJob_DB1/_DB2` in production XXL-Job | Required before outbox flag enable |
| Oncall lead approves production flag enable window | Hard gate before step 5 in deployment order |
| B23-D evidence file filled in and signed | Required for final Phase 2.3-D sign-off |

**Validation gate:** `bash scripts/validate-fulfillment-service-b23-d-production-gate.sh` — all checks PASS (local/static).

**Evidence doc:** `docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md`

**Remaining blockers:**
- Phase 2.2 staging GO (B17 evidence — staging ledger DDL, outbox DDL, XXL-Job registration all pending)
- `credit_award_task` DDL applied to staging and outbox poller validated end-to-end
- Decision on where `DispatchCreditAwardTaskJob` runs after cutover (fulfillment-service vs. stays in message-job-service)

## 6. Known risk: UserCreditRandomAward writes user_credit_account directly

`UserCreditRandomAward` never calls `ICreditAdjustService`. The credit write goes through `AwardRepository.saveGiveOutPrizesAggregate` → `updateOrCreateCreditAccount` → `userCreditAccountDao` directly. This is NOT mediated by account-service's credit domain service.

The outbox (Phase 2.2-B6) was built to handle exactly this: when enabled, `saveGiveOutPrizesAggregate` inserts an outbox row, and `DispatchCreditAwardTaskJob` calls `IAccountCreditWriteAdapter.createOrder()` which routes to account-service's `ICreditAdjustService.createOrder()`.

**Action required before B23-B:** Confirm that `DispatchCreditAwardTaskJob` will run in fulfillment-service (not message-job-service) after the cutover, or add it to fulfillment-service's scan and remove from message-job-service. The job should live in whichever service owns the outbox tables. This decision should be made in B23-B design.

## 7. Phase 2.3-A dark launch summary

**Completed in this batch:**
- `big-market-fulfillment-service` module created (port 8087, Dubbo port 20882)
- `FulfillmentAwardServiceRPC` Dubbo provider registered; delegates to existing `AwardService` bean
- `account.award-credit-outbox.enabled=false` in all configs (gate confirmed by `validate-fulfillment-service-b23-a.sh` S4/S5/S6/S15)
- `docker-compose.yml` entry added; `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED` env var wired
- Smoke test extended to 18/18
- `validate-fulfillment-service-b23-a.sh` 15/15 PASS
- All existing baseline scripts remain green (B18 12/12, B20 11/11, B17 6/6, B6 17/17, MQ 12/12, DDL 14/14)
- `mvn clean package -DskipTests`: BUILD SUCCESS (all 14 modules)
