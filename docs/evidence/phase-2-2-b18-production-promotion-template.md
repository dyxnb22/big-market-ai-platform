# Phase 2.2-B18 Production Promotion Evidence Template

**Purpose:** Operator-filled record of the live production promotion window.
Preserve this file as the final Phase 2.2 sign-off artefact.
Do NOT enable `remote-quota-decrement=true` on all production instances until every section is filled in and every gate check shows PASS.

**Script:** `./scripts/validate-account-service-production-b18.sh`
**Tag:** `phase-2.2-b18-production-promotion-gate`
**Production promotion date:** ___________________________________
**Operator(s):** ___________________________________
**Oncall lead approver:** ___________________________________

---

## Phase A — B17 Staging Evidence Validation

| Check | Result |
|-------|--------|
| B17 evidence file path | ___________________ |
| B18_STAGING_EVIDENCE validation command | `B18_STAGING_EVIDENCE=docs/evidence/b17-staging-evidence-<YYYYMMDD>.md ./scripts/validate-account-service-production-b18.sh` |
| B17 evidence consistency command | `./scripts/validate-b17-evidence-consistency.sh docs/evidence/b17-staging-evidence-<YYYYMMDD>.md` |
| Validation result | PASS / FAIL |
| B17 pre-flight count matches script dry-run | YES / NO |
| All B17 Phases A–K present | YES / NO |
| All required fields non-empty | YES / NO |
| Phase K staging GO decision recorded | YES / NO |
| Phase K decision by | ___________________ |
| Phase K decision timestamp | ___________________ |

**Phase A gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase B if Phase A gate is FAIL.
> The B17 staging evidence file must be fully filled out with a GO decision before any production action.

---

## Phase B — Production DDL Apply

| | big_market_01 | big_market_02 |
|---|---|---|
| Ledger DDL applied by | ___________________ | ___________________ |
| Ledger DDL timestamp | ___________________ | ___________________ |
| Ledger DDL result | SUCCESS / ERROR | SUCCESS / ERROR |
| Outbox DDL applied by | ___________________ | ___________________ |
| Outbox DDL timestamp | ___________________ | ___________________ |
| Outbox DDL result | SUCCESS / ERROR | SUCCESS / ERROR |

Commands applied:
```bash
mysql -h <prod-host> -u <admin> -p big_market_01 < docs/sql/proposed-quota-decrement-ledger.sql
mysql -h <prod-host> -u <admin> -p big_market_02 < docs/sql/proposed-quota-decrement-ledger.sql
mysql -h <prod-host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql
mysql -h <prod-host> -u <admin> -p big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql
```

---

## Phase C — Production DB Verification (CONNECT_REMOTE, read-only)

Command run:
```bash
CONNECT_REMOTE=true MYSQL_HOST=<prod-host> MYSQL_PORT=3306 MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
    ./scripts/validate-account-service-production-b18.sh
```

| Check | Result |
|-------|--------|
| B16 CONNECT_REMOTE PASS count | ___________________ |
| B16 CONNECT_REMOTE FAIL count | ___________________ (must be 0) |
| Log/screenshot path | ___________________ |

**Phase C gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase D if Phase C gate is FAIL.

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

## Phase D — Canary Flag Enable Window

