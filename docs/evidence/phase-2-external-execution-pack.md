# Phase 2 External Execution Pack

**Date:** 2026-06-10
**Scope:** Phase 2.2-B17 staging GO + Phase 2.3-C staging evidence + Phase 2.3-D production gate + Phase 2.3-E cutover window preparation
**Status:** TEMPLATE — no staging or production traffic enabled

> **THIS DOCUMENT IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC.**
> All three dangerous flags must remain `false` by default throughout this document.
> Enabling any flag in staging or production requires the external sign-offs and
> evidence listed below. No repo action substitutes for real staging/prod execution.

---

## Safety Invariants (hard constraints — never override)

| Flag | Services | Default | Hard Rule |
|------|----------|---------|-----------|
| `account.award-credit-outbox.enabled` | message-job-service, fulfillment-service, big-market-app | **`false`** | Never enable without DBA DDL confirmation + unique-key verification |
| `account.service.remote-quota-decrement.enabled` | market-service | **`false`** | Never enable without Phase 2.2-B17 staging GO and full evidence file |
| `account.fulfillment.remote-award.enabled` | message-job-service, big-market-app | **`false`** | Never enable before outbox flag is stable and B23-C staging evidence signed |

**`DispatchCreditAwardTaskJob` remains in `big-market-message-job-service` permanently through Phase 2.3.**
Do NOT move it to fulfillment-service in any batch covered by this pack.

---

## Strict Execution Ordering

Execute phases in this order. Each gate is a hard dependency for the next.

```
Phase 1 — Phase 2.2-B17 Staging GO
    DBA: apply ledger DDL + outbox DDL to staging big_market_01 and big_market_02
    Ops: register DispatchCreditAwardTaskJob_DB1 and _DB2 in staging XXL-Job
    Engineer: run CONNECT_REMOTE verification + E2E draw/rollback/outbox tests
    Oncall: sign Phase K go/no-go decision in b17-staging-evidence-20260610.md
    Gate: b17-staging-evidence-20260610.md Phase K = GO
          ↓ BLOCKED until Phase 1 gate is green

Phase 2 — Phase 2.3-C Staging Evidence (SE1–SE11)
    Pre-req: Phase 1 gate green
    DBA: credit_award_task DDL already applied (shared with B17 — verify it landed)
    Ops: DispatchCreditAwardTaskJob already registered (shared with B17 — verify)
    Engineer: outbox E2E + remote-award Dubbo E2E + flag restore
    Oncall: sign SE11 staging GO decision in phase-2-3-c-fulfillment-staging-readiness.md
    Gate: SE1–SE11 all signed
          ↓ BLOCKED until Phase 2 gate is green

Phase 3 — Phase 2.3-D Production Gate Sign-off
    Pre-req: Phase 2 gate green
    DBA: apply credit_award_task DDL to production big_market_01 and big_market_02
    Ops: register DispatchCreditAwardTaskJob_DB1/_DB2 in production XXL-Job
    Oncall: issue written approval for production cutover window
    Gate: B23-D evidence file (phase-2-3-d-fulfillment-production-promotion-gate.md)
          Phase A–E complete and signed
          ↓ BLOCKED until Phase 3 gate is green

Phase 4 — Phase 2.3-E Cutover Window Preparation
    Pre-req: Phase 3 gate green + oncall written approval recorded
    Engineer: run static pre-flight S1/P1 immediately before window
    Execute staging cutover S1–S8 (phase-2-3-e-fulfillment-cutover-execution.md)
    Then production cutover P1–P8 (phase-2-3-e-fulfillment-cutover-execution.md)
    Gate: Evidence table E1–E12 complete; final Phase E sign-off = GO
```

---

## Hard NO-GO Rules

Stop and escalate immediately if ANY of the following:

