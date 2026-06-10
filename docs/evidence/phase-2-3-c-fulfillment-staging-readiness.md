# Phase 2.3-C: Fulfillment-Service Staging Readiness Evidence

**Date:** 2026-06-10
**Status:** Awaiting staging access — all local/static gates PASS
**Branch:** main (tag: phase-2.3-c-fulfillment-staging-readiness)

---

## Job Ownership Decision (resolved 2026-06-10)

**Question:** Should `DispatchCreditAwardTaskJob` move to fulfillment-service after traffic cutover?

**Decision: No. The job remains in message-job-service permanently through Phase 2.3-C and beyond.**

**Audit findings:**

| Evidence | What it means |
|----------|--------------|
| `credit_award_task_mapper.xml` exists only in `big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/` | Moving the job requires copying mapper XML — not safe without a dedicated batch |
| `FulfillmentServiceApplication.scanBasePackages` explicitly excludes `trigger.job` | The fulfillment-service design deliberately does not host XXL-Job handlers |
| Both services share the same physical shard DBs (`big_market_01`, `big_market_02`) | The poller can read `credit_award_task` rows regardless of which service wrote them |
| `DispatchCreditAwardTaskJob` class is in `com.dyx.market.message.job.config` | Not on any fulfillment-service scan path; no move needed |
| `account.award-credit-outbox.enabled` flag is present in both services' configs | When enabled in staging, message-job-service runs the poller; fulfillment-service writes outbox rows after cutover |

**Operational model after Phase 2.3-C cutover:**
- `SendAwardConsumer` (message-job-service) → `RemoteAwardDispatchAdapter` → Dubbo → `FulfillmentAwardServiceRPC`
- `AwardRepository.saveGiveOutPrizesAggregate` runs in fulfillment-service → inserts `credit_award_task` row (when outbox flag=true)
- `DispatchCreditAwardTaskJob` (message-job-service) polls same DB → dispatches credit to account-service
- This is coherent: both services connect to the same DB shards; job placement is irrelevant to DB ownership

**Phase 2.3-D consideration:** If operational simplicity requires co-locating writer and poller in fulfillment-service, that move can be a dedicated B23-D sub-task. It is NOT a prerequisite for staging validation.

---

## Prerequisites

All of the following must be true before starting staging validation:

| # | Prerequisite | Status |
|---|-------------|--------|
| P1 | Phase 2.2-B17 staging GO decision issued | **Blocked — staging ledger DDL pending** |
| P2 | `credit_award_task` DDL applied to staging `big_market_01` and `big_market_02` | **Blocked — requires staging DB access** |
| P3 | `DispatchCreditAwardTaskJob_DB1` and `DispatchCreditAwardTaskJob_DB2` registered in XXL-Job admin | **Blocked — requires XXL-Job admin access** |
| P4 | `validate-fulfillment-service-b23-b.sh` 16/16 PASS | PASS (static, local) |
| P5 | `validate-fulfillment-service-b23-c-readiness.sh` all PASS | PASS (static, local) |
| P6 | `mvn clean package -DskipTests` BUILD SUCCESS | PASS (local) |
| P7 | All three dangerous flags are `false` in all configs | Confirmed (see Flag Matrix) |

---

## Flag Matrix

All production/dark-launch flags must remain `false` unless explicitly noted for staging validation.

| Flag | Config Location | Default | Staging Validation Value | Production Value |
|------|----------------|---------|--------------------------|-----------------|
| `account.award-credit-outbox.enabled` | message-job-service, fulfillment-service, big-market-app | `false` | `true` (staging only, temporarily) | `false` until B23-D sign-off |
| `account.fulfillment.remote-award.enabled` | message-job-service, big-market-app | `false` | `false` (not yet) | `false` until B23-D sign-off |
| `account.service.remote-quota-decrement.enabled` | market-service | `false` | `false` | `false` until dedicated batch |

**IMPORTANT:** Production flags remain `false` throughout Phase 2.3-C. Only `award-credit-outbox.enabled` is temporarily set to `true` in staging for outbox E2E validation, and must be restored to `false` after validation.

---

## Staging Validation Commands

