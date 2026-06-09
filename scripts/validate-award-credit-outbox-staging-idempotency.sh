#!/usr/bin/env bash
# Phase 2.2-B8: Award credit outbox staging validation + idempotency verification scaffold.
#
# SAFE BY DEFAULT — default mode is a dry-run: no data mutation, no service restart.
#
# Modes (controlled by environment variables):
#
#   Default (static + Docker read-only checks):
#     ./scripts/validate-award-credit-outbox-staging-idempotency.sh
#
#   Write-mode (inserts/deletes a single local test row, localhost only):
#     STAGING_IDEMPOTENCY_WRITE=true \
#       ./scripts/validate-award-credit-outbox-staging-idempotency.sh
#
#   Custom test identifiers for write-mode:
#     STAGING_IDEMPOTENCY_WRITE=true \
#     B8_TEST_USER_ID=myuser \
#     B8_TEST_AWARD_ORDER_ID=my-order-b8-001 \
#       ./scripts/validate-award-credit-outbox-staging-idempotency.sh
#
# Safety guarantees:
#   - STAGING_IDEMPOTENCY_WRITE=true is BLOCKED if MYSQL_HOST is not localhost/127.0.0.1
#   - STAGING_IDEMPOTENCY_WRITE=true always cleans up the test row on EXIT (EXIT trap)
#   - No service restarts in any mode
#   - Does not enable account.award-credit-outbox.enabled — flag remains false by default
#   - Does not push to remote or change production config
#
# Checks (static):
#   1.  credit_award_task_mapper.xml: updateDispatched sets state='dispatched'
#   2.  credit_award_task_mapper.xml: updateRetryFailed increments retry_count
#   3.  credit_award_task_mapper.xml: updateRetryFailed transitions to 'failed' at retry_count >= 5
#   4.  credit_award_task_mapper.xml: queryPendingTasks polls state='pending' and retry_count < 5
#   5.  DispatchCreditAwardTaskJob.dispatchTask: outBusinessNo = task.getAwardOrderId()
#   6.  DispatchCreditAwardTaskJob.dispatchTask: calls creditAwardTaskDao.updateDispatched (success path)
#   7.  DispatchCreditAwardTaskJob.dispatchTask: calls creditAwardTaskDao.updateRetryFailed (failure path)
#   8.  DispatchCreditAwardTaskJob_DB1 and _DB2 handler names present (XXL-Job registration names)
#   9.  DispatchCreditAwardTaskJob scans tbIdx < 4 (all 4 table shards per DB)
#   10. DDL: UNIQUE KEY uq_award_order_id on (user_id, award_order_id) — outbox idempotency constraint
#   11. DB schema DDL: user_credit_order has UNIQUE KEY on out_business_no (account-side idempotency)
#   12. CreditRepository catches DuplicateKeyException in saveUserCreditTradeOrder (account-side dedup)
#   13. TradeNameVO.AWARD_CREDIT enum value exists (consumer dispatch enum wired correctly)
#
# Docker read-only checks (if MySQL running, 9 checks):
#   14. MySQL container reachable
#   15-18. user_credit_order_000..003 exist in big_market_01 (account ledger tables — idempotency substrate)
#   19-22. credit_award_task_000..003 exist in big_market_01 (outbox tables, if DDL applied)
#   23-26. credit_award_task_000..003 exist in big_market_02
#
# STAGING_IDEMPOTENCY_WRITE=true checks (localhost only, 4 checks + cleanup):
#   27. Test outbox row inserted (state=pending, retry_count=0)
#   28. Row readable with correct state=pending
#   29. Duplicate insert blocked by UNIQUE KEY (MySQL DuplicateKeyError)
#   30. No user_credit_order row for test out_business_no (credit not yet dispatched — clean)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ─── Configuration ─────────────────────────────────────────────────────────────
STAGING_IDEMPOTENCY_WRITE="${STAGING_IDEMPOTENCY_WRITE:-false}"
B8_TEST_USER_ID="${B8_TEST_USER_ID:-b8-test-user}"
B8_TEST_AWARD_ORDER_ID="${B8_TEST_AWARD_ORDER_ID:-b8-test-order-b8-001}"
B8_TEST_CREDIT_AMOUNT="${B8_TEST_CREDIT_AMOUNT:-10.00}"

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"

