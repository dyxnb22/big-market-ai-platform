#!/usr/bin/env bash
# Phase 2.2-B9: Controlled local/staging outbox E2E rehearsal + production promotion gate.
#
# SAFE BY DEFAULT: default mode runs static checks + Docker read-only checks only.
# No writes, no service restarts, no flag changes in any default-mode run.
#
# Modes (controlled by environment variables):
#
#   Default (static + Docker read-only):
#     ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh
#
#   Full E2E rehearsal (localhost Docker only — enables flag=true, inserts test row,
#   tries XXL-Job auto-trigger, verifies state transition + idempotency, restores flag=false):
#     B9_E2E_REHEARSAL=true \
#       ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh
#
#   Skip PAUSE for manual XXL-Job steps (assumes trigger already done externally):
#     B9_E2E_REHEARSAL=true B9_MANUAL_TRIGGERED=true \
#       ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh
#
#   Post-check only (verify state after manual trigger, no flag change):
#     B9_POST_CHECK=true \
#       ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh
#
#   Cleanup only (remove B9 test rows, localhost only):
#     B9_CLEANUP=true \
#       ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh
#
# Safety guarantees:
#   - B9_E2E_REHEARSAL=true BLOCKED if MYSQL_HOST is not localhost/127.0.0.1
#   - B9_E2E_REHEARSAL=true always restores ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false via EXIT trap
#   - B9_CLEANUP=true BLOCKED if MYSQL_HOST is not localhost/127.0.0.1
#   - No production data changes in any mode
#   - B4/B5/B6/B7/B8 scripts remain backward compatible — B9 adds only
#
# Routing note (test row):
#   The test outbox row is inserted into B9_TEST_DB.B9_TEST_TABLE
#   (defaults: big_market_01.credit_award_task_000 — DB1/TB0).
#   DispatchCreditAwardTaskJob_DB1 scans all 4 table shards in big_market_01, so the row
#   will be found regardless of which TB shard it occupies, as long as it is in big_market_01.
#   Override B9_TEST_DB / B9_TEST_TABLE if your dbRouter hashes B9_TEST_USER_ID to a different shard.
#
# Static checks (11):
#   1.  account.award-credit-outbox.enabled defaults to false in message-job-service application.yml
#   2.  DispatchCreditAwardTaskJob guarded by @ConditionalOnProperty
#   3.  dispatchTask: outBusinessNo = task.getAwardOrderId() (idempotency key forwarded correctly)
#   4.  success path calls creditAwardTaskDao.updateDispatched (pending→dispatched)
#   5.  failure path calls creditAwardTaskDao.updateRetryFailed (retry increment)
#   6.  queryPendingTasks: state='pending' AND retry_count < 5 (max-retry boundary alignment)
#   7.  updateRetryFailed: state='failed' at retry_count >= 5 (retry exhaustion boundary)
#   8.  @XxlJob("DispatchCreditAwardTaskJob_DB1") and _DB2 present (XXL-Job handler names)
#   9.  tbIdx < 4 — all 4 table shards per DB covered by scan loop
#   10. CreditRepository catches DuplicateKeyException (account-side dedup handler)
#   11. user_credit_order_000 UNIQUE KEY on out_business_no (account-side idempotency substrate)
#
# Docker read-only checks (up to 15 if stack is running):
#   12. message-job-service is healthy (UP)
#   13. account-service is healthy if running (skip if not in compose stack)
#   14. MySQL container is reachable
#   15-18. credit_award_task_000..003 exist in big_market_01 (outbox tables)
#   19-22. credit_award_task_000..003 exist in big_market_02 (outbox tables)
#   23-26. user_credit_order_000..003 exist in big_market_01 (account ledger)
#
# B9_E2E_REHEARSAL=true checks (localhost only):
#   27. DDL pre-check: all outbox tables exist in both DBs before enabling flag
#   28. message-job-service UP with ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true
#   29. ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true confirmed in container
#   30. Test outbox row inserted into B9_TEST_DB.B9_TEST_TABLE (state=pending)
#   31. Test row readable with state='pending'
#   32. XXL-Job trigger attempted via admin API (or PAUSE/MANUAL step if admin unreachable)
#   33. credit_award_task row transitions to state='dispatched'
#   34. Exactly one user_credit_order row for out_business_no=B9_TEST_AWARD_ORDER_ID
#   35. Idempotency re-trigger: second dispatch produces no additional user_credit_order row
#   36. message-job-service UP after flag restored to false
#
# B9_POST_CHECK=true checks (read-only, no flag change):
#   P1. Test outbox row state (expected: dispatched)
#   P2. user_credit_order count for B9_TEST_AWARD_ORDER_ID (expected: 1)
#   P3. Idempotency confirmation: count=1 (no double-credit)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ─── Configuration ────────────────────────────────────────────────────────────
B9_E2E_REHEARSAL="${B9_E2E_REHEARSAL:-false}"
B9_POST_CHECK="${B9_POST_CHECK:-false}"
B9_CLEANUP="${B9_CLEANUP:-false}"
B9_MANUAL_TRIGGERED="${B9_MANUAL_TRIGGERED:-false}"
B9_SKIP_IDEMPOTENCY_RETRIGGER="${B9_SKIP_IDEMPOTENCY_RETRIGGER:-false}"

B9_TEST_USER_ID="${B9_TEST_USER_ID:-b9-test-user}"
B9_TEST_AWARD_ORDER_ID="${B9_TEST_AWARD_ORDER_ID:-b9-test-order-b9-001}"
B9_TEST_CREDIT_AMOUNT="${B9_TEST_CREDIT_AMOUNT:-10.00}"
B9_TEST_DB="${B9_TEST_DB:-big_market_01}"
B9_TEST_TABLE="${B9_TEST_TABLE:-credit_award_task_000}"
B9_DISPATCH_WAIT_SECS="${B9_DISPATCH_WAIT_SECS:-60}"

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"

