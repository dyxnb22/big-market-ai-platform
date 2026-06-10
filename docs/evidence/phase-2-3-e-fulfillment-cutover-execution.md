# Phase 2.3-E: Fulfillment-Service Remote-Award Cutover Execution

**Date:** 2026-06-10
**Status:** TEMPLATE — awaiting B23-C staging evidence and B23-D sign-off before execution
**Branch:** main (tag: phase-2.3-e-fulfillment-cutover-execution-pack)

> **IMPORTANT: This repo batch does NOT enable any production or staging traffic.**
> All three dangerous flags remain `false` by default. No config change enables traffic.
> Production cutover is BLOCKED until B23-C staging evidence (SE1–SE11) is signed off,
> B23-D evidence file is complete, DBA DDL is applied, ops registers XXL-Job handlers,
> and the oncall lead approves the cutover window.

> **DispatchCreditAwardTaskJob remains in `big-market-message-job-service`.**
> It is NOT moved to fulfillment-service in Phase 2.3-E or any prior batch.
> Any future move requires a dedicated batch with its own evidence and validator.

---

## Preconditions Inherited from B23-C and B23-D

All of the following must be true and documented before executing any step in this runbook.

### B23-C Staging Evidence (SE1–SE11)

| # | Required Evidence | Status | Signed By |
|---|------------------|--------|-----------|
| SE1 | B23-C evidence file path (e.g. `docs/evidence/b23-c-staging-evidence-YYYYMMDD.md`) | **BLOCKED** | ___ |
| SE2 | `validate-fulfillment-service-b23-c-readiness.sh` all PASS (log attached) | **BLOCKED** | ___ |
| SE3 | `credit_award_task` DDL applied to staging `big_market_01` (4 shard tables confirmed) | **BLOCKED** | DBA |
| SE4 | `credit_award_task` DDL applied to staging `big_market_02` (4 shard tables confirmed) | **BLOCKED** | DBA |
| SE5 | `DispatchCreditAwardTaskJob_DB1` and `_DB2` registered in staging XXL-Job admin | **BLOCKED** | Ops |
| SE6 | E2E outbox flow validated: `state='pending'` → `state='dispatched'`, `user_credit_order` count = 1 | **BLOCKED** | Engineer |
| SE7 | Idempotency confirmed: re-trigger produces 0 new `user_credit_order` rows | **BLOCKED** | Engineer |
| SE8 | `account.fulfillment.remote-award.enabled=true` validated in staging (full Dubbo path) | **BLOCKED** | Engineer |
| SE9 | End-to-end: raffle win → MQ → `SendAwardConsumer` → Dubbo → fulfillment-service → outbox → job → account credit | **BLOCKED** | Engineer |
| SE10 | All three dangerous flags restored to `false` after staging validation | **BLOCKED** | Engineer |
| SE11 | B23-C staging GO decision issued and signed | **BLOCKED** | Oncall lead |

### B23-D Production Gate Sign-off

| # | Required Sign-off | Status | Signed By |
|---|------------------|--------|-----------|
| D1 | `validate-fulfillment-service-b23-d-production-gate.sh` all PASS | **BLOCKED** | Engineer |
| D2 | B23-D evidence file (`phase-2-3-d-fulfillment-production-promotion-gate.md`) complete and signed | **BLOCKED** | Oncall lead |
| D3 | DBA applied `credit_award_task` DDL to production `big_market_01` | **BLOCKED** | DBA |
| D4 | DBA applied `credit_award_task` DDL to production `big_market_02` | **BLOCKED** | DBA |
| D5 | `UNIQUE KEY uq_award_order_id` confirmed on all 8 production outbox shards | **BLOCKED** | DBA |
| D6 | `UNIQUE KEY uq_out_business_no` confirmed on all production `user_credit_order_*` shards | **BLOCKED** | DBA |
| D7 | `DispatchCreditAwardTaskJob_DB1` and `_DB2` registered in production XXL-Job admin | **BLOCKED** | Ops |
| D8 | Oncall lead approved production flag enable window (written approval required) | **BLOCKED** | Oncall lead |

