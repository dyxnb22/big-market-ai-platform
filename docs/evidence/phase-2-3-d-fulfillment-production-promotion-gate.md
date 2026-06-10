# Phase 2.3-D: Fulfillment-Service Production Promotion Gate

**Date:** 2026-06-10
**Status:** AWAITING B23-C STAGING EVIDENCE — local/static gates PASS
**Branch:** main (tag: phase-2.3-d-fulfillment-production-gate)

> **IMPORTANT: This batch does NOT enable production traffic.**
> Remote-award cutover (`account.fulfillment.remote-award.enabled=true` in production)
> is BLOCKED until B23-C staging evidence is attached below and approved by DBA + oncall lead.

---

## Staging Evidence Dependency

Before any production action is taken, the following B23-C staging evidence must be attached here:

| # | Required Staging Evidence | Status | Attached By |
|---|--------------------------|--------|-------------|
| SE1 | B23-C evidence file path (e.g. `docs/evidence/b23-c-staging-evidence-YYYYMMDD.md`) | **BLOCKED — awaiting staging access** | ___ |
| SE2 | `validate-fulfillment-service-b23-c-readiness.sh` all PASS (screenshot or log) | **BLOCKED** | ___ |
| SE3 | `credit_award_task` DDL applied to staging `big_market_01` (4 shard tables confirmed) | **BLOCKED** | DBA |
| SE4 | `credit_award_task` DDL applied to staging `big_market_02` (4 shard tables confirmed) | **BLOCKED** | DBA |
| SE5 | `DispatchCreditAwardTaskJob_DB1` and `_DB2` registered in staging XXL-Job admin | **BLOCKED** | Ops |
| SE6 | E2E outbox flow validated: `state='pending'` → `state='dispatched'`, `user_credit_order` count = 1 | **BLOCKED** | Engineer |
| SE7 | Idempotency confirmed: re-trigger produces 0 new `user_credit_order` rows | **BLOCKED** | Engineer |
| SE8 | `account.fulfillment.remote-award.enabled=true` validated in staging (full Dubbo path) | **BLOCKED** | Engineer |
| SE9 | End-to-end: raffle win → MQ → `SendAwardConsumer` → Dubbo → fulfillment-service → outbox → job → account credit | **BLOCKED** | Engineer |
| SE10 | All three dangerous flags restored to `false` after staging validation | **BLOCKED** | Engineer |
| SE11 | B23-C staging GO decision issued and signed off | **BLOCKED** | Oncall lead |

**Hard gate:** Do NOT proceed to any production step until SE1–SE11 are all complete and signed off.

---

## Production Prerequisites

| # | Prerequisite | Status |
|---|-------------|--------|
| PP1 | Phase 2.2-B17 staging GO issued (ledger DDL, outbox DDL, XXL-Job registration) | **BLOCKED — staging access required** |
| PP2 | Phase 2.3-C B23-C staging GO decision issued (SE1–SE11 above) | **BLOCKED** |
| PP3 | `credit_award_task` DDL applied to production `big_market_01` (4 shards: 000–003) | **BLOCKED — requires DBA sign-off** |
| PP4 | `credit_award_task` DDL applied to production `big_market_02` (4 shards: 000–003) | **BLOCKED — requires DBA sign-off** |
| PP5 | `UNIQUE KEY uq_award_order_id` confirmed on all 8 production outbox shards | **BLOCKED** |
| PP6 | `UNIQUE KEY uq_out_business_no` confirmed on all production `user_credit_order_*` shards | **BLOCKED** |
| PP7 | `DispatchCreditAwardTaskJob_DB1` and `_DB2` registered in production XXL-Job admin | **BLOCKED — requires ops** |
| PP8 | fulfillment-service image tested on staging with production-equivalent config | **BLOCKED** |
| PP9 | Oncall lead approved production flag enable window | **BLOCKED** |
| PP10 | All static validator scripts PASS (see below) | PASS (local) |
| PP11 | `mvn clean package -DskipTests` BUILD SUCCESS | PASS (local) |

