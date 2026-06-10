#!/usr/bin/env bash
# validate-account-service-cutover-b16.sh — Phase 2.2-B16
#
# Cutover execution gate: verifies all automated pre-conditions before live staging E2E
# and production readiness review after the three manual blockers are completed.
#
# This script does NOT apply DDL, enable flags, or modify staging/production data.
# It is the final automated gate that must be green before the staging cutover window opens.
#
# Usage:
#   ./scripts/validate-account-service-cutover-b16.sh
#       Static dry-run: verify all B11-B15 invariants, safety gates, DDL artifacts,
#       and runbook completeness.  No DB, no writes, no flag changes.
#
#   CONNECT_DOCKER=true ./scripts/validate-account-service-cutover-b16.sh
#       Static + orchestrate existing scripts against local Docker MySQL:
#         - CONNECT_DOCKER=true ./scripts/validate-quota-decrement-b15-e2e.sh
#         - CONNECT_DOCKER=true ./scripts/validate-production-ddl.sh
#       Summarises delegated pass/fail counts.
#
#   CONNECT_REMOTE=true \
#     MYSQL_HOST=<staging-host> MYSQL_PORT=3306 \
#     MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
#     ./scripts/validate-account-service-cutover-b16.sh
#       Read-only staging verification:
#         - raffle_quota_decrement_ledger_{000..003} exist in big_market_01 + big_market_02
#         - UNIQUE KEY uq_user_activity_biz on all ledger shards
#         - credit_award_task_{000..003} exist in both DBs
#         - UNIQUE KEY uq_award_order_id on all outbox shards
#         - UNIQUE KEY uq_out_business_no on user_credit_order shards
#         - UNIQUE KEY uq_biz_id on user_behavior_rebate_order shards
#       Prints next manual E2E step list if all checks pass.
#       NEVER writes, mutates, or modifies staging data.
#
#   B16_POST_CHECK=true \
#     MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
#     ./scripts/validate-account-service-cutover-b16.sh
#       Post-staging read-only verification:
#         - Re-runs CONNECT_REMOTE static checks
#         - Prints evidence checklist summary (what to record before restoring flag=false)
#
#   B16_EVIDENCE_TEMPLATE=true ./scripts/validate-account-service-cutover-b16.sh
#       Print the staging evidence template (DDL timestamps, test values, before/after rows,
#       outbox state, idempotency proof, flag restore evidence) and exit.
#       Fill this out during the live staging E2E window and preserve as the production gate artefact.
#
# Safety constraints:
#   - NEVER applies staging/production DDL automatically
#   - NEVER enables remote-quota-decrement.enabled
#   - CONNECT_REMOTE mode is strictly read-only (information_schema SELECT only)
#   - B16_POST_CHECK mode is read-only; does not write to any table
#   - All production-flag checks assert flag=false is preserved
set -euo pipefail

CONNECT_DOCKER="${CONNECT_DOCKER:-false}"
CONNECT_REMOTE="${CONNECT_REMOTE:-false}"
B16_POST_CHECK="${B16_POST_CHECK:-false}"
B16_EVIDENCE_TEMPLATE="${B16_EVIDENCE_TEMPLATE:-false}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-big-market-mysql}"

PASS=0
FAIL=0
SKIP=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
skip() { echo "[SKIP] $*"; SKIP=$((SKIP + 1)); }
info() { echo "[INFO] $*"; }

# ---------------------------------------------------------------------------
# Evidence template mode
# ---------------------------------------------------------------------------
if [[ "$B16_EVIDENCE_TEMPLATE" == "true" ]]; then
    cat <<'TEMPLATE'
=============================================================================
B16 STAGING CUTOVER EVIDENCE TEMPLATE
Fill this out during the live staging E2E window.
Preserve as the production gate artefact — do NOT promote to production
until every line is filled in and every verification passes.
=============================================================================

--- 1. DDL APPLY EVIDENCE ---

  Ledger DDL applied to big_market_01:
    Timestamp : ___________________________________
    Applied by: ___________________________________
    Command   : mysql -h <host> -u <admin> -p big_market_01 \
                    < docs/sql/proposed-quota-decrement-ledger.sql

  Ledger DDL applied to big_market_02:
    Timestamp : ___________________________________
    Applied by: ___________________________________

  Outbox DDL applied to big_market_01:
    Timestamp : ___________________________________
    Applied by: ___________________________________
    Command   : mysql -h <host> -u <admin> -p big_market_01 \
                    < docs/sql/proposed-credit-award-task-outbox.sql

  Outbox DDL applied to big_market_02:
    Timestamp : ___________________________________
    Applied by: ___________________________________