**Hard gate:** Do NOT proceed to any staging cutover step until SE1–SE11 are all complete.
**Hard gate:** Do NOT proceed to any production cutover step until D1–D8 are all complete.

---

## Flag Matrix

All three dangerous flags must remain `false` by default. This repo batch does NOT change any default.

| Flag | Service | Config Default | Staging Cutover Value | Production Cutover Value | Hard Rule |
|------|---------|---------------|-----------------------|--------------------------|-----------|
| `account.award-credit-outbox.enabled` | message-job-service, fulfillment-service, big-market-app | `false` | `true` (step S2, after SE3/SE4 DDL confirmed) | `true` (step P5, after D3/D4 DDL confirmed) | Never enable without DBA DDL confirmation + unique-key verification |
| `account.fulfillment.remote-award.enabled` | message-job-service, big-market-app | `false` | `true` (step S5, after outbox canary stable in staging) | `true` (step P7, after outbox canary stable in production) | Never enable before outbox flag is stable and all preconditions met |
| `account.service.remote-quota-decrement.enabled` | market-service | `false` | `false` | `false` | Phase 2.2 separate gate — do NOT enable in this batch under any circumstance |

---

## Staging Cutover Steps

> Execute only after all SE1–SE11 preconditions are satisfied and signed off.
> These steps use staging environment only. No production access is required or used.

### S1: Static pre-flight (local — no staging access required)

```bash
bash scripts/validate-fulfillment-service-b23-b.sh
# Expected: 16/16 PASS

bash scripts/validate-fulfillment-service-b23-c-readiness.sh
# Expected: all PASS

bash scripts/validate-fulfillment-service-b23-d-production-gate.sh
# Expected: all PASS

bash scripts/validate-fulfillment-service-b23-e-cutover-execution.sh
# Expected: all PASS

mvn clean package -DskipTests
# Expected: BUILD SUCCESS (all modules)
```

**Gate S1:** All scripts PASS + BUILD SUCCESS. Record result: ___

### S2: Enable outbox flag in staging message-job-service

```bash
# Enable outbox flag (staging only)
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Verify flag is active
docker compose exec big-market-message-job-service \
  env | grep ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED
# Expected: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true

# Verify service is healthy
curl -sf http://localhost:8085/actuator/health | jq .status
# Expected: "UP"
```

**Gate S2:** Service healthy + flag confirmed true. Record result: ___

### S3: Verify outbox poller is active (staging)

Trigger `DispatchCreditAwardTaskJob_DB1` manually from the staging XXL-Job admin UI.
- Navigate to: XXL-Job Admin → Job Management → Executor: `big-market-message-job-service`
- Trigger `DispatchCreditAwardTaskJob_DB1` once (no params)
- Check logs for: `DispatchCreditAwardTaskJob_DB1 triggered` with exit code SUCCESS

```bash
# Check for any stuck pending rows in staging DB (should be none at baseline)
mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT COUNT(*) as pending_count FROM credit_award_task_000 WHERE state='pending';"
# Expected: 0 (or known test rows only)
```

**Gate S3:** Poller triggers cleanly, no unexpected stuck rows. Record result: ___

### S4: Deploy fulfillment-service in staging with outbox flag enabled

```bash
# fulfillment-service needs outbox flag=true to write outbox rows correctly after remote-award cutover
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-fulfillment-service

# Verify health
curl -sf http://localhost:8087/actuator/health | jq .status
# Expected: "UP"

# Verify Dubbo provider is registered (check nacos or logs)
docker compose logs --tail=50 big-market-fulfillment-service | grep -i "dubbo.*register\|provider.*export\|20882"
# Expected: provider export on port 20882 with no errors
```

**Gate S4:** fulfillment-service healthy, Dubbo provider registered. Record result: ___

### S5: Enable remote-award flag in staging message-job-service (cutover)

