#!/usr/bin/env bash
# execute-account-service-staging-b17.sh — Phase 2.2-B17
#
# Live staging cutover execution package.
# Operator-driven, conservative-by-default.  Covers the ordered cutover plan,
# pre-flight gates, remote DB verification, evidence capture, and post-window
# verification.  Does NOT apply DDL, enable flags, or write to any DB.
#
# Usage:
#   ./scripts/execute-account-service-staging-b17.sh
#       Default dry-run: print operator summary + blocker status.  No DB, no writes.
#
#   B17_PRINT_PLAN=true ./scripts/execute-account-service-staging-b17.sh
#       Print the exact ordered cutover plan (phases A-H with commands) and exit.
#
#   CONNECT_REMOTE=true \
#     MYSQL_HOST=<staging-host> MYSQL_PORT=3306 \
#     MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
#     ./scripts/execute-account-service-staging-b17.sh
#       Read-only staging DB verification (delegates to B16 CONNECT_REMOTE mode).
#       Verifies all ledger/outbox tables and idempotency UNIQUE KEYs.
#       NEVER writes, mutates, or modifies any staging data.
#
#   B17_EVIDENCE_FILE=<path> ./scripts/execute-account-service-staging-b17.sh
#       Write (or append) the B17 evidence template to the specified file.
#       If the file already exists, the template is appended with a run separator.
#       Safe to run multiple times — each run appends a fresh timestamped section.
#
#   B17_POST_CHECK=true \
#     MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
#     ./scripts/execute-account-service-staging-b17.sh
#       Post-window verification: delegates to B16 B16_POST_CHECK mode (read-only).
#       Run this after restoring flag=false (Phase H) to confirm post-window state.
#
# Combined examples (all flags compose freely):
#   B17_PRINT_PLAN=true CONNECT_REMOTE=true MYSQL_HOST=<host> MYSQL_USER=<u> MYSQL_PASS=<p> \
#     B17_EVIDENCE_FILE=docs/evidence/b17-run-$(date +%Y%m%d).md \
#     ./scripts/execute-account-service-staging-b17.sh
#
# Safety constraints:
#   - NEVER applies staging/production DDL automatically
#   - NEVER enables or disables remote-quota-decrement.enabled
#   - CONNECT_REMOTE delegates to B16 read-only mode — strictly SELECT from information_schema
#   - B17_POST_CHECK delegates to B16 post-check mode — no writes
#   - B17_EVIDENCE_FILE writes only the local file at the given path; no staging/prod writes
#   - No production actions in any mode
set -euo pipefail

B17_PRINT_PLAN="${B17_PRINT_PLAN:-false}"
CONNECT_REMOTE="${CONNECT_REMOTE:-false}"
B17_EVIDENCE_FILE="${B17_EVIDENCE_FILE:-}"
B17_POST_CHECK="${B17_POST_CHECK:-false}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"

B16_SCRIPT="scripts/validate-account-service-cutover-b16.sh"

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }
warn() { echo "[WARN] $*"; }

# ---------------------------------------------------------------------------
# Cutover plan
# ---------------------------------------------------------------------------
CUTOVER_PLAN='
=============================================================================
B17 STAGED CUTOVER PLAN — ORDERED EXECUTION
All phases must be executed in order.  Do NOT skip or re-order.
This script is read-only.  Each phase step is executed MANUALLY by the operator.
=============================================================================

Phase A: Apply ledger DDL to staging
-------------------------------------
  Command (run manually — NOT by this script):

    mysql -h <staging-host> -u <admin-user> -p big_market_01 \
        < docs/sql/proposed-quota-decrement-ledger.sql

    mysql -h <staging-host> -u <admin-user> -p big_market_02 \
        < docs/sql/proposed-quota-decrement-ledger.sql

  Record DDL apply timestamps in evidence file.
  Gate: CONNECT_REMOTE=true verification in Phase B must show all ledger tables present.

