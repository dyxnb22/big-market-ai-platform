# B17 Staging Cutover Evidence — 2026-06-10

**Script:** `./scripts/execute-account-service-staging-b17.sh`
**Run at:** 2026-06-10 17:02:41 HKT
**Environment:** staging

---

## Pre-Flight Verification (automated — 2026-06-10 ~17:03 HKT)

### Repo State (validation run)
| Item | Value |
|------|-------|
| HEAD commit | `89187e4` feat: add B17 evidence file safety guard and armory step |
| Tag at HEAD | `phase-2.2-b17-evidence-file-safety-guard` |
| Working tree | CLEAN |
| git diff --check | PASS (no whitespace errors) |

### Evidence Preservation
| Item | Value |
|------|-------|
| Evidence file commit | `c26f635` docs: add B17 staging evidence file for 2026-06-10 |
| Evidence tag | `phase-2.2-b17-staging-evidence-20260610` |
| Count policy | B17 Pre-Flight Gate counts only `./scripts/execute-account-service-staging-b17.sh` dry-run checks; local evidence-file materialization is recorded separately. |

### B17 Pre-Flight Gate: 6/6 PASS
| Check | Result |
|-------|--------|
| P1: B16 gate script exists and is executable | PASS |
| P2: B16 static gate 18/18 | PASS |
| P3: `proposed-quota-decrement-ledger.sql` exists | PASS |
| P4: `proposed-credit-award-task-outbox.sql` exists | PASS |
| P5: Evidence template exists | PASS |
| P6: `remote-quota-decrement=false` in all configs | PASS |

### Evidence File Materialization
| Check | Result |
|-------|--------|
| Dated evidence file exists at `docs/evidence/b17-staging-evidence-20260610.md` | PASS |
| Blank template was not modified for this evidence capture | PASS |

### B17 Evidence Consistency: PASS
`./scripts/validate-b17-evidence-consistency.sh docs/evidence/b17-staging-evidence-20260610.md` confirms this file's B17 pre-flight count matches the current script dry-run summary (`6/6 PASS`, `0 FAIL`).

### B20 Hardening Gate: 11/11 PASS
| Check | Result |
|-------|--------|
| S1: DynamicTableNamePlugin includes raffle_quota_decrement_ledger | PASS |
| S2: docker-compose.yml passes ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED | PASS |
| S3: docker-compose.yml default is false (production gate preserved) | PASS |
| S4: IRaffleQuotaDecrementLedgerDao.insert has @DBRouter | PASS |
| S5: IRaffleQuotaDecrementLedgerDao.queryByKey has @DBRouter | PASS |
| S6: IRaffleQuotaDecrementLedgerDao.updateStatusToRolledBack has @DBRouter | PASS |
| S7: IRaffleQuotaDecrementLedgerDao has @DBRouterStrategy(splitTable=true) | PASS |
| S8: CreditRepository routes before updateTaskSendMessageCompleted | PASS |
| S9: CreditRepository routes before updateTaskSendMessageFail | PASS |
| S10: LocalActivityAccountPort @ConditionalOnProperty havingValue=false | PASS |
| S11: B16 gate script exists (full B14-B16 chain inherited) | PASS |

### Manual Blocker Status (as of 2026-06-10)
| Blocker | Status | Notes |
|---------|--------|-------|
| B1: Ledger DDL (big_market_01 + 02) | **PENDING** | Staging DB credentials not in environment |
| B2: Credit-award outbox DDL (big_market_01 + 02) | **PENDING** | Staging DB credentials not in environment |
| B3: XXL-Job handler registration | **PENDING** | XXL-Job UI access not available in this session |

### Staging Credentials
No staging `MYSQL_HOST`, `MYSQL_USER`, or `MYSQL_PASS` values were available in this local session. Phase C gate remains **PENDING** and cannot be executed automatically here. See exact read-only verification command in Phase C below.

---

## Phase A — Ledger DDL Apply  ⚠ PENDING

> **Blocker:** Staging DB admin credentials required. Run these commands only inside the approved staging maintenance window.

```bash
# big_market_01
mysql -h <staging-host> -u <admin> -p big_market_01 \
    < docs/sql/proposed-quota-decrement-ledger.sql

# big_market_02
mysql -h <staging-host> -u <admin> -p big_market_02 \
    < docs/sql/proposed-quota-decrement-ledger.sql
```

| | big_market_01 | big_market_02 |
|---|---|---|
| Applied by | ___________________ | ___________________ |
| Timestamp | ___________________ | ___________________ |
| Status | PENDING | PENDING |

---

## Phase B — Credit-Award Outbox DDL Apply  ⚠ PENDING

> **Blocker:** Staging DB admin credentials required. Run these commands only inside the approved staging maintenance window.