```bash
# Enable both flags together (outbox must already be stable from S2 above)
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Verify both flags
docker compose exec big-market-message-job-service env | grep -E "OUTBOX|REMOTE_AWARD"
# Expected:
#   ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true
#   ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true

# Verify service healthy
curl -sf http://localhost:8085/actuator/health | jq .status
# Expected: "UP"
```

**Gate S5:** Both flags confirmed true, service healthy. Record result: ___

### S6: Staging canary window (≥15 min)

Monitor the following during the canary window:

```bash
# Insert a test award event and watch it flow through
# (Use the raffle/draw endpoint if available in staging, or insert a synthetic MQ message)

# Watch credit_award_task outbox table for rows written by fulfillment-service
watch -n 5 'mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT state, count(*) FROM credit_award_task_000 GROUP BY state;"'

# Watch user_credit_order for new rows (idempotency)
mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT out_business_no, COUNT(*) as cnt FROM user_credit_order_000 \
   GROUP BY out_business_no HAVING cnt > 1;"
# Expected: 0 rows (no duplicates)
```

Observability checks during staging canary:

| Metric | Expected | NO-GO Threshold |
|--------|----------|-----------------|
| Draw endpoint error rate | 0% | > 0% → rollback |
| Dubbo RPC error rate to fulfillment-service | 0% | > 1% → rollback remote-award flag |
| `user_credit_order` double-count | 0 | Any duplicate → rollback immediately |
| `credit_award_task` stuck in `pending` > 5 min | 0 | > 3 → investigate, > 10 → rollback |
| fulfillment-service heap/GC anomaly | None | Any OOM → rollback |

**Gate S6:** ≥15 min canary clean. Record result: ___

### S7: Staging E2E validation

```bash
# Verify an end-to-end test award completed:
# 1. raffle win → 2. MQ send_award → 3. SendAwardConsumer → 4. RemoteAwardDispatchAdapter
# → 5. Dubbo → 6. FulfillmentAwardServiceRPC → 7. AwardRepository (outbox row)
# → 8. DispatchCreditAwardTaskJob → 9. account-service credit

mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT award_order_id, state FROM credit_award_task_000 ORDER BY create_time DESC LIMIT 10;"
# Expected: recent rows with state='dispatched'

mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT out_business_no, COUNT(*) FROM user_credit_order_000 \
   GROUP BY out_business_no HAVING COUNT(*) > 1;"
# Expected: 0 rows (idempotency confirmed)
```

**Gate S7:** E2E flow confirmed, no double-credit. Record result: ___

### S8: Restore staging flags to false after validation

```bash
# ALWAYS restore after staging validation
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-fulfillment-service

# Confirm restoration
docker compose exec big-market-message-job-service env | grep -E "OUTBOX|REMOTE_AWARD"
# Expected: both false or not set
```

**Gate S8:** All flags restored to false. Record result: ___

---

## Production Cutover Steps

> Execute only after ALL staging steps S1–S8 are complete AND B23-D sign-offs D1–D8 are complete
> AND the oncall lead has issued written approval for the production cutover window.

### P1: Static pre-flight (local — no production access required)

```bash
bash scripts/validate-fulfillment-service-b23-b.sh        # 16/16 PASS
bash scripts/validate-fulfillment-service-b23-c-readiness.sh   # all PASS
bash scripts/validate-fulfillment-service-b23-d-production-gate.sh  # all PASS
bash scripts/validate-fulfillment-service-b23-e-cutover-execution.sh # all PASS
mvn clean package -DskipTests  # BUILD SUCCESS
```

**Gate P1:** All scripts PASS + BUILD SUCCESS. Record result: ___

### P2: Confirm production DDL is applied

```bash
mysql -h <prod-host> -u <ro-user> -p -e "SHOW TABLES LIKE 'credit_award_task%';" big_market_01
mysql -h <prod-host> -u <ro-user> -p -e "SHOW TABLES LIKE 'credit_award_task%';" big_market_02
# Expected: credit_award_task_000, credit_award_task_001, credit_award_task_002, credit_award_task_003

# Confirm unique-key constraints
mysql -h <prod-host> -u <ro-user> -p -e \
  "SHOW INDEX FROM credit_award_task_000;" big_market_01 | grep -E "uq_award_order_id|uq_out_business_no"
# Expected: both unique keys present on every shard
```