MAPPER_MJS="$REPO_ROOT/big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
JOB_FILE="$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
DDL_FILE="$REPO_ROOT/docs/sql/proposed-credit-award-task-outbox.sql"
SCHEMA_SQL_01="$REPO_ROOT/docs/dev-ops/mysql/sql/big_market_01.sql"
CREDIT_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java"
TRADE_NAME_VO="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain/credit/model/valobj/TradeNameVO.java"

DATABASES_OUTBOX=("big_market_01" "big_market_02")
DATABASES_LEDGER=("big_market_01")
TABLE_SUFFIXES=("000" "001" "002" "003")

PASS=0
FAIL=0
SKIP=0

# ─── Helpers ───────────────────────────────────────────────────────────────────
check_pass() { echo "  [PASS] $1"; PASS=$((PASS + 1)); }
check_fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }
check_skip() { echo "  [SKIP] $1"; SKIP=$((SKIP + 1)); }
section()    { echo ""; echo "─── $1 ───"; }

docker_available() {
    command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

mysql_available() {
    docker exec "$MYSQL_CONTAINER" mysqladmin ping -u"$MYSQL_USER" -p"$MYSQL_PASS" --silent >/dev/null 2>&1
}

mysql_exec() {
    local db="$1" sql="$2"
    docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" \
        --silent --skip-column-names -e "$sql" "$db" 2>/dev/null
}

table_exists() {
    local db="$1" table="$2"
    local cnt
    cnt="$(mysql_exec "$db" \
        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${db}' AND TABLE_NAME='${table}';" \
        || echo "0")"
    [ "${cnt:-0}" = "1" ]
}

# ─── Write-mode cleanup (EXIT trap) ────────────────────────────────────────────
_WRITE_CLEANED=false
cleanup_test_row() {
    if [ "$_WRITE_CLEANED" = "true" ]; then return; fi
    _WRITE_CLEANED=true
    if [ "$STAGING_IDEMPOTENCY_WRITE" = "true" ] && docker_available && mysql_available 2>/dev/null; then
        echo ""
        echo "─── Cleanup: removing B8 test row ───"
        for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
            mysql_exec "big_market_01" \
                "DELETE FROM credit_award_task_${SUFFIX} WHERE user_id='${B8_TEST_USER_ID}' AND award_order_id='${B8_TEST_AWARD_ORDER_ID}';" \
                2>/dev/null || true
        done
        echo "  Test row cleanup complete (user_id=${B8_TEST_USER_ID} award_order_id=${B8_TEST_AWARD_ORDER_ID})"
    fi
}

# ─── Header ────────────────────────────────────────────────────────────────────
echo "=== Phase 2.2-B8 Award Credit Outbox Staging Idempotency Validation ==="
echo "STAGING_IDEMPOTENCY_WRITE = $STAGING_IDEMPOTENCY_WRITE"
echo "B8_TEST_USER_ID           = $B8_TEST_USER_ID"
echo "B8_TEST_AWARD_ORDER_ID    = $B8_TEST_AWARD_ORDER_ID"
echo ""
echo "SAFE BY DEFAULT: dry-run mode — no data mutation, no service restart."
echo "Set STAGING_IDEMPOTENCY_WRITE=true (localhost only) to run write-mode checks."

# ═══════════════════════════════════════════════════════════════════════════════
section "1. Static checks — state machine and idempotency invariants (no Docker required)"
# ═══════════════════════════════════════════════════════════════════════════════

# Check 1: updateDispatched sets state='dispatched'
if [[ -f "$MAPPER_MJS" ]] && grep -q "state = 'dispatched'" "$MAPPER_MJS"; then
    check_pass "credit_award_task_mapper.xml: updateDispatched sets state='dispatched' (pending→dispatched transition defined)"
else
    check_fail "credit_award_task_mapper.xml: updateDispatched does NOT set state='dispatched' — state transition missing or renamed"
fi

# Check 2: updateRetryFailed increments retry_count
if [[ -f "$MAPPER_MJS" ]] && grep -q "retry_count = retry_count + 1" "$MAPPER_MJS"; then
    check_pass "credit_award_task_mapper.xml: updateRetryFailed increments retry_count (retry model present)"
else
    check_fail "credit_award_task_mapper.xml: updateRetryFailed does NOT increment retry_count — retry model broken"
fi

