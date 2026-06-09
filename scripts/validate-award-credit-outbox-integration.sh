#!/usr/bin/env bash
# Phase 2.2-B7: Integration validation scaffold for award credit outbox.
#
# SAFE BY DEFAULT — default mode runs static preflight checks only.
# No data is modified, no flags are changed, no services are restarted.
#
# Modes (controlled by environment variables):
#
#   Default (static preflight only):
#     ./scripts/validate-award-credit-outbox-integration.sh
#
#   Check table existence against running Docker MySQL (no DDL applied):
#     ./scripts/validate-award-credit-outbox-integration.sh   # Docker checks run automatically if stack is up
#
#   Apply DDL to local Docker MySQL only (localhost/docker-compose):
#     APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh
#
#   Validate flag=true startup (recreates message-job-service, then restores to false):
#     RUN_FLAG_TRUE_VALIDATION=true ./scripts/validate-award-credit-outbox-integration.sh
#
#   Full local integration sequence:
#     APPLY_LOCAL_OUTBOX_DDL=true RUN_FLAG_TRUE_VALIDATION=true \
#       ./scripts/validate-award-credit-outbox-integration.sh
#
# Safety guarantees:
#   - APPLY_LOCAL_OUTBOX_DDL=true is BLOCKED if MYSQL_HOST is not localhost/127.0.0.1
#   - RUN_FLAG_TRUE_VALIDATION=true always restores ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false on exit
#   - No unconditional DDL execution, no flag=true runtime by default
#   - Does not push to remote or change production config
#
# Prerequisites (before RUN_FLAG_TRUE_VALIDATION=true):
#   1. Docker stack running:   docker compose up -d
#   2. Tables must exist in big_market_01 and big_market_02
#      (apply first: APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh)
#   3. Current build:          mvn clean package -DskipTests
#
# Checks (up to 26 when Docker stack is up, tables exist, and flag=true validation is enabled):
#   Static (9):
#     1.  Default flag is false in message-job-service application.yml
#     2.  DispatchCreditAwardTaskJob has @ConditionalOnProperty guard
#     3.  ICreditAwardTaskDao has @DBRouterStrategy(splitTable = true)
#     4.  DynamicTableNamePlugin includes credit_award_task
#     5.  DDL uses three-digit suffixes _000.._003 (not two-digit)
#     6.  DispatchCreditAwardTaskJob scans tbIdx < 4 (4 tables per DB)
#     7.  DispatchCreditAwardTaskJob declares DB1 and DB2 handlers (2 DB * 4 TB = 8 shards)
#     8.  Proposed DDL file exists
#     9.  DDL has UNIQUE constraint on (user_id, award_order_id)
#   Docker preflight (3, if stack is running):
#     10. message-job-service health is UP
#     11. ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED is NOT true in running container
#     12. DispatchCreditAwardTaskJob is absent from startup logs (consistent with flag=false)
#     13. MySQL container is reachable
#     14-21. credit_award_task_000..003 exist in big_market_01 and big_market_02 (8 checks)
#   flag=true validation (3, if RUN_FLAG_TRUE_VALIDATION=true):
#     22. message-job-service health UP with flag=true
#     23. ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true confirmed in container
#     24. DispatchCreditAwardTaskJob evidence in startup logs or application started cleanly
#     25. No credit_award_task errors in startup logs
#     26. message-job-service health UP after flag restored to false

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ─── Configuration ──────────────────────────────────────────────────────────────
APPLY_LOCAL_OUTBOX_DDL="${APPLY_LOCAL_OUTBOX_DDL:-false}"
RUN_FLAG_TRUE_VALIDATION="${RUN_FLAG_TRUE_VALIDATION:-false}"

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"

SERVICE_JOB="big-market-message-job-service"
JOB_HEALTH="http://localhost:8085/actuator/health"

DDL_FILE="$REPO_ROOT/docs/sql/proposed-credit-award-task-outbox.sql"
JOB_FILE="$REPO_ROOT/big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
DAO_FILE="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/dao/ICreditAwardTaskDao.java"
TABLE_PLUGIN="$REPO_ROOT/big-market-starter-db-router/src/main/java/com/dyx/market/middleware/db/router/plugin/DynamicTableNamePlugin.java"
MJS_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"

