#!/usr/bin/env bash
# validate-production-ddl.sh — Phase 2.2-B10
#
# Read-only DDL verification for all physical shard DBs.
# Verifies that credit_award_task outbox tables and idempotency UNIQUE KEYs
# exist across both logical DBs before enabling outbox feature flags.
#
# Targets checked:
#   big_market_01  credit_award_task_{000..003}    uq_award_order_id(user_id,award_order_id)
#   big_market_02  credit_award_task_{000..003}    uq_award_order_id(user_id,award_order_id)
#   big_market_01  user_credit_order_{000..003}    uq_out_business_no(out_business_no)
#   big_market_02  user_credit_order_{000..003}    uq_out_business_no(out_business_no)
#   big_market_01  user_behavior_rebate_order_{000..003}  uq_biz_id(biz_id)
#   big_market_02  user_behavior_rebate_order_{000..003}  uq_biz_id(biz_id)
#
# Usage:
#   ./scripts/validate-production-ddl.sh
#       Default: dry-run static checks only (no DB connection required)
#
#   CONNECT_DOCKER=true ./scripts/validate-production-ddl.sh
#       Also connect to local Docker MySQL and verify table/key existence
#
#   MYSQL_HOST=staging-db MYSQL_PORT=3306 MYSQL_USER=ro MYSQL_PASS=secret \
#       CONNECT_REMOTE=true ./scripts/validate-production-ddl.sh
#       Connect to a remote MySQL host (staging/prod) instead of Docker
#
# Safety:
#   - All queries are read-only (SELECT/SHOW/information_schema only)
#   - No DDL or DML is executed at any time
#   - No localhost guard needed — this script never writes
set -euo pipefail

CONNECT_DOCKER="${CONNECT_DOCKER:-false}"
CONNECT_REMOTE="${CONNECT_REMOTE:-false}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-big-market-mysql}"

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }

# ---------------------------------------------------------------------------
# Section 1 — Static checks (no DB required)
# ---------------------------------------------------------------------------
info "=== Section 1: Static checks (no DB) ==="

# S1: Outbox DDL file exists
if [[ -f "docs/sql/proposed-credit-award-task-outbox.sql" ]]; then
    ok "S1: docs/sql/proposed-credit-award-task-outbox.sql exists"
else
    fail "S1: docs/sql/proposed-credit-award-task-outbox.sql missing"
fi

# S2: Outbox DDL defines 4 shard tables (_000.._003)
if grep -q "credit_award_task_000" docs/sql/proposed-credit-award-task-outbox.sql \
    && grep -q "credit_award_task_003" docs/sql/proposed-credit-award-task-outbox.sql; then
    ok "S2: outbox DDL defines tables _000 and _003"
else
    fail "S2: outbox DDL missing _000 or _003"
fi

# S3: Outbox UNIQUE KEY uq_award_order_id present in DDL
if grep -q "uq_award_order_id" docs/sql/proposed-credit-award-task-outbox.sql; then
    ok "S3: outbox DDL contains uq_award_order_id UNIQUE KEY"
else
    fail "S3: outbox DDL missing uq_award_order_id"
fi

# S4: user_credit_order DDL defines uq_out_business_no
if grep -q "uq_out_business_no" docs/dev-ops/mysql/sql/big_market_01.sql; then
    ok "S4: big_market_01.sql contains uq_out_business_no on user_credit_order"
else
    fail "S4: big_market_01.sql missing uq_out_business_no"
fi

# S5: user_behavior_rebate_order DDL defines uq_biz_id
if grep -q "uq_biz_id" docs/dev-ops/mysql/sql/big_market_01.sql; then
    ok "S5: big_market_01.sql contains uq_biz_id on user_behavior_rebate_order"
else
    fail "S5: big_market_01.sql missing uq_biz_id"
fi

# S6: CreditRepository catches DuplicateKeyException
if grep -rq "DuplicateKeyException" big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java 2>/dev/null; then
    ok "S6: CreditRepository handles DuplicateKeyException"
else
    fail "S6: CreditRepository does not handle DuplicateKeyException"
fi

# S7: BehaviorRebateRepository catches DuplicateKeyException
if grep -rq "DuplicateKeyException" big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/BehaviorRebateRepository.java 2>/dev/null; then
    ok "S7: BehaviorRebateRepository handles DuplicateKeyException"
else
    fail "S7: BehaviorRebateRepository does not handle DuplicateKeyException"
fi

# S8: ActivityRepository catches DuplicateKeyException (user_raffle_order idempotency)
if grep -rq "DuplicateKeyException" big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java 2>/dev/null; then
    ok "S8: ActivityRepository handles DuplicateKeyException"
else
    fail "S8: ActivityRepository does not handle DuplicateKeyException"
fi

# S9: CreditAdjustSuccessConsumer handles INDEX_DUP
if grep -rq "INDEX_DUP" big-market-trigger/src/main/java/com/dyx/market/trigger/listener/CreditAdjustSuccessConsumer.java 2>/dev/null; then
    ok "S9: CreditAdjustSuccessConsumer handles INDEX_DUP"