### Phase 1: Static pre-flight (no staging access required)

Run all static validation scripts before connecting to any staging environment:

```bash
# B23-B adapter scaffold validation
bash scripts/validate-fulfillment-service-b23-b.sh
# Expected: 16/16 PASS

# B23-C readiness (config safety + wiring + docs)
bash scripts/validate-fulfillment-service-b23-c-readiness.sh
# Expected: all PASS

# Prior outbox batch validations (B4-B10)
bash scripts/validate-award-credit-path.sh                           # B4: 8 checks
bash scripts/validate-award-credit-outbox-readiness.sh               # B5: 8 checks
bash scripts/validate-award-credit-outbox-b6.sh                      # B6: 17 checks
bash scripts/validate-award-credit-outbox-integration.sh             # B7 static
bash scripts/validate-award-credit-outbox-staging-idempotency.sh     # B8 static
bash scripts/validate-award-credit-outbox-e2e-rehearsal.sh           # B9 static
bash scripts/validate-production-ddl.sh                              # B10 static
bash scripts/validate-mq-idempotency.sh                              # B10 MQ static
```

### Phase 2: Apply DDL to staging (requires DBA / staging DB access)

```bash
# Apply credit_award_task outbox DDL to both staging shard DBs
# (file: docs/sql/proposed-credit-award-task-outbox.sql)
mysql -h <staging-db> -u <user> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql
mysql -h <staging-db> -u <user> -p big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql

# Verify tables exist
mysql -h <staging-db> -u <user> -p -e "SHOW TABLES LIKE 'credit_award_task%';" big_market_01
mysql -h <staging-db> -u <user> -p -e "SHOW TABLES LIKE 'credit_award_task%';" big_market_02
# Expected: credit_award_task_000, credit_award_task_001, credit_award_task_002, credit_award_task_003
```

### Phase 3: Enable outbox flag in staging message-job-service

```bash
# Temporarily enable outbox flag in staging (message-job-service only)
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Verify flag is active
docker compose exec big-market-message-job-service \
  env | grep ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED
# Expected: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true

# Verify service is healthy
curl -sf http://localhost:8080/actuator/health
```

### Phase 4: Register XXL-Job handlers (manual — staging XXL-Job admin)

In the XXL-Job admin UI:
1. Find executor: `big-market-message-job-service`
2. Register job: `DispatchCreditAwardTaskJob_DB1` — CRON: `0/30 * * * * ?` — Routing: FIRST
3. Register job: `DispatchCreditAwardTaskJob_DB2` — CRON: `0/30 * * * * ?` — Routing: FIRST

### Phase 5: E2E outbox flow validation (staging)

```bash
# Insert test outbox row directly into staging DB
mysql -h <staging-db> -u <user> -p big_market_01 <<'EOF'
INSERT INTO credit_award_task_000
  (user_id, award_order_id, credit_amount, state, retry_count, create_time, update_time)
VALUES
  ('test-user-b23c', 'test-award-order-b23c-001', 10.00, 'pending', 0, NOW(), NOW());
EOF

# Trigger DispatchCreditAwardTaskJob_DB1 manually from XXL-Job admin UI
# (or via admin API if job ID is known)
curl -X POST http://<xxl-job-admin>:<port>/xxl-job-admin/jobinfo/triggerJob \
  -H "XXL-JOB-ACCESS-TOKEN: <token>" \
  -d "id=<job-id>&executorParam="

# Wait 30s, then verify state transition
mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT state, retry_count FROM credit_award_task_000 WHERE award_order_id='test-award-order-b23c-001';"
# Expected: state='dispatched'

# Verify credit order created in account-service
mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT COUNT(*) FROM user_credit_order_000 WHERE out_business_no='test-award-order-b23c-001';"
# Expected: COUNT=1

# Idempotency re-trigger: reset row to pending and trigger again
mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "UPDATE credit_award_task_000 SET state='pending' WHERE award_order_id='test-award-order-b23c-001';"
# Trigger DispatchCreditAwardTaskJob_DB1 again from XXL-Job admin

# Verify no double-credit
mysql -h <staging-db> -u <user> -p big_market_01 -e \
  "SELECT COUNT(*) FROM user_credit_order_000 WHERE out_business_no='test-award-order-b23c-001';"
# Expected: COUNT still 1 (idempotency confirmed)
```