---

## DBA / Ops Sign-Offs

All sign-offs below are required before the production flag enable window.

| Role | Action | Name | Timestamp | Result |
|------|--------|------|-----------|--------|
| DBA | Apply `credit_award_task` DDL to `big_market_01` | ___ | ___ | SUCCESS / ERROR |
| DBA | Apply `credit_award_task` DDL to `big_market_02` | ___ | ___ | SUCCESS / ERROR |
| DBA | Verify `UNIQUE KEY uq_award_order_id` on all 8 outbox shards | ___ | ___ | CONFIRMED / FAIL |
| DBA | Verify `UNIQUE KEY uq_out_business_no` on all `user_credit_order_*` | ___ | ___ | CONFIRMED / FAIL |
| Ops | Register `DispatchCreditAwardTaskJob_DB1` in production XXL-Job | ___ | ___ | REGISTERED / FAIL |
| Ops | Register `DispatchCreditAwardTaskJob_DB2` in production XXL-Job | ___ | ___ | REGISTERED / FAIL |
| Oncall lead | Approve production flag enable window | ___ | ___ | APPROVED / DENIED |
| Engineer | B23-C staging GO evidence reviewed and attached | ___ | ___ | ATTACHED / MISSING |

---

## Deployment Order

> Follow this order exactly. Do NOT skip or reorder steps.

1. **DBA: Apply production outbox DDL** (both shard DBs, idempotent — safe to re-run)
   ```bash
   mysql -h <prod-host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql
   mysql -h <prod-host> -u <admin> -p big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql
   # Verify:
   mysql -h <prod-host> -u <ro-user> -p -e "SHOW TABLES LIKE 'credit_award_task%';" big_market_01
   mysql -h <prod-host> -u <ro-user> -p -e "SHOW TABLES LIKE 'credit_award_task%';" big_market_02
   # Expected: credit_award_task_000, credit_award_task_001, credit_award_task_002, credit_award_task_003
   ```

2. **Ops: Register XXL-Job handlers in production XXL-Job admin**
   - Executor: `big-market-message-job-service`
   - Job 1: `DispatchCreditAwardTaskJob_DB1` — CRON: `0/30 * * * * ?` — Routing: FIRST
   - Job 2: `DispatchCreditAwardTaskJob_DB2` — CRON: `0/30 * * * * ?` — Routing: FIRST

3. **Deploy fulfillment-service** (flags still false — dark launch only)
   ```bash
   docker compose up -d --no-deps --force-recreate big-market-fulfillment-service
   # Verify health:
   curl -sf http://localhost:8087/actuator/health | jq .status
   # Expected: "UP"
   ```

4. **Oncall lead approves** the flag enable window (written approval required before step 5).

5. **Enable outbox flag in message-job-service** (canary first)
   ```bash
   ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
     docker compose up -d --no-deps --force-recreate big-market-message-job-service
   # Confirm:
   docker compose exec big-market-message-job-service env | grep ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED
   # Expected: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true
   ```

6. **Monitor canary window** (≥15 min — see Observability Checks below). If any NO-GO trigger fires, execute rollback immediately.

7. **Enable remote-award flag** (only after outbox flag is stable and canary is healthy)
   ```bash
   ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true \
     docker compose up -d --no-deps --force-recreate big-market-message-job-service
   # Confirm:
   docker compose exec big-market-message-job-service env | grep ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED
   # Expected: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true
   ```

8. **Monitor post-cutover window** (≥30 min). Validate end-to-end award dispatch through fulfillment-service.

9. **Record final GO/NO-GO decision** in Phase J below.

---

## Flag Matrix

All three dangerous flags must remain `false` in production until each step above is explicitly approved.