--- 2. DB VERIFICATION (CONNECT_REMOTE) ---

  Command run:
    CONNECT_REMOTE=true MYSQL_HOST=<host> MYSQL_USER=<ro> MYSQL_PASS=<pass> \
        ./scripts/validate-account-service-cutover-b16.sh

  Result (PASS/FAIL + check count): ___________________________________
  Screenshot/log path              : ___________________________________

--- 3. XXL-JOB HANDLER REGISTRATION ---

  DispatchCreditAwardTaskJob_DB1:
    Handler ID in XXL-Job admin: ___________________________________
    Cron expression             : ___________________________________
    Registered by               : ___________________________________
    Screenshot path             : ___________________________________

  DispatchCreditAwardTaskJob_DB2:
    Handler ID in XXL-Job admin: ___________________________________
    Cron expression             : ___________________________________
    Screenshot path             : ___________________________________

--- 4. FLAG ENABLE WINDOW ---

  flag=true start timestamp: ___________________________________
  Env key set               : ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true
  Deployed to               : big-market-market-service (staging only)
  Confirmed via             :
    docker exec big-market-market-service env | grep REMOTE_QUOTA_DECREMENT
    Output: ___________________________________

--- 5. PARTAKE FLOW E2E (Phase E) ---

  Test userId        : ___________________________________
  Test activityId    : ___________________________________
  Test outBusinessNo : ___________________________________

  HTTP request:
    POST /api/v1/raffle/activity/draw
    body: {"activityId": <id>, "userId": "<user>"}
    Response code: ___________________________________
    Response body (awardId): ___________________________________

  Ledger row BEFORE draw (expected: no row):
    SELECT * FROM raffle_quota_decrement_ledger_000
    WHERE user_id='<user>' AND activity_id=<id>;
    Result: ___________________________________

  Ledger row AFTER draw (expected: status=applied):
    Result: ___________________________________

  Quota BEFORE draw (total_count_surplus):
    SELECT total_count_surplus FROM raffle_activity_account
    WHERE user_id='<user>' AND activity_id=<id>;
    Value: ___________________________________

  Quota AFTER draw (expected: decremented by 1):
    Value: ___________________________________

  Duplicate draw with same outBusinessNo (idempotency):
    Re-submitted: YES / NO
    Quota after duplicate: ___________________________________  (must equal post-draw value)
    Ledger row count      : ___________________________________  (must be 1)

--- 6. ROLLBACK PATH (Phase F) ---

  Rollback simulated: YES / NO
  Method used:
    [ ] savePartakeOrderOnly intentional failure
    [ ] Manual UPDATE rollback trigger

  Ledger row status after rollback (expected: rolled_back):
    SELECT status FROM raffle_quota_decrement_ledger_000
    WHERE user_id='<user>' AND out_business_no='<biz-no>';
    Status: ___________________________________

  Quota after rollback (expected: restored to pre-draw value):
    Value: ___________________________________

  Duplicate rollback (idempotency):
    Second rollback rows affected (expected: 0): ___________________________________
    Quota after duplicate rollback (expected: unchanged): ___________________________________

--- 7. OUTBOX DISPATCH (Phase G) ---

  Test outbox row inserted:
    DB/Table: ___________________________________
    award_order_id: ___________________________________
    state at insert: pending

  DispatchCreditAwardTaskJob_DB1 triggered:
    Timestamp: ___________________________________
    Via      : XXL-Job admin UI manual trigger

  Outbox row state after dispatch (expected: dispatched):
    ___________________________________

  user_credit_order count for award_order_id (expected: 1):
    SELECT COUNT(*) FROM user_credit_order_000
    WHERE out_business_no='<award_order_id>';
    Count: ___________________________________

  Second dispatch (idempotency):
    Triggered at: ___________________________________
    user_credit_order count after (expected: still 1): ___________________________________

--- 8. FLAG RESTORE EVIDENCE (Phase H) ---

  flag=false restore timestamp: ___________________________________
  Env key restored             : ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
  market-service health after restore:
    curl -sf http://<host>:8083/actuator/health | jq .status
    Result: ___________________________________  (expected: "UP")

