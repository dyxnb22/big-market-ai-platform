# Phase 2.2-B17 Staging Cutover Evidence Template

**Purpose:** Operator-filled record of the live staging E2E cutover window.
Preserve this file as the production promotion gate artefact.
Do NOT enable `remote-quota-decrement=true` in production until every section is filled in and every gate check shows PASS.

**Script:** `./scripts/execute-account-service-staging-b17.sh`
**Tag:** `phase-2.2-b17-staging-cutover-execution-package`
**Cutover window date:** ___________________________________
**Operator(s):** ___________________________________

---

## Operator Quick Start — Ordered Staging Path

Execute these steps in order. Fill in each Phase section below as you go.

```
Step 1 — Apply ledger DDL (manual, both shards):
    mysql -h <staging-host> -u <admin> -p big_market_01 < docs/sql/proposed-quota-decrement-ledger.sql
    mysql -h <staging-host> -u <admin> -p big_market_02 < docs/sql/proposed-quota-decrement-ledger.sql

Step 2 — Apply outbox DDL (manual, both shards):
    mysql -h <staging-host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql
    mysql -h <staging-host> -u <admin> -p big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql

Step 3 — Register XXL-Job handlers in staging admin UI (manual):
    DispatchCreditAwardTaskJob_DB1   cron: 0/30 * * * * ?
    DispatchCreditAwardTaskJob_DB2   cron: 0/30 * * * * ?

Step 4 — Verify all DDL landed (CONNECT_REMOTE gate — read-only, never writes):
    CONNECT_REMOTE=true MYSQL_HOST=<host> MYSQL_PORT=3306 MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
        ./scripts/execute-account-service-staging-b17.sh
    Must show 0 FAIL. Hard gate: do NOT enable flag if any check fails.

Step 5 — Enable flag on staging market-service ONLY:
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true
    (redeploy big-market-market-service on staging; confirm via env grep)

Step 6 — Armory before draw (required — draw returns code=0001 without it):
    GET /api/v1/raffle/activity/armory?activityId=<activityId>
    Expect HTTP 200, code=0000, data=true

Step 7 — E2E draw test (fill Phases F-H below):
    a) POST /api/v1/raffle/activity/draw  -> verify ledger=applied, quota decremented by 1
    b) Duplicate draw (same outBusinessNo) -> verify quota unchanged, ledger row count = 1
    c) Trigger rollback -> verify ledger=rolled_back, quota restored
    d) Duplicate rollback -> verify 0 rows affected, quota unchanged
    e) Manual XXL-Job trigger -> verify outbox pending->dispatched, user_credit_order count = 1
    f) Second XXL-Job trigger -> verify user_credit_order count still = 1 (no double credit)

Step 8 — Restore flag (Phase I):
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
    (redeploy; confirm health: curl -sf http://<host>:8083/actuator/health | jq .status)

Step 9 — Post-window verification (Phase J):
    B17_POST_CHECK=true MYSQL_HOST=<host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
        ./scripts/execute-account-service-staging-b17.sh
    Must show 0 FAIL.

Step 10 — Fill Phase K go/no-go decision and sign off.
```

> **Production flag must remain `false` throughout this entire window.**
> Do NOT merge B18 production promotion until Phase K shows GO and this file is fully signed off.

---

## Phase A — Ledger DDL Apply

| | big_market_01 | big_market_02 |
|---|---|---|
| Applied by | ___________________ | ___________________ |
| Timestamp | ___________________ | ___________________ |
| Command | `mysql -h <host> -u <admin> -p big_market_01 < docs/sql/proposed-quota-decrement-ledger.sql` | _(same DDL, target big_market_02)_ |
| Result | SUCCESS / ERROR | SUCCESS / ERROR |

---

## Phase B — Credit-Award Outbox DDL Apply

| | big_market_01 | big_market_02 |
|---|---|---|
| Applied by | ___________________ | ___________________ |
| Timestamp | ___________________ | ___________________ |
| Command | `mysql -h <host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql` | _(same DDL, target big_market_02)_ |
| Result | SUCCESS / ERROR | SUCCESS / ERROR |

---

## Phase C — Remote DB Verification (CONNECT_REMOTE)

Command run:
```bash
CONNECT_REMOTE=true MYSQL_HOST=<host> MYSQL_PORT=3306 MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
    ./scripts/execute-account-service-staging-b17.sh
```

| Check | Result |
|-------|--------|
| B16 CONNECT_REMOTE pass count | ___________________ |
| B16 CONNECT_REMOTE fail count | ___________________ (must be 0) |
| Log/screenshot path | ___________________ |

**Phase C gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase E if Phase C gate is FAIL.