# Check 3: updateRetryFailed transitions to 'failed' at retry_count >= 5
if [[ -f "$MAPPER_MJS" ]] \
    && grep -q "'failed'" "$MAPPER_MJS" \
    && grep -q ">= 5" "$MAPPER_MJS"; then
    check_pass "credit_award_task_mapper.xml: updateRetryFailed transitions to state='failed' at retry_count >= 5 (max-retry boundary)"
else
    check_fail "credit_award_task_mapper.xml: max-retry boundary (state='failed' at retry_count >= 5) NOT found — exhausted tasks may loop forever"
fi

# Check 4: queryPendingTasks polls state='pending' and retry_count < 5 (consistent with max retry)
# XML mappers use &lt; for < so match either literal or XML-escaped form.
if [[ -f "$MAPPER_MJS" ]] \
    && grep -q "state = 'pending'" "$MAPPER_MJS" \
    && grep -qE "retry_count.*(< 5|&lt; 5)" "$MAPPER_MJS"; then
    check_pass "credit_award_task_mapper.xml: queryPendingTasks filters state='pending' AND retry_count < 5 (consistent with max-retry threshold)"
else
    check_fail "credit_award_task_mapper.xml: queryPendingTasks filter (state='pending' and retry_count < 5) NOT found — poll may not align with retry threshold"
fi

# Check 5: dispatchTask uses task.getAwardOrderId() as outBusinessNo
if [[ -f "$JOB_FILE" ]] && grep -q "outBusinessNo(task.getAwardOrderId())" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob.dispatchTask: outBusinessNo = task.getAwardOrderId() (award_order_id passed as idempotency key)"
else
    check_fail "DispatchCreditAwardTaskJob.dispatchTask: outBusinessNo(task.getAwardOrderId()) NOT found — idempotency key not correctly forwarded to account-service"
fi

# Check 6: success path calls updateDispatched
if [[ -f "$JOB_FILE" ]] && grep -q "creditAwardTaskDao.updateDispatched" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob.dispatchTask: calls creditAwardTaskDao.updateDispatched on success (pending→dispatched write)"
else
    check_fail "DispatchCreditAwardTaskJob.dispatchTask: creditAwardTaskDao.updateDispatched NOT called — success path does not mark rows as dispatched"
fi

# Check 7: failure path calls updateRetryFailed
if [[ -f "$JOB_FILE" ]] && grep -q "creditAwardTaskDao.updateRetryFailed" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob.dispatchTask: calls creditAwardTaskDao.updateRetryFailed on failure (retry increment write)"
else
    check_fail "DispatchCreditAwardTaskJob.dispatchTask: creditAwardTaskDao.updateRetryFailed NOT called — failure path does not record retry count"
fi

# Check 8: DB1 and DB2 handler names (critical for XXL-Job registration)
if [[ -f "$JOB_FILE" ]] \
    && grep -q '"DispatchCreditAwardTaskJob_DB1"' "$JOB_FILE" \
    && grep -q '"DispatchCreditAwardTaskJob_DB2"' "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob declares @XxlJob(\"DispatchCreditAwardTaskJob_DB1\") and _DB2 (XXL-Job registration names verified)"
else
    check_fail "DispatchCreditAwardTaskJob is missing _DB1 or _DB2 @XxlJob handler — XXL-Job admin registration will fail"
fi

# Check 9: tbIdx < 4 (all 4 table shards per DB covered)
if [[ -f "$JOB_FILE" ]] && grep -q "tbIdx < 4" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob scans tbIdx < 4 (all 4 table shards per DB — no shard missed)"
else
    check_fail "DispatchCreditAwardTaskJob does NOT scan tbIdx < 4 — pending tasks in some shards may be silently skipped"
fi

# Check 10: DDL has UNIQUE KEY on (user_id, award_order_id) — outbox idempotency
if [[ -f "$DDL_FILE" ]] && grep -qE "UNIQUE KEY .*award_order_id" "$DDL_FILE"; then
    check_pass "DDL: UNIQUE KEY uq_award_order_id (user_id, award_order_id) present — outbox INSERT idempotency constraint verified"
else
    check_fail "DDL: UNIQUE KEY on (user_id, award_order_id) NOT found — duplicate outbox rows possible on MQ retry; double-credit risk"
fi

# Check 11: user_credit_order schema has UNIQUE KEY on out_business_no (account-side idempotency)
if [[ -f "$SCHEMA_SQL_01" ]] \
    && grep -A 30 "CREATE TABLE.*user_credit_order_000" "$SCHEMA_SQL_01" \
       | grep -q "UNIQUE KEY.*out_business_no"; then
    check_pass "DB schema: user_credit_order_000 has UNIQUE KEY on out_business_no (account-side dedup key present in schema DDL)"