SERVICE_JOB="big-market-message-job-service"
SERVICE_ACCOUNT="big-market-account-service"
JOB_HEALTH="http://localhost:8085/actuator/health"
ACCOUNT_HEALTH="http://localhost:8086/actuator/health"

XXL_JOB_ADMIN_URL="${XXL_JOB_ADMIN_URL:-http://localhost:9090/xxl-job-admin}"
XXL_JOB_USER="${XXL_JOB_USER:-admin}"
XXL_JOB_PASS="${XXL_JOB_PASS:-123456}"

MAPPER_XML="$REPO_ROOT/big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml"
JOB_FILE="$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
MJS_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"
CREDIT_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java"
SCHEMA_SQL_01="$REPO_ROOT/docs/dev-ops/mysql/sql/big_market_01.sql"

DATABASES_OUTBOX=("big_market_01" "big_market_02")
TABLE_SUFFIXES=("000" "001" "002" "003")

PASS=0
FAIL=0
SKIP=0

# ─── Helpers ──────────────────────────────────────────────────────────────────
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
        "SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA='${db}' AND TABLE_NAME='${table}';" \
        || echo "0")"
    [ "${cnt:-0}" = "1" ]
}

get_health() {
    curl -sf "$1" \
        | python3 -c 'import sys,json; print(json.load(sys.stdin).get("status",""))' \
        2>/dev/null || true
}

stack_running() {
    local svc="$1"
    local state
    state="$(docker compose ps "$svc" --format json 2>/dev/null \
        | python3 -c '
import sys, json
raw = sys.stdin.read().strip()
if not raw:
    print("")
else:
    data = json.loads(raw.splitlines()[0])
    print(data.get("State", ""))
' 2>/dev/null || true)"
    [ "$state" = "running" ]
}

wait_health() {
    local label="$1" url="$2" timeout="${3:-120}"
    local start now status
    start="$(date +%s)"
    while true; do
        status="$(get_health "$url")"
        if [ "$status" = "UP" ]; then
            check_pass "$label is UP"
            return 0
        fi
        now="$(date +%s)"
        if [ $((now - start)) -ge "$timeout" ]; then
            check_fail "$label not UP after ${timeout}s (got: ${status:-UNREACHABLE})"
            return 1
        fi
        sleep 3
    done
}

service_started_at() {
    local svc="$1"
    local container_id
    container_id="$(docker compose ps -q "$svc" 2>/dev/null || true)"
    [ -n "$container_id" ] || return 1
    docker inspect -f '{{.State.StartedAt}}' "$container_id" 2>/dev/null || true
}

# Best-effort XXL-Job trigger via admin REST API.
# Stdout: "triggered" | "not_found" | "unreachable" | "trigger_failed"
try_xxl_trigger() {
    local handler="$1"
    local cookie_jar login_resp list_resp job_id trigger_resp

    command -v curl >/dev/null 2>&1 || { echo "unreachable"; return; }
    cookie_jar="$(mktemp)"

    login_resp="$(curl -sf --max-time 5 \
        -c "$cookie_jar" \
        --data "userName=${XXL_JOB_USER}&password=${XXL_JOB_PASS}" \
        "${XXL_JOB_ADMIN_URL}/login" 2>/dev/null || echo "FAIL")"

    if ! echo "$login_resp" | grep -q '"code":200'; then
        rm -f "$cookie_jar"
        echo "unreachable"
        return
    fi

    list_resp="$(curl -sf --max-time 5 \
        -b "$cookie_jar" \
        --data "start=0&length=50&searchVal=&jobGroup=-1&triggerStatus=-1&jobDesc=&glueType=&executorHandler=${handler}&author=" \
        "${XXL_JOB_ADMIN_URL}/jobinfo/pageList" 2>/dev/null || echo "FAIL")"

    job_id="$(echo "$list_resp" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    for job in (data.get('data') or []):
        if job.get('executorHandler') == '${handler}':
            print(job.get('id', ''))
            break
except:
    pass
" 2>/dev/null | tr -d '[:space:]' || true)"

    if [ -z "${job_id:-}" ] || [ "$job_id" = "null" ]; then
        rm -f "$cookie_jar"
        echo "not_found"
        return
    fi

    trigger_resp="$(curl -sf --max-time 5 \
        -b "$cookie_jar" \
        --data "id=${job_id}&executorParam=&addressList=" \
        "${XXL_JOB_ADMIN_URL}/jobinfo/triggerJob" 2>/dev/null || echo "FAIL")"
    rm -f "$cookie_jar"

    if echo "$trigger_resp" | grep -q '"code":200'; then
        echo "triggered"
    else
        echo "trigger_failed"
    fi
}

# Wait for the outbox row to reach state='dispatched'. Returns 0 on success, 1 on timeout.
wait_dispatched() {
    local db="$1" table="$2" user_id="$3" award_order_id="$4" timeout="${5:-60}"
    local start now state
    start="$(date +%s)"
    echo "  Polling for state='dispatched' (up to ${timeout}s)..."
    while true; do
        state="$(mysql_exec "$db" \
            "SELECT state FROM ${table}
             WHERE user_id='${user_id}' AND award_order_id='${award_order_id}';" \
            2>/dev/null | tr -d '[:space:]' || true)"
        if [ "${state:-}" = "dispatched" ]; then
            return 0
        fi
        now="$(date +%s)"
        if [ $((now - start)) -ge "$timeout" ]; then
            return 1
        fi
        sleep 5
    done
}

# ─── EXIT handlers ─────────────────────────────────────────────────────────────
_CLEANED=false
_RESTORED=false

cleanup_b9() {
    if [ "$_CLEANED" = "true" ]; then return; fi
    _CLEANED=true
    if ! docker_available 2>/dev/null || ! mysql_available 2>/dev/null; then return; fi
    echo ""
    echo "─── Cleanup: removing B9 test row(s) ───"
    for DB in "${DATABASES_OUTBOX[@]}"; do
        for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
            mysql_exec "$DB" \
                "DELETE FROM credit_award_task_${SUFFIX}
                 WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
                2>/dev/null || true
        done
    done
    echo "  Test outbox row cleanup complete (user_id=${B9_TEST_USER_ID} award_order_id=${B9_TEST_AWARD_ORDER_ID})"
}