**Gate P2:** 8 outbox shard tables present + unique keys confirmed. Record result: ___

### P3: Deploy fulfillment-service to production (flags still false — dark launch verification)

```bash
docker compose up -d --no-deps --force-recreate big-market-fulfillment-service

# Verify health
curl -sf http://localhost:8087/actuator/health | jq .status
# Expected: "UP"

# Verify Dubbo provider registration in production Nacos
docker compose logs --tail=50 big-market-fulfillment-service | grep -i "dubbo.*register\|provider.*export\|20882"
# Expected: provider export on port 20882 with no errors
```

**Gate P3:** fulfillment-service healthy, Dubbo provider registered in production. Record result: ___

### P4: Oncall approval checkpoint

> STOP. Written approval from the oncall lead is REQUIRED before enabling any flag.
> Do not proceed to P5 without the written approval recorded below.

| Field | Value |
|-------|-------|
| Oncall lead name | ___ |
| Approval timestamp | ___ |
| Approved cutover window | ___ |
| Any conditions or restrictions | ___ |

**Gate P4:** Written approval present. Record result: ___

### P5: Enable outbox flag in production message-job-service (canary)

```bash
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Confirm flag
docker compose exec big-market-message-job-service env | grep ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED
# Expected: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true

# Verify service healthy
curl -sf http://localhost:8085/actuator/health | jq .status
# Expected: "UP"
```

**Gate P5:** Outbox flag confirmed true, service healthy. Record result: ___

### P6: Production outbox canary window (≥15 min)

Monitor the following during the outbox canary window:

| Metric | Expected | Hard NO-GO Threshold |
|--------|----------|----------------------|
| Draw endpoint error rate | 0% | > 0% → rollback immediately |
| `user_credit_order` double-count | 0 | Any duplicate → rollback + escalate |
| `credit_award_task` stuck in `pending` > 5 min | 0 | > 3 → investigate; > 10 → rollback |
| message-job-service heap/GC | Normal | Any OOM → rollback |
| account-service latency P99 | Baseline ±20% | > +50% → rollback |
| Log path | ___ | — |

```bash
# Watch for double-credit during canary window
mysql -h <prod-host> -u <ro-user> -p big_market_01 -e \
  "SELECT out_business_no, COUNT(*) as cnt FROM user_credit_order_000 \
   GROUP BY out_business_no HAVING cnt > 1;"
# Expected: 0 rows at all times during canary window
```

**Gate P6:** ≥15 min canary clean, zero errors, zero double-credit. Record result: ___

### P7: Enable remote-award flag in production message-job-service (traffic cutover)

> Only execute after P6 canary is confirmed clean.

```bash
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Confirm both flags
docker compose exec big-market-message-job-service env | grep -E "OUTBOX|REMOTE_AWARD"
# Expected:
#   ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true
#   ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true

# Verify service healthy
curl -sf http://localhost:8085/actuator/health | jq .status
# Expected: "UP"
```

**Gate P7:** Both flags confirmed true, service healthy. Record result: ___

### P8: Post-cutover monitoring window (≥30 min)

Monitor end-to-end award dispatch through fulfillment-service:

| Metric | Expected | Hard NO-GO Threshold |
|--------|----------|----------------------|
| Draw endpoint error rate | 0% | > 0% → rollback remote-award flag immediately |
| Dubbo RPC error rate (fulfillment-service) | 0% | > 1% → rollback remote-award flag |
| `user_credit_order` double-count | 0 | Any duplicate → rollback + escalate immediately |
| `credit_award_task` stuck in `pending` > 5 min | 0 | > 3 → investigate; > 10 → rollback |
| fulfillment-service heap/GC | Normal | Any OOM → rollback fulfillment-service |
| account-service latency P99 | Baseline ±20% | > +50% → rollback |
| Log path (post-cutover window) | ___ | — |