else
    check_fail "DB schema: UNIQUE KEY on out_business_no NOT found in user_credit_order_000 — account-service cannot deduplicate on outBusinessNo"
fi

# Check 12: CreditRepository catches DuplicateKeyException (account-side dedup handler)
if [[ -f "$CREDIT_REPO" ]] && grep -q "DuplicateKeyException" "$CREDIT_REPO"; then
    check_pass "CreditRepository.saveUserCreditTradeOrder catches DuplicateKeyException (account-side dedup: duplicate dispatch returns without double-credit)"
else
    check_fail "CreditRepository does NOT catch DuplicateKeyException — duplicate outBusinessNo from a retry would propagate as an unhandled exception"
fi

# Check 13: TradeNameVO.AWARD_CREDIT enum value (consumer dispatch enum wired correctly)
if [[ -f "$TRADE_NAME_VO" ]] && grep -q "AWARD_CREDIT" "$TRADE_NAME_VO"; then
    check_pass "TradeNameVO.AWARD_CREDIT enum value exists (consumer passes correct tradeName to account-service)"
else
    check_fail "TradeNameVO.AWARD_CREDIT NOT found — account-service will reject dispatch with ILLEGAL_PARAMETER on tradeName validation"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "2. Docker read-only checks — account ledger and outbox table presence"
# ═══════════════════════════════════════════════════════════════════════════════

if ! docker_available; then
    check_skip "Docker not available — skipping all Docker checks (checks 14-26)"
    SKIP=$((SKIP + 13))
elif ! mysql_available 2>/dev/null; then
    echo "  INFO: MySQL container ($MYSQL_CONTAINER) not reachable — start the Docker stack first:"
    echo "        docker compose -f docs/dev-ops/docker-compose-environment.yml up -d"
    check_skip "MySQL not reachable — Docker read-only checks skipped (checks 14-26)"
    SKIP=$((SKIP + 13))
else
    # Check 14: MySQL reachable
    check_pass "MySQL container ($MYSQL_CONTAINER) is reachable"

    # Checks 15-18: user_credit_order_000..003 in big_market_01
    # These tables must exist for account-service idempotency to function.
    for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
        TABLE="user_credit_order_${SUFFIX}"
        if table_exists "big_market_01" "$TABLE"; then
            check_pass "big_market_01.${TABLE} exists (account ledger table — out_business_no UNIQUE constraint active)"
        else
            check_fail "big_market_01.${TABLE} MISSING — account-service cannot write credit orders; idempotency substrate absent"
        fi
    done

    # Checks 19-22: credit_award_task_000..003 in big_market_01 (outbox tables — apply DDL first)
    # Checks 23-26: credit_award_task_000..003 in big_market_02
    ALL_OUTBOX_PRESENT=true
    for DB in "${DATABASES_OUTBOX[@]}"; do
        for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
            TABLE="credit_award_task_${SUFFIX}"
            if table_exists "$DB" "$TABLE"; then
                check_pass "${DB}.${TABLE} exists (outbox table ready for flag=true dispatch)"
            else
                check_fail "${DB}.${TABLE} MISSING — apply DDL before enabling flag=true (APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh)"
                ALL_OUTBOX_PRESENT=false
            fi
        done
    done

    if [ "$ALL_OUTBOX_PRESENT" = "false" ]; then
        echo ""
        echo "  ACTION REQUIRED: Apply outbox DDL before enabling flag=true or running write-mode checks."
        echo "  Option A — auto-apply locally:"
        echo "    APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh"
        echo "  Option B — apply manually:"
        echo "    docker exec -i mysql mysql -uroot -p123456 big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql"
        echo "    docker exec -i mysql mysql -uroot -p123456 big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql"
    fi
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "3. STAGING_IDEMPOTENCY_WRITE — local test row + duplicate-key verification"
# ═══════════════════════════════════════════════════════════════════════════════