```bash
# big_market_01
mysql -h <staging-host> -u <admin> -p big_market_01 \
    < docs/sql/proposed-credit-award-task-outbox.sql

# big_market_02
mysql -h <staging-host> -u <admin> -p big_market_02 \
    < docs/sql/proposed-credit-award-task-outbox.sql
```

| | big_market_01 | big_market_02 |
|---|---|---|
| Applied by | ___________________ | ___________________ |
| Timestamp | ___________________ | ___________________ |
| Status | PENDING | PENDING |

---

## Phase C — Remote DB Verification (CONNECT_REMOTE)  ⚠ PENDING

> Run after Phases A and B are complete. Requires read-only staging DB credentials.

```bash
CONNECT_REMOTE=true \
  MYSQL_HOST=<host> \
  MYSQL_PORT=3306 \
  MYSQL_USER=<ro-user> \
  MYSQL_PASS=<pass> \
    ./scripts/execute-account-service-staging-b17.sh
```

Result (PASS/FAIL + check count): PENDING
Log/screenshot path: ___________________________________
Phase C gate: PENDING

---

## Phase D — XXL-Job Handler Registration  ⚠ PENDING

> **Blocker:** XXL-Job staging admin UI access required.
> Log into staging XXL-Job admin, create two job handlers with the exact values below.

| Handler name | Cron expression | AppName | Description |
|-------------|----------------|---------|-------------|
| `DispatchCreditAwardTaskJob_DB1` | `0/30 * * * * ?` | big-market-job | Dispatch credit award outbox — shard DB1 |
| `DispatchCreditAwardTaskJob_DB2` | `0/30 * * * * ?` | big-market-job | Dispatch credit award outbox — shard DB2 |

| Handler | Handler ID | Cron | Registered by | Screenshot path |
|---------|-----------|------|--------------|----------------|
| DispatchCreditAwardTaskJob_DB1 | PENDING | `0/30 * * * * ?` | ___________________ | ___________________ |
| DispatchCreditAwardTaskJob_DB2 | PENDING | `0/30 * * * * ?` | ___________________ | ___________________ |

---

## Phase E — Flag Enable Window

| | Value |
|---|---|
| flag=true start timestamp | ___________________ |
| Env key | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true` |
| Deployed to | big-market-market-service (staging only) |
| Confirmed via | `docker exec big-market-market-service env | grep REMOTE_QUOTA_DECREMENT` |
| Confirmation output | ___________________ |

---

## Phase F — Partake Flow E2E

**Test values:**

| | Value |
|---|---|
| userId | ___________________ |
| activityId | ___________________ |
| outBusinessNo | ___________________ |

**Step F.0 — Armory (REQUIRED before draw):**

> ⚠ The `/api/v1/raffle/activity/draw` endpoint returns `code=0001` if the raffle strategy
> has not been assembled. Armory MUST succeed before calling draw. Do NOT skip this step.

```bash
curl -s "http://<staging-host>:8091/api/v1/raffle/activity/armory?activityId=<activityId>"
# Expected: HTTP 200, body contains {"code":"0000","data":true}
```

| | Value |
|---|---|
| HTTP status | ___________________ (expected: 200) |
| code field | ___________________ (expected: 0000) |
| data field | ___________________ (expected: true) |
| Armory gate | PASS / FAIL — proceed to draw ONLY if PASS |

**HTTP request:**
```
POST /api/v1/raffle/activity/draw
{"activityId": <id>, "userId": "<user>"}
Response code: ___________________
Response body (awardId): ___________________
```

**Ledger row BEFORE draw** (expected: no row):
```sql
SELECT * FROM raffle_quota_decrement_ledger_000
WHERE user_id='<user>' AND activity_id=<id>;
```
Result: ___________________________________

**Ledger row AFTER draw** (expected: status=applied):
Result: ___________________________________

**Quota BEFORE draw** (total_count_surplus):
```sql
SELECT total_count_surplus FROM raffle_activity_account
WHERE user_id='<user>' AND activity_id=<id>;
```
Value: ___________________________________

**Quota AFTER draw** (expected: decremented by 1):
Value: ___________________________________

**Idempotency — duplicate draw (same outBusinessNo):**

| | Value |
|---|---|
| Re-submitted | YES / NO |
| Quota after duplicate | ___________________ (must equal post-draw value) |
| Ledger row count | ___________________ (must be 1) |

---

## Phase G — Rollback Path

**Rollback method:**
- [ ] savePartakeOrderOnly intentional failure
- [ ] Manual UPDATE rollback trigger

**Ledger row status after rollback** (expected: rolled_back):
```sql
SELECT status FROM raffle_quota_decrement_ledger_000
WHERE user_id='<user>' AND out_business_no='<biz-no>';
```
Status: ___________________________________

**Quota after rollback** (expected: restored to pre-draw value):
Value: ___________________________________

**Idempotency — duplicate rollback:**

| | Value |
|---|---|
| Second rollback rows affected | ___________________ (expected: 0) |
| Quota after duplicate rollback | ___________________ (expected: unchanged) |

---

## Phase H — Outbox Dispatch

**Test outbox row:**

| | Value |
|---|---|
| DB/Table | ___________________ |
| award_order_id | ___________________ |
| State at insert | pending |

**DispatchCreditAwardTaskJob_DB1 triggered:**

| | Value |
|---|---|
| Trigger timestamp | ___________________ |
| Via | XXL-Job admin UI manual trigger |
| Outbox row state after dispatch | ___________________ (expected: dispatched) |

**user_credit_order count** (expected: 1):
```sql
SELECT COUNT(*) FROM user_credit_order_000
WHERE out_business_no='<award_order_id>';
```
Count: ___________________________________

**Idempotency — second dispatch:**

| | Value |
|---|---|
| Triggered at | ___________________ |
| user_credit_order count after | ___________________ (expected: still 1) |

---

## Phase I — Flag Restore

| | Value |
|---|---|
| flag=false restore timestamp | ___________________ |
| Env key restored | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false` |
| Health check command | `curl -sf http://<host>:8083/actuator/health | jq .status` |
| Health result | ___________________ (expected: "UP") |