### Phase 6: Restore flag to false after validation

```bash
# ALWAYS restore after staging validation — never leave outbox flag=true in staging
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Verify restoration
curl -sf http://localhost:8080/actuator/health
docker compose exec big-market-message-job-service \
  env | grep ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED
# Expected: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false (or not set)
```

---

## Expected MQ/Outbox Behavior

```
When account.award-credit-outbox.enabled=true AND account.fulfillment.remote-award.enabled=false:

MQ: send_award → SendAwardConsumer (message-job-service)
  → LocalAwardDispatchAdapter.distributeAward()     [flag=false, in-process]
  → AwardService.distributeAward()
  → UserCreditRandomAward.giveOutPrizes()
  → AwardRepository.saveGiveOutPrizesAggregate()
       transactionTemplate {
         creditAwardTaskDao.insert(pending row)     [NEW — outbox row instead of direct credit write]
         updateAwardRecordCompletedState()
       }

Polling (every ~30s): DispatchCreditAwardTaskJob_DB1/DB2 (message-job-service)
  → creditAwardTaskDao.queryPendingTasks()
  → IAccountCreditWriteAdapter.createOrder(outBusinessNo=awardOrderId)
  → account-service: CreditAdjustService.createOrder() deduplicates on out_business_no
  → creditAwardTaskDao.updateDispatched()
```

```
When BOTH flags are eventually true (Phase 2.3-C cutover — NOT for B23-C validation):

MQ: send_award → SendAwardConsumer (message-job-service)
  → RemoteAwardDispatchAdapter.distributeAward()    [flag=true, Dubbo RPC]
  → FulfillmentAwardServiceRPC (fulfillment-service, port 20882)
  → AwardRepository.saveGiveOutPrizesAggregate()    [runs in fulfillment-service's JVM]
       transactionTemplate {
         creditAwardTaskDao.insert(pending row)
         updateAwardRecordCompletedState()
       }

Polling: DispatchCreditAwardTaskJob_DB1/DB2 (still in message-job-service)
  → same DB shards → reads rows written by fulfillment-service
  → dispatches credit to account-service
```

---

## Rollback Plan

### Rollback outbox flag (always safe, no data loss)

```bash
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
```

All pending `credit_award_task` rows remain in the table with `state='pending'`. When the flag is re-enabled, the poller will retry them. No data is lost.

### Rollback remote-award flag (if somehow enabled during testing)

```bash
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
```

### Emergency rollback if double-credit detected

1. Stop message-job-service immediately: `docker compose stop big-market-message-job-service`
2. Restore flags: `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` and restart
3. Inspect `user_credit_order` for duplicate `out_business_no` rows
4. Escalate: **DO NOT re-enable until root cause is identified and `UNIQUE KEY uq_out_business_no` is confirmed on deployed tables**
5. Do NOT promote to production

---

## Evidence Checklist

All of the following must be checked off before Phase 2.3-C GO:

### Static (automated — verify locally first)

- [ ] `validate-fulfillment-service-b23-b.sh` 16/16 PASS
- [ ] `validate-fulfillment-service-b23-c-readiness.sh` all PASS
- [ ] `validate-award-credit-path.sh` 8/8 PASS
- [ ] `validate-award-credit-outbox-b6.sh` 17/17 PASS
- [ ] `validate-award-credit-outbox-staging-idempotency.sh` 13/13 PASS (static)
- [ ] `validate-award-credit-outbox-e2e-rehearsal.sh` 11/11 PASS (static/dry-run)
- [ ] `mvn clean package -DskipTests` BUILD SUCCESS (all 14 modules)
- [ ] Zero config files with dangerous flags set to `true` by default

### Staging (manual — requires staging access)