| | Value |
|---|---|
| Oncall lead approver | ___________________ |
| Approval timestamp | ___________________ |
| Canary instance identifier | ___________________ |
| flag=true start timestamp | ___________________ |
| Env key set | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true` |
| Deployed to | big-market-market-service (canary instance only) |
| Confirmed via | `docker exec big-market-market-service env \| grep REMOTE_QUOTA_DECREMENT` |
| Confirmation output | ___________________ |

> IMPORTANT: production flag=true on canary instance only.
> All other production instances remain flag=false until Phase I GO decision.

---

## Phase E — Canary Partake Flow

### Test Values

| | Value |
|---|---|
| canary userId | ___________________ |
| activityId | ___________________ |
| outBusinessNo | ___________________ |

### HTTP Request

```
POST /api/v1/raffle/activity/draw
{"activityId": <id>, "userId": "<canary-user>"}
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
WHERE user_id='<canary-user>' AND activity_id=<id>;
```

### Quota State

| | Value |
|---|---|
| total_count_surplus BEFORE draw | ___________________ |
| total_count_surplus AFTER draw | ___________________ (expected: before - 1) |

Query:
```sql
SELECT total_count_surplus FROM raffle_activity_account
WHERE user_id='<canary-user>' AND activity_id=<id>;
```

### Idempotency — Duplicate Draw (same outBusinessNo)

| | Value |
|---|---|
| Re-submitted | YES / NO |
| Quota after duplicate draw | ___________________ (must equal post-draw value) |
| Ledger row count after duplicate | ___________________ (must be 1) |

**Phase E gate:** PASS / FAIL

> Hard gate: any FAIL → immediately execute rollback (flag=false) and record in Phase I.

---

## Phase F — Rollback Path Verification

### Rollback Method

- [ ] `savePartakeOrderOnly` intentional failure
- [ ] Controlled test trigger

### Ledger State After Rollback

```sql
SELECT status FROM raffle_quota_decrement_ledger_000
WHERE user_id='<canary-user>' AND out_business_no='<biz-no>';
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

**Phase F gate:** PASS / FAIL

---

## Phase G — Credit-Award Outbox Dispatch/Idempotency

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

**Phase G gate:** PASS / FAIL

---

## Phase H — Monitoring/Log Checks (Canary Window)

| Metric | Observed Value | Threshold | Status |
|--------|---------------|-----------|--------|
| Draw endpoint error rate | ___________________ | 0% | PASS / FAIL |
| Quota leak observations | ___________________ | 0 | PASS / FAIL |
| `user_credit_order` double-count | ___________________ | 0 | PASS / FAIL |
| Latency P99 `/draw` | ___________________ | baseline ±20% | PASS / FAIL |
| account-service heap/GC anomaly | ___________________ | none | PASS / FAIL |
| market-service heap/GC anomaly | ___________________ | none | PASS / FAIL |
| Canary window duration | ___________________ | ≤15 min | — |
| Log/screenshot path | ___________________ | — | — |

**Phase H gate:** PASS / FAIL

> Hard gate: any anomaly → flag=false rollback immediately and record in Phase I.

---

## Phase I — Production Rollout or Flag=false Restore

| | Value |
|---|---|
| Decision | **GO (full rollout) / NO-GO (flag=false)** |
| Decision timestamp | ___________________ |
| Decision by | ___________________ |

**If GO — Full Rollout:**

| | Value |
|---|---|
| Full rollout start timestamp | ___________________ |
| Full rollout complete timestamp | ___________________ |
| All instances confirmed flag=true | YES / NO |
| Post-rollout draw error rate | ___________________ |
| Post-rollout quota leak count | ___________________ |

**If NO-GO — Rollback:**