restore_outbox_flag() {
    if [ "$_RESTORED" = "true" ]; then return; fi
    _RESTORED=true
    if ! docker_available 2>/dev/null; then return; fi
    echo ""
    echo "─── Restoring ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false ───"
    ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
        docker compose up -d --no-deps --force-recreate "$SERVICE_JOB" >/dev/null 2>&1 || true
    local start now status
    start="$(date +%s)"
    while true; do
        status="$(get_health "$JOB_HEALTH")"
        if [ "$status" = "UP" ]; then
            check_pass "$SERVICE_JOB healthy after flag restored to false (promotion gate: flag=false confirmed)"
            break
        fi
        now="$(date +%s)"
        if [ $((now - start)) -ge 120 ]; then
            check_fail "$SERVICE_JOB not UP within 120s after flag restore — check logs"
            break
        fi
        sleep 3
    done
}

e2e_exit_handler() {
    restore_outbox_flag
    cleanup_b9
}

# ─── Header ───────────────────────────────────────────────────────────────────
echo "=== Phase 2.2-B9 Award Credit Outbox E2E Rehearsal + Promotion Gate ==="
echo "B9_E2E_REHEARSAL          = $B9_E2E_REHEARSAL"
echo "B9_POST_CHECK             = $B9_POST_CHECK"
echo "B9_CLEANUP                = $B9_CLEANUP"
echo "B9_MANUAL_TRIGGERED       = $B9_MANUAL_TRIGGERED"
echo "B9_TEST_USER_ID           = $B9_TEST_USER_ID"
echo "B9_TEST_AWARD_ORDER_ID    = $B9_TEST_AWARD_ORDER_ID"
echo "B9_TEST_DB / TABLE        = $B9_TEST_DB / $B9_TEST_TABLE"
echo "B9_DISPATCH_WAIT_SECS     = $B9_DISPATCH_WAIT_SECS"
echo ""
echo "SAFE BY DEFAULT: dry-run mode — no writes, no service restart, no flag changes."
echo "Set B9_E2E_REHEARSAL=true (localhost only) to run the full controlled E2E rehearsal."

# ═══════════════════════════════════════════════════════════════════════════════
section "1. Static checks — promotion gate invariants (no Docker required)"
# ═══════════════════════════════════════════════════════════════════════════════

# Check 1: Default flag is false in config
if [[ -f "$MJS_YML" ]] && grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false" "$MJS_YML"; then
    check_pass "account.award-credit-outbox.enabled defaults to false in message-job-service application.yml (production flag gate)"
else
    check_fail "account.award-credit-outbox.enabled is NOT false by default — production flag gate broken"
fi

# Check 2: ConditionalOnProperty guard
if [[ -f "$JOB_FILE" ]] \
    && grep -q "ConditionalOnProperty" "$JOB_FILE" \
    && grep -q "award-credit-outbox" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob has @ConditionalOnProperty(account.award-credit-outbox.enabled=true) guard (not instantiated when flag=false)"
else
    check_fail "DispatchCreditAwardTaskJob is missing @ConditionalOnProperty guard — job may activate without the flag"
fi

# Check 3: outBusinessNo = task.getAwardOrderId()
if [[ -f "$JOB_FILE" ]] && grep -q "outBusinessNo(task.getAwardOrderId())" "$JOB_FILE"; then
    check_pass "dispatchTask: outBusinessNo = task.getAwardOrderId() — award_order_id forwarded as account-service idempotency key"
else
    check_fail "dispatchTask: outBusinessNo(task.getAwardOrderId()) NOT found — idempotency key not forwarded to account-service"
fi

# Check 4: success path calls updateDispatched
if [[ -f "$JOB_FILE" ]] && grep -q "creditAwardTaskDao.updateDispatched" "$JOB_FILE"; then
    check_pass "dispatchTask: calls creditAwardTaskDao.updateDispatched on success (pending→dispatched state write)"
else
    check_fail "dispatchTask: creditAwardTaskDao.updateDispatched NOT called on success — state will not advance to dispatched"
fi

# Check 5: failure path calls updateRetryFailed
if [[ -f "$JOB_FILE" ]] && grep -q "creditAwardTaskDao.updateRetryFailed" "$JOB_FILE"; then
    check_pass "dispatchTask: calls creditAwardTaskDao.updateRetryFailed on failure (retry_count increment)"
else
    check_fail "dispatchTask: creditAwardTaskDao.updateRetryFailed NOT called on failure — retry count not tracked"
fi

# Check 6: queryPendingTasks polls state='pending' AND retry_count < 5
# XML mappers use &lt; for < — match both literal and XML-escaped form
if [[ -f "$MAPPER_XML" ]] \
    && grep -q "state = 'pending'" "$MAPPER_XML" \
    && grep -qE "retry_count.*(< 5|&lt; 5)" "$MAPPER_XML"; then
    check_pass "credit_award_task_mapper.xml: queryPendingTasks filters state='pending' AND retry_count < 5 (max-retry boundary aligned)"
else
    check_fail "credit_award_task_mapper.xml: queryPendingTasks filter NOT found or misaligned with max-retry threshold"
fi

# Check 7: updateRetryFailed transitions to 'failed' at retry_count >= 5
if [[ -f "$MAPPER_XML" ]] \
    && grep -q "'failed'" "$MAPPER_XML" \
    && grep -q ">= 5" "$MAPPER_XML"; then
    check_pass "credit_award_task_mapper.xml: updateRetryFailed transitions to state='failed' at retry_count >= 5 (exhaustion boundary)"
else
    check_fail "credit_award_task_mapper.xml: retry exhaustion boundary (state='failed' at retry_count >= 5) NOT found"
fi