| Flag | Service | Default | Outbox Canary Value | Remote-Award Cutover Value | Hard Rule |
|------|---------|---------|--------------------|-----------------------------|-----------|
| `account.award-credit-outbox.enabled` | message-job-service, fulfillment-service, big-market-app | `false` | `true` (step 5 only, after DBA sign-off) | `true` | Never enable without DBA confirmation of DDL + unique keys |
| `account.fulfillment.remote-award.enabled` | message-job-service, big-market-app | `false` | `false` (not yet) | `true` (step 7 only, after outbox canary stable) | Never enable before outbox flag is stable and B23-C staging evidence attached |
| `account.service.remote-quota-decrement.enabled` | market-service | `false` | `false` | `false` | Phase 2.2 — separate gate; do NOT enable in this batch |

**`DispatchCreditAwardTaskJob` remains in `big-market-message-job-service`** for this promotion window.
It is NOT moved to fulfillment-service in Phase 2.3-D. Any future move requires a dedicated batch.

---

## Rollback Plan

### Instant rollback — remote-award flag (no data loss)

```bash
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
# Verify:
curl -sf http://localhost:8080/actuator/health | jq .status
# Expected: "UP"
```

Traffic immediately routes back to `LocalAwardDispatchAdapter` (in-process). All in-flight outbox rows continue to be processed by `DispatchCreditAwardTaskJob`.

### Instant rollback — outbox flag (no data loss)

```bash
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
```

Pending `credit_award_task` rows remain with `state='pending'`. When the flag is re-enabled the poller will retry them. No credit writes are lost.

### Emergency rollback — double-credit detected

1. Stop `big-market-message-job-service` immediately: `docker compose stop big-market-message-job-service`
2. Restore both flags to `false` and restart.
3. Inspect `user_credit_order_*` for duplicate `out_business_no` rows.
4. Escalate — DO NOT re-enable either flag until root cause identified and `UNIQUE KEY uq_out_business_no` confirmed on all deployed tables.
5. File incident report before any further production action.

### Rollback fulfillment-service (if Dubbo provider unstable)

```bash
docker compose stop big-market-fulfillment-service
# message-job-service continues on LocalAwardDispatchAdapter if remote-award flag is already false
```

---

## Observability Checks

Run during canary window (step 6) and post-cutover window (step 8).

| Metric | Expected | Hard NO-GO Threshold |
|--------|----------|----------------------|
| Draw endpoint error rate | 0% | > 0% → immediate rollback |
| `user_credit_order` double-count | 0 | Any duplicate → immediate rollback |
| `credit_award_task` rows stuck in `pending` > 5 min | 0 | > 3 stuck rows → investigate, > 10 → rollback |
| Dubbo RPC error rate (fulfillment-service) | 0% | > 1% → rollback remote-award flag |
| fulfillment-service heap/GC anomaly | None | Any OOM or GC pause > 2s → rollback |
| message-job-service heap/GC anomaly | None | Any OOM → rollback |
| account-service latency P99 | Baseline ±20% | > +50% → rollback |
| Log path (canary window) | ___ | — |
| Log path (post-cutover window) | ___ | — |

---

## GO/NO-GO Checklist

### Phase A — Static pre-flight (local, no staging required)

| Check | Result |
|-------|--------|
| `validate-fulfillment-service-b23-d-production-gate.sh` all PASS | ___ |
| `validate-fulfillment-service-b23-b.sh` 16/16 PASS | ___ |
| `validate-fulfillment-service-b23-c-readiness.sh` all PASS | ___ |
| `mvn clean package -DskipTests` BUILD SUCCESS | ___ |
| Zero config files with any of three dangerous flags set to `true` | ___ |

**Phase A gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase B if Phase A gate is FAIL.

### Phase B — Staging evidence validation

| Check | Result |
|-------|--------|
| B23-C evidence file path | ___ |
| B23-C staging GO decision issued and signed | ___ |
| All SE1–SE11 items attached and confirmed | ___ |
| Idempotency confirmed in staging (no double-credit) | ___ |
| Full Dubbo path validated in staging (remote-award flow) | ___ |

**Phase B gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase C if Phase B gate is FAIL.

### Phase C — Production DDL verification