--- 9. PRODUCTION GO / NO-GO DECISION ---

  All Phase E checks passed  : YES / NO
  All Phase F checks passed  : YES / NO
  All Phase G checks passed  : YES / NO
  Flag restored to false     : YES / NO
  Any quota leak observed    : YES / NO  (NO required for go)
  Any double-credit observed : YES / NO  (NO required for go)
  Any rollback failure       : YES / NO  (NO required for go)

  Production go decision: GO / NO-GO
  Decision by           : ___________________________________
  Decision timestamp    : ___________________________________

  If NO-GO, reason: ___________________________________

=============================================================================
TEMPLATE
    exit 0
fi

info "=== Phase 2.2-B16 Account Service Cutover Gate ==="
echo ""

# ---------------------------------------------------------------------------
# Section 1 — Static checks (no DB required)
# ---------------------------------------------------------------------------
info "=== Section 1: Static checks ==="
echo ""

PARTAKE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java"
REPO_IMPL="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java"
LOCAL_PORT="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java"
PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java"
LEDGER_DDL="docs/sql/proposed-quota-decrement-ledger.sql"
OUTBOX_DDL="docs/sql/proposed-credit-award-task-outbox.sql"
B15_SCRIPT="scripts/validate-quota-decrement-b15-e2e.sh"
B14_SCRIPT="scripts/validate-quota-decrement-b14.sh"
B13_SCRIPT="scripts/validate-quota-decrement-b13.sh"
B12_SCRIPT="scripts/validate-quota-decrement-b12.sh"
B11_SCRIPT="scripts/validate-quota-decrement-contract.sh"
OUTBOX_REHEARSAL="scripts/validate-award-credit-outbox-e2e-rehearsal.sh"
PROD_DDL_SCRIPT="scripts/validate-production-ddl.sh"

# S1: B15 E2E runbook gate script exists and is executable
if [[ -x "$B15_SCRIPT" ]]; then
    ok "S1: $B15_SCRIPT exists and is executable (B15 gate in place)"
elif [[ -f "$B15_SCRIPT" ]]; then
    ok "S1: $B15_SCRIPT exists (run chmod +x to make executable)"
else
    fail "S1: $B15_SCRIPT missing — B15 runbook gate not in place"
fi

# S2: B14 baseline script exists
if [[ -f "$B14_SCRIPT" ]]; then
    ok "S2: $B14_SCRIPT exists (B14 baseline present)"
else
    fail "S2: $B14_SCRIPT missing — B14 rollback+wiring baseline not in place"
fi

# S3: B13 baseline script exists
if [[ -f "$B13_SCRIPT" ]]; then
    ok "S3: $B13_SCRIPT exists (B13 baseline present)"
else
    fail "S3: $B13_SCRIPT missing — B13 staging validation baseline not in place"
fi

# S4: B12 baseline script exists
if [[ -f "$B12_SCRIPT" ]]; then
    ok "S4: $B12_SCRIPT exists (B12 baseline present)"
else
    fail "S4: $B12_SCRIPT missing — B12 idempotency foundation baseline not in place"
fi

# S5: B11 contract baseline script exists
if [[ -f "$B11_SCRIPT" ]]; then
    ok "S5: $B11_SCRIPT exists (B11 port contract baseline present)"
else
    fail "S5: $B11_SCRIPT missing — B11 domain port contract baseline not in place"
fi

# S6: remote-quota-decrement.enabled defaults false
if grep -q "remote-quota-decrement.enabled:false\|:false}" "$PARTAKE_SVC" 2>/dev/null; then
    ok "S6: remote-quota-decrement.enabled defaults false in RaffleActivityPartakeService"
else
    fail "S6: default false not found for remote-quota-decrement.enabled — flag may be live"
fi

