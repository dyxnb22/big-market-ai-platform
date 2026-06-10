# Phase 2 DBA Checklist

**Date:** 2026-06-10
**Scope:** Phase 2.2-B17 staging DDL + Phase 2.3-D production DDL
**Status:** TEMPLATE — fill in as each action is completed

> **This checklist is for DBA use only.**
> Do not enable any application flags. Flag enabling is done by the Engineer
> after DBA sign-off is recorded here.
>
> **Do not rollback DDL (DROP TABLE) without written approval from the incident lead.**

---

## SQL Files to Apply

| File | Applies To | Tables Created |
|------|-----------|---------------|
| `docs/sql/proposed-quota-decrement-ledger.sql` | B17 staging only | `raffle_quota_decrement_ledger_000–003` |
| `docs/sql/proposed-credit-award-task-outbox.sql` | B17 staging + B23-D production | `credit_award_task_000–003` |

Both files use `CREATE TABLE IF NOT EXISTS` — safe to re-run; idempotent.

---

## Phase 1: Staging DDL — Phase 2.2-B17

### 1a. Apply Ledger DDL to Staging

```bash
# Apply to big_market_01
mysql -h <staging-host> -u <admin> -p big_market_01 \
  < docs/sql/proposed-quota-decrement-ledger.sql

# Apply to big_market_02
mysql -h <staging-host> -u <admin> -p big_market_02 \
  < docs/sql/proposed-quota-decrement-ledger.sql
```

| DB | Applied By | Timestamp | Result |
|----|-----------|-----------|--------|
| `big_market_01` | ___ | ___ | SUCCESS / ERROR |
| `big_market_02` | ___ | ___ | SUCCESS / ERROR |

### 1b. Apply Outbox DDL to Staging

```bash
# Apply to big_market_01
mysql -h <staging-host> -u <admin> -p big_market_01 \
  < docs/sql/proposed-credit-award-task-outbox.sql

# Apply to big_market_02
mysql -h <staging-host> -u <admin> -p big_market_02 \
  < docs/sql/proposed-credit-award-task-outbox.sql
```

| DB | Applied By | Timestamp | Result |
|----|-----------|-----------|--------|
| `big_market_01` | ___ | ___ | SUCCESS / ERROR |
| `big_market_02` | ___ | ___ | SUCCESS / ERROR |

### 1c. Verify Staging Ledger Tables

```sql
-- Run on both big_market_01 and big_market_02
SHOW TABLES LIKE 'raffle_quota_decrement_ledger%';
-- Expected: raffle_quota_decrement_ledger_000, _001, _002, _003 (4 tables per DB)

-- Verify unique key on each shard
SHOW INDEX FROM raffle_quota_decrement_ledger_000;
-- Expected: uq_user_activity_biz on (user_id, activity_id, out_business_no)

SHOW INDEX FROM raffle_quota_decrement_ledger_001;
SHOW INDEX FROM raffle_quota_decrement_ledger_002;
SHOW INDEX FROM raffle_quota_decrement_ledger_003;
```

| Check | big_market_01 | big_market_02 |
|-------|--------------|--------------|
| `raffle_quota_decrement_ledger_000` exists | YES / NO | YES / NO |
| `raffle_quota_decrement_ledger_001` exists | YES / NO | YES / NO |
| `raffle_quota_decrement_ledger_002` exists | YES / NO | YES / NO |
| `raffle_quota_decrement_ledger_003` exists | YES / NO | YES / NO |
| `uq_user_activity_biz` on all 4 shards | YES / NO | YES / NO |

### 1d. Verify Staging Outbox Tables

```sql
-- Run on both big_market_01 and big_market_02
SHOW TABLES LIKE 'credit_award_task%';
-- Expected: credit_award_task_000, _001, _002, _003 (4 tables per DB)

-- Verify unique key on each shard (idempotency constraint)
SHOW INDEX FROM credit_award_task_000;
-- Expected: uq_award_order_id on (user_id, award_order_id)

SHOW INDEX FROM credit_award_task_001;
SHOW INDEX FROM credit_award_task_002;
SHOW INDEX FROM credit_award_task_003;
```

| Check | big_market_01 | big_market_02 |
|-------|--------------|--------------|
| `credit_award_task_000` exists | YES / NO | YES / NO |
| `credit_award_task_001` exists | YES / NO | YES / NO |
| `credit_award_task_002` exists | YES / NO | YES / NO |
| `credit_award_task_003` exists | YES / NO | YES / NO |
| `uq_award_order_id` on all 4 shards | YES / NO | YES / NO |

### 1e. Verify Staging user_credit_order Unique Key

```sql
-- Run on both big_market_01 and big_market_02
-- Verify the idempotency guard that prevents double-credit
SHOW INDEX FROM user_credit_order_000;
-- Expected: uq_out_business_no on (out_business_no)

SHOW INDEX FROM user_credit_order_001;
SHOW INDEX FROM user_credit_order_002;
SHOW INDEX FROM user_credit_order_003;
```

| Check | big_market_01 | big_market_02 |
|-------|--------------|--------------|
| `uq_out_business_no` on `user_credit_order_000` | YES / NO | YES / NO |
| `uq_out_business_no` on `user_credit_order_001` | YES / NO | YES / NO |
| `uq_out_business_no` on `user_credit_order_002` | YES / NO | YES / NO |
| `uq_out_business_no` on `user_credit_order_003` | YES / NO | YES / NO |

### Staging DDL Gate

**All staging DDL checks PASS:** YES / NO

> Hard gate: Do NOT sign off Phase 1 if any check is NO.
> Do NOT allow the Engineer to enable any flag until this gate is YES.