Phase B: Apply credit-award outbox DDL to staging
---------------------------------------------------
  Command (run manually):

    mysql -h <staging-host> -u <admin-user> -p big_market_01 \
        < docs/sql/proposed-credit-award-task-outbox.sql

    mysql -h <staging-host> -u <admin-user> -p big_market_02 \
        < docs/sql/proposed-credit-award-task-outbox.sql

  Record DDL apply timestamps in evidence file.
  Gate: CONNECT_REMOTE=true verification must show all outbox tables present.

Phase C: Remote DB verification (read-only)
--------------------------------------------
  Command (run this script in CONNECT_REMOTE mode):

    CONNECT_REMOTE=true \
      MYSQL_HOST=<staging-host> MYSQL_PORT=3306 \
      MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
      ./scripts/execute-account-service-staging-b17.sh

  All 4 UNIQUE KEYs across all shards and both DBs must show PASS.
  Record result and check count in evidence file.
  HARD GATE: do NOT proceed past Phase C if any remote check fails.

Phase D: Register XXL-Job handlers in staging admin UI
--------------------------------------------------------
  Handlers to register (manual — NOT by this script):

    DispatchCreditAwardTaskJob_DB1   (cron: 0/30 * * * * ?)
    DispatchCreditAwardTaskJob_DB2   (cron: 0/30 * * * * ?)

  Record handler IDs and registration screenshots in evidence file.

Phase E: Enable flag in staging market-service only
-----------------------------------------------------
  Deploy big-market-market-service on staging with:
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true

  Confirm via:
    docker exec big-market-market-service env | grep REMOTE_QUOTA_DECREMENT
    Expected output: ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true

  Record flag=true start timestamp in evidence file.
  IMPORTANT: flag=true in staging only.  Production flag remains false.

Phase F: Partake flow E2E test
--------------------------------
  POST /api/v1/raffle/activity/draw  {"activityId": <id>, "userId": "<user>"}
  Expected: HTTP 200, awardId present.

  Verify ledger row (must be status=applied after draw):
    SELECT * FROM raffle_quota_decrement_ledger_000
    WHERE user_id='"'"'<user>'"'"' AND activity_id=<id>;

  Verify quota decrement:
    SELECT total_count_surplus FROM raffle_activity_account
    WHERE user_id='"'"'<user>'"'"' AND activity_id=<id>;
    Expected: value = (quota before draw) - 1

  Idempotency (duplicate draw, same outBusinessNo):
    Re-submit same request.
    Expected: quota unchanged, ledger row count = 1.

  Record all values in evidence file.

Phase G: Rollback path test
-----------------------------
  Trigger rollback (savePartakeOrderOnly intentional failure or manual UPDATE).

  Verify ledger row status = rolled_back:
    SELECT status FROM raffle_quota_decrement_ledger_000
    WHERE user_id='"'"'<user>'"'"' AND out_business_no='"'"'<biz-no>'"'"';

  Verify quota restored (must equal pre-draw value):
    SELECT total_count_surplus FROM raffle_activity_account
    WHERE user_id='"'"'<user>'"'"' AND activity_id=<id>;

  Idempotency (duplicate rollback):
    Trigger rollback again — rows_affected must be 0; quota must be unchanged.

  Record all values in evidence file.

Phase H: Outbox dispatch test
-------------------------------
  Verify outbox row state = pending:
    SELECT state FROM credit_award_task_000
    WHERE award_order_id='"'"'<award_order_id>'"'"';

  Trigger DispatchCreditAwardTaskJob_DB1 via XXL-Job admin UI manual trigger.

  Verify outbox row state = dispatched after trigger.
  Verify exactly 1 user_credit_order row:
    SELECT COUNT(*) FROM user_credit_order_000
    WHERE out_business_no='"'"'<award_order_id>'"'"';
    Expected: 1

  Idempotency (second dispatch):
    Trigger DispatchCreditAwardTaskJob_DB1 again.
    Expected: user_credit_order count still = 1 (no double credit).

  Record all values in evidence file.