```bash
# Confirm end-to-end flow is going through fulfillment-service
mysql -h <prod-host> -u <ro-user> -p big_market_01 -e \
  "SELECT state, COUNT(*) FROM credit_award_task_000 GROUP BY state;"
# Expected: recent 'dispatched' rows accumulating; 'pending' count ≤ normal in-flight queue

# Confirm no double-credit
mysql -h <prod-host> -u <ro-user> -p big_market_01 -e \
  "SELECT out_business_no, COUNT(*) as cnt FROM user_credit_order_000 \
   GROUP BY out_business_no HAVING cnt > 1;"
# Expected: 0 rows
```

**Gate P8:** ≥30 min post-cutover window clean. Record result: ___

---

## Rollback Commands

All rollbacks are instant and safe — no data is lost. Pending `credit_award_task` rows
remain in the table and will be retried when the flag is re-enabled.

### Rollback remote-award flag only (preserves outbox flow)

```bash
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
# Traffic immediately routes back to LocalAwardDispatchAdapter (in-process).
# Outbox poller continues normally via DispatchCreditAwardTaskJob in message-job-service.
```

### Rollback outbox flag (full rollback to pre-B23 behavior)

```bash
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
# Pending credit_award_task rows remain with state='pending'.
# When re-enabled, the poller will retry them. No credit writes are lost.
```

### Rollback fulfillment-service (if Dubbo provider is unstable)

```bash
docker compose stop big-market-fulfillment-service
# message-job-service continues on LocalAwardDispatchAdapter if remote-award flag is already false.
# Rollback remote-award flag first, then stop fulfillment-service.
```

### Emergency rollback — double-credit detected

1. **Immediately** stop `big-market-message-job-service`: `docker compose stop big-market-message-job-service`
2. Restore both flags to `false` and restart.
3. Inspect `user_credit_order_*` for duplicate `out_business_no` rows on all shards.
4. Escalate — **DO NOT re-enable either flag until root cause is identified and
   `UNIQUE KEY uq_out_business_no` is confirmed present on all deployed tables.**
5. File an incident report before any further production action.
6. Do NOT promote or declare GO for any subsequent batch until the incident is closed.

---

## Observability Checklist

Confirm each item before and during the cutover window.

### Pre-cutover baseline (record before step P5)

- [ ] Draw endpoint P50/P95/P99 baseline recorded: ___
- [ ] account-service credit endpoint P99 baseline: ___
- [ ] fulfillment-service GC/heap baseline (new service — note the startup state): ___
- [ ] `credit_award_task` pending count baseline (should be 0 in prod before outbox enabled): ___
- [ ] Dashboard / log path being monitored: ___

### During outbox canary (P6)

- [ ] Draw endpoint error rate: ___% (must be 0%)
- [ ] `credit_award_task` pending queue draining normally: ___
- [ ] No `user_credit_order` duplicates: ___
- [ ] message-job-service GC and heap normal: ___
- [ ] account-service P99 within baseline ±20%: ___

### During remote-award canary (P7–P8)

- [ ] Dubbo RPC calls to fulfillment-service appearing in logs: ___
- [ ] Draw endpoint error rate: ___% (must be 0%)
- [ ] fulfillment-service GC and heap normal: ___
- [ ] `credit_award_task` rows written by fulfillment-service JVM (confirm via app host/thread in logs): ___
- [ ] No `user_credit_order` duplicates: ___
- [ ] DispatchCreditAwardTaskJob still running in message-job-service (not fulfillment-service): ___
- [ ] account-service P99 within baseline ±20%: ___

---

## Evidence Attachment Table

Complete this table before declaring GO at any phase.