else
    fail "S9: CreditAdjustSuccessConsumer does not handle INDEX_DUP"
fi

# S10: RebateMessageConsumer handles INDEX_DUP
if grep -rq "INDEX_DUP" big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java 2>/dev/null; then
    ok "S10: RebateMessageConsumer handles INDEX_DUP"
else
    fail "S10: RebateMessageConsumer does not handle INDEX_DUP"
fi

# S11: decrementQuota stub exists and returns UN_ERROR (not accidentally wired)
if grep -q "decrementQuota not yet implemented" big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java 2>/dev/null; then
    ok "S11: AccountQuotaServiceRPC.decrementQuota is stub-only (returns UN_ERROR)"
else
    fail "S11: AccountQuotaServiceRPC.decrementQuota stub guard missing"
fi

# S12: No caller wires decrementQuota in market-service or message-job-service
decrementQuota_callers=$(grep -r "decrementQuota" \
    big-market-market-service big-market-message-job-service \
    big-market-trigger big-market-infrastructure big-market-domain \
    2>/dev/null \
    | grep -v "IAccountQuotaService.java" \
    | grep -v "AccountQuotaServiceRPC.java" \
    | wc -l | tr -d ' ') || true
if [[ "$decrementQuota_callers" -eq 0 ]]; then
    ok "S12: decrementQuota not wired in any live service caller"
else
    fail "S12: decrementQuota has unexpected callers: $decrementQuota_callers references found"
fi

# ---------------------------------------------------------------------------
# Section 2 — Docker DB verification (CONNECT_DOCKER=true)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_DOCKER" != "true" && "$CONNECT_REMOTE" != "true" ]]; then
    info ""
    info "=== Section 2: DB verification skipped (set CONNECT_DOCKER=true or CONNECT_REMOTE=true) ==="
    info "    Re-run with CONNECT_DOCKER=true to verify actual tables and UNIQUE KEYs"
else
    info ""
    info "=== Section 2: DB verification ==="

    # Determine how to run mysql client
    run_mysql() {
        local db="$1"
        local query="$2"
        if [[ "$CONNECT_DOCKER" == "true" ]]; then
            docker exec "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                -e "$query" "$db" 2>/dev/null
        else
            mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" \
                -s -N -e "$query" "$db" 2>/dev/null
        fi
    }

    check_table_exists() {
        local db="$1"
        local table="$2"
        local count
        count=$(run_mysql "$db" \
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table';") || true
        echo "${count:-0}"
    }

    check_unique_key_exists() {
        local db="$1"
        local table="$2"
        local key_name="$3"
        local count
        count=$(run_mysql "$db" \
            "SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table'
               AND INDEX_NAME='$key_name' AND NON_UNIQUE=0;") || true
        echo "${count:-0}"
    }

    CHECK_NUM=13

    # credit_award_task shards: 4 per DB × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="credit_award_task_$shard"
            cnt=$(check_table_exists "$db" "$table")
            if [[ "$cnt" -gt 0 ]]; then
                ok "C${CHECK_NUM}: $db.$table exists"
            else
                fail "C${CHECK_NUM}: $db.$table NOT FOUND (apply docs/sql/proposed-credit-award-task-outbox.sql)"
            fi
            ((CHECK_NUM++))

            key_cnt=$(check_unique_key_exists "$db" "$table" "uq_award_order_id")
            if [[ "$key_cnt" -gt 0 ]]; then
                ok "C${CHECK_NUM}: $db.$table has UNIQUE KEY uq_award_order_id"
            else
                fail "C${CHECK_NUM}: $db.$table missing UNIQUE KEY uq_award_order_id"
            fi
            ((CHECK_NUM++))
        done
    done

    # user_credit_order shards: 4 per DB × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="user_credit_order_$shard"
            key_cnt=$(check_unique_key_exists "$db" "$table" "uq_out_business_no")
            if [[ "$key_cnt" -gt 0 ]]; then
                ok "C${CHECK_NUM}: $db.$table has UNIQUE KEY uq_out_business_no"
            else
                fail "C${CHECK_NUM}: $db.$table missing UNIQUE KEY uq_out_business_no"
            fi
            ((CHECK_NUM++))
        done
    done

    # user_behavior_rebate_order shards: 4 per DB × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="user_behavior_rebate_order_$shard"
            key_cnt=$(check_unique_key_exists "$db" "$table" "uq_biz_id")
            if [[ "$key_cnt" -gt 0 ]]; then
                ok "C${CHECK_NUM}: $db.$table has UNIQUE KEY uq_biz_id"
            else
                fail "C${CHECK_NUM}: $db.$table missing UNIQUE KEY uq_biz_id"
            fi
            ((CHECK_NUM++))
        done
    done
fi

# ---------------------------------------------------------------------------
# Result
# ---------------------------------------------------------------------------
echo ""
echo "=== validate-production-ddl.sh: $PASS passed, $FAIL failed ==="

if [[ "$FAIL" -gt 0 ]]; then
    echo "RESULT: FAIL — resolve failed checks before promoting outbox to staging"
    exit 1
else
    echo "RESULT: PASS"
    exit 0
fi