Phase I: Restore flag=false and verify health
----------------------------------------------
  Redeploy big-market-market-service with:
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false

  Confirm health:
    curl -sf http://<host>:8083/actuator/health | jq .status
    Expected: "UP"

  Record flag=false restore timestamp and health result in evidence file.

Phase J: Post-window verification (read-only)
----------------------------------------------
  Command (run this script in B17_POST_CHECK mode):

    B17_POST_CHECK=true \
      MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
      ./scripts/execute-account-service-staging-b17.sh

  All post-check items must be confirmed before making a go/no-go decision.

Phase K: Production go/no-go decision
---------------------------------------
  Review complete evidence file.
  All phases A-J must have passed with no anomalies.
  Go decision requires:
    - All Phase F idempotency checks passed (duplicate draw = no quota change)
    - All Phase G rollback checks passed (quota restored, duplicate rollback = 0 rows)
    - All Phase H outbox checks passed (no double credit, count = 1)
    - Flag successfully restored to false (Phase I)
    - No quota leak observed at any step
    - No double-credit observed at any step

  Record decision, approver, and timestamp in evidence file.
  Evidence file path: docs/evidence/phase-2-2-b17-staging-cutover-template.md
  (or the B17_EVIDENCE_FILE path you provided)

=============================================================================
'

# ---------------------------------------------------------------------------
# Mode: B17_PRINT_PLAN
# ---------------------------------------------------------------------------
if [[ "$B17_PRINT_PLAN" == "true" ]]; then
    echo "$CUTOVER_PLAN"
    exit 0
fi

info "=== Phase 2.2-B17 Staging Cutover Execution Package ==="
echo ""

# ---------------------------------------------------------------------------
# Section 1: Static pre-flight checks
# ---------------------------------------------------------------------------
info "=== Section 1: Static pre-flight checks ==="
echo ""

# P1: B16 gate script exists and is executable
if [[ -x "$B16_SCRIPT" ]]; then
    ok "P1: $B16_SCRIPT exists and is executable (B16 gate in place)"
elif [[ -f "$B16_SCRIPT" ]]; then
    ok "P1: $B16_SCRIPT exists (run chmod +x to make executable)"
else
    fail "P1: $B16_SCRIPT missing — B16 cutover gate not in place; B17 cannot proceed"
fi

# P2: B16 static checks must be green before B17 can run
B16_STATIC_OUT=$(./scripts/validate-account-service-cutover-b16.sh 2>&1) || true
B16_STATIC_PASS=$(echo "$B16_STATIC_OUT" | grep -c "^\[PASS\]" || true)
B16_STATIC_FAIL=$(echo "$B16_STATIC_OUT" | grep -c "^\[FAIL\]" || true)
if [[ "${B16_STATIC_FAIL:-0}" -eq 0 && "${B16_STATIC_PASS:-0}" -gt 0 ]]; then
    ok "P2: B16 static gate: ${B16_STATIC_PASS}/18 PASS (all B11-B15 invariants intact)"
else
    fail "P2: B16 static gate: ${B16_STATIC_PASS} PASS, ${B16_STATIC_FAIL} FAIL — resolve B16 failures before B17"
fi

# P3: Ledger DDL artefact present
if [[ -f "docs/sql/proposed-quota-decrement-ledger.sql" ]]; then
    ok "P3: docs/sql/proposed-quota-decrement-ledger.sql exists (Phase A DDL artefact ready)"
else
    fail "P3: docs/sql/proposed-quota-decrement-ledger.sql missing — Phase A blocker not ready"
fi

# P4: Outbox DDL artefact present
if [[ -f "docs/sql/proposed-credit-award-task-outbox.sql" ]]; then
    ok "P4: docs/sql/proposed-credit-award-task-outbox.sql exists (Phase B DDL artefact ready)"
else
    fail "P4: docs/sql/proposed-credit-award-task-outbox.sql missing — Phase B blocker not ready"
fi