Checks verified:
- [ ] `raffle_quota_decrement_ledger_{000..003}` in `big_market_01` — all 4 tables present
- [ ] `raffle_quota_decrement_ledger_{000..003}` in `big_market_02` — all 4 tables present
- [ ] `UNIQUE KEY uq_user_activity_biz` on all 8 ledger shards
- [ ] `credit_award_task_{000..003}` in `big_market_01` — all 4 tables present
- [ ] `credit_award_task_{000..003}` in `big_market_02` — all 4 tables present
- [ ] `UNIQUE KEY uq_award_order_id` on all 8 outbox shards
- [ ] `UNIQUE KEY uq_out_business_no` on all `user_credit_order_*` shards
- [ ] `UNIQUE KEY uq_biz_id` on all `user_behavior_rebate_order_*` shards

---

## Phase D — XXL-Job Handler Registration

| Handler | Handler ID | Cron | Registered by | Screenshot path |
|---------|-----------|------|--------------|----------------|
| `DispatchCreditAwardTaskJob_DB1` | ___________________ | `0/30 * * * * ?` | ___________________ | ___________________ |
| `DispatchCreditAwardTaskJob_DB2` | ___________________ | `0/30 * * * * ?` | ___________________ | ___________________ |

---

## Phase E — Flag Enable Window

| | Value |
|---|---|
| flag=true start timestamp | ___________________ |
| Env key set | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true` |
| Deployed to | big-market-market-service (staging only) |
| Confirmed via | `docker exec big-market-market-service env \| grep REMOTE_QUOTA_DECREMENT` |
| Confirmation output | ___________________ |

> Important: production flag must remain false throughout this window.

---

## Phase F — Partake Flow E2E

### Test Values

| | Value |
|---|---|
| userId | ___________________ |
| activityId | ___________________ |
| outBusinessNo | ___________________ |

### HTTP Request

```
POST /api/v1/raffle/activity/draw
{"activityId": <id>, "userId": "<user>"}
```

| | Value |
|---|---|
| Response code | ___________________ (expected: 200) |
| Response body (awardId) | ___________________ |

### Ledger State

| | Value |
|---|---|
| Ledger row BEFORE draw | ___________________ (expected: no row) |
| Ledger row AFTER draw (status) | ___________________ (expected: applied) |

Query:
```sql
SELECT * FROM raffle_quota_decrement_ledger_000
WHERE user_id='<user>' AND activity_id=<id>;
```

### Quota State

| | Value |
|---|---|
| total_count_surplus BEFORE draw | ___________________ |
| total_count_surplus AFTER draw | ___________________ (expected: before - 1) |

Query:
```sql
SELECT total_count_surplus FROM raffle_activity_account
WHERE user_id='<user>' AND activity_id=<id>;
```

### Idempotency — Duplicate Draw (same outBusinessNo)

| | Value |
|---|---|
| Re-submitted | YES / NO |
| Quota after duplicate draw | ___________________ (must equal post-draw value) |
| Ledger row count after duplicate | ___________________ (must be 1) |

---

## Phase G — Rollback Path

### Rollback Method

- [ ] `savePartakeOrderOnly` intentional failure
- [ ] Manual UPDATE rollback trigger

### Ledger State After Rollback

```sql
SELECT status FROM raffle_quota_decrement_ledger_000
WHERE user_id='<user>' AND out_business_no='<biz-no>';
```

| | Value |
|---|---|
| Ledger status after rollback | ___________________ (expected: rolled_back) |
| Quota after rollback | ___________________ (expected: restored to pre-draw value) |

### Idempotency — Duplicate Rollback

| | Value |
|---|---|
| Second rollback rows affected | ___________________ (expected: 0) |
| Quota after duplicate rollback | ___________________ (expected: unchanged) |

---

## Phase H — Outbox Dispatch

### Test Outbox Row

| | Value |
|---|---|
| DB/Table | ___________________ |
| award_order_id | ___________________ |
| State at insert | pending |

### First Dispatch — DispatchCreditAwardTaskJob_DB1

| | Value |
|---|---|
| Trigger timestamp | ___________________ |
| Via | XXL-Job admin UI manual trigger |
| Outbox row state after dispatch | ___________________ (expected: dispatched) |

```sql
SELECT COUNT(*) FROM user_credit_order_000
WHERE out_business_no='<award_order_id>';
```

| | Value |
|---|---|
| user_credit_order count | ___________________ (expected: 1) |

### Idempotency — Second Dispatch

| | Value |
|---|---|
| Second trigger timestamp | ___________________ |
| user_credit_order count after second dispatch | ___________________ (expected: still 1, no double credit) |

---

## Phase I — Flag Restore

| | Value |
|---|---|
| flag=false restore timestamp | ___________________ |
| Env key restored | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false` |
| Health check command | `curl -sf http://<host>:8083/actuator/health \| jq .status` |
| Health result | ___________________ (expected: "UP") |

---

## Phase J — Post-Window Verification