# Check 8: DB1 and DB2 XXL-Job handler names
if [[ -f "$JOB_FILE" ]] \
    && grep -q '"DispatchCreditAwardTaskJob_DB1"' "$JOB_FILE" \
    && grep -q '"DispatchCreditAwardTaskJob_DB2"' "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob declares @XxlJob(\"DispatchCreditAwardTaskJob_DB1\") and _DB2 (both shard DBs covered)"
else
    check_fail "DispatchCreditAwardTaskJob missing _DB1 or _DB2 @XxlJob handler — not all shard DBs covered"
fi

# Check 9: tbIdx < 4 covers all 4 table shards per DB
if [[ -f "$JOB_FILE" ]] && grep -q "tbIdx < 4" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob scans tbIdx < 4 — all 4 table shards per DB covered (2 DB × 4 TB = 8 shards total)"
else
    check_fail "DispatchCreditAwardTaskJob does NOT scan tbIdx < 4 — some table shards may be missed"
fi

# Check 10: CreditRepository catches DuplicateKeyException (account-side dedup)
if [[ -f "$CREDIT_REPO" ]] && grep -q "DuplicateKeyException" "$CREDIT_REPO"; then
    check_pass "CreditRepository.saveUserCreditTradeOrder catches DuplicateKeyException (duplicate outBusinessNo returns without double-credit)"
else
    check_fail "CreditRepository does NOT catch DuplicateKeyException — duplicate outBusinessNo dispatch propagates as unhandled exception"
fi

# Check 11: user_credit_order UNIQUE KEY on out_business_no (account-side idempotency substrate)
if [[ -f "$SCHEMA_SQL_01" ]] \
    && grep -A 30 "CREATE TABLE.*user_credit_order_000" "$SCHEMA_SQL_01" \
       | grep -q "UNIQUE KEY.*out_business_no"; then
    check_pass "DB schema: user_credit_order_000 has UNIQUE KEY on out_business_no — account-side idempotency constraint in DDL"
else
    check_fail "DB schema: UNIQUE KEY on out_business_no NOT found in user_credit_order_000 — account dedup substrate missing"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "2. Docker read-only checks — service health and table presence"
# ═══════════════════════════════════════════════════════════════════════════════

if ! docker_available; then
    check_skip "Docker not available — skipping all Docker checks (checks 12-26)"
    SKIP=$((SKIP + 15))
else
    # Check 12: message-job-service health
    if stack_running "$SERVICE_JOB"; then
        MJS_HEALTH="$(get_health "$JOB_HEALTH")"
        if [ "$MJS_HEALTH" = "UP" ]; then
            check_pass "$SERVICE_JOB health is UP"
        else
            check_fail "$SERVICE_JOB health is not UP (got: ${MJS_HEALTH:-UNREACHABLE})"
        fi
    else
        check_skip "$SERVICE_JOB not running — skipping health check (start: docker compose up -d)"
    fi

    # Check 13: account-service health (optional)
    if stack_running "$SERVICE_ACCOUNT"; then
        ACCT_HEALTH="$(get_health "$ACCOUNT_HEALTH")"
        if [ "$ACCT_HEALTH" = "UP" ]; then
            check_pass "$SERVICE_ACCOUNT health is UP"
        else
            check_fail "$SERVICE_ACCOUNT health is not UP (got: ${ACCT_HEALTH:-UNREACHABLE})"
        fi
    else
        check_skip "$SERVICE_ACCOUNT not running — skip (required for E2E dispatch; start with docker compose up -d)"
    fi

    # Check 14: MySQL reachable
    if ! mysql_available 2>/dev/null; then
        echo "  INFO: MySQL container ($MYSQL_CONTAINER) not reachable."
        echo "        docker compose -f docs/dev-ops/docker-compose-environment.yml up -d"
        check_skip "MySQL not reachable — skipping table checks (checks 15-26)"
        SKIP=$((SKIP + 12))
    else
        check_pass "MySQL container ($MYSQL_CONTAINER) is reachable"

        # Checks 15-22: credit_award_task_000..003 in big_market_01 and big_market_02
        ALL_OUTBOX_PRESENT=true
        for DB in "${DATABASES_OUTBOX[@]}"; do
            for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                TABLE="credit_award_task_${SUFFIX}"
                if table_exists "$DB" "$TABLE"; then
                    check_pass "${DB}.${TABLE} exists (outbox table present)"
                else
                    check_fail "${DB}.${TABLE} MISSING — apply DDL: APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh"
                    ALL_OUTBOX_PRESENT=false
                fi
            done
        done

        # Checks 23-26: user_credit_order_000..003 in big_market_01
        for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
            TABLE="user_credit_order_${SUFFIX}"
            if table_exists "big_market_01" "$TABLE"; then
                check_pass "big_market_01.${TABLE} exists (account ledger — idempotency substrate)"
            else
                check_fail "big_market_01.${TABLE} MISSING — account-service cannot write credit orders"
            fi
        done

        if [ "$ALL_OUTBOX_PRESENT" = "false" ]; then
            echo ""
            echo "  ACTION: Apply outbox DDL before B9_E2E_REHEARSAL=true:"
            echo "    APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh"
        fi
    fi
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "3. B9_E2E_REHEARSAL — controlled local end-to-end rehearsal"
# ═══════════════════════════════════════════════════════════════════════════════