# P5: Evidence template file exists in docs
EVIDENCE_TEMPLATE="docs/evidence/phase-2-2-b17-staging-cutover-template.md"
if [[ -f "$EVIDENCE_TEMPLATE" ]]; then
    ok "P5: $EVIDENCE_TEMPLATE exists (evidence template artefact ready)"
else
    fail "P5: $EVIDENCE_TEMPLATE missing — run B17_EVIDENCE_FILE=<path> to generate"
fi

# P6: remote-quota-decrement defaults false (production gate)
ENABLED_MATCH=$(grep -r \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null \
    | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "P6: remote-quota-decrement=false in all configs (production gate preserved)"
else
    fail "P6: remote-quota-decrement enabled in config — PRODUCTION GATE VIOLATION: $ENABLED_MATCH"
fi

echo ""

# ---------------------------------------------------------------------------
# Section 2: Manual blocker status
# ---------------------------------------------------------------------------
info "=== Section 2: Manual blocker status ==="
echo ""
info "The following blockers cannot be verified automatically."
info "They must be confirmed manually before opening the staging cutover window."
echo ""
cat <<'BLOCKERS'
  [BLOCKER 1] Staging ledger DDL
    Apply docs/sql/proposed-quota-decrement-ledger.sql to big_market_01 and big_market_02.
    Verify with: CONNECT_REMOTE=true ./scripts/execute-account-service-staging-b17.sh
    Status: PENDING (not verified by this script)

  [BLOCKER 2] Staging credit-award outbox DDL
    Apply docs/sql/proposed-credit-award-task-outbox.sql to big_market_01 and big_market_02.
    Verify with: CONNECT_REMOTE=true ./scripts/execute-account-service-staging-b17.sh
    Status: PENDING (not verified by this script)

  [BLOCKER 3] XXL-Job handler registration
    Register DispatchCreditAwardTaskJob_DB1 (cron: 0/30 * * * * ?) in staging XXL-Job admin.
    Register DispatchCreditAwardTaskJob_DB2 (cron: 0/30 * * * * ?) in staging XXL-Job admin.
    Status: PENDING (not verified by this script)

BLOCKERS

# ---------------------------------------------------------------------------
# Mode: CONNECT_REMOTE — delegate to B16 read-only staging verification
# ---------------------------------------------------------------------------
if [[ "$CONNECT_REMOTE" == "true" ]]; then
    echo ""
    info "=== Section 3: Remote staging DB verification (delegating to B16) ==="
    info "    Host: $MYSQL_HOST:$MYSQL_PORT  User: $MYSQL_USER"
    info "    This is read-only — delegating to CONNECT_REMOTE=true $B16_SCRIPT"
    echo ""

    REMOTE_OUT=$(CONNECT_REMOTE=true \
        MYSQL_HOST="$MYSQL_HOST" MYSQL_PORT="$MYSQL_PORT" \
        MYSQL_USER="$MYSQL_USER" MYSQL_PASS="$MYSQL_PASS" \
        ./"$B16_SCRIPT" 2>&1) || true

    echo "$REMOTE_OUT"

    REMOTE_PASS=$(echo "$REMOTE_OUT" | grep -c "^\[PASS\]" || true)
    REMOTE_FAIL=$(echo "$REMOTE_OUT" | grep -c "^\[FAIL\]" || true)

    echo ""
    if [[ "${REMOTE_FAIL:-0}" -eq 0 && "${REMOTE_PASS:-0}" -gt 0 ]]; then
        ok "R1: B16 CONNECT_REMOTE: ${REMOTE_PASS} PASS, 0 FAIL — all staging tables and UNIQUE KEYs verified"
        info "Phase C gate: PASS — proceed to Phase D (XXL-Job registration) and Phase E (flag enable)."
    else
        fail "R1: B16 CONNECT_REMOTE: ${REMOTE_PASS} PASS, ${REMOTE_FAIL} FAIL — resolve DDL blockers before Phase E"
        info "Phase C gate: FAIL — apply missing DDL and re-run CONNECT_REMOTE verification."
    fi
else
    echo ""
    info "=== Section 3: Remote staging DB verification — SKIPPED ==="
    info "    Set CONNECT_REMOTE=true with MYSQL_HOST/MYSQL_USER/MYSQL_PASS after applying DDL."
    info "    This is the Phase C gate (read-only — will not write to staging)."
fi

# ---------------------------------------------------------------------------
# Mode: B17_EVIDENCE_FILE — write/append evidence template to file
# ---------------------------------------------------------------------------
if [[ -n "$B17_EVIDENCE_FILE" ]]; then
    echo ""
    info "=== Section 4: Evidence file write ==="
    info "    Target: $B17_EVIDENCE_FILE"

    EVIDENCE_DIR=$(dirname "$B17_EVIDENCE_FILE")
    if [[ ! -d "$EVIDENCE_DIR" ]]; then
        mkdir -p "$EVIDENCE_DIR"
        info "Created directory: $EVIDENCE_DIR"
    fi

    RUN_TS=$(date '+%Y-%m-%d %H:%M:%S %Z')
    RUN_SEPARATOR="---
<!-- B17 evidence section appended at: ${RUN_TS} -->
---
"

    if [[ -f "$B17_EVIDENCE_FILE" ]]; then
        {
            echo ""
            echo "$RUN_SEPARATOR"
        } >> "$B17_EVIDENCE_FILE"
        info "Appending new evidence section to existing file."
    fi

    cat >> "$B17_EVIDENCE_FILE" <<EVIDENCE_BODY
# B17 Staging Cutover Evidence — $(date '+%Y-%m-%d')

**Script:** \`./scripts/execute-account-service-staging-b17.sh\`
**Run at:** ${RUN_TS}
**Environment:** staging

---

## Phase A — Ledger DDL Apply

| | big_market_01 | big_market_02 |
|---|---|---|
| Applied by | ___________________ | ___________________ |
| Timestamp | ___________________ | ___________________ |
| Command | \`mysql -h <host> -u <admin> -p big_market_01 < docs/sql/proposed-quota-decrement-ledger.sql\` | _(same DDL, target big_market_02)_ |

---

## Phase B — Credit-Award Outbox DDL Apply

| | big_market_01 | big_market_02 |
|---|---|---|
| Applied by | ___________________ | ___________________ |
| Timestamp | ___________________ | ___________________ |
| Command | \`mysql -h <host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql\` | _(same DDL, target big_market_02)_ |

---

## Phase C — Remote DB Verification (CONNECT_REMOTE)

Command run:
\`\`\`bash
CONNECT_REMOTE=true MYSQL_HOST=<host> MYSQL_PORT=3306 MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \\
    ./scripts/execute-account-service-staging-b17.sh
\`\`\`

Result (PASS/FAIL + check count): ___________________________________
Log/screenshot path: ___________________________________
Phase C gate: PASS / FAIL

---

## Phase D — XXL-Job Handler Registration

| Handler | Handler ID | Cron | Registered by | Screenshot path |
|---------|-----------|------|--------------|----------------|
| DispatchCreditAwardTaskJob_DB1 | ___________________ | \`0/30 * * * * ?\` | ___________________ | ___________________ |
| DispatchCreditAwardTaskJob_DB2 | ___________________ | \`0/30 * * * * ?\` | ___________________ | ___________________ |

---

## Phase E — Flag Enable Window

| | Value |
|---|---|
| flag=true start timestamp | ___________________ |
| Env key | \`ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true\` |
| Deployed to | big-market-market-service (staging only) |
| Confirmed via | \`docker exec big-market-market-service env | grep REMOTE_QUOTA_DECREMENT\` |
| Confirmation output | ___________________ |

---

## Phase F — Partake Flow E2E

**Test values:**

| | Value |
|---|---|
| userId | ___________________ |
| activityId | ___________________ |
| outBusinessNo | ___________________ |

**HTTP request:**
\`\`\`
POST /api/v1/raffle/activity/draw
{"activityId": <id>, "userId": "<user>"}
Response code: ___________________
Response body (awardId): ___________________
\`\`\`

**Ledger row BEFORE draw** (expected: no row):
\`\`\`sql
SELECT * FROM raffle_quota_decrement_ledger_000
WHERE user_id='<user>' AND activity_id=<id>;
\`\`\`
Result: ___________________________________

**Ledger row AFTER draw** (expected: status=applied):
Result: ___________________________________

**Quota BEFORE draw** (total_count_surplus):
\`\`\`sql
SELECT total_count_surplus FROM raffle_activity_account
WHERE user_id='<user>' AND activity_id=<id>;
\`\`\`
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
\`\`\`sql
SELECT status FROM raffle_quota_decrement_ledger_000
WHERE user_id='<user>' AND out_business_no='<biz-no>';
\`\`\`
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
\`\`\`sql
SELECT COUNT(*) FROM user_credit_order_000
WHERE out_business_no='<award_order_id>';
\`\`\`
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
| Env key restored | \`ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false\` |
| Health check command | \`curl -sf http://<host>:8083/actuator/health | jq .status\` |
| Health result | ___________________ (expected: "UP") |

---

## Phase J — Post-Window Verification

Command:
\`\`\`bash
B17_POST_CHECK=true MYSQL_HOST=<host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \\
    ./scripts/execute-account-service-staging-b17.sh
\`\`\`

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

Do NOT enable \`remote-quota-decrement=true\` in production until ALL of the following:

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
\`\`\`bash
# Set env and redeploy market-service
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
docker compose up -d --no-deps --build big-market-market-service
\`\`\`
The \`saveCreatePartakeOrderAggregate\` path takes effect immediately — no data loss.

**Short production canary window (after go decision):**
- Enable \`remote-quota-decrement=true\` for ~15 minutes on one production market-service instance.
- Monitor: quota leak queries, user_credit_order double-count, error rate, latency P99.
- Expand to full production only if canary is clean.
- Rollback at any anomaly: restore flag=false and redeploy.

EVIDENCE_BODY

    ok "E1: Evidence template written/appended to $B17_EVIDENCE_FILE"
    info "Fill this file out during the live staging cutover window."
else
    echo ""
    info "=== Section 4: Evidence file write — SKIPPED ==="
    info "    Set B17_EVIDENCE_FILE=<path> to write the evidence template to a local file."
    info "    Suggested: B17_EVIDENCE_FILE=docs/evidence/phase-2-2-b17-staging-cutover-template.md"
fi

# ---------------------------------------------------------------------------
# Mode: B17_POST_CHECK — delegate to B16 post-check mode
# ---------------------------------------------------------------------------
if [[ "$B17_POST_CHECK" == "true" ]]; then
    echo ""
    info "=== Section 5: Post-window verification (delegating to B16 B16_POST_CHECK) ==="
    info "    Host: $MYSQL_HOST  User: $MYSQL_USER"
    info "    This is read-only — no writes to staging."
    echo ""

    POST_OUT=$(B16_POST_CHECK=true \
        MYSQL_HOST="$MYSQL_HOST" MYSQL_PORT="$MYSQL_PORT" \
        MYSQL_USER="$MYSQL_USER" MYSQL_PASS="$MYSQL_PASS" \
        ./"$B16_SCRIPT" 2>&1) || true

    echo "$POST_OUT"

    POST_PASS=$(echo "$POST_OUT" | grep -c "^\[PASS\]" || true)
    POST_FAIL=$(echo "$POST_OUT" | grep -c "^\[FAIL\]" || true)

    echo ""
    if [[ "${POST_FAIL:-0}" -eq 0 && "${POST_PASS:-0}" -gt 0 ]]; then
        ok "PC1: B16 post-check: ${POST_PASS} PASS, 0 FAIL"
        info "Phase J gate: PASS — proceed to Phase K (production go/no-go decision)."
    else
        fail "PC1: B16 post-check: ${POST_PASS} PASS, ${POST_FAIL} FAIL — investigate before go/no-go."
        info "Phase J gate: FAIL — resolve all failures before making a go decision."
    fi
else
    echo ""
    info "=== Section 5: Post-window verification — SKIPPED ==="
    info "    Set B17_POST_CHECK=true with MYSQL_HOST/MYSQL_USER/MYSQL_PASS after Phase I (flag restore)."
    info "    This is the Phase J gate (read-only)."
fi

# ---------------------------------------------------------------------------
# Dry-run summary (default mode only)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_REMOTE" != "true" && -z "$B17_EVIDENCE_FILE" && "$B17_POST_CHECK" != "true" ]]; then
    echo ""
    info "=== Dry-run: B17 Execution Checklist ==="
    cat <<'DRY_RUN'

  To execute the B17 cutover, run these commands in order:

  Step 1 — Print the ordered cutover plan:
    B17_PRINT_PLAN=true ./scripts/execute-account-service-staging-b17.sh

  Step 2 — Generate evidence file:
    B17_EVIDENCE_FILE=docs/evidence/phase-2-2-b17-staging-cutover-template.md \
        ./scripts/execute-account-service-staging-b17.sh

  Step 3 — Apply Blocker 1: ledger DDL (manual):
    mysql -h <staging-host> -u <admin> -p big_market_01 \
        < docs/sql/proposed-quota-decrement-ledger.sql
    mysql -h <staging-host> -u <admin> -p big_market_02 \
        < docs/sql/proposed-quota-decrement-ledger.sql

  Step 4 — Apply Blocker 2: outbox DDL (manual):
    mysql -h <staging-host> -u <admin> -p big_market_01 \
        < docs/sql/proposed-credit-award-task-outbox.sql
    mysql -h <staging-host> -u <admin> -p big_market_02 \
        < docs/sql/proposed-credit-award-task-outbox.sql

  Step 5 — Phase C gate (remote DB verification, read-only):
    CONNECT_REMOTE=true MYSQL_HOST=<host> MYSQL_PORT=3306 \
      MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
      ./scripts/execute-account-service-staging-b17.sh

  Step 6 — Register XXL-Job handlers (manual — Blocker 3, Phase D):
    Log into staging XXL-Job admin UI.
    Register: DispatchCreditAwardTaskJob_DB1  (cron: 0/30 * * * * ?)
    Register: DispatchCreditAwardTaskJob_DB2  (cron: 0/30 * * * * ?)

  Step 7 — Open flag=true window (Phase E, manual):
    Deploy big-market-market-service with ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true

  Step 8 — Run Phases F, G, H (manual E2E, per cutover plan):
    B17_PRINT_PLAN=true ./scripts/execute-account-service-staging-b17.sh | grep -A 200 "Phase F"

  Step 9 — Restore flag=false (Phase I, manual):
    Redeploy big-market-market-service with ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false

  Step 10 — Phase J post-check gate (read-only):
    B17_POST_CHECK=true MYSQL_HOST=<host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
        ./scripts/execute-account-service-staging-b17.sh

  Step 11 — Make go/no-go decision (Phase K):
    Fill out Phase K section in evidence file.
    Record decision, approver, and timestamp.

  Baseline validations (must remain green throughout):
    ./scripts/validate-account-service-cutover-b16.sh        # B16: 18/18
    ./scripts/validate-quota-decrement-b15-e2e.sh            # B15: 20/20
    ./scripts/validate-quota-decrement-b14.sh                # B14: 21/21
    ./scripts/validate-production-ddl.sh                     # DDL: 14/14
    ./scripts/validate-mq-idempotency.sh                     # MQ:  12/12
    mvn compile                                              # BUILD SUCCESS

DRY_RUN
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B17 Execution Package Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B17 pre-flight checks pass."
    echo "     Follow the cutover plan (B17_PRINT_PLAN=true) to execute the staging window."
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed. Resolve before opening the staging cutover window."
    exit 1
fi