| | Value |
|---|---|
| Rollback command run | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false` |
| flag=false restore timestamp | ___________________ |
| Health check result | ___________________ (expected: "UP") |
| NO-GO reason | ___________________ |
| Next action | ___________________ |

---

## Phase J — Final Phase 2.2 Sign-Off

| Check | Result |
|-------|--------|
| Phase A: B17 staging evidence validated | YES / NO |
| Phase B: Production DDL applied (both DBs, both tables) | YES / NO |
| Phase C: CONNECT_REMOTE all checks PASS (0 FAIL) | YES / NO |
| Phase D: Canary instance flag=true confirmed, approver recorded | YES / NO |
| Phase E: Partake E2E: HTTP 200, ledger applied, quota decremented | YES / NO |
| Phase E idempotency: quota unchanged, ledger count = 1 | YES / NO |
| Phase F: Rollback: ledger rolled_back, quota restored | YES / NO |
| Phase F idempotency: duplicate rollback = 0 rows, quota unchanged | YES / NO |
| Phase G: Outbox dispatched, user_credit_order count = 1 | YES / NO |
| Phase G idempotency: second dispatch count still = 1 | YES / NO |
| Phase H: No quota leak, no double-credit, latency within threshold | YES / NO |
| Phase I: Full GO rollout complete OR clean NO-GO restore | YES / NO |
| No quota leak observed at any step | YES / NO **(YES required for GO)** |
| No double-credit observed at any step | YES / NO **(YES required for GO)** |
| B18 evidence file complete | YES / NO **(YES required for sign-off)** |

**Final Phase 2.2 decision:** **GO / NO-GO**
**Sign-off by:** ___________________________________
**Role:** ___________________________________
**Timestamp:** ___________________________________
**If NO-GO, reason and next batch:** ___________________________________

---

## Production No-Go Criteria

> Do NOT proceed (or immediately rollback) if ANY of the following:

1. B17 staging evidence validation FAIL or evidence file incomplete.
2. Any FAIL in B18 static checks (P1–P12).
3. Any FAIL in Phase C CONNECT_REMOTE checks.
4. Any FAIL in Phase E (partake E2E or idempotency violation).
5. Any FAIL in Phase F (rollback failure or quota not restored).
6. Any FAIL in Phase G (outbox dispatch failure or double credit).
7. Quota changed on duplicate draw (`user_credit_order` count > 1 for same `out_business_no`).
8. Any quota leak detected in Phase H monitoring.
9. Phase H anomaly: error rate spike, GC pressure, latency regression beyond threshold.
10. B18 evidence file incomplete or unsigned.

**Hard no-go conditions** (any one triggers immediate flag=false rollback):
- Quota changed on duplicate draw (idempotency violation)
- `user_credit_order` count > 1 for same `out_business_no` (double credit)
- Quota not restored after rollback (data integrity failure)
- Any error rate > 0% on draw endpoint during canary window

---

## Rollback Plan

**Instant rollback (any phase — no data loss):**
```bash
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
docker compose up -d --no-deps --build big-market-market-service
```
The `saveCreatePartakeOrderAggregate` path takes effect immediately.

**Quota leak repair (if automatic rollback did not fire in time):**
```sql
UPDATE raffle_quota_decrement_ledger_000
  SET status='rolled_back'
  WHERE user_id='<user>' AND out_business_no='<biz-no>';

UPDATE raffle_activity_account
  SET total_count_surplus = total_count_surplus + 1
  WHERE user_id='<user>' AND activity_id=<id>;
```

**Health verification after rollback:**
```bash
curl -sf http://<prod-host>:8083/actuator/health | jq .status
# Expected: "UP"
```

---

## Remaining Blockers (at time of template generation)

The following blockers were unresolved when this template was generated.
Update this section when each blocker is completed.

1. **Staging ledger DDL** — apply `docs/sql/proposed-quota-decrement-ledger.sql` to staging `big_market_01` and `big_market_02`. Status: **PENDING**
2. **Staging credit-award outbox DDL** — apply `docs/sql/proposed-credit-award-task-outbox.sql` to staging `big_market_01` and `big_market_02`. Status: **PENDING**
3. **XXL-Job handlers on staging** — register `DispatchCreditAwardTaskJob_DB1` and `DispatchCreditAwardTaskJob_DB2` in staging XXL-Job admin UI. Status: **PENDING**
4. **B17 staging cutover** — complete the full staging E2E window (Phases A–K) and record a GO decision. Status: **PENDING**
5. **Production DDL** — apply ledger + outbox DDL to production `big_market_01` and `big_market_02` (Phase B). Status: **PENDING** (after B17 GO)
6. **Phase 2.2 sign-off** — complete Phases A–J above and record final decision. Status: **PENDING**