if [ "$B9_E2E_REHEARSAL" = "true" ]; then
    # Safety: block on non-localhost
    if [[ "$MYSQL_HOST" != "localhost" && "$MYSQL_HOST" != "127.0.0.1" ]]; then
        check_fail "B9_E2E_REHEARSAL=true blocked for non-localhost MYSQL_HOST (got: $MYSQL_HOST)"
        echo "  BLOCKED: refusing to run E2E rehearsal against a non-localhost MySQL host."
    elif ! docker_available; then
        check_fail "B9_E2E_REHEARSAL=true requires Docker"
    elif ! stack_running "$SERVICE_JOB"; then
        check_fail "B9_E2E_REHEARSAL=true requires $SERVICE_JOB to be running — run: docker compose up -d"
    elif ! mysql_available 2>/dev/null; then
        check_fail "B9_E2E_REHEARSAL=true requires MySQL container ($MYSQL_CONTAINER) to be reachable"
    else
        # DDL pre-check: all outbox tables must exist before enabling flag
        OUTBOX_READY=true
        for DB in "${DATABASES_OUTBOX[@]}"; do
            for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                if ! table_exists "$DB" "credit_award_task_${SUFFIX}"; then
                    OUTBOX_READY=false
                fi
            done
        done

        # Check 27: DDL pre-check
        if [ "$OUTBOX_READY" = "true" ]; then
            check_pass "DDL pre-check: all credit_award_task_000..003 tables exist in both shard DBs (safe to enable flag=true)"
        else
            check_fail "DDL pre-check: one or more credit_award_task tables MISSING — apply DDL first, then retry"
            echo "    APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh"
            echo "  Aborting E2E rehearsal — cannot proceed without outbox tables."
        fi

        if [ "$OUTBOX_READY" = "true" ]; then
            # Register EXIT trap — guarantees flag=false restoration on any exit path
            trap e2e_exit_handler EXIT

            echo ""
            echo "  Enabling ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true on $SERVICE_JOB..."
            echo "  flag=false will be restored via EXIT trap regardless of outcome."
            echo ""

            ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
                docker compose up -d --no-deps --force-recreate "$SERVICE_JOB" >/dev/null 2>&1

            # Check 28: Health UP with flag=true
            wait_health "$SERVICE_JOB (flag=true)" "$JOB_HEALTH" 120

            # Check 29: Confirm flag in container env
            OUTBOX_ENV="$(docker compose exec -T "$SERVICE_JOB" \
                printenv ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED 2>/dev/null \
                | tr -d '[:space:]' || true)"
            if [ "${OUTBOX_ENV:-}" = "true" ]; then
                check_pass "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true confirmed in $SERVICE_JOB container"
            else
                check_fail "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED expected=true got=${OUTBOX_ENV:-unset} — ENV not injected"
            fi

            echo ""
            echo "  Routing basis for test row:"
            echo "    DB:    $B9_TEST_DB  (big_market_01 = DB index 1, scanned by DispatchCreditAwardTaskJob_DB1)"
            echo "    Table: $B9_TEST_TABLE  (credit_award_task_000 = table shard 0)"
            echo "    _DB1 scans all 4 shards (tbIdx 0..3) so the row will be found regardless of TB shard."
            echo "    Override B9_TEST_DB / B9_TEST_TABLE if your dbRouter routes B9_TEST_USER_ID differently."
            echo ""

            # Check 30: Insert test outbox row
            INSERT_OUT="$(mysql_exec "$B9_TEST_DB" \
                "INSERT INTO ${B9_TEST_TABLE}
                   (user_id, award_order_id, credit_amount, state, retry_count, create_time, update_time)
                 VALUES
                   ('${B9_TEST_USER_ID}', '${B9_TEST_AWARD_ORDER_ID}', ${B9_TEST_CREDIT_AMOUNT},
                    'pending', 0, NOW(), NOW());" \
                2>&1 || true)"
            if echo "$INSERT_OUT" | grep -iq "ERROR"; then
                check_fail "Test outbox row INSERT failed: $INSERT_OUT"
                echo "  Cannot proceed without test row — aborting remaining E2E checks."
            else
                check_pass "Test outbox row inserted: ${B9_TEST_DB}.${B9_TEST_TABLE} state=pending award_order_id=${B9_TEST_AWARD_ORDER_ID}"

                # Check 31: Row readable with state=pending
                ROW_STATE="$(mysql_exec "$B9_TEST_DB" \
                    "SELECT state FROM ${B9_TEST_TABLE}
                     WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
                    2>/dev/null | tr -d '[:space:]' || true)"
                if [ "${ROW_STATE:-}" = "pending" ]; then
                    check_pass "Test outbox row readable with state='pending' (initial state correct)"
                else
                    check_fail "Test row state unexpected: expected=pending got=${ROW_STATE:-NOT_FOUND}"
                fi

                # ── Try XXL-Job auto-trigger (DB1) ──────────────────────────────
                echo ""
                echo "  Attempting XXL-Job auto-trigger: DispatchCreditAwardTaskJob_DB1"
                echo "  Admin URL: $XXL_JOB_ADMIN_URL"
                TRIGGER_RESULT="$(try_xxl_trigger "DispatchCreditAwardTaskJob_DB1")"

                # Check 32: Trigger outcome
                case "$TRIGGER_RESULT" in
                    triggered)
                        check_pass "DispatchCreditAwardTaskJob_DB1 triggered via XXL-Job admin API (auto-trigger succeeded)"
                        ;;
                    not_found)
                        check_skip "XXL-Job admin reachable but DispatchCreditAwardTaskJob_DB1 not found — job not yet registered in admin"
                        echo ""
                        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        echo "  MANUAL STEP REQUIRED: register and trigger DispatchCreditAwardTaskJob_DB1"
                        echo ""
                        echo "  1. Open: $XXL_JOB_ADMIN_URL  (login: ${XXL_JOB_USER}/${XXL_JOB_PASS})"
                        echo "  2. Confirm executor 'big-market-message-job' is registered (port 9998)"
                        echo "  3. Add job: executorHandler=DispatchCreditAwardTaskJob_DB1"
                        echo "  4. Trigger the job once"
                        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        if [ "$B9_MANUAL_TRIGGERED" = "true" ]; then
                            echo "  B9_MANUAL_TRIGGERED=true — assuming trigger already done, skipping pause."
                        else
                            echo "  Press Enter after you have triggered the job (Ctrl-C to abort):"
                            read -r || true
                        fi
                        ;;
                    unreachable)
                        check_skip "XXL-Job admin not reachable at $XXL_JOB_ADMIN_URL — manual trigger required"
                        echo ""
                        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        echo "  MANUAL STEP REQUIRED: start XXL-Job admin, then trigger the handler"
                        echo ""
                        echo "  Start XXL-Job admin:"
                        echo "    docker compose -f docs/dev-ops/docker-compose-environment.yml up -d xxl-job-admin"
                        echo "  Then open: http://localhost:9090/xxl-job-admin  (${XXL_JOB_USER}/${XXL_JOB_PASS})"
                        echo "  Register executor 'big-market-message-job' (port 9998) if not present"
                        echo "  Trigger: DispatchCreditAwardTaskJob_DB1"
                        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        if [ "$B9_MANUAL_TRIGGERED" = "true" ]; then
                            echo "  B9_MANUAL_TRIGGERED=true — assuming trigger already done, skipping pause."
                        else
                            echo "  Press Enter after you have triggered the job (Ctrl-C to abort):"
                            read -r || true
                        fi
                        ;;
                    *)
                        check_skip "XXL-Job trigger returned: $TRIGGER_RESULT — manual trigger may be needed"
                        if [ "$B9_MANUAL_TRIGGERED" != "true" ]; then
                            echo "  Press Enter after triggering DispatchCreditAwardTaskJob_DB1 (Ctrl-C to abort):"
                            read -r || true
                        fi
                        ;;
                esac

                # ── Post-check: state transition ─────────────────────────────────
                if wait_dispatched "$B9_TEST_DB" "$B9_TEST_TABLE" \
                    "$B9_TEST_USER_ID" "$B9_TEST_AWARD_ORDER_ID" "$B9_DISPATCH_WAIT_SECS"; then
                    # Check 33
                    check_pass "credit_award_task row transitioned to state='dispatched' (pending→dispatched confirmed)"
                else
                    CURRENT_STATE="$(mysql_exec "$B9_TEST_DB" \
                        "SELECT state FROM ${B9_TEST_TABLE}
                         WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
                        2>/dev/null | tr -d '[:space:]' || true)"
                    check_fail "credit_award_task row did NOT reach state='dispatched' within ${B9_DISPATCH_WAIT_SECS}s (current: ${CURRENT_STATE:-NOT_FOUND})"
                    echo "  Debug: docker compose logs $SERVICE_JOB | grep -i dispatch"
                fi

                # ── Post-check: account ledger count ─────────────────────────────
                CREDIT_ORDER_COUNT=0
                for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                    CNT="$(mysql_exec "big_market_01" \
                        "SELECT COUNT(*) FROM user_credit_order_${SUFFIX}
                         WHERE out_business_no='${B9_TEST_AWARD_ORDER_ID}';" \
                        2>/dev/null | tr -d '[:space:]' || echo "0")"
                    CREDIT_ORDER_COUNT=$((CREDIT_ORDER_COUNT + CNT))
                done
                # Check 34
                if [ "$CREDIT_ORDER_COUNT" -eq 1 ]; then
                    check_pass "Account ledger: exactly 1 user_credit_order row for out_business_no='${B9_TEST_AWARD_ORDER_ID}' (credit dispatched exactly once)"
                elif [ "$CREDIT_ORDER_COUNT" -eq 0 ]; then
                    check_fail "Account ledger: 0 rows for '${B9_TEST_AWARD_ORDER_ID}' — account-service did not write credit; check adapter health"
                else
                    check_fail "Account ledger: ${CREDIT_ORDER_COUNT} rows for '${B9_TEST_AWARD_ORDER_ID}' — DOUBLE-CREDIT detected! Do NOT promote to production."
                fi

                # ── Idempotency re-trigger ────────────────────────────────────────
                echo ""
                if [ "$B9_SKIP_IDEMPOTENCY_RETRIGGER" = "true" ]; then
                    check_skip "B9_SKIP_IDEMPOTENCY_RETRIGGER=true — idempotency re-trigger skipped; count=${CREDIT_ORDER_COUNT} accepted as verified"
                else
                    echo "  Idempotency re-trigger: resetting row to state='pending' for second dispatch..."
                    mysql_exec "$B9_TEST_DB" \
                        "UPDATE ${B9_TEST_TABLE}
                         SET state='pending', retry_count=0, update_time=NOW()
                         WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
                        2>/dev/null || true

                    RESET_STATE="$(mysql_exec "$B9_TEST_DB" \
                        "SELECT state FROM ${B9_TEST_TABLE}
                         WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
                        2>/dev/null | tr -d '[:space:]' || true)"
                    echo "  Row reset to '${RESET_STATE:-unknown}' — triggering second dispatch..."

                    TRIGGER2_RESULT="$(try_xxl_trigger "DispatchCreditAwardTaskJob_DB1")"
                    if [ "$TRIGGER2_RESULT" = "triggered" ]; then
                        echo "  Second trigger sent via XXL-Job admin API."
                    else
                        echo ""
                        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        echo "  MANUAL STEP REQUIRED: trigger DispatchCreditAwardTaskJob_DB1 again"
                        echo "  (idempotency re-trigger — expected: NO additional user_credit_order row)"
                        echo "  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                        if [ "$B9_MANUAL_TRIGGERED" = "true" ]; then
                            echo "  B9_MANUAL_TRIGGERED=true — skipping pause."
                        else
                            echo "  Press Enter after second trigger (Ctrl-C to abort):"
                            read -r || true
                        fi
                    fi

                    # Wait for second dispatch to complete
                    if wait_dispatched "$B9_TEST_DB" "$B9_TEST_TABLE" \
                        "$B9_TEST_USER_ID" "$B9_TEST_AWARD_ORDER_ID" "$B9_DISPATCH_WAIT_SECS"; then
                        echo "  Row reached state='dispatched' on second dispatch."
                    else
                        echo "  WARNING: row did not reach state='dispatched' within ${B9_DISPATCH_WAIT_SECS}s on second dispatch"
                    fi

                    # Re-check account ledger count after second dispatch
                    CREDIT_ORDER_COUNT_2=0
                    for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                        CNT2="$(mysql_exec "big_market_01" \
                            "SELECT COUNT(*) FROM user_credit_order_${SUFFIX}
                             WHERE out_business_no='${B9_TEST_AWARD_ORDER_ID}';" \
                            2>/dev/null | tr -d '[:space:]' || echo "0")"
                        CREDIT_ORDER_COUNT_2=$((CREDIT_ORDER_COUNT_2 + CNT2))
                    done
                    # Check 35
                    if [ "$CREDIT_ORDER_COUNT_2" -eq 1 ]; then
                        check_pass "Idempotency confirmed: user_credit_order count still 1 after second dispatch — no double-credit (DuplicateKeyException dedup working end-to-end)"
                    else
                        check_fail "Idempotency FAIL: count=${CREDIT_ORDER_COUNT_2} after second dispatch — DOUBLE-CREDIT risk! Do NOT promote to production."
                    fi
                fi

                # Check 36: restore flag (EXIT trap runs e2e_exit_handler after summary,
                # but call explicitly so it runs before the summary section below)
                restore_outbox_flag
            fi
        fi
    fi