1. Any static validator script fails (`validate-fulfillment-service-phase-2-3.sh` not all PASS).
2. Any dangerous flag found hardcoded `true` in any config file.
3. `UNIQUE KEY uq_award_order_id` or `uq_out_business_no` missing from any deployed shard table.
4. `user_credit_order` count > 1 for same `out_business_no` (double-credit — escalate immediately).
5. Any quota change on duplicate draw (idempotency violation).
6. Draw endpoint error rate > 0% during any canary window.
7. fulfillment-service OOM during any canary window.
8. B23-C staging GO decision not signed before production cutover.
9. Oncall written approval not recorded before P5 production flag enable.
10. `DispatchCreditAwardTaskJob` found running in fulfillment-service (must stay in message-job-service).

---

## Section A — DBA Tasks

### DBA Scope

The DBA is responsible for applying and verifying all DDL to staging and production shard databases. No traffic flags are enabled by the DBA — that is done by the Engineer after DBA sign-off.

### DBA Copy/Paste Task List

#### Phase 1: B17 Staging DDL

```bash
# Step 1a — Apply ledger DDL to both staging shards
mysql -h <staging-host> -u <admin> -p big_market_01 < docs/sql/proposed-quota-decrement-ledger.sql
mysql -h <staging-host> -u <admin> -p big_market_02 < docs/sql/proposed-quota-decrement-ledger.sql

# Step 1b — Apply outbox DDL to both staging shards
mysql -h <staging-host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql
mysql -h <staging-host> -u <admin> -p big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql

# Step 1c — Verify ledger tables (expected: 4 tables per DB)
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW TABLES LIKE 'raffle_quota_decrement_ledger%';" big_market_01
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW TABLES LIKE 'raffle_quota_decrement_ledger%';" big_market_02

# Step 1d — Verify outbox tables (expected: 4 tables per DB)
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW TABLES LIKE 'credit_award_task%';" big_market_01
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW TABLES LIKE 'credit_award_task%';" big_market_02

# Step 1e — Verify unique keys on outbox shards (run for each shard _000 to _003 on each DB)
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW INDEX FROM credit_award_task_000;" big_market_01 | grep uq_award_order_id
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW INDEX FROM credit_award_task_000;" big_market_02 | grep uq_award_order_id

# Step 1f — Verify unique key on user_credit_order shards
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW INDEX FROM user_credit_order_000;" big_market_01 | grep uq_out_business_no
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW INDEX FROM user_credit_order_000;" big_market_02 | grep uq_out_business_no

# Step 1g — Verify unique key on ledger shards
mysql -h <staging-host> -u <ro-user> -p -e \
  "SHOW INDEX FROM raffle_quota_decrement_ledger_000;" big_market_01 | grep uq_user_activity_biz
```

#### Phase 3: B23-D Production DDL

```bash
# Step 3a — Apply outbox DDL to both production shards
mysql -h <prod-host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql
mysql -h <prod-host> -u <admin> -p big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql

# Step 3b — Verify outbox tables in production (expected: 4 per DB)
mysql -h <prod-host> -u <ro-user> -p -e \
  "SHOW TABLES LIKE 'credit_award_task%';" big_market_01
mysql -h <prod-host> -u <ro-user> -p -e \
  "SHOW TABLES LIKE 'credit_award_task%';" big_market_02

# Step 3c — Verify uq_award_order_id on all 8 production outbox shards
for shard in 000 001 002 003; do
  echo "=== big_market_01.credit_award_task_${shard} ==="
  mysql -h <prod-host> -u <ro-user> -p -e \
    "SHOW INDEX FROM credit_award_task_${shard};" big_market_01 | grep uq_award_order_id
  echo "=== big_market_02.credit_award_task_${shard} ==="
  mysql -h <prod-host> -u <ro-user> -p -e \
    "SHOW INDEX FROM credit_award_task_${shard};" big_market_02 | grep uq_award_order_id
done

# Step 3d — Verify uq_out_business_no on all production user_credit_order shards
for shard in 000 001 002 003; do
  echo "=== big_market_01.user_credit_order_${shard} ==="
  mysql -h <prod-host> -u <ro-user> -p -e \
    "SHOW INDEX FROM user_credit_order_${shard};" big_market_01 | grep uq_out_business_no
  echo "=== big_market_02.user_credit_order_${shard} ==="
  mysql -h <prod-host> -u <ro-user> -p -e \
    "SHOW INDEX FROM user_credit_order_${shard};" big_market_02 | grep uq_out_business_no
done
```