if [ "$STAGING_IDEMPOTENCY_WRITE" = "true" ]; then
    # Block on non-localhost hosts — prevent accidental staging/prod data mutation
    if [[ "$MYSQL_HOST" != "localhost" && "$MYSQL_HOST" != "127.0.0.1" ]]; then
        check_fail "STAGING_IDEMPOTENCY_WRITE=true is only allowed when MYSQL_HOST=localhost or 127.0.0.1 (got: $MYSQL_HOST)"
        echo "  BLOCKED: refusing to write test data against a non-localhost MySQL host."
    elif ! docker_available; then
        check_fail "STAGING_IDEMPOTENCY_WRITE=true requires Docker"
    elif ! mysql_available 2>/dev/null; then
        check_fail "STAGING_IDEMPOTENCY_WRITE=true requires MySQL container ($MYSQL_CONTAINER) to be reachable"
    elif ! table_exists "big_market_01" "credit_award_task_000"; then
        check_fail "STAGING_IDEMPOTENCY_WRITE=true requires credit_award_task_000 to exist in big_market_01 — apply DDL first"
        echo "  Apply DDL: APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh"
    else
        # Register EXIT trap to clean up test row
        trap cleanup_test_row EXIT

        echo "  Writing test outbox row:"
        echo "    DB:            big_market_01"
        echo "    Table:         credit_award_task_000"
        echo "    user_id:       $B8_TEST_USER_ID"
        echo "    award_order_id: $B8_TEST_AWARD_ORDER_ID"
        echo "    credit_amount: $B8_TEST_CREDIT_AMOUNT"
        echo "  Test row will be deleted on EXIT (trap registered)."
        echo ""

        # Check 27: Insert test outbox row
        INSERT_OUT="$(mysql_exec "big_market_01" \
            "INSERT INTO credit_award_task_000 (user_id, award_order_id, credit_amount, state, retry_count, create_time, update_time)
             VALUES ('${B8_TEST_USER_ID}', '${B8_TEST_AWARD_ORDER_ID}', ${B8_TEST_CREDIT_AMOUNT}, 'pending', 0, NOW(), NOW());" \
            2>&1 || true)"
        if echo "$INSERT_OUT" | grep -iq "ERROR\|error"; then
            check_fail "Test outbox row INSERT failed: $INSERT_OUT"
        else
            check_pass "Test outbox row inserted into big_market_01.credit_award_task_000 (state=pending, retry_count=0)"
        fi

        # Check 28: Verify row readable with correct state
        ROW_STATE="$(mysql_exec "big_market_01" \
            "SELECT state FROM credit_award_task_000
             WHERE user_id='${B8_TEST_USER_ID}' AND award_order_id='${B8_TEST_AWARD_ORDER_ID}';" \
            2>/dev/null | tr -d '[:space:]' || true)"
        if [ "${ROW_STATE:-}" = "pending" ]; then
            check_pass "Test outbox row readable with state='pending' (state machine initial state correct)"
        else
            check_fail "Test outbox row state unexpected (expected='pending' got='${ROW_STATE:-NOT FOUND}')"
        fi

        # Check 29: Duplicate insert blocked by UNIQUE KEY (idempotency constraint on outbox table).
        # Approach: compare row count before and after the duplicate INSERT attempt.
        # A working UNIQUE KEY leaves count at 1; a missing constraint would leave count at 2.
        COUNT_BEFORE="$(mysql_exec "big_market_01" \
            "SELECT COUNT(*) FROM credit_award_task_000
             WHERE user_id='${B8_TEST_USER_ID}' AND award_order_id='${B8_TEST_AWARD_ORDER_ID}';" \
            2>/dev/null | tr -d '[:space:]' || echo "0")"
        # Attempt duplicate insert; suppress output — success means the row was silently
        # rejected (ON DUPLICATE KEY or the table engine silently ignored it), which is wrong.
        docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" --silent \
            -e "INSERT IGNORE INTO credit_award_task_000 (user_id, award_order_id, credit_amount, state, retry_count, create_time, update_time)
                VALUES ('${B8_TEST_USER_ID}', '${B8_TEST_AWARD_ORDER_ID}', ${B8_TEST_CREDIT_AMOUNT}, 'pending', 0, NOW(), NOW());" \
            "big_market_01" 2>/dev/null || true
        COUNT_AFTER="$(mysql_exec "big_market_01" \
            "SELECT COUNT(*) FROM credit_award_task_000
             WHERE user_id='${B8_TEST_USER_ID}' AND award_order_id='${B8_TEST_AWARD_ORDER_ID}';" \
            2>/dev/null | tr -d '[:space:]' || echo "0")"
        if [ "${COUNT_BEFORE:-0}" = "1" ] && [ "${COUNT_AFTER:-0}" = "1" ]; then
            check_pass "Duplicate outbox INSERT blocked by UNIQUE KEY (row count stable at 1 — uq_award_order_id constraint working)"
        else
            check_fail "Duplicate outbox INSERT was NOT blocked — row count changed from ${COUNT_BEFORE} to ${COUNT_AFTER}; UNIQUE KEY constraint may be missing"
        fi

        # Check 30: No user_credit_order row for test out_business_no
        # The test award_order_id must not already exist in user_credit_order — confirms no prior dispatch
        CREDIT_ORDER_COUNT=0
        for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
            CNT="$(mysql_exec "big_market_01" \
                "SELECT COUNT(*) FROM user_credit_order_${SUFFIX}
                 WHERE out_business_no='${B8_TEST_AWARD_ORDER_ID}';" \
                2>/dev/null || echo "0")"
            CREDIT_ORDER_COUNT=$((CREDIT_ORDER_COUNT + CNT))
        done
        if [ "$CREDIT_ORDER_COUNT" -eq 0 ]; then
            check_pass "No user_credit_order row exists for out_business_no='${B8_TEST_AWARD_ORDER_ID}' (clean — credit not yet dispatched; account-service idempotency substrate intact)"
        else
            check_fail "user_credit_order already has ${CREDIT_ORDER_COUNT} row(s) for out_business_no='${B8_TEST_AWARD_ORDER_ID}' — test identifier collision; choose a unique B8_TEST_AWARD_ORDER_ID"
        fi

        # Cleanup: called by EXIT trap; also call explicitly so it runs before summary
        cleanup_test_row
    fi