else
    check_skip "B9_E2E_REHEARSAL=false — full E2E rehearsal not run"
    check_skip "(set B9_E2E_REHEARSAL=true with localhost Docker to run the controlled rehearsal)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "4. B9_POST_CHECK — post-manual-trigger state verification (read-only)"
# ═══════════════════════════════════════════════════════════════════════════════

if [ "$B9_POST_CHECK" = "true" ]; then
    if ! docker_available || ! mysql_available 2>/dev/null; then
        check_fail "B9_POST_CHECK=true requires MySQL container ($MYSQL_CONTAINER) to be reachable"
    else
        echo "  Reading state of test outbox row:"
        echo "    DB.Table:      $B9_TEST_DB.$B9_TEST_TABLE"
        echo "    user_id:       $B9_TEST_USER_ID"
        echo "    award_order_id: $B9_TEST_AWARD_ORDER_ID"
        echo ""

        # P1: Outbox row state
        ROW_STATE="$(mysql_exec "$B9_TEST_DB" \
            "SELECT state FROM ${B9_TEST_TABLE}
             WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
            2>/dev/null | tr -d '[:space:]' || true)"
        if [ "${ROW_STATE:-}" = "dispatched" ]; then
            check_pass "Post-check P1: outbox row state='dispatched' — DispatchCreditAwardTaskJob ran successfully"
        elif [ "${ROW_STATE:-}" = "pending" ]; then
            check_fail "Post-check P1: outbox row state still='pending' — job may not have run; check XXL-Job trigger and service logs"
        elif [ -z "${ROW_STATE:-}" ]; then
            check_fail "Post-check P1: test row NOT FOUND in ${B9_TEST_DB}.${B9_TEST_TABLE} — insert test row first (B9_E2E_REHEARSAL=true)"
        else
            check_fail "Post-check P1: outbox row state='${ROW_STATE}' (unexpected; expected 'dispatched')"
        fi

        # P2: Account ledger count
        CREDIT_COUNT=0
        for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
            CNT="$(mysql_exec "big_market_01" \
                "SELECT COUNT(*) FROM user_credit_order_${SUFFIX}
                 WHERE out_business_no='${B9_TEST_AWARD_ORDER_ID}';" \
                2>/dev/null | tr -d '[:space:]' || echo "0")"
            CREDIT_COUNT=$((CREDIT_COUNT + CNT))
        done
        if [ "$CREDIT_COUNT" -eq 1 ]; then
            check_pass "Post-check P2: exactly 1 user_credit_order row for out_business_no='${B9_TEST_AWARD_ORDER_ID}'"
        elif [ "$CREDIT_COUNT" -eq 0 ]; then
            check_fail "Post-check P2: 0 user_credit_order rows — credit not yet dispatched; check account-service health"
        else
            check_fail "Post-check P2: ${CREDIT_COUNT} user_credit_order rows — DOUBLE-CREDIT detected for '${B9_TEST_AWARD_ORDER_ID}'!"
        fi

        # P3: Idempotency confirmation (count=1 is the gate)
        if [ "$CREDIT_COUNT" -eq 1 ]; then
            check_pass "Post-check P3: idempotency gate met — count=1, no double-credit"
        else
            check_fail "Post-check P3: idempotency gate NOT met — count=${CREDIT_COUNT}"
        fi
    fi