- [ ] `credit_award_task` DDL applied to `big_market_01` (4 shard tables: 000–003)
- [ ] `credit_award_task` DDL applied to `big_market_02` (4 shard tables: 000–003)
- [ ] `DispatchCreditAwardTaskJob_DB1` registered in XXL-Job admin on staging executor
- [ ] `DispatchCreditAwardTaskJob_DB2` registered in XXL-Job admin on staging executor
- [ ] message-job-service starts cleanly with `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true`
- [ ] Test outbox row inserts with `state='pending'`
- [ ] `DispatchCreditAwardTaskJob_DB1` triggered manually → row transitions to `state='dispatched'`
- [ ] Exactly 1 `user_credit_order` row for `out_business_no=test-award-order-b23c-001`
- [ ] Idempotency re-trigger: still exactly 1 `user_credit_order` row (no double-credit)
- [ ] message-job-service healthy after `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED` restored to `false`
- [ ] All three dangerous flags confirmed `false` after restoration

### Phase 2.3-B cutover validation (additional — after outbox is staging-validated)

- [ ] `account.fulfillment.remote-award.enabled=true` tested in staging
- [ ] Award dispatch flows through `RemoteAwardDispatchAdapter` → `FulfillmentAwardServiceRPC`
- [ ] Outbox row written by fulfillment-service's `AwardRepository` is dispatched by message-job-service's poller
- [ ] End-to-end: raffle win → MQ → SendAwardConsumer → Dubbo → fulfillment → outbox → DispatchCreditAwardTaskJob → account-service credit

---

## GO/NO-GO Criteria

### GO: Phase 2.3-C staging validation complete

All of the following must be true:

1. All static checks PASS (no exceptions)
2. Staging outbox DDL applied and verified (4 tables per shard DB)
3. `DispatchCreditAwardTaskJob` handlers registered in XXL-Job admin
4. End-to-end outbox flow validated: pending → dispatched, exactly 1 credit order row
5. Idempotency confirmed: re-trigger produces 0 new credit order rows
6. All three dangerous flags confirmed `false` after staging restoration

### NO-GO: Any of the following

- Any static check FAIL
- `credit_award_task` DDL not applied to both shard DBs
- `state` does not transition to `dispatched` within 5 retries
- `user_credit_order` count > 1 for the same `out_business_no` (double-credit — escalate immediately)
- Service unhealthy after flag restoration
- `UNIQUE KEY uq_award_order_id` or `uq_out_business_no` missing from deployed tables

---

## What Remains Blocked (requires staging access)

| Blocker | Owner | Gate |
|---------|-------|------|
| Phase 2.2-B17 staging GO (ledger DDL, outbox DDL, XXL-Job registration) | DBA + ops | Required before any staging cutover |
| `credit_award_task` DDL applied to staging shard DBs | DBA | Required before `outbox.enabled=true` in staging |
| XXL-Job handler registration for `DispatchCreditAwardTaskJob_DB1/_DB2` | Ops | Required for E2E dispatch |
| Staging E2E manual steps (Phases 2–6 above) | Engineer with staging access | Required for Phase 2.3-C GO |
| Phase 2.3-B traffic cutover (`remote-award.enabled=true` in staging) | Post B23-C GO | Required for Phase 2.3-D |

---

## Related Files

| File | Purpose |
|------|---------|
| `docs/microservices-split-phase-2-3-fulfillment-service.md` | Phase 2.3 design and batch history |
| `docs/sql/proposed-credit-award-task-outbox.sql` | DDL for `credit_award_task` tables (apply to staging first) |
| `scripts/validate-fulfillment-service-b23-b.sh` | B23-B static validation (16 checks) |
| `scripts/validate-fulfillment-service-b23-c-readiness.sh` | B23-C readiness validation (this batch) |
| `scripts/validate-award-credit-outbox-e2e-rehearsal.sh` | B9 E2E rehearsal + promotion gate |
| `big-market-message-job-service/.../config/DispatchCreditAwardTaskJob.java` | Outbox poller (stays in message-job-service) |
| `big-market-fulfillment-service/.../provider/FulfillmentAwardServiceRPC.java` | Dubbo provider (dark launch, port 20882) |
| `big-market-trigger/.../adapter/IAwardDispatchAdapter.java` | Dispatch adapter seam (B23-B) |