else
    check_skip "STAGING_IDEMPOTENCY_WRITE=false — write-mode checks not run"
    check_skip "(set STAGING_IDEMPOTENCY_WRITE=true with localhost MySQL to verify outbox UNIQUE KEY and account ledger state)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "4. Manual staging checklist (reference only — NOT auto-executed)"
# ═══════════════════════════════════════════════════════════════════════════════

cat <<'STAGING_STEPS'

  Complete these steps manually in staging after all static and write-mode checks pass.
  This script does NOT execute them.

  ── Pre-flight ────────────────────────────────────────────────────────────────
  Prerequisite scripts must all pass before proceeding:
    ./scripts/validate-award-credit-path.sh                        # B4: 8 checks
    ./scripts/validate-award-credit-outbox-readiness.sh            # B5: 8 checks
    ./scripts/validate-award-credit-outbox-b6.sh                   # B6: 17 checks
    ./scripts/validate-award-credit-outbox-integration.sh          # B7: static + Docker checks
    ./scripts/validate-award-credit-outbox-staging-idempotency.sh  # B8: this script

  ── Step 1: Apply DDL (if not yet done) ──────────────────────────────────────
    APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh
  Verify: credit_award_task_000..003 exist in both big_market_01 and big_market_02.

  ── Step 2: Enable flag=true and register XXL-Job handlers ───────────────────
    ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
      docker compose up -d --no-deps --force-recreate big-market-message-job-service

  In XXL-Job admin (http://localhost:9090):
    1. Confirm executor "big-market-message-job" is registered (port 9998).
    2. Add job: DispatchCreditAwardTaskJob_DB1 (scans big_market_01, all 4 table shards)
    3. Add job: DispatchCreditAwardTaskJob_DB2 (scans big_market_02, all 4 table shards)

  ── Step 3: Insert test outbox row ───────────────────────────────────────────
  Determine the physical shard for your test userId (userId=xiaofuge → big_market_01 in
  the default 2-DB setup; verify with dbRouter.doRouter before inserting).

    docker exec -i mysql mysql -uroot -p123456 big_market_01 -e "
      INSERT INTO credit_award_task_000
        (user_id, award_order_id, credit_amount, state, retry_count)
      VALUES
        ('xiaofuge', 'staging-award-b8-001', 10.00, 'pending', 0)
      ON DUPLICATE KEY UPDATE id = id;
    "

  ── Step 4: Trigger XXL-Job handler and verify state transition ───────────────
  Trigger DispatchCreditAwardTaskJob_DB1 from XXL-Job admin.

  Verify pending→dispatched:
    docker exec -i mysql mysql -uroot -p123456 big_market_01 -e "
      SELECT user_id, award_order_id, state, retry_count, update_time
        FROM credit_award_task_000
       WHERE award_order_id = 'staging-award-b8-001'\G
    "
  Expected: state = 'dispatched'

  ── Step 5: Verify account-service credit ledger ─────────────────────────────
    docker exec -i mysql mysql -uroot -p123456 big_market_01 -e "
      SELECT id, user_id, out_business_no, trade_amount, create_time
        FROM user_credit_order_000
       WHERE out_business_no = 'staging-award-b8-001'\G
    "
  Expected: exactly ONE row with out_business_no = 'staging-award-b8-001'

  ── Step 6: Idempotency re-trigger (no double-credit) ────────────────────────
  Re-trigger DispatchCreditAwardTaskJob_DB1 while the row is already 'dispatched'.
  Expected: NO additional user_credit_order row (account-service deduplicates on outBusinessNo
            via UNIQUE KEY uq_out_business_no; CreditRepository catches DuplicateKeyException).

  Verify count is still 1:
    docker exec -i mysql mysql -uroot -p123456 big_market_01 -e "
      SELECT COUNT(*) FROM user_credit_order_000
       WHERE out_business_no = 'staging-award-b8-001';
    "
  Expected: 1

  ── Step 7: Restore flag=false ───────────────────────────────────────────────
    ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
      docker compose up -d --no-deps --force-recreate big-market-message-job-service

  ── Rollback ─────────────────────────────────────────────────────────────────
  If any step above fails:
  1. Restore flag=false immediately (Step 7 above).
  2. Check logs: docker compose logs big-market-message-job-service | tail -100
  3. If tables are missing, re-apply DDL (Step 1).
  4. If XXL-Job handlers are missing, re-register in XXL-Job admin.
  5. If double-credit is suspected: query user_credit_order for out_business_no duplicates
     and escalate — do NOT proceed to production until idempotency is confirmed.

  ── Acceptance criteria ───────────────────────────────────────────────────────
  All of the following must pass before production promotion:
  [ ] B4/B5/B6/B7/B8 static scripts: 0 FAIL
  [ ] B8 write-mode (STAGING_IDEMPOTENCY_WRITE=true): 0 FAIL
  [ ] Step 4: credit_award_task row transitions pending → dispatched
  [ ] Step 5: exactly one user_credit_order row per award_order_id
  [ ] Step 6: re-trigger produces NO additional user_credit_order row
  [ ] Step 7: service healthy with flag restored to false

STAGING_STEPS

# ═══════════════════════════════════════════════════════════════════════════════
section "Summary"
# ═══════════════════════════════════════════════════════════════════════════════

echo ""
echo "PASS: $PASS  FAIL: $FAIL  SKIP: $SKIP"
echo ""
echo "Remaining risks before enabling outbox in production:"
echo "  1. Outbox tables (credit_award_task_000..003) must exist in both big_market_01 and big_market_02"
echo "  2. XXL-Job handlers DispatchCreditAwardTaskJob_DB1/_DB2 must be registered in XXL-Job admin"
echo "  3. State transition pending→dispatched must be confirmed in staging (Step 4)"
echo "  4. Idempotency re-trigger (Step 6) must pass — exactly 1 user_credit_order row per award_order_id"
echo "  5. account-service outBusinessNo dedup confirmed end-to-end (Step 5+6)"
echo "  6. RaffleActivityPartakeService quota decrement still deferred (high risk)"
echo "  7. MQ idempotency end-to-end verification still required before enabling remote write flags"
echo ""
echo "Quick reference:"
echo "  Dry-run (default):   ./scripts/validate-award-credit-outbox-staging-idempotency.sh"
echo "  Write-mode:          STAGING_IDEMPOTENCY_WRITE=true ./scripts/validate-award-credit-outbox-staging-idempotency.sh"
echo "  B7 (DDL/flag test):  APPLY_LOCAL_OUTBOX_DDL=true RUN_FLAG_TRUE_VALIDATION=true ./scripts/validate-award-credit-outbox-integration.sh"
echo "  Restore flag:        ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false docker compose up -d --no-deps --force-recreate big-market-message-job-service"
echo ""
echo "B8 is a staging idempotency validation scaffold — not production enablement."
echo "Do NOT set account.award-credit-outbox.enabled=true in production until"
echo "all checks pass and Steps 1-7 complete successfully in staging."
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo "RESULT: FAIL — $FAIL check(s) failed."
    exit 1
else
    echo "RESULT: PASS — $PASS checks passed ($SKIP skipped)."
    exit 0
fi