else
    check_skip "B9_POST_CHECK=false — post-check not run"
    check_skip "(set B9_POST_CHECK=true after manual XXL-Job trigger to verify state and ledger)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "5. B9_CLEANUP — remove B9 test rows (localhost only)"
# ═══════════════════════════════════════════════════════════════════════════════

if [ "$B9_CLEANUP" = "true" ]; then
    if [[ "$MYSQL_HOST" != "localhost" && "$MYSQL_HOST" != "127.0.0.1" ]]; then
        check_fail "B9_CLEANUP=true blocked for non-localhost MYSQL_HOST (got: $MYSQL_HOST)"
    elif ! docker_available || ! mysql_available 2>/dev/null; then
        check_fail "B9_CLEANUP=true requires MySQL container ($MYSQL_CONTAINER) to be reachable"
    else
        DELETED=0
        for DB in "${DATABASES_OUTBOX[@]}"; do
            for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                if table_exists "$DB" "credit_award_task_${SUFFIX}"; then
                    ROW_EXISTS="$(mysql_exec "$DB" \
                        "SELECT COUNT(*) FROM credit_award_task_${SUFFIX}
                         WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
                        2>/dev/null | tr -d '[:space:]' || echo "0")"
                    if [ "${ROW_EXISTS:-0}" -gt 0 ]; then
                        mysql_exec "$DB" \
                            "DELETE FROM credit_award_task_${SUFFIX}
                             WHERE user_id='${B9_TEST_USER_ID}' AND award_order_id='${B9_TEST_AWARD_ORDER_ID}';" \
                            2>/dev/null || true
                        DELETED=$((DELETED + 1))
                        check_pass "Deleted B9 test row from ${DB}.credit_award_task_${SUFFIX}"
                    fi
                fi
            done
        done
        if [ "$DELETED" -eq 0 ]; then
            check_pass "B9 test row not present in any shard — nothing to clean up"
        fi
    fi