### DBA Evidence Artifacts to Attach

| # | Artifact | File / Screenshot / Command Output | Phase |
|---|----------|-------------------------------------|-------|
| DA1 | Staging ledger DDL apply result (big_market_01) | ___ | Phase 1 |
| DA2 | Staging ledger DDL apply result (big_market_02) | ___ | Phase 1 |
| DA3 | Staging outbox DDL apply result (big_market_01) | ___ | Phase 1 |
| DA4 | Staging outbox DDL apply result (big_market_02) | ___ | Phase 1 |
| DA5 | Staging `SHOW TABLES LIKE 'credit_award_task%'` both DBs | ___ | Phase 1 |
| DA6 | Staging `SHOW INDEX` output confirming uq_award_order_id on all 8 shards | ___ | Phase 1 |
| DA7 | Staging `SHOW INDEX` output confirming uq_out_business_no on user_credit_order | ___ | Phase 1 |
| DA8 | Production outbox DDL apply result (big_market_01) | ___ | Phase 3 |
| DA9 | Production outbox DDL apply result (big_market_02) | ___ | Phase 3 |
| DA10 | Production `SHOW TABLES LIKE 'credit_award_task%'` both DBs | ___ | Phase 3 |
| DA11 | Production `SHOW INDEX` confirming uq_award_order_id on all 8 production shards | ___ | Phase 3 |
| DA12 | Production `SHOW INDEX` confirming uq_out_business_no on all user_credit_order shards | ___ | Phase 3 |

### DBA Sign-off

| Role | Action | Name | Timestamp | Result |
|------|--------|------|-----------|--------|
| DBA | Staging DDL complete (DA1–DA7) | ___ | ___ | DONE / FAIL |
| DBA | Production DDL complete (DA8–DA12) | ___ | ___ | DONE / FAIL |

> **Rollback guidance:** Do not rollback DDL (DROP TABLE) without written approval from the incident lead.
> DDL rollback on live shards can cause data loss. If tables are empty and must be removed,
> open an incident ticket and wait for incident lead sign-off before executing DROP TABLE.

---

## Section B — Ops Tasks

### Ops Scope

The Ops team is responsible for registering XXL-Job handlers in staging and production XXL-Job admin. The same two handlers are required in both environments.

### Ops Copy/Paste Task List

#### Phase 1: B17 Staging XXL-Job Registration