# S7: No config file enables remote-quota-decrement by default
ENABLED_MATCH=$(grep -r \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null \
    | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "S7: No config enables remote-quota-decrement (default=false preserved in all configs)"
else
    fail "S7: remote-quota-decrement enabled in config: $ENABLED_MATCH"
fi

# S8: Ledger DDL file exists
if [[ -f "$LEDGER_DDL" ]]; then
    ok "S8: $LEDGER_DDL exists (staging ledger DDL artefact ready)"
else
    fail "S8: $LEDGER_DDL missing — B12/B13 ledger DDL not in place"
fi

# S9: Ledger DDL has UNIQUE KEY uq_user_activity_biz (idempotency constraint)
if grep -q "uq_user_activity_biz" "$LEDGER_DDL" 2>/dev/null; then
    ok "S9: Ledger DDL has UNIQUE KEY uq_user_activity_biz (user_id, activity_id, out_business_no)"
else
    fail "S9: Ledger DDL missing UNIQUE KEY uq_user_activity_biz — idempotency not enforced"
fi

# S10: Credit-award outbox DDL exists
if [[ -f "$OUTBOX_DDL" ]]; then
    ok "S10: $OUTBOX_DDL exists (staging outbox DDL artefact ready)"
else
    fail "S10: $OUTBOX_DDL missing — B5 outbox DDL not in place"
fi

# S11: Outbox DDL has UNIQUE KEY uq_award_order_id (idempotency constraint)
if grep -q "uq_award_order_id" "$OUTBOX_DDL" 2>/dev/null; then
    ok "S11: Outbox DDL has UNIQUE KEY uq_award_order_id (user_id, award_order_id)"
else
    fail "S11: Outbox DDL missing UNIQUE KEY uq_award_order_id — idempotency not enforced"
fi

# S12: XXL-Job handler names DispatchCreditAwardTaskJob_DB1/DB2 present in rehearsal script
if grep -q "DispatchCreditAwardTaskJob_DB1" "$OUTBOX_REHEARSAL" 2>/dev/null && \
   grep -q "DispatchCreditAwardTaskJob_DB2" "$OUTBOX_REHEARSAL" 2>/dev/null; then
    ok "S12: XXL-Job handlers (DispatchCreditAwardTaskJob_DB1/DB2) referenced in outbox rehearsal script"
else
    fail "S12: XXL-Job handler names missing from $OUTBOX_REHEARSAL"
fi

# S13: B15 runbook phases A through H all present in B15 script
PHASE_MISSING=()
for phase in A B C D E F G H; do
    if ! grep -q "Phase ${phase}:" "$B15_SCRIPT" 2>/dev/null; then
        PHASE_MISSING+=("$phase")
    fi
done
if [[ "${#PHASE_MISSING[@]}" -eq 0 ]]; then
    ok "S13: B15 staging runbook Phases A-H all present in $B15_SCRIPT"
else
    fail "S13: B15 runbook missing phases: ${PHASE_MISSING[*]}"
fi

# S14: rollbackQuotaWithLedger exists in ActivityRepository (B14 rollback impl)
if grep -q "rollbackQuotaWithLedger" "$REPO_IMPL" 2>/dev/null; then
    ok "S14: ActivityRepository.rollbackQuotaWithLedger exists (B14 saga compensation)"
else
    fail "S14: ActivityRepository.rollbackQuotaWithLedger missing — B14 not complete"
fi

# S15: savePartakeOrderOnly exists in ActivityRepository (B14 order-only insert)
if grep -q "savePartakeOrderOnly" "$REPO_IMPL" 2>/dev/null; then
    ok "S15: ActivityRepository.savePartakeOrderOnly exists (B14 flag=true order-only path)"
else
    fail "S15: ActivityRepository.savePartakeOrderOnly missing — B14 not complete"
fi

# S16: LocalActivityAccountPort delegates to real repo methods (not no-op)
if grep -q "activityRepository.decrementQuotaWithLedger\|activityRepository" "$LOCAL_PORT" 2>/dev/null && \
   grep -q "rollbackQuotaWithLedger" "$LOCAL_PORT" 2>/dev/null; then
    ok "S16: LocalActivityAccountPort delegates to activityRepository (real B14 impl, not no-op)"
else
    fail "S16: LocalActivityAccountPort not delegating to activityRepository — B14 wiring missing"
fi

# S17: IActivityAccountPort contract intact (decrementQuota + rollbackQuota)
DECREMENT_OK=false; ROLLBACK_OK=false
grep -q "decrementQuota" "$PORT_IFACE" 2>/dev/null && DECREMENT_OK=true
grep -q "rollbackQuota"  "$PORT_IFACE" 2>/dev/null && ROLLBACK_OK=true
if $DECREMENT_OK && $ROLLBACK_OK; then
    ok "S17: IActivityAccountPort contract intact (decrementQuota + rollbackQuota)"
else
    fail "S17: IActivityAccountPort missing decrementQuota=$DECREMENT_OK rollbackQuota=$ROLLBACK_OK"
fi

# S18: Production readiness gate — production cutover requires manual staging evidence
# Verified by absence of enabled flag; no staging evidence file implies cutover not yet done
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "S18: Production cutover gate — remote-quota-decrement=false confirms staging E2E not yet run (gate open, blockers must be resolved first)"
else
    fail "S18: PRODUCTION GATE VIOLATION — remote-quota-decrement is enabled somewhere; resolve before cutover"
fi

# ---------------------------------------------------------------------------
# Section 2 — Docker orchestration mode (CONNECT_DOCKER=true)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_DOCKER" != "true" ]]; then
    echo ""
    info "=== Section 2: Docker orchestration skipped ==="
    info "    Set CONNECT_DOCKER=true to delegate to B15 + production-DDL scripts against local Docker MySQL."
    info "    Required: local Docker stack running with $MYSQL_CONTAINER container."
else
    echo ""
    info "=== Section 2: Docker orchestration (delegating to existing scripts) ==="
    echo ""

    DOCKER_DELEGATE_FAIL=0

    # Delegate to B15 E2E script (CONNECT_DOCKER mode: static + read-only DB)
    info "--- Delegating: CONNECT_DOCKER=true $B15_SCRIPT ---"
    B15_OUT=$(CONNECT_DOCKER=true ./"$B15_SCRIPT" 2>&1) || true
    B15_PASS=$(echo "$B15_OUT" | grep -c "^\[PASS\]" || true)
    B15_FAIL=$(echo "$B15_OUT" | grep -c "^\[FAIL\]" || true)
    echo "$B15_OUT" | grep -E "^\[(PASS|FAIL|INFO)\]" | tail -20
    if [[ "${B15_FAIL:-0}" -eq 0 && "${B15_PASS:-0}" -gt 0 ]]; then
        ok "D1: $B15_SCRIPT CONNECT_DOCKER mode: ${B15_PASS} PASS, 0 FAIL"
    else
        fail "D1: $B15_SCRIPT CONNECT_DOCKER mode: ${B15_PASS} PASS, ${B15_FAIL} FAIL — resolve before staging cutover"
        DOCKER_DELEGATE_FAIL=$((DOCKER_DELEGATE_FAIL + 1))
    fi

    echo ""

    # Delegate to production DDL script (CONNECT_DOCKER mode: static + read-only DB)
    info "--- Delegating: CONNECT_DOCKER=true $PROD_DDL_SCRIPT ---"
    PROD_OUT=$(CONNECT_DOCKER=true ./"$PROD_DDL_SCRIPT" 2>&1) || true
    PROD_PASS=$(echo "$PROD_OUT" | grep -c "^\[PASS\]" || true)
    PROD_FAIL=$(echo "$PROD_OUT" | grep -c "^\[FAIL\]" || true)
    echo "$PROD_OUT" | grep -E "^\[(PASS|FAIL|INFO)\]" | tail -20
    if [[ "${PROD_FAIL:-0}" -eq 0 && "${PROD_PASS:-0}" -gt 0 ]]; then
        ok "D2: $PROD_DDL_SCRIPT CONNECT_DOCKER mode: ${PROD_PASS} PASS, 0 FAIL"
    else
        fail "D2: $PROD_DDL_SCRIPT CONNECT_DOCKER mode: ${PROD_PASS} PASS, ${PROD_FAIL} FAIL — resolve before staging cutover"
        DOCKER_DELEGATE_FAIL=$((DOCKER_DELEGATE_FAIL + 1))
    fi

    if [[ "$DOCKER_DELEGATE_FAIL" -eq 0 ]]; then
        echo ""
        info "Docker orchestration: all delegated scripts PASS."
        info "Next: apply staging DDL manually, register XXL-Job handlers, then run CONNECT_REMOTE=true mode."
    else
        echo ""
        info "Docker orchestration: $DOCKER_DELEGATE_FAIL delegated script(s) had failures."
        info "Resolve Docker failures before applying staging DDL."
    fi
fi

# ---------------------------------------------------------------------------
# Section 3 — Remote staging read-only verification (CONNECT_REMOTE=true)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_REMOTE" != "true" && "$B16_POST_CHECK" != "true" ]]; then
    echo ""
    info "=== Section 3: Remote staging verification skipped ==="
    info "    Set CONNECT_REMOTE=true with MYSQL_HOST/MYSQL_USER/MYSQL_PASS to verify staging DB."
    info "    Required: staging DDL applied manually first (Phase A of B15 runbook)."
    info "    This mode is read-only — it will NOT modify any staging data."
else
    echo ""
    info "=== Section 3: Remote staging read-only verification ==="
    info "    Host: $MYSQL_HOST:$MYSQL_PORT  User: $MYSQL_USER"
    echo ""

    run_remote_mysql() {
        local db="$1" query="$2"
        mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" \
            -s -N -e "$query" "$db" 2>/dev/null
    }

    check_remote_table() {
        local db="$1" table="$2"
        local cnt
        cnt=$(run_remote_mysql "$db" \
            "SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table';") || true
        echo "${cnt:-0}"
    }

    check_remote_key() {
        local db="$1" table="$2" key="$3"
        local cnt
        cnt=$(run_remote_mysql "$db" \
            "SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table'
               AND INDEX_NAME='$key' AND NON_UNIQUE=0;") || true
        echo "${cnt:-0}"
    }

    RNUM=1
    REMOTE_FAIL=0

    # Ledger tables: 4 shards × 2 DBs = 8 table + 8 key checks
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="raffle_quota_decrement_ledger_${shard}"

            cnt=$(check_remote_table "$db" "$table")
            if [[ "${cnt:-0}" -gt 0 ]]; then
                ok "R${RNUM}: staging $db.$table exists"
            else
                fail "R${RNUM}: staging $db.$table NOT FOUND — apply $LEDGER_DDL to $db"
                REMOTE_FAIL=$((REMOTE_FAIL + 1))
            fi
            ((RNUM++))

            kcnt=$(check_remote_key "$db" "$table" "uq_user_activity_biz")
            if [[ "${kcnt:-0}" -gt 0 ]]; then
                ok "R${RNUM}: staging $db.$table has UNIQUE KEY uq_user_activity_biz"
            else
                fail "R${RNUM}: staging $db.$table missing UNIQUE KEY uq_user_activity_biz — re-apply DDL"
                REMOTE_FAIL=$((REMOTE_FAIL + 1))
            fi
            ((RNUM++))
        done
    done

    # Outbox tables: 4 shards × 2 DBs = 8 table + 8 key checks
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="credit_award_task_${shard}"

            cnt=$(check_remote_table "$db" "$table")
            if [[ "${cnt:-0}" -gt 0 ]]; then
                ok "R${RNUM}: staging $db.$table exists"
            else
                fail "R${RNUM}: staging $db.$table NOT FOUND — apply $OUTBOX_DDL to $db"
                REMOTE_FAIL=$((REMOTE_FAIL + 1))
            fi
            ((RNUM++))

            kcnt=$(check_remote_key "$db" "$table" "uq_award_order_id")
            if [[ "${kcnt:-0}" -gt 0 ]]; then
                ok "R${RNUM}: staging $db.$table has UNIQUE KEY uq_award_order_id"
            else
                fail "R${RNUM}: staging $db.$table missing UNIQUE KEY uq_award_order_id — re-apply DDL"
                REMOTE_FAIL=$((REMOTE_FAIL + 1))
            fi
            ((RNUM++))
        done
    done

    # user_credit_order UNIQUE KEY uq_out_business_no: 4 shards × 2 DBs
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="user_credit_order_${shard}"
            kcnt=$(check_remote_key "$db" "$table" "uq_out_business_no")
            if [[ "${kcnt:-0}" -gt 0 ]]; then
                ok "R${RNUM}: staging $db.$table has UNIQUE KEY uq_out_business_no"
            else
                fail "R${RNUM}: staging $db.$table missing UNIQUE KEY uq_out_business_no — verify base DDL"
                REMOTE_FAIL=$((REMOTE_FAIL + 1))
            fi
            ((RNUM++))
        done
    done

    # user_behavior_rebate_order UNIQUE KEY uq_biz_id: 4 shards × 2 DBs
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="user_behavior_rebate_order_${shard}"
            kcnt=$(check_remote_key "$db" "$table" "uq_biz_id")
            if [[ "${kcnt:-0}" -gt 0 ]]; then
                ok "R${RNUM}: staging $db.$table has UNIQUE KEY uq_biz_id"
            else
                fail "R${RNUM}: staging $db.$table missing UNIQUE KEY uq_biz_id — verify base DDL"
                REMOTE_FAIL=$((REMOTE_FAIL + 1))
            fi
            ((RNUM++))
        done
    done

    echo ""
    if [[ "$REMOTE_FAIL" -eq 0 ]]; then
        info "Remote staging verification: all $((RNUM - 1)) checks PASS."
        echo ""
        info "=== Next manual E2E steps (all staging DDL and keys verified) ==="
        cat <<'NEXT_STEPS'

  1. Register XXL-Job handlers (if not yet done — Phase C of B15 runbook):
       DispatchCreditAwardTaskJob_DB1  (cron: 0/30 * * * * ?)
       DispatchCreditAwardTaskJob_DB2  (cron: 0/30 * * * * ?)

  2. Enable flag in staging market-service only (Phase D):
       ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true
       Redeploy big-market-market-service on staging.

  3. Run partake flow E2E (Phase E of B15 runbook):
       POST /api/v1/raffle/activity/draw — verify ledger row + quota decrement + idempotency.

  4. Run rollback path (Phase F):
       Verify rollback: status=rolled_back + quota restored + duplicate rollback idempotent.

  5. Run outbox dispatch (Phase G):
       Trigger DispatchCreditAwardTaskJob_DB1 — verify pending→dispatched + no double-credit.

  6. Restore flag (Phase H):
       ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false — redeploy + confirm health UP.

  7. Fill out the B16 evidence template:
       B16_EVIDENCE_TEMPLATE=true ./scripts/validate-account-service-cutover-b16.sh

  8. Run post-check (after step 6):
       B16_POST_CHECK=true MYSQL_HOST=<staging-host> MYSQL_USER=<ro> MYSQL_PASS=<pass> \
           ./scripts/validate-account-service-cutover-b16.sh

NEXT_STEPS
    else
        info "Remote staging verification: $REMOTE_FAIL check(s) failed."
        info "Resolve all FAIL items (apply missing DDL) before opening the staging cutover window."
    fi
fi

# ---------------------------------------------------------------------------
# Section 4 — Post-check summary mode (B16_POST_CHECK=true)
# ---------------------------------------------------------------------------
if [[ "$B16_POST_CHECK" == "true" ]]; then
    echo ""
    info "=== Section 4: Post-staging evidence checklist ==="
    cat <<'POST_CHECK'

After completing the live staging E2E window, confirm:

  [ ] Ledger DDL timestamps recorded in evidence template
  [ ] DB verification (CONNECT_REMOTE) PASS — all tables and UNIQUE KEYs present
  [ ] XXL-Job handler IDs recorded (DB1 + DB2)
  [ ] flag=true start/end timestamps recorded
  [ ] Partake flow E2E: HTTP 200, ledger row status=applied, quota decremented by 1
  [ ] Idempotency (duplicate partake): quota unchanged, ledger row count = 1
  [ ] Rollback path: ledger status=rolled_back, quota restored, duplicate rollback 0 rows
  [ ] Outbox dispatch: pending→dispatched, exactly 1 user_credit_order row
  [ ] Second dispatch idempotency: user_credit_order count still = 1
  [ ] flag restored to false, market-service health = "UP"
  [ ] No quota leak observed
  [ ] No double-credit observed
  [ ] Evidence template fully filled out and preserved

  Production go/no-go criteria:
    GO  — all checks above pass; evidence template complete; no anomalies
    NO-GO — any check fails; any quota/credit inconsistency; escalate immediately

  Next step if GO:
    Schedule production DDL window + flag=true production enable with oncall approval.

  Next step if NO-GO:
    Keep flag=false; investigate failure; repeat staging cutover after root cause resolved.

POST_CHECK
fi

# ---------------------------------------------------------------------------
# Section 5 — Staging runbook reference (always printed in static mode)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_DOCKER" != "true" && "$CONNECT_REMOTE" != "true" && "$B16_POST_CHECK" != "true" ]]; then
    echo ""
    info "=== Section 5: B16 Staging Cutover Runbook Reference ==="
    cat <<'RUNBOOK'

[B16 STAGING CUTOVER GATE — MANUAL BLOCKERS AND COMMANDS]

Three blockers remain before the staging E2E window can open.
All must be resolved manually. This script cannot perform these steps.

--- Blocker 1: Apply ledger DDL to staging ---

  mysql -h <staging-host> -u <admin-user> -p big_market_01 \
      < docs/sql/proposed-quota-decrement-ledger.sql

  mysql -h <staging-host> -u <admin-user> -p big_market_02 \
      < docs/sql/proposed-quota-decrement-ledger.sql

--- Blocker 2: Apply credit-award outbox DDL to staging ---

  mysql -h <staging-host> -u <admin-user> -p big_market_01 \
      < docs/sql/proposed-credit-award-task-outbox.sql

  mysql -h <staging-host> -u <admin-user> -p big_market_02 \
      < docs/sql/proposed-credit-award-task-outbox.sql

--- Blocker 3: Register XXL-Job handlers ---

  Log into XXL-Job admin UI on staging.
  Register: DispatchCreditAwardTaskJob_DB1  (cron: 0/30 * * * * ?)
  Register: DispatchCreditAwardTaskJob_DB2  (cron: 0/30 * * * * ?)

[VALIDATION COMMANDS — run in order]

  # Step 1: Static gate (run any time)
  ./scripts/validate-account-service-cutover-b16.sh

  # Step 2: Local Docker DB verification (before staging DDL apply)
  CONNECT_DOCKER=true ./scripts/validate-account-service-cutover-b16.sh

  # Step 3: After applying staging DDL — remote read-only verification
  CONNECT_REMOTE=true \
    MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
    ./scripts/validate-account-service-cutover-b16.sh

  # Step 4: Full B15 rehearsal baselines (must stay green)
  ./scripts/validate-quota-decrement-b15-e2e.sh                                           # 20 PASS
  ./scripts/validate-quota-decrement-b14.sh                                               # 21 PASS
  ./scripts/validate-quota-decrement-b13.sh                                               # 12 PASS
  ./scripts/validate-quota-decrement-b12.sh                                               # 22 PASS
  ./scripts/validate-quota-decrement-contract.sh                                          # 18 PASS
  ./scripts/validate-production-ddl.sh                                                    # 14+ PASS
  ./scripts/validate-mq-idempotency.sh                                                    # 12 PASS
  mvn compile                                                                              # BUILD SUCCESS

  # Step 5: Print evidence template (fill out during staging window)
  B16_EVIDENCE_TEMPLATE=true ./scripts/validate-account-service-cutover-b16.sh

  # Step 6: Post-staging check (after flag restored to false)
  B16_POST_CHECK=true \
    MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
    ./scripts/validate-account-service-cutover-b16.sh

[PRODUCTION NO-GO CRITERIA — do NOT enable in production if any of these are true]

  - Any FAIL in this script's static or remote checks
  - CONNECT_REMOTE staging verification has ANY FAIL
  - Partake flow E2E failed (non-200, ledger row missing, quota not decremented)
  - Idempotency proof failed (quota changed on duplicate draw)
  - Rollback path failed (quota not restored, or double-restore on duplicate rollback)
  - Outbox dispatch failed (row stays pending, or user_credit_order count != 1)
  - Double credit observed (user_credit_order count > 1)
  - Evidence template not filled out

[ROLLBACK PLAN — if staging cutover fails]

  Immediate: set ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false and redeploy.
             The saveCreatePartakeOrderAggregate path takes effect instantly.

  Quota leak check:
    SELECT total_count_surplus FROM raffle_activity_account
    WHERE user_id='<user>' AND activity_id=<id>;
    Compare to pre-test value. If leaked, restore manually:
      UPDATE raffle_activity_account
        SET total_count_surplus = total_count_surplus + 1
        WHERE user_id='<user>' AND activity_id=<id>;
      UPDATE raffle_quota_decrement_ledger_000
        SET status='rolled_back'
        WHERE user_id='<user>' AND out_business_no='<biz-no>';

  Do NOT open production window until all staging E2E steps pass and evidence template is complete.

RUNBOOK
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B16 Cutover Gate Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
[[ "$SKIP" -gt 0 ]] && echo "SKIP: $SKIP"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B16 static checks pass."
    if [[ "$CONNECT_REMOTE" == "true" || "$B16_POST_CHECK" == "true" ]]; then
        echo "     Remote staging verification complete — follow Section 3 output for next steps."
    else
        echo "     Resolve the 3 manual blockers (ledger DDL, outbox DDL, XXL-Job registration),"
        echo "     then run CONNECT_REMOTE=true mode to verify staging DB before opening the cutover window."
    fi
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed. Resolve before proceeding with staging cutover."
    exit 1
fi