| Check | Result |
|-------|--------|
| `credit_award_task_000–003` in `big_market_01` — all 4 tables present | ___ |
| `credit_award_task_000–003` in `big_market_02` — all 4 tables present | ___ |
| `UNIQUE KEY uq_award_order_id` on all 8 outbox shards | ___ |
| `UNIQUE KEY uq_out_business_no` on all `user_credit_order_*` shards | ___ |
| `DispatchCreditAwardTaskJob_DB1/_DB2` registered in production XXL-Job | ___ |

**Phase C gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase D if Phase C gate is FAIL.

### Phase D — Production canary window

| Check | Result |
|-------|--------|
| Oncall lead approval recorded | ___ |
| Outbox flag enabled on canary instance; confirmed via env | ___ |
| Canary window ≥15 min with 0 errors | ___ |
| No `user_credit_order` double-count | ___ |
| No `credit_award_task` rows stuck in pending | ___ |
| Remote-award flag enabled after outbox stable | ___ |
| End-to-end award dispatch through fulfillment-service confirmed | ___ |

**Phase D gate:** PASS / FAIL

### Phase E — Final sign-off

| Check | Result |
|-------|--------|
| Phase A: static pre-flight PASS | YES / NO |
| Phase B: staging evidence validated and GO signed | YES / NO |
| Phase C: production DDL verified | YES / NO |
| Phase D: canary window clean | YES / NO |
| No double-credit observed at any step | YES / NO **(YES required)** |
| No quota leak observed at any step | YES / NO **(YES required)** |
| All three dangerous flags confirmed in correct state post-rollout | YES / NO |
| B23-D evidence file complete | YES / NO **(YES required for sign-off)** |

**Final Phase 2.3-D decision:** **GO / NO-GO**
**Sign-off by:** ___
**Role:** ___
**Timestamp:** ___
**If NO-GO, reason and next batch:** ___

---

## NO-GO Triggers

Do NOT proceed (or immediately rollback) if ANY of the following:

1. B23-C staging evidence missing, incomplete, or not signed off.
2. Any FAIL in Phase A static checks.
3. Production DDL not applied or unique-key constraints missing.
4. Draw endpoint error rate > 0% during canary window.
5. Any `user_credit_order` double-count (idempotency violation) — escalate immediately.
6. Any `credit_award_task` rows stuck in `pending` beyond retry threshold.
7. Dubbo RPC error rate > 1% to fulfillment-service.
8. fulfillment-service OOM or GC pressure anomaly.
9. account-service latency P99 > +50% above baseline.
10. B23-D evidence file incomplete or unsigned.
11. `DispatchCreditAwardTaskJob` found in fulfillment-service (premature migration — must stay in message-job-service).

**Hard no-go conditions** (any one triggers immediate flag=false rollback):
- `user_credit_order` count > 1 for same `out_business_no` (double credit)
- Any error rate > 0% on draw endpoint during canary window
- fulfillment-service OOM during canary window
- B23-C staging GO decision not present

---

## Related Files

| File | Purpose |
|------|---------|
| `docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md` | B23-C staging evidence template (must be completed before this gate) |
| `docs/evidence/phase-2-2-b18-production-promotion-template.md` | B18 production promotion pattern reference |
| `docs/microservices-split-phase-2-3-fulfillment-service.md` | Phase 2.3 design and batch history |
| `docs/sql/proposed-credit-award-task-outbox.sql` | DDL for `credit_award_task` tables |
| `scripts/validate-fulfillment-service-b23-d-production-gate.sh` | B23-D static validator (run before any production action) |
| `scripts/validate-fulfillment-service-b23-b.sh` | B23-B adapter scaffold validation (16 checks) |
| `scripts/validate-fulfillment-service-b23-c-readiness.sh` | B23-C readiness validation |
| `big-market-message-job-service/.../config/DispatchCreditAwardTaskJob.java` | Outbox poller (stays in message-job-service through 2.3-D) |
| `big-market-fulfillment-service/.../provider/FulfillmentAwardServiceRPC.java` | Dubbo provider (dark launch, port 20882) |