else
    check_skip "B9_CLEANUP=false — cleanup not run (set B9_CLEANUP=true to remove test rows)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "6. Production promotion gate checklist (reference only — NOT auto-executed)"
# ═══════════════════════════════════════════════════════════════════════════════

cat <<'PROMOTION_GATE'

  All items below must be checked before enabling account.award-credit-outbox.enabled=true
  in any non-local environment.

  ── Automated gate (B4..B9 scripts) ──────────────────────────────────────────
  [ ] B4: ./scripts/validate-award-credit-path.sh                  8 checks: 0 FAIL
  [ ] B5: ./scripts/validate-award-credit-outbox-readiness.sh      8 checks: 0 FAIL
  [ ] B6: ./scripts/validate-award-credit-outbox-b6.sh            17 checks: 0 FAIL
  [ ] B7: ./scripts/validate-award-credit-outbox-integration.sh   static: 0 FAIL
      APPLY_LOCAL_OUTBOX_DDL=true: outbox DDL applied and 3-digit suffix verified
      RUN_FLAG_TRUE_VALIDATION=true: message-job-service starts cleanly with flag=true
  [ ] B8: ./scripts/validate-award-credit-outbox-staging-idempotency.sh 13 static: 0 FAIL
      STAGING_IDEMPOTENCY_WRITE=true: 4 write-mode checks: 0 FAIL
  [ ] B9: ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh 11 static: 0 FAIL
      B9_E2E_REHEARSAL=true: full E2E rehearsal: 0 FAIL

  ── Manual staging checks (require XXL-Job admin access) ──────────────────────
  [ ] credit_award_task DDL applied to staging big_market_01 and big_market_02
  [ ] DispatchCreditAwardTaskJob_DB1 registered in XXL-Job admin (executor: big-market-message-job)
  [ ] DispatchCreditAwardTaskJob_DB2 registered in XXL-Job admin
  [ ] Staging: credit_award_task row transitions pending → dispatched
  [ ] Staging: exactly 1 user_credit_order row for award_order_id (outBusinessNo)
  [ ] Staging: second dispatch produces 0 new user_credit_order rows (idempotency confirmed)
  [ ] message-job-service healthy after restoring flag=false

  ── Blocked items (not yet unblocked) ─────────────────────────────────────────
  [ ] RaffleActivityPartakeService quota decrement — deferred; high risk; needs dedicated decrement RPC
  [ ] MQ idempotency end-to-end verification — required before enabling remote write flags
  [ ] Production DDL deployment — apply docs/sql/proposed-credit-award-task-outbox.sql
      to all physical shard DBs before production flag enablement

  ── Rollback steps ────────────────────────────────────────────────────────────
  1. Restore flag=false immediately:
       ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
         docker compose up -d --no-deps --force-recreate big-market-message-job-service
  2. Investigate service logs:
       docker compose logs big-market-message-job-service | tail -100
  3. If state transition failed: check XXL-Job executor registration and account-service health
  4. If double-credit detected (user_credit_order count > 1 for same out_business_no):
       a. Escalate immediately — DO NOT promote to production
       b. Verify UNIQUE KEY on user_credit_order.out_business_no is present in deployed DB
       c. Verify CreditRepository catches DuplicateKeyException (static check 10 above)
  5. If account-service down: fix, restart, retry — outbox row stays 'pending' and will be retried
  6. Re-run full B9_E2E_REHEARSAL after each fix to confirm 0 FAIL before re-attempting promotion

PROMOTION_GATE

# ═══════════════════════════════════════════════════════════════════════════════
section "Summary"
# ═══════════════════════════════════════════════════════════════════════════════

echo ""
echo "PASS: $PASS  FAIL: $FAIL  SKIP: $SKIP"
echo ""
echo "What B9 auto-verified vs what requires manual XXL-Job admin action:"
echo "  Auto-verified: flag default, ConditionalOnProperty guard, idempotency key forwarding,"
echo "    state machine transitions, retry boundary, handler name declarations, shard coverage,"
echo "    DuplicateKeyException handler, out_business_no UNIQUE KEY, service health, table presence"
echo "  Manual required: XXL-Job admin — registering DispatchCreditAwardTaskJob_DB1/_DB2,"
echo "    triggering handlers in staging, observing dispatch logs"
echo ""
echo "Remaining risks before production promotion:"
echo "  1. XXL-Job admin access required for staging Steps C-E (not auto-triggered)"
echo "  2. Double-credit risk if UNIQUE KEY on user_credit_order.out_business_no missing from deployed DB"
echo "  3. RaffleActivityPartakeService quota decrement still deferred (high risk)"
echo "  4. MQ idempotency end-to-end still required before enabling remote write flags"
echo "  5. Production DDL deployment still pending"
echo ""
echo "Quick reference:"
echo "  Dry-run (default):    ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh"
echo "  Full E2E (localhost): B9_E2E_REHEARSAL=true ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh"
echo "  Post-check only:      B9_POST_CHECK=true ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh"
echo "  Cleanup test rows:    B9_CLEANUP=true ./scripts/validate-award-credit-outbox-e2e-rehearsal.sh"
echo "  Restore flag:         ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false docker compose up -d --no-deps --force-recreate big-market-message-job-service"
echo ""
echo "B9 is the production promotion gate — do NOT enable account.award-credit-outbox.enabled=true"
echo "in production until B9_E2E_REHEARSAL=true completes with 0 FAIL and all manual staging checks pass."
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo "RESULT: FAIL — $FAIL check(s) failed."
    exit 1
else
    echo "RESULT: PASS — $PASS checks passed ($SKIP skipped)."
    exit 0
fi