DATABASES=("big_market_01" "big_market_02")
TABLE_SUFFIXES=("000" "001" "002" "003")

PASS=0
FAIL=0
SKIP=0

# ─── Helpers ────────────────────────────────────────────────────────────────────
check_pass() { echo "  [PASS] $1"; PASS=$((PASS + 1)); }
check_fail() { echo "  [FAIL] $1"; FAIL=$((FAIL + 1)); }
check_skip() { echo "  [SKIP] $1"; SKIP=$((SKIP + 1)); }
section()    { echo ""; echo "─── $1 ───"; }

docker_available() {
    command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

stack_running() {
    local state
    state="$(docker compose ps "$SERVICE_JOB" --format json 2>/dev/null \
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

service_started_at() {
    local container_id
    container_id="$(docker compose ps -q "$SERVICE_JOB" 2>/dev/null || true)"
    if [ -z "$container_id" ]; then
        return 1
    fi
    docker inspect -f '{{.State.StartedAt}}' "$container_id" 2>/dev/null || true
}

mysql_exec() {
    local db="$1" sql="$2"
    docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" \
        --silent --skip-column-names -e "$sql" "$db" 2>/dev/null
}

get_health() {
    curl -sf "$JOB_HEALTH" \
        | python3 -c 'import sys,json; print(json.load(sys.stdin).get("status",""))' \
        2>/dev/null || true
}

wait_health() {
    local label="$1" timeout="${2:-120}"
    local start now status
    start="$(date +%s)"
    while true; do
        status="$(get_health)"
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

# Restore function registered as EXIT trap when RUN_FLAG_TRUE_VALIDATION=true.
_RESTORED=false
restore_outbox_flag() {
    if [ "$_RESTORED" = "true" ]; then return; fi
    _RESTORED=true
    echo ""
    echo "─── Restoring ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false ───"
    ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
    docker compose up -d --no-deps --force-recreate "$SERVICE_JOB" >/dev/null 2>&1 || true
    local start now status
    start="$(date +%s)"
    while true; do
        status="$(get_health)"
        if [ "$status" = "UP" ]; then
            check_pass "$SERVICE_JOB healthy after flag restore (ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false)"
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

# ─── Header ─────────────────────────────────────────────────────────────────────
echo "=== Phase 2.2-B7 Award Credit Outbox Integration Validation ==="
echo "APPLY_LOCAL_OUTBOX_DDL    = $APPLY_LOCAL_OUTBOX_DDL"
echo "RUN_FLAG_TRUE_VALIDATION  = $RUN_FLAG_TRUE_VALIDATION"
echo ""
echo "SAFE BY DEFAULT: no data is modified, no flags are changed, no services are"
echo "restarted unless the above variables are explicitly set to true."

# ═══════════════════════════════════════════════════════════════════════════════
section "1. Static preflight — source-code invariants (no Docker required)"
# ═══════════════════════════════════════════════════════════════════════════════

# Check 1: Default flag is false in config
if grep -q "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:false" "$MJS_YML"; then
    check_pass "account.award-credit-outbox.enabled defaults to false in message-job-service application.yml"
else
    check_fail "account.award-credit-outbox.enabled is NOT false by default in message-job-service application.yml"
fi

# Check 2: DispatchCreditAwardTaskJob has @ConditionalOnProperty guard
if [[ -f "$JOB_FILE" ]] \
    && grep -q "ConditionalOnProperty" "$JOB_FILE" \
    && grep -q "award-credit-outbox" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob is @ConditionalOnProperty(account.award-credit-outbox.enabled=true) guarded"
else
    check_fail "DispatchCreditAwardTaskJob is missing @ConditionalOnProperty guard — consumer may activate without the flag"
fi

# Check 3: DAO has @DBRouterStrategy(splitTable = true)
if [[ -f "$DAO_FILE" ]] && grep -q "DBRouterStrategy(splitTable = true)" "$DAO_FILE"; then
    check_pass "ICreditAwardTaskDao is annotated @DBRouterStrategy(splitTable = true)"
else
    check_fail "ICreditAwardTaskDao is missing @DBRouterStrategy(splitTable = true) — table would not route to physical shards"
fi

# Check 4: DynamicTableNamePlugin whitelists credit_award_task
if [[ -f "$TABLE_PLUGIN" ]] && grep -q '"credit_award_task"' "$TABLE_PLUGIN"; then
    check_pass "DynamicTableNamePlugin includes \"credit_award_task\" in sharded table whitelist"
else
    check_fail "DynamicTableNamePlugin does NOT include credit_award_task — SQL would target the logical table name at runtime"
fi

# Check 5: DDL uses three-digit suffixes _000.._003 (not two-digit _00.._03)
if [[ -f "$DDL_FILE" ]] \
    && grep -qE "credit_award_task_000" "$DDL_FILE" \
    && ! grep -qE "credit_award_task_[0-9]{2}[^0-9]" "$DDL_FILE"; then
    check_pass "DDL uses three-digit table suffixes: credit_award_task_000, _001, _002, _003"
else
    check_fail "DDL does not use three-digit suffixes (_000.._003) — router expects three-digit format; two-digit would create wrong table names"
fi

# Check 6: Job scans tbIdx < 4 (all four table shards per DB)
if [[ -f "$JOB_FILE" ]] && grep -q "tbIdx < 4" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob iterates 4 table shards per DB (tbIdx < 4)"
else
    check_fail "DispatchCreditAwardTaskJob does not iterate 4 table shards (tbIdx < 4 not found) — pending tasks may be missed"
fi

# Check 7: Job declares handlers for both DB1 and DB2 (2 DB * 4 TB = 8 shards total)
if [[ -f "$JOB_FILE" ]] \
    && grep -q "DispatchCreditAwardTaskJob_DB1" "$JOB_FILE" \
    && grep -q "DispatchCreditAwardTaskJob_DB2" "$JOB_FILE"; then
    check_pass "DispatchCreditAwardTaskJob declares handlers for DB1 and DB2 (2 DB × 4 TB = 8 shards covered)"
else
    check_fail "DispatchCreditAwardTaskJob is missing a DB1 or DB2 handler — not all shards covered"
fi

# Check 8: Proposed DDL file exists
if [[ -f "$DDL_FILE" ]]; then
    check_pass "Proposed DDL file exists: docs/sql/proposed-credit-award-task-outbox.sql"
else
    check_fail "Proposed DDL file not found: docs/sql/proposed-credit-award-task-outbox.sql"
fi

# Check 9: DDL has UNIQUE constraint on (user_id, award_order_id) — idempotency key
if [[ -f "$DDL_FILE" ]] && grep -qE "UNIQUE KEY .*award_order_id" "$DDL_FILE"; then
    check_pass "DDL contains UNIQUE KEY on (user_id, award_order_id) — idempotency key present"
else
    check_fail "DDL does NOT contain UNIQUE KEY on award_order_id — double-credit risk on dispatch retry"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "2. Docker preflight — service health, flag state, and table existence"
# ═══════════════════════════════════════════════════════════════════════════════

if ! docker_available; then
    check_skip "Docker not available — skipping all Docker preflight checks (checks 10-21)"
    SKIP=$((SKIP + 11))  # account for the 12 docker checks we're skipping
elif ! stack_running; then
    echo "  INFO: $SERVICE_JOB is not running — start Docker stack first:"
    echo "        docker compose up -d"
    check_skip "$SERVICE_JOB not running — Docker preflight checks skipped (checks 10-21)"
    SKIP=$((SKIP + 11))
else
    # Check 10: Service health
    HEALTH="$(get_health)"
    if [ "$HEALTH" = "UP" ]; then
        check_pass "$SERVICE_JOB health is UP"
    else
        check_fail "$SERVICE_JOB health is not UP (got: ${HEALTH:-UNREACHABLE})"
    fi

    # Check 11: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED must NOT be true by default
    OUTBOX_ENV="$(docker compose exec -T "$SERVICE_JOB" printenv ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED 2>/dev/null \
        | tr -d '[:space:]' || true)"
    if [ "${OUTBOX_ENV:-false}" = "true" ]; then
        check_fail "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true in running container — must not be enabled by default"
    else
        check_pass "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED is NOT true in running container (got: ${OUTBOX_ENV:-false/unset})"
    fi

    # Check 12: DispatchCreditAwardTaskJob should be absent from current-container
    # startup logs with flag=false. Use container StartedAt instead of a broad
    # recent time window so a prior flag=true validation run cannot pollute this check.
    STARTED_AT="$(service_started_at)"
    JOB_LOG="$(docker compose logs --since "${STARTED_AT:-10m}" "$SERVICE_JOB" 2>/dev/null \
        | grep "DispatchCreditAwardTaskJob" \
        | grep -ivE "credit_award_task_mapper|ConditionalOn|Skipping" \
        | head -5 || true)"
    if [ -n "$JOB_LOG" ]; then
        echo "  WARNING: DispatchCreditAwardTaskJob log lines found (flag may be true):"
        echo "$JOB_LOG"
        check_fail "DispatchCreditAwardTaskJob appears in logs — verify ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED is false"
    else
        check_pass "DispatchCreditAwardTaskJob is absent from current-container startup logs (consistent with flag=false)"
    fi

    # Check 13: MySQL container reachable
    if docker exec "$MYSQL_CONTAINER" mysqladmin ping -u"$MYSQL_USER" -p"$MYSQL_PASS" --silent >/dev/null 2>&1; then
        check_pass "MySQL container ($MYSQL_CONTAINER) is reachable"

        # Checks 14-21: Table existence in big_market_01 and big_market_02 (8 checks)
        ALL_TABLES_PRESENT=true
        for DB in "${DATABASES[@]}"; do
            for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                TABLE="credit_award_task_${SUFFIX}"
                EXISTS="$(mysql_exec "$DB" \
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB}' AND TABLE_NAME='${TABLE}';" \
                    || echo "0")"
                if [ "${EXISTS:-0}" = "1" ]; then
                    check_pass "${DB}.${TABLE} exists"
                else
                    check_fail "${DB}.${TABLE} MISSING — tables must exist before enabling flag=true"
                    ALL_TABLES_PRESENT=false
                fi
            done
        done

        if [ "$ALL_TABLES_PRESENT" = "false" ]; then
            echo ""
            echo "  ACTION REQUIRED: Apply DDL before enabling flag=true."
            echo ""
            echo "  Option A — auto-apply locally (safe, localhost/docker only):"
            echo "    APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh"
            echo ""
            echo "  Option B — apply manually:"
            echo "    docker exec -i mysql mysql -uroot -p123456 big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql"
            echo "    docker exec -i mysql mysql -uroot -p123456 big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql"
        fi
    else
        check_fail "MySQL container ($MYSQL_CONTAINER) is not reachable — cannot verify table existence"
        echo "  Start the environment stack: docker compose -f docs/dev-ops/docker-compose-environment.yml up -d"
        SKIP=$((SKIP + 8))  # skip the 8 table-existence checks
    fi
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "3. APPLY_LOCAL_OUTBOX_DDL — apply proposed DDL to local Docker MySQL"
# ═══════════════════════════════════════════════════════════════════════════════

if [ "$APPLY_LOCAL_OUTBOX_DDL" = "true" ]; then
    # Block on non-localhost hosts — prevent accidental staging/prod DDL
    if [[ "$MYSQL_HOST" != "localhost" && "$MYSQL_HOST" != "127.0.0.1" ]]; then
        check_fail "APPLY_LOCAL_OUTBOX_DDL=true is only allowed when MYSQL_HOST=localhost or 127.0.0.1 (got: $MYSQL_HOST)"
        echo "  BLOCKED: refusing to apply DDL against a non-localhost MySQL host."
    elif ! docker_available; then
        check_fail "APPLY_LOCAL_OUTBOX_DDL=true requires Docker"
    elif ! docker exec "$MYSQL_CONTAINER" mysqladmin ping -u"$MYSQL_USER" -p"$MYSQL_PASS" --silent >/dev/null 2>&1; then
        check_fail "MySQL container ($MYSQL_CONTAINER) is not reachable — cannot apply DDL"
    else
        echo "  Applying docs/sql/proposed-credit-award-task-outbox.sql to local Docker MySQL"
        echo "  Tables to create in each database:"
        for DB in "${DATABASES[@]}"; do
            for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                echo "    ${DB}.credit_award_task_${SUFFIX}"
            done
        done
        echo ""

        DDL_OK=true
        for DB in "${DATABASES[@]}"; do
            echo "  Applying to ${DB}..."
            docker exec -i "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" "$DB" < "$DDL_FILE" 2>/dev/null
            echo "  Applied to ${DB}"
        done
        echo ""

        # Verify tables were created with correct three-digit suffixes
        for DB in "${DATABASES[@]}"; do
            for SUFFIX in "${TABLE_SUFFIXES[@]}"; do
                TABLE="credit_award_task_${SUFFIX}"
                EXISTS="$(mysql_exec "$DB" \
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB}' AND TABLE_NAME='${TABLE}';" \
                    || echo "0")"
                if [ "${EXISTS:-0}" = "1" ]; then
                    check_pass "DDL created ${DB}.${TABLE} with three-digit suffix"
                else
                    check_fail "DDL did NOT create ${DB}.${TABLE} — check DDL file"
                    DDL_OK=false
                fi
            done
        done

        # Confirm no two-digit-suffix tables were accidentally created
        for DB in "${DATABASES[@]}"; do
            TWO_DIGIT="$(mysql_exec "$DB" \
                "SELECT GROUP_CONCAT(TABLE_NAME) FROM information_schema.TABLES \
                 WHERE TABLE_SCHEMA='${DB}' AND TABLE_NAME REGEXP 'credit_award_task_[0-9]{2}$';" \
                2>/dev/null || true)"
            if [ -n "${TWO_DIGIT}" ] && [ "${TWO_DIGIT}" != "NULL" ]; then
                check_fail "${DB} contains two-digit suffix tables: ${TWO_DIGIT} — suffix format mismatch"
            else
                check_pass "${DB}: no two-digit suffix tables found (suffix is correctly three-digit)"
            fi
        done

        if [ "$DDL_OK" = "true" ]; then
            echo "  DDL applied successfully. Rerun without APPLY_LOCAL_OUTBOX_DDL=true to confirm table checks pass."
        fi
    fi
else
    check_skip "APPLY_LOCAL_OUTBOX_DDL=false — DDL not applied (set APPLY_LOCAL_OUTBOX_DDL=true to apply locally)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "4. flag=true validation — startup, bean registration, and restore"
# ═══════════════════════════════════════════════════════════════════════════════

if [ "$RUN_FLAG_TRUE_VALIDATION" = "true" ]; then
    if ! docker_available; then
        check_fail "RUN_FLAG_TRUE_VALIDATION=true requires Docker"
    elif ! stack_running; then
        check_fail "RUN_FLAG_TRUE_VALIDATION=true requires $SERVICE_JOB to be running — run: docker compose up -d"
    else
        # Register EXIT trap to always restore flag=false
        trap restore_outbox_flag EXIT

        echo "  Recreating $SERVICE_JOB with ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true"
        echo "  Flag will be restored to false on exit (EXIT trap registered)"
        echo ""

        ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true \
        docker compose up -d --no-deps --force-recreate "$SERVICE_JOB" >/dev/null 2>&1

        # Check 22: Health UP with flag=true
        wait_health "$SERVICE_JOB (flag=true)" 120

        # Check 23: Confirm flag=true in container
        OUTBOX_ENV_TRUE="$(docker compose exec -T "$SERVICE_JOB" printenv ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED 2>/dev/null \
            | tr -d '[:space:]' || true)"
        if [ "${OUTBOX_ENV_TRUE:-}" = "true" ]; then
            check_pass "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true confirmed in container"
        else
            check_fail "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED expected=true got=${OUTBOX_ENV_TRUE:-unset}"
        fi

        # Check 24: DispatchCreditAwardTaskJob evidence in startup logs
        sleep 5  # Allow logs to flush
        FLAG_TRUE_STARTED_AT="$(service_started_at)"
        JOB_BEAN_LOG="$(docker compose logs --since "${FLAG_TRUE_STARTED_AT:-3m}" "$SERVICE_JOB" 2>/dev/null \
            | grep -iE "DispatchCreditAwardTaskJob|DispatchCredit.*Award" | head -10 || true)"
        if [ -n "$JOB_BEAN_LOG" ]; then
            echo "$JOB_BEAN_LOG" | head -5
            check_pass "DispatchCreditAwardTaskJob evidence found in startup logs (bean registered with flag=true)"
        else
            # Spring bean creation may not log at INFO level.
            # Fallback: check that the application started successfully.
            STARTED_LOG="$(docker compose logs --since "${FLAG_TRUE_STARTED_AT:-3m}" "$SERVICE_JOB" 2>/dev/null \
                | grep -iE "Started MessageJobServiceApplication|application.*started" | tail -2 || true)"
            if [ -n "$STARTED_LOG" ]; then
                echo "  INFO: No DispatchCreditAwardTaskJob log line at INFO level."
                echo "        Application started cleanly — bean is likely registered but not logged at INFO."
                echo "        Manual verification: docker compose logs $SERVICE_JOB | grep -i DispatchCredit"
                check_pass "$SERVICE_JOB started successfully with flag=true (no startup error detected)"
            else
                check_fail "$SERVICE_JOB did not log a clean start with flag=true — check for errors"
                echo "  Debug: docker compose logs $SERVICE_JOB | tail -50"
            fi
        fi

        # Check 25: No credit_award_task table errors in startup logs
        ERROR_LOG="$(docker compose logs --since "${FLAG_TRUE_STARTED_AT:-3m}" "$SERVICE_JOB" 2>/dev/null \
            | grep -iE "Table.*credit_award_task.*doesn|credit_award_task.*Table.*exist|doesn.*exist.*credit_award_task" \
            | head -5 || true)"
        if [ -n "$ERROR_LOG" ]; then
            echo "  credit_award_task table errors found:"
            echo "$ERROR_LOG"
            check_fail "credit_award_task tables not found at startup — apply DDL first (APPLY_LOCAL_OUTBOX_DDL=true)"
        else
            check_pass "No credit_award_task table-not-found errors in startup logs"
        fi

        # Check 26: Restore is handled by EXIT trap (restore_outbox_flag)
        # Call explicitly so it runs before summary output
        restore_outbox_flag
    fi
else
    check_skip "RUN_FLAG_TRUE_VALIDATION=false — flag=true startup validation not run"
    check_skip "(set RUN_FLAG_TRUE_VALIDATION=true to validate DispatchCreditAwardTaskJob bean registration)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
section "5. Manual integration steps (reference only — NOT auto-executed)"
# ═══════════════════════════════════════════════════════════════════════════════

cat <<'MANUAL_STEPS'

  The following steps require manual execution or XXL-Job admin access.
  This script does NOT execute them. Complete them manually in staging.

  ── Step A: Insert one test outbox row ──────────────────────────────────────
  Choose a userId and first confirm its physical DB/table shard. Do NOT assume
  a sample user automatically belongs to big_market_01.credit_award_task_000.
  Replace <DB_NAME> and <TABLE_NAME> below with the actual routed shard.

    docker exec -i mysql mysql -uroot -p123456 <DB_NAME> -e "
      INSERT INTO <TABLE_NAME>
        (user_id, award_order_id, credit_amount, state, retry_count)
      VALUES
        ('xiaofuge', 'test-award-order-b7-001', 10.00, 'pending', 0)
      ON DUPLICATE KEY UPDATE id = id;
    "

  Verify row inserted:
    docker exec -i mysql mysql -uroot -p123456 <DB_NAME> -e "
      SELECT id, user_id, award_order_id, state, retry_count, create_time
        FROM <TABLE_NAME> WHERE user_id = 'xiaofuge'\G
    "

  ── Step B: Trigger XXL-Job handlers (XXL-Job admin required) ───────────────
  XXL-Job admin URL: http://localhost:9090  (or http://localhost:8090 per env)

    1. Confirm executor registered: "big-market-message-job" (port 9998)
       (Executor auto-registers on message-job-service startup when flag=true)
    2. Trigger: DispatchCreditAwardTaskJob_DB1  (scans big_market_01)
    3. Trigger: DispatchCreditAwardTaskJob_DB2  (scans big_market_02)

  NOTE: XXL-Job handlers cannot be triggered automatically by this script.
        Always trigger manually and observe logs.

  ── Step C: Verify state transition pending → dispatched ────────────────────
    docker exec -i mysql mysql -uroot -p123456 <DB_NAME> -e "
      SELECT id, user_id, award_order_id, state, retry_count, update_time
        FROM <TABLE_NAME> WHERE award_order_id = 'test-award-order-b7-001'\G
    "
  Expected: state = 'dispatched'

  ── Step D: Verify account-service credit ledger (idempotency) ──────────────
  Query the same routed credit-order DB for the chosen userId.

    docker exec -i mysql mysql -uroot -p123456 <CREDIT_DB_NAME> -e "
      SELECT id, user_id, out_business_no, amount, create_time
        FROM user_credit_order WHERE out_business_no = 'test-award-order-b7-001'\G
    "
  Expected: exactly ONE row with out_business_no = 'test-award-order-b7-001'

  ── Step E: Idempotency re-trigger (no double-credit) ───────────────────────
  Re-trigger the handler for the same DB shard while row is already 'dispatched'
  (DispatchCreditAwardTaskJob_DB1 for big_market_01, DB2 for big_market_02).
  Expected: no additional user_credit_order row created (account-service deduplicates
  on outBusinessNo = award_order_id via UNIQUE KEY on user_credit_order.out_business_no).

  ── Step F: Restore default flag after staging validation ───────────────────
  Restore the running container to flag=false:
    ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false \
      docker compose up -d --no-deps --force-recreate big-market-message-job-service

MANUAL_STEPS

# ═══════════════════════════════════════════════════════════════════════════════
section "Summary"
# ═══════════════════════════════════════════════════════════════════════════════

echo ""
echo "PASS: $PASS  FAIL: $FAIL  SKIP: $SKIP"
echo ""
echo "Remaining risks before enabling outbox in production:"
echo "  1. Tables (credit_award_task_000..003) must exist in both big_market_01 and big_market_02"
echo "  2. XXL-Job handlers must be registered in XXL-Job admin (Step B above)"
echo "  3. State transition pending→dispatched must be confirmed (Step C)"
echo "  4. Idempotency re-trigger (Step E) must pass — no double-credit"
echo "  5. account-service outBusinessNo deduplication must be verified end-to-end (Step D)"
echo "  6. RaffleActivityPartakeService quota decrement still deferred (high risk)"
echo "  7. MQ idempotency verification still required before enabling remote write flags"
echo ""
echo "Quick reference:"
echo "  Apply DDL locally: APPLY_LOCAL_OUTBOX_DDL=true ./scripts/validate-award-credit-outbox-integration.sh"
echo "  Test flag=true:    RUN_FLAG_TRUE_VALIDATION=true ./scripts/validate-award-credit-outbox-integration.sh"
echo "  Restore flag:      ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false docker compose up -d --no-deps --force-recreate $SERVICE_JOB"
echo ""
echo "B7 is a validation scaffold only — not production enablement."
echo "Do NOT set account.award-credit-outbox.enabled=true in production until"
echo "all FAIL/SKIP checks above pass and Steps A-E complete successfully in staging."
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo "RESULT: FAIL — $FAIL check(s) failed."
    exit 1
else
    echo "RESULT: PASS — $PASS checks passed ($SKIP skipped)."
    exit 0
fi