Command:
```bash
B17_POST_CHECK=true MYSQL_HOST=<host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
    ./scripts/execute-account-service-staging-b17.sh
```

| Check | Result |
|-------|--------|
| Post-check PASS count | ___________________ |
| Post-check FAIL count | ___________________ (must be 0) |
| Log/screenshot path | ___________________ |

**Phase J gate:** PASS / FAIL

Post-window checklist:
- [ ] Phase A & B: DDL apply timestamps recorded
- [ ] Phase C: CONNECT_REMOTE all checks PASS (0 FAIL)
- [ ] Phase D: XXL-Job handler IDs recorded for both DB1 and DB2
- [ ] Phase E: flag=true start timestamp recorded
- [ ] Phase F: HTTP 200, ledger status=applied, quota decremented by exactly 1
- [ ] Phase F idempotency: quota unchanged, ledger row count = 1 after duplicate draw
- [ ] Phase G: ledger status=rolled_back, quota restored to pre-draw value
- [ ] Phase G idempotency: duplicate rollback rows_affected = 0, quota unchanged
- [ ] Phase H: outbox pending→dispatched, user_credit_order count = 1
- [ ] Phase H idempotency: user_credit_order count still = 1 after second dispatch (no double credit)
- [ ] Phase I: flag restored to false, market-service health = "UP"
- [ ] No quota leak observed at any step
- [ ] No double-credit observed at any step
- [ ] Evidence file fully filled out (this document)

---

## Phase K — Production Go/No-Go Decision

| Check | Result |
|-------|--------|
| All Phase F E2E checks passed | YES / NO |
| All Phase G rollback checks passed | YES / NO |
| All Phase H outbox checks passed | YES / NO |
| Flag restored to false (Phase I) | YES / NO |
| Any quota leak observed | YES / NO **(NO required for GO)** |
| Any double-credit observed | YES / NO **(NO required for GO)** |
| Any rollback failure observed | YES / NO **(NO required for GO)** |
| Evidence file complete | YES / NO **(YES required for GO)** |

**Production go decision:** **GO / NO-GO**
**Decision by:** ___________________________________
**Decision timestamp:** ___________________________________
**If NO-GO, reason:** ___________________________________

---

## Production Promotion Criteria

> Do NOT enable `remote-quota-decrement=true` in production until ALL of the following are confirmed:

1. This evidence file is fully filled out and preserved.
2. Phase C gate: all CONNECT_REMOTE checks PASS (0 FAIL).
3. Phase F idempotency: duplicate draw does not change quota; ledger row count = 1.
4. Phase G rollback: quota fully restored; duplicate rollback = 0 rows affected.
5. Phase H outbox: no double credit; `user_credit_order` count = 1 after two dispatches.
6. Phase I: flag successfully restored to false; market-service health = "UP".
7. No quota leak at any step.
8. No double-credit at any step.
9. Phase K go decision recorded with approver name and timestamp.

**Hard no-go conditions** (any one blocks production promotion):
- Any FAIL in B17 pre-flight or CONNECT_REMOTE checks
- Quota changed on duplicate draw (idempotency violation)
- `user_credit_order` count > 1 for same `out_business_no` (double credit)
- Quota not restored after rollback (data integrity failure)
- Evidence file incomplete or unsigned

---

## Rollback Plan

**Instant rollback (staging or production):**
```bash
# Set env and redeploy market-service
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
docker compose up -d --no-deps --build big-market-market-service
```
The `saveCreatePartakeOrderAggregate` path takes effect immediately — no data loss.

**Quota leak repair (if rollback did not fire in time):**
```sql
UPDATE raffle_quota_decrement_ledger_000
  SET status='rolled_back'
  WHERE user_id='<user>' AND out_business_no='<biz-no>';

UPDATE raffle_activity_account
  SET total_count_surplus = total_count_surplus + 1
  WHERE user_id='<user>' AND activity_id=<id>;
```

**Short production canary window (post-go-decision):**
- Enable `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true` on one production market-service instance for ~15 minutes.
- Monitor: quota leak queries, `user_credit_order` double-count, error rate, latency P99.
- Expand to full production only if canary is clean.
- Rollback at any anomaly: restore flag=false and redeploy.

---

## Remaining Blockers (at time of template generation)

The following blockers were unresolved when this template was generated.
Update this section when each blocker is completed.

1. **Staging ledger DDL** — apply `docs/sql/proposed-quota-decrement-ledger.sql` to `big_market_01` and `big_market_02`. Status: **PENDING**
2. **Staging credit-award outbox DDL** — apply `docs/sql/proposed-credit-award-task-outbox.sql` to `big_market_01` and `big_market_02`. Status: **PENDING**
3. **XXL-Job handlers** — register `DispatchCreditAwardTaskJob_DB1` and `DispatchCreditAwardTaskJob_DB2` in staging XXL-Job admin UI. Status: **PENDING**