---

## Phase J — Post-Window Verification

Command:
```bash
B17_POST_CHECK=true MYSQL_HOST=<host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
    ./scripts/execute-account-service-staging-b17.sh
```

Result (PASS/FAIL + check count): ___________________________________
Log/screenshot path: ___________________________________

Post-window checklist:
- [ ] Ledger DDL timestamps recorded (Phases A & B)
- [ ] DB verification (CONNECT_REMOTE) PASS — all tables and UNIQUE KEYs present
- [ ] XXL-Job handler IDs recorded (DB1 + DB2)
- [ ] flag=true start/end timestamps recorded
- [ ] Partake flow E2E: HTTP 200, ledger status=applied, quota decremented by 1
- [ ] Idempotency (duplicate draw): quota unchanged, ledger row count = 1
- [ ] Rollback: ledger status=rolled_back, quota restored
- [ ] Duplicate rollback: 0 rows affected, quota unchanged
- [ ] Outbox dispatch: pending→dispatched, exactly 1 user_credit_order row
- [ ] Second dispatch: user_credit_order count still = 1 (no double credit)
- [ ] flag restored to false, market-service health = "UP"
- [ ] No quota leak observed at any step
- [ ] No double-credit observed at any step
- [ ] Evidence template fully filled out

---

## Phase K — Production Go/No-Go Decision

| Check | Result |
|-------|--------|
| All Phase F E2E checks passed | YES / NO |
| All Phase G rollback checks passed | YES / NO |
| All Phase H outbox checks passed | YES / NO |
| Flag restored to false (Phase I) | YES / NO |
| Any quota leak observed | YES / NO (NO required for GO) |
| Any double-credit observed | YES / NO (NO required for GO) |
| Any rollback failure | YES / NO (NO required for GO) |

**Production go decision:** GO / NO-GO
**Decision by:** ___________________________________
**Decision timestamp:** ___________________________________
**If NO-GO, reason:** ___________________________________

---

## Production Promotion Criteria

Do NOT enable `remote-quota-decrement=true` in production until ALL of the following:

1. Complete staging evidence file preserved (this document, fully filled out).
2. All CONNECT_REMOTE checks PASS (Phase C gate green).
3. All Phase F idempotency checks passed (duplicate draw = no quota change, ledger count = 1).
4. All Phase G rollback checks passed (quota restored, duplicate rollback = 0 rows).
5. All Phase H outbox checks passed (no double credit, user_credit_order count = 1).
6. Phase I: flag successfully restored to false, health = "UP".
7. No quota leak at any step.
8. No double-credit at any step.
9. Go decision recorded with approver name and timestamp (Phase K).

**Hard no-go conditions** (any one blocks production promotion):
- Any FAIL in B17 pre-flight or CONNECT_REMOTE checks
- Quota changed on duplicate draw
- Double credit (user_credit_order count > 1 for same out_business_no)
- Rollback failure or quota not restored
- Evidence template incomplete or unsigned

---

## Rollback Plan

**Instant rollback:**
```bash
# Set env and redeploy market-service
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
docker compose up -d --no-deps --build big-market-market-service
```
The `saveCreatePartakeOrderAggregate` path takes effect immediately — no data loss.

**Short production canary window (after go decision):**
- Enable `remote-quota-decrement=true` for ~15 minutes on one production market-service instance.
- Monitor: quota leak queries, user_credit_order double-count, error rate, latency P99.
- Expand to full production only if canary is clean.
- Rollback at any anomaly: restore flag=false and redeploy.