**DBA sign-off (staging):**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## Phase 2: Production DDL — Phase 2.3-D

> Apply production DDL only after B23-C staging evidence (SE1–SE11) is signed off
> and the B23-D production gate (Phase B) is passed.
> Do NOT apply production ledger DDL here — ledger DDL is Phase 2.2 only.
> Only the outbox DDL applies to production in Phase 2.3.

### 2a. Apply Outbox DDL to Production

```bash
# Apply to big_market_01
mysql -h <prod-host> -u <admin> -p big_market_01 \
  < docs/sql/proposed-credit-award-task-outbox.sql

# Apply to big_market_02
mysql -h <prod-host> -u <admin> -p big_market_02 \
  < docs/sql/proposed-credit-award-task-outbox.sql
```

| DB | Applied By | Timestamp | Result |
|----|-----------|-----------|--------|
| `big_market_01` | ___ | ___ | SUCCESS / ERROR |
| `big_market_02` | ___ | ___ | SUCCESS / ERROR |

### 2b. Verify Production Outbox Tables

```sql
-- Run on both big_market_01 and big_market_02
SHOW TABLES LIKE 'credit_award_task%';
-- Expected: credit_award_task_000, _001, _002, _003

SHOW INDEX FROM credit_award_task_000;
-- Expected: uq_award_order_id on (user_id, award_order_id)

SHOW INDEX FROM credit_award_task_001;
SHOW INDEX FROM credit_award_task_002;
SHOW INDEX FROM credit_award_task_003;
```

| Check | big_market_01 | big_market_02 |
|-------|--------------|--------------|
| `credit_award_task_000` exists | YES / NO | YES / NO |
| `credit_award_task_001` exists | YES / NO | YES / NO |
| `credit_award_task_002` exists | YES / NO | YES / NO |
| `credit_award_task_003` exists | YES / NO | YES / NO |
| `uq_award_order_id` on all 4 shards | YES / NO | YES / NO |

### 2c. Verify Production user_credit_order Unique Key

```sql
-- Run on both big_market_01 and big_market_02
-- This key is the last line of defense against double-credit in production
SHOW INDEX FROM user_credit_order_000;
-- Expected: uq_out_business_no on (out_business_no)

SHOW INDEX FROM user_credit_order_001;
SHOW INDEX FROM user_credit_order_002;
SHOW INDEX FROM user_credit_order_003;
```

| Check | big_market_01 | big_market_02 |
|-------|--------------|--------------|
| `uq_out_business_no` on `user_credit_order_000` | YES / NO | YES / NO |
| `uq_out_business_no` on `user_credit_order_001` | YES / NO | YES / NO |
| `uq_out_business_no` on `user_credit_order_002` | YES / NO | YES / NO |
| `uq_out_business_no` on `user_credit_order_003` | YES / NO | YES / NO |

### Production DDL Gate

**All production DDL checks PASS:** YES / NO

> Hard gate: Do NOT sign off Phase 2 if any check is NO.
> Do NOT allow Engineer to enable outbox flag (P5) until this gate is YES.

**DBA sign-off (production):**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## Rollback Guidance

### Rollback Rule

**Do not rollback DDL (DROP TABLE) without written approval from the incident lead.**

If a deployed table needs to be removed:
1. Open an incident ticket immediately.
2. Wait for incident lead written approval before any DROP TABLE.
3. Verify the table is empty (SELECT COUNT(*) = 0) before DROP.
4. Document the incident in the evidence file.

### Safe Rollback Alternatives

For most issues, application-level rollback is safer than DDL rollback:

```bash
# Rollback outbox flag — no DDL change needed
ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
  docker compose up -d --no-deps --force-recreate big-market-message-job-service
# Pending rows remain in credit_award_task with state='pending'.
# Poller will retry them when flag is re-enabled. No data loss.
```

### If Tables Were Incorrectly Applied

1. Stop the affected service immediately.
2. Contact incident lead.
3. Do not run DROP TABLE without approval.
4. Assess whether the table is empty before any structural change.

---

## Evidence Attachment Summary

| # | Evidence | Screenshot / Output Path | Signed By | Timestamp |
|---|----------|--------------------------|-----------|-----------|
| DA1 | Staging big_market_01 ledger DDL apply | ___ | ___ | ___ |
| DA2 | Staging big_market_02 ledger DDL apply | ___ | ___ | ___ |
| DA3 | Staging big_market_01 outbox DDL apply | ___ | ___ | ___ |
| DA4 | Staging big_market_02 outbox DDL apply | ___ | ___ | ___ |
| DA5 | Staging SHOW TABLES: raffle_quota_decrement_ledger% (both DBs) | ___ | ___ | ___ |
| DA6 | Staging SHOW TABLES: credit_award_task% (both DBs) | ___ | ___ | ___ |
| DA7 | Staging SHOW INDEX: uq_award_order_id on all 8 outbox shards | ___ | ___ | ___ |
| DA8 | Staging SHOW INDEX: uq_out_business_no on all user_credit_order shards | ___ | ___ | ___ |
| DA9 | Staging SHOW INDEX: uq_user_activity_biz on all ledger shards | ___ | ___ | ___ |
| DA10 | Production big_market_01 outbox DDL apply | ___ | ___ | ___ |
| DA11 | Production big_market_02 outbox DDL apply | ___ | ___ | ___ |
| DA12 | Production SHOW TABLES: credit_award_task% (both DBs) | ___ | ___ | ___ |
| DA13 | Production SHOW INDEX: uq_award_order_id on all 8 production outbox shards | ___ | ___ | ___ |
| DA14 | Production SHOW INDEX: uq_out_business_no on all user_credit_order shards | ___ | ___ | ___ |