| # | Evidence Item | File / Screenshot / Log Path | Attached By | Timestamp |
|---|--------------|------------------------------|-------------|-----------|
| E1 | Static validator results (B23-B, C, D, E — all PASS) | ___ | ___ | ___ |
| E2 | `mvn clean package -DskipTests` BUILD SUCCESS log | ___ | ___ | ___ |
| E3 | B23-C staging GO evidence (SE1–SE11 complete) | ___ | ___ | ___ |
| E4 | B23-D production gate sign-off (D1–D8 complete) | ___ | ___ | ___ |
| E5 | Staging canary window logs (S6 — ≥15 min clean) | ___ | ___ | ___ |
| E6 | Staging E2E validation results (S7 — dispatched rows, zero duplicates) | ___ | ___ | ___ |
| E7 | Oncall written approval for production cutover window (P4) | ___ | ___ | ___ |
| E8 | Production DDL verification (P2 — 8 shard tables + unique keys) | ___ | ___ | ___ |
| E9 | Production outbox canary logs (P6 — ≥15 min clean) | ___ | ___ | ___ |
| E10 | Production post-cutover logs (P8 — ≥30 min clean) | ___ | ___ | ___ |
| E11 | Zero `user_credit_order` duplicates confirmed across all production shards | ___ | ___ | ___ |
| E12 | DispatchCreditAwardTaskJob confirmed in message-job-service (not moved) | ___ | ___ | ___ |

---

## Final GO/NO-GO Decision Table

### Phase A — Static pre-flight

| Check | Result |
|-------|--------|
| `validate-fulfillment-service-b23-e-cutover-execution.sh` all PASS | ___ |
| `validate-fulfillment-service-b23-d-production-gate.sh` all PASS | ___ |
| `validate-fulfillment-service-b23-b.sh` 16/16 PASS | ___ |
| `mvn clean package -DskipTests` BUILD SUCCESS | ___ |
| Zero configs with any dangerous flag set to `true` | ___ |

**Phase A gate:** PASS / FAIL
> Hard gate: Do NOT proceed if Phase A gate is FAIL.

### Phase B — Staging evidence validated

| Check | Result |
|-------|--------|
| B23-C staging evidence SE1–SE11 all complete and signed | ___ |
| B23-C staging GO decision signed by oncall lead | ___ |
| Staging canary window ≥15 min clean (gate S6) | ___ |
| Staging E2E confirmed: outbox → dispatched, zero double-credit (gate S7) | ___ |
| All staging flags restored to false after validation (gate S8) | ___ |

**Phase B gate:** PASS / FAIL
> Hard gate: Do NOT proceed to production if Phase B gate is FAIL.

### Phase C — Production DDL and ops sign-off

| Check | Result |
|-------|--------|
| B23-D sign-offs D1–D8 all complete | ___ |
| `credit_award_task_000–003` in `big_market_01` — 4 tables present | ___ |
| `credit_award_task_000–003` in `big_market_02` — 4 tables present | ___ |
| `UNIQUE KEY uq_award_order_id` on all 8 outbox shards | ___ |
| `UNIQUE KEY uq_out_business_no` on all `user_credit_order_*` shards | ___ |
| `DispatchCreditAwardTaskJob_DB1/_DB2` registered in production XXL-Job | ___ |

**Phase C gate:** PASS / FAIL
> Hard gate: Do NOT proceed to Phase D if Phase C gate is FAIL.

### Phase D — Production cutover execution

| Check | Result |
|-------|--------|
| Oncall lead written approval for cutover window (gate P4) | ___ |
| Outbox flag enabled on production canary; service healthy (gate P5) | ___ |
| Production outbox canary ≥15 min clean, zero errors, zero double-credit (gate P6) | ___ |
| Remote-award flag enabled; both flags confirmed true (gate P7) | ___ |
| Post-cutover monitoring ≥30 min clean (gate P8) | ___ |
| End-to-end award dispatch through fulfillment-service confirmed | ___ |
| `DispatchCreditAwardTaskJob` confirmed running in message-job-service (not fulfillment-service) | ___ |

**Phase D gate:** PASS / FAIL

### Phase E — Final sign-off