In the **staging** XXL-Job admin UI (http://\<xxl-job-admin-staging\>:\<port\>/xxl-job-admin):

1. Navigate to: Job Management → Executor: `big-market-message-job-service`
2. Create job 1:
   - **JobHandler:** `DispatchCreditAwardTaskJob_DB1`
   - **Cron:** `0/30 * * * * ?`
   - **Routing Strategy:** FIRST
   - **Description:** Dispatch credit award outbox — shard DB1
   - **AppName:** `big-market-job`
3. Create job 2:
   - **JobHandler:** `DispatchCreditAwardTaskJob_DB2`
   - **Cron:** `0/30 * * * * ?`
   - **Routing Strategy:** FIRST
   - **Description:** Dispatch credit award outbox — shard DB2
   - **AppName:** `big-market-job`
4. Manually trigger `DispatchCreditAwardTaskJob_DB1` once (no params) and confirm SUCCESS exit code in the execution log.
5. Manually trigger `DispatchCreditAwardTaskJob_DB2` once (no params) and confirm SUCCESS exit code.

Expected logs (check XXL-Job execution log):
```
DispatchCreditAwardTaskJob_DB1 triggered
exitCode=200
handleMsg=SUCCESS
```

#### Phase 3: B23-D Production XXL-Job Registration

In the **production** XXL-Job admin UI:

1. Navigate to: Job Management → Executor: `big-market-message-job-service`
2. Create job 1 (same spec as staging):
   - **JobHandler:** `DispatchCreditAwardTaskJob_DB1`
   - **Cron:** `0/30 * * * * ?`
   - **Routing Strategy:** FIRST
3. Create job 2:
   - **JobHandler:** `DispatchCreditAwardTaskJob_DB2`
   - **Cron:** `0/30 * * * * ?`
   - **Routing Strategy:** FIRST
4. Do NOT trigger the production jobs yet — wait for Engineer/Oncall to enable the outbox flag (Step P5 in the cutover runbook).

### Ops Evidence Artifacts to Attach

| # | Artifact | File / Screenshot | Phase |
|---|----------|-------------------|-------|
| OA1 | Staging XXL-Job: DispatchCreditAwardTaskJob_DB1 registration screenshot | ___ | Phase 1 |
| OA2 | Staging XXL-Job: DispatchCreditAwardTaskJob_DB2 registration screenshot | ___ | Phase 1 |
| OA3 | Staging XXL-Job: manual trigger result for DB1 (exitCode=200) | ___ | Phase 1 |
| OA4 | Staging XXL-Job: manual trigger result for DB2 (exitCode=200) | ___ | Phase 1 |
| OA5 | Production XXL-Job: DispatchCreditAwardTaskJob_DB1 registration screenshot | ___ | Phase 3 |
| OA6 | Production XXL-Job: DispatchCreditAwardTaskJob_DB2 registration screenshot | ___ | Phase 3 |

### Ops Sign-off

| Role | Action | Name | Timestamp | Result |
|------|--------|------|-----------|--------|
| Ops | Staging XXL-Job handlers registered and manually verified (OA1–OA4) | ___ | ___ | DONE / FAIL |
| Ops | Production XXL-Job handlers registered (OA5–OA6) | ___ | ___ | DONE / FAIL |

### Ops NO-GO Triggers

Do not proceed (or escalate immediately) if:
- XXL-Job executor `big-market-message-job-service` is not online in the target environment.
- Manual trigger returns exitCode ≠ 200 or logs contain exception/error.
- Any of DA1–DA12 DBA evidence is still PENDING before Ops marks phase as DONE.

---

## Section C — Engineer Tasks

### Engineer Scope

The Engineer is responsible for running static pre-flight validation, executing the E2E staging tests, and enabling/restoring flags during the validated maintenance window. The Engineer does NOT enable production flags without oncall written approval.

### Engineer Copy/Paste Task List

#### Pre-flight (any time, no staging/prod access required)

```bash
# Run evidence consistency validator — checks doc coverage, gitignore policy, tags, flags, cross-links
bash scripts/validate-phase-2-evidence-consistency.sh
# Expected: ALL CHECKS PASS

# Run full suite validator — must be all PASS before any staging action
bash scripts/validate-fulfillment-service-phase-2-3.sh
# Expected: ALL SUITES PASS

# Collect local evidence snapshot (output gitignored — local only, never committed)
bash scripts/collect-phase-2-external-evidence.sh
# Output: docs/evidence/generated/phase2-evidence-YYYYMMDDHHMMSS/
# Note: docs/evidence/generated/ is listed in .gitignore. No staging or production
# traffic is enabled by running this script. All dangerous flags remain false by default.
```

#### Phase 1: B17 Staging E2E (after DBA Phase 1 and Ops Phase 1 complete)

```bash
# Step 1 — Verify DDL via CONNECT_REMOTE
CONNECT_REMOTE=true MYSQL_HOST=<staging-host> MYSQL_PORT=3306 \
  MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
  ./scripts/execute-account-service-staging-b17.sh
# Expected: 0 FAIL

# Step 2 — Enable remote-quota-decrement flag in staging market-service
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-market-service

# Step 3 — Armory (required before draw)
curl -s "http://<staging-host>:8091/api/v1/raffle/activity/armory?activityId=<activityId>"
# Expected: HTTP 200, code=0000, data=true

# Step 4 — E2E draw test (fill b17-staging-evidence-20260610.md phases F-H)
# ... (see b17-staging-evidence-20260610.md for exact steps)

# Step 5 — Restore flag
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-market-service

# Step 6 — Post-window verification
B17_POST_CHECK=true MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
  ./scripts/execute-account-service-staging-b17.sh
# Expected: 0 FAIL
```

Fill in `docs/evidence/b17-staging-evidence-20260610.md` Phases A–K and sign off.

#### Phase 2: B23-C Staging Evidence (after Phase 1 GO)

```bash
# Step 1 — Enable outbox flag in staging message-job-service
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Step 2 — Insert test outbox row and trigger DispatchCreditAwardTaskJob_DB1
mysql -h <staging-host> -u <user> -p big_market_01 <<'EOF'
INSERT INTO credit_award_task_000
  (user_id, award_order_id, credit_amount, state, retry_count, create_time, update_time)
VALUES ('test-b23c', 'test-order-b23c-001', 10.00, 'pending', 0, NOW(), NOW());
EOF
# Trigger DB1 from XXL-Job admin, then verify:
mysql -h <staging-host> -u <user> -p big_market_01 -e \
  "SELECT state FROM credit_award_task_000 WHERE award_order_id='test-order-b23c-001';"
# Expected: state=dispatched

# Step 3 — Verify idempotency (no double-credit)
mysql -h <staging-host> -u <user> -p big_market_01 -e \
  "SELECT COUNT(*) FROM user_credit_order_000 WHERE out_business_no='test-order-b23c-001';"
# Expected: 1

# Step 4 — Enable remote-award flag and validate Dubbo path
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service

# Step 5 — Restore all flags
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
```

Fill in SE1–SE11 in `docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md`.

#### Phase 4: B23-E Cutover Execution

Follow `docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md` steps S1–S8 (staging) then P1–P8 (production).

### Engineer Evidence Artifacts to Attach

| # | Artifact | File / Screenshot / Log Path | Phase |
|---|----------|------------------------------|-------|
| EA1 | `validate-fulfillment-service-phase-2-3.sh` all PASS log | ___ | Pre-flight |
| EA2 | `collect-phase-2-external-evidence.sh` output directory | ___ | Pre-flight |
| EA3 | B17 CONNECT_REMOTE verification: 0 FAIL | ___ | Phase 1 |
| EA4 | B17 E2E draw result (HTTP 200, ledger=applied, quota decremented) | ___ | Phase 1 |
| EA5 | B17 outbox dispatch: pending→dispatched, user_credit_order count=1 | ___ | Phase 1 |
| EA6 | B17 post-window check: 0 FAIL | ___ | Phase 1 |
| EA7 | B23-C outbox E2E: pending→dispatched, count=1, idempotency confirmed | ___ | Phase 2 |
| EA8 | B23-C remote-award Dubbo E2E confirmed | ___ | Phase 2 |
| EA9 | B23-C all flags restored to false | ___ | Phase 2 |
| EA10 | B23-E staging canary S6: ≥15 min clean | ___ | Phase 4 |
| EA11 | B23-E production canary P6: ≥15 min clean | ___ | Phase 4 |
| EA12 | B23-E post-cutover P8: ≥30 min clean, zero double-credit | ___ | Phase 4 |

### Engineer Sign-off

| Role | Action | Name | Timestamp | Result |
|------|--------|------|-----------|--------|
| Engineer | Pre-flight all PASS | ___ | ___ | PASS / FAIL |
| Engineer | B17 staging E2E complete (EA3–EA6) | ___ | ___ | DONE / FAIL |
| Engineer | B23-C staging evidence SE1–SE11 complete (EA7–EA9) | ___ | ___ | DONE / FAIL |
| Engineer | B23-E cutover complete (EA10–EA12) | ___ | ___ | DONE / FAIL |

---

## Section D — Oncall Lead Tasks

### Oncall Scope

The Oncall lead is responsible for reviewing evidence at each gate, issuing written GO decisions, and approving production flag enable windows. No code or DDL changes are made by the Oncall lead.

### Oncall Copy/Paste Task List

```
After Phase 1 (B17) — sign Phase K in b17-staging-evidence-20260610.md:
    Review: DA1–DA7 (DBA), OA1–OA4 (Ops), EA3–EA6 (Engineer)
    Decision: GO / NO-GO
    Sign: Name + Timestamp + Role in "Phase K" section

After Phase 2 (B23-C) — sign SE11 in phase-2-3-c-fulfillment-staging-readiness.md:
    Review: SE1–SE10 filled and confirmed
    Decision: B23-C staging GO / NO-GO
    Sign: Name + Timestamp + Role in "SE11" row

After Phase 3 (B23-D) — sign Final Phase 2.3-D decision in phase-2-3-d-fulfillment-production-promotion-gate.md:
    Review: DBA sign-offs (PP3–PP6), Ops sign-off (PP7), SE1–SE11, B23-D Phase A–D
    Issue written approval for production flag enable window
    Sign: Name + Timestamp + Role in "Phase E — Final sign-off"

Before Phase 4 production flag enable (P4 checkpoint) — issue written approval:
    Record the following in phase-2-3-e-fulfillment-cutover-execution.md P4 checkpoint:
        Oncall lead name: ___
        Approval timestamp: ___
        Approved cutover window: ___
        Any conditions or restrictions: ___

After Phase 4 completion — sign Final Phase 2.3-E decision:
    Review: Evidence table E1–E12 complete
    Decision: GO / NO-GO
    Sign: Name + Timestamp + Role in "Phase E — Final sign-off"
```

### Oncall Evidence Artifacts to Attach

| # | Artifact | Document + Section | Phase |
|---|----------|--------------------|-------|
| OC1 | B17 Phase K GO decision (name + timestamp) | b17-staging-evidence-20260610.md § Phase K | Phase 1 |
| OC2 | B23-C SE11 staging GO decision (name + timestamp) | phase-2-3-c-fulfillment-staging-readiness.md § SE11 | Phase 2 |
| OC3 | B23-D Final Phase E sign-off + written production window approval | phase-2-3-d-fulfillment-production-promotion-gate.md § Phase E | Phase 3 |
| OC4 | P4 written approval for production flag enable | phase-2-3-e-fulfillment-cutover-execution.md § P4 | Phase 4 |
| OC5 | B23-E Final Phase E GO decision (name + timestamp) | phase-2-3-e-fulfillment-cutover-execution.md § Phase E | Phase 4 |

### Oncall Sign-off Summary

| Gate | Document | Section | Signed By | Timestamp | Decision |
|------|----------|---------|-----------|-----------|---------|
| B17 staging GO | b17-staging-evidence-20260610.md | Phase K | ___ | ___ | GO / NO-GO |
| B23-C staging GO | phase-2-3-c-fulfillment-staging-readiness.md | SE11 | ___ | ___ | GO / NO-GO |
| B23-D production gate | phase-2-3-d-fulfillment-production-promotion-gate.md | Phase E | ___ | ___ | GO / NO-GO |
| Production flag enable approval | phase-2-3-e-fulfillment-cutover-execution.md | P4 | ___ | ___ | APPROVED / DENIED |
| B23-E final GO | phase-2-3-e-fulfillment-cutover-execution.md | Phase E | ___ | ___ | GO / NO-GO |

---

## Gate Summary Table

| Gate | Owner | Blocks | Status |
|------|-------|--------|--------|
| B17 staging DDL applied (DA1–DA7) | DBA | B17 E2E (Phase 1) | PENDING |
| B17 XXL-Job registered (OA1–OA4) | Ops | B17 E2E (Phase 1) | PENDING |
| B17 E2E complete + Phase K GO | Engineer + Oncall | B23-C (Phase 2) | PENDING |
| B23-C SE1–SE10 complete (EA7–EA9) | Engineer | SE11 oncall sign-off | PENDING |
| B23-C SE11 signed (OC2) | Oncall | B23-D production gate (Phase 3) | PENDING |
| Production DDL applied (DA8–DA12) | DBA | Phase 3 gate | PENDING |
| Production XXL-Job registered (OA5–OA6) | Ops | Phase 3 gate | PENDING |
| B23-D Phase E sign-off + written production window approval (OC3) | Oncall | Phase 4 cutover | PENDING |
| P4 written approval (OC4) | Oncall | P5 production flag enable | PENDING |
| B23-E Phase E GO (OC5) | Oncall | Done | PENDING |

---

## Related Documents

| Document | Purpose |
|----------|---------|
| [`docs/evidence/b17-staging-evidence-20260610.md`](b17-staging-evidence-20260610.md) | B17 staging E2E evidence (operator fills Phases A–K) |
| [`docs/evidence/phase-2-2-b17-staging-cutover-template.md`](phase-2-2-b17-staging-cutover-template.md) | B17 staging cutover template reference |
| [`docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md`](phase-2-3-c-fulfillment-staging-readiness.md) | B23-C staging evidence (SE1–SE11) |
| [`docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md`](phase-2-3-d-fulfillment-production-promotion-gate.md) | B23-D production gate (Phase A–E sign-offs) |
| [`docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md`](phase-2-3-e-fulfillment-cutover-execution.md) | B23-E cutover runbook (S1–S8 staging, P1–P8 production) |
| [`docs/evidence/phase-2-dba-checklist.md`](phase-2-dba-checklist.md) | DBA detailed checklist with SQL files and verification queries |
| [`docs/evidence/phase-2-ops-xxl-job-checklist.md`](phase-2-ops-xxl-job-checklist.md) | Ops XXL-Job registration checklist |
| [`docs/sql/proposed-quota-decrement-ledger.sql`](../sql/proposed-quota-decrement-ledger.sql) | Ledger DDL (apply to staging and production) |
| [`docs/sql/proposed-credit-award-task-outbox.sql`](../sql/proposed-credit-award-task-outbox.sql) | Outbox DDL (apply to staging and production) |
| [`scripts/collect-phase-2-external-evidence.sh`](../../scripts/collect-phase-2-external-evidence.sh) | Local evidence collector; output written to `docs/evidence/generated/` (gitignored — local only, never committed) |
| [`scripts/validate-phase-2-external-execution-pack.sh`](../../scripts/validate-phase-2-external-execution-pack.sh) | Validator for this pack (no staging/prod access required) |
| [`scripts/validate-fulfillment-service-phase-2-3.sh`](../../scripts/validate-fulfillment-service-phase-2-3.sh) | Full Phase 2.3 suite validator (run before any staging/prod action) |
| [`scripts/validate-phase-2-evidence-consistency.sh`](../../scripts/validate-phase-2-evidence-consistency.sh) | Evidence consistency validator: checks Phase 2.2/2.3 doc coverage, gitignore policy, key tags, dangerous flags, and cross-links (no network/Docker/DB required) |
| [`scripts/validate-phase-2-external-evidence-intake.sh`](../../scripts/validate-phase-2-external-evidence-intake.sh) | Intake validator: checks all four evidence intake templates exist and contain required role sections, B23-E prerequisites, and flag safety language (no network/Docker/DB required) |
| [`scripts/validate-phase-2-external-evidence-completion.sh`](../../scripts/validate-phase-2-external-evidence-completion.sh) | **Completion gate validator** (2026-06-10 completion-gates batch): reads the `## Completion Status` table in each intake template; distinguishes TEMPLATE_READY / PARTIAL / COMPLETE / NO_GO; reports B23-E gate status; fails only on NO-GO or malformed templates (no network/Docker/DB required) |

### Evidence Intake Templates (operator fills these during execution)

| Template | Owner | Gate |
|----------|-------|------|
| [`docs/evidence/intake-dba-ddl-evidence.md`](intake-dba-ddl-evidence.md) | DBA | DA1–DA14; blocks B17 E2E and P5 outbox flag enable |
| [`docs/evidence/intake-ops-xxl-job-evidence.md`](intake-ops-xxl-job-evidence.md) | Ops | OA1–OA6; blocks B23-C E2E and P5 outbox flag enable |
| [`docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md`](intake-engineer-b17-b23c-e2e-evidence.md) | Engineer | EA1–EA10; blocks SE11 and B23-D gate |
| [`docs/evidence/intake-oncall-signoff-evidence.md`](intake-oncall-signoff-evidence.md) | Oncall Lead | OC1–OC5; all five sign-off gates including P4 written approval |

### Readiness Dashboard (2026-06-10 completion-gates batch)

| Document | Purpose |
|----------|---------|
| [`docs/evidence/phase-2-external-readiness-dashboard.md`](phase-2-external-readiness-dashboard.md) | Per-role completion state, B23-E gate status, remaining external blockers, and validator commands |