| Check | Result |
|-------|--------|
| Phase A: static pre-flight PASS | YES / NO |
| Phase B: staging evidence validated and GO signed | YES / NO |
| Phase C: production DDL verified and ops sign-offs complete | YES / NO |
| Phase D: production cutover execution clean | YES / NO |
| No double-credit observed at any stage | YES / NO **(YES required)** |
| No quota leak observed at any stage | YES / NO **(YES required)** |
| `DispatchCreditAwardTaskJob` remained in `message-job-service` throughout | YES / NO **(YES required)** |
| All three dangerous flags in correct final state | YES / NO |
| B23-E evidence file complete with all evidence attachments (E1–E12) | YES / NO **(YES required)** |

**Final Phase 2.3-E decision:** **GO / NO-GO**
**Sign-off by:** ___
**Role:** ___
**Timestamp:** ___
**If NO-GO, reason and next batch:** ___

---

## NO-GO Triggers

Immediately rollback and escalate if ANY of the following:

1. B23-C staging evidence missing, incomplete, or not signed (blocks all staging steps).
2. B23-D sign-offs incomplete (blocks all production steps).
3. Any FAIL in Phase A static pre-flight.
4. Production DDL not applied or unique-key constraints missing.
5. Draw endpoint error rate > 0% during any canary window.
6. Any `user_credit_order` double-count (idempotency violation) — escalate immediately.
7. Any `credit_award_task` rows stuck in `pending` beyond retry threshold.
8. Dubbo RPC error rate > 1% to fulfillment-service.
9. fulfillment-service OOM or GC pressure anomaly.
10. account-service latency P99 > +50% above baseline.
11. `DispatchCreditAwardTaskJob` found in fulfillment-service (premature migration — must stay in message-job-service).
12. Oncall lead approval not recorded before production flag enable.

**Hard no-go conditions** (any one triggers immediate flag=false rollback):
- `user_credit_order` count > 1 for same `out_business_no` (double credit)
- Draw endpoint error rate > 0% during canary window
- fulfillment-service OOM during canary window
- B23-C staging GO decision not present at time of production cutover

---

## What Remains Blocked

Production cutover from this repo batch requires all of the following external actions.
This repo batch does NOT perform any of them.

| Blocker | Owner | Gate |
|---------|-------|------|
| B23-C staging evidence (SE1–SE11) completed, signed off | DBA + Ops + Engineer + Oncall lead | Required before staging cutover steps (S2–S8) |
| B23-D evidence file completed and signed | Oncall lead | Required before production steps (P1–P8) |
| DBA applies `credit_award_task` DDL to production `big_market_01` | DBA | Required before P5 outbox flag enable |
| DBA applies `credit_award_task` DDL to production `big_market_02` | DBA | Required before P5 outbox flag enable |
| Ops registers `DispatchCreditAwardTaskJob_DB1/_DB2` in production XXL-Job | Ops | Required before P5 outbox flag enable |
| Oncall lead issues written approval for production cutover window | Oncall lead | Hard gate before P5 |
| Actual cutover window scheduled and confirmed | Oncall lead | Scheduling artifact; not repo-tracked |

---

## Related Files

| File | Purpose |
|------|---------|
| `docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md` | B23-C staging evidence template (SE1–SE11 must be completed here) |
| `docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md` | B23-D production gate (D1–D8 sign-offs, deployment order, NO-GO triggers) |
| `docs/microservices-split-phase-2-3-fulfillment-service.md` | Phase 2.3 design and batch history |
| `docs/sql/proposed-credit-award-task-outbox.sql` | DDL for `credit_award_task` tables (apply to staging first, then production) |
| `scripts/validate-fulfillment-service-b23-e-cutover-execution.sh` | B23-E static validator (no network/Docker/DB required) |
| `scripts/validate-fulfillment-service-b23-d-production-gate.sh` | B23-D static validator |
| `scripts/validate-fulfillment-service-b23-c-readiness.sh` | B23-C readiness validation |
| `scripts/validate-fulfillment-service-b23-b.sh` | B23-B adapter scaffold validation (16 checks) |
| `big-market-message-job-service/.../config/DispatchCreditAwardTaskJob.java` | Outbox poller (remains in message-job-service — do NOT move) |
| `big-market-fulfillment-service/.../provider/FulfillmentAwardServiceRPC.java` | Dubbo provider (dark launch, port 20882) |
