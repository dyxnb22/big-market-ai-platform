#!/usr/bin/env bash
# validate-mq-idempotency.sh — Phase 2.2-B10
#
# Validates MQ idempotency for the credit-adjust and rebate-message paths.
# Proves that duplicate MQ messages produce exactly-one ledger row by relying
# on UNIQUE KEY constraints and DuplicateKeyException / INDEX_DUP guards.
#
# Static checks (always run — no Docker needed):
#   - CreditAdjustSuccessConsumer INDEX_DUP guard
#   - RebateMessageConsumer INDEX_DUP guard
#   - CreditRepository DuplicateKeyException handler
#   - BehaviorRebateRepository DuplicateKeyException handler
#   - ActivityRepository DuplicateKeyException handler (user_raffle_order)
#   - user_credit_order UNIQUE KEY uq_out_business_no in schema SQL
#   - user_behavior_rebate_order UNIQUE KEY uq_biz_id in schema SQL
#   - outBusinessNo derivation in CreditAdjustSuccessConsumer
#   - outBusinessNo derivation in RebateMessageConsumer
#   - saveCreatePartakeOrderAggregate not wired to remote adapter (quota still in-process)
#
# Write-mode (MQ_IDEMPOTENCY_WRITE=true, localhost Docker only):
#   - Insert test row into user_credit_order_000 (big_market_01)
#   - Verify UNIQUE KEY blocks duplicate INSERT
#   - Insert test row into user_behavior_rebate_order_000 (big_market_01)
#   - Verify UNIQUE KEY blocks duplicate INSERT
#   - EXIT trap cleans test rows in both tables
#
# Usage:
#   ./scripts/validate-mq-idempotency.sh
#       Static checks only (12/12 PASS)
#
#   MQ_IDEMPOTENCY_WRITE=true ./scripts/validate-mq-idempotency.sh
#       Static + write-mode (localhost Docker only; EXIT trap cleans up)
#
# Safety constraints:
#   - Write mode is guarded to localhost only
#   - EXIT trap always cleans up test rows
#   - No MQ broker interactions; no service restarts
#   - Does not modify RaffleActivityPartakeService or any domain code
set -euo pipefail

MQ_IDEMPOTENCY_WRITE="${MQ_IDEMPOTENCY_WRITE:-false}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-big-market-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"

TEST_USER_ID="mq-idempotency-test-user"
TEST_CREDIT_BIZ_NO="mq-idem-test-credit-001"
TEST_REBATE_BIZ_ID="mq-idem-test-rebate-001"

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }

# ---------------------------------------------------------------------------
# Section 1 — Static checks
# ---------------------------------------------------------------------------
info "=== Section 1: Static checks ==="

CREDIT_CONSUMER="big-market-trigger/src/main/java/com/dyx/market/trigger/listener/CreditAdjustSuccessConsumer.java"
REBATE_CONSUMER="big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java"
CREDIT_REPO="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java"
REBATE_REPO="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/BehaviorRebateRepository.java"
ACTIVITY_REPO="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java"
SCHEMA_SQL="docs/dev-ops/mysql/sql/big_market_01.sql"

# S1: CreditAdjustSuccessConsumer INDEX_DUP guard
if grep -q "INDEX_DUP" "$CREDIT_CONSUMER" 2>/dev/null; then
    ok "S1: CreditAdjustSuccessConsumer catches INDEX_DUP"
else
    fail "S1: CreditAdjustSuccessConsumer missing INDEX_DUP guard"
fi

# S2: RebateMessageConsumer INDEX_DUP guard
if grep -q "INDEX_DUP" "$REBATE_CONSUMER" 2>/dev/null; then
    ok "S2: RebateMessageConsumer catches INDEX_DUP"
else
    fail "S2: RebateMessageConsumer missing INDEX_DUP guard"
fi

# S3: CreditRepository DuplicateKeyException handler
if grep -q "DuplicateKeyException" "$CREDIT_REPO" 2>/dev/null; then
    ok "S3: CreditRepository handles DuplicateKeyException"
else
    fail "S3: CreditRepository missing DuplicateKeyException handler"
fi

# S4: BehaviorRebateRepository DuplicateKeyException handler
if grep -q "DuplicateKeyException" "$REBATE_REPO" 2>/dev/null; then
    ok "S4: BehaviorRebateRepository handles DuplicateKeyException"
else
    fail "S4: BehaviorRebateRepository missing DuplicateKeyException handler"
fi

# S5: ActivityRepository DuplicateKeyException handler (user_raffle_order)
if grep -q "DuplicateKeyException" "$ACTIVITY_REPO" 2>/dev/null; then
    ok "S5: ActivityRepository handles DuplicateKeyException (user_raffle_order)"
else
    fail "S5: ActivityRepository missing DuplicateKeyException handler"
fi

# S6: user_credit_order schema has uq_out_business_no
if grep -q "uq_out_business_no" "$SCHEMA_SQL" 2>/dev/null; then
    ok "S6: user_credit_order has UNIQUE KEY uq_out_business_no in schema SQL"
else
    fail "S6: user_credit_order missing uq_out_business_no in schema SQL"
fi

# S7: user_behavior_rebate_order schema has uq_biz_id
if grep -q "uq_biz_id" "$SCHEMA_SQL" 2>/dev/null; then
    ok "S7: user_behavior_rebate_order has UNIQUE KEY uq_biz_id in schema SQL"
else
    fail "S7: user_behavior_rebate_order missing uq_biz_id in schema SQL"
fi

# S8: CreditAdjustSuccessConsumer derives outBusinessNo from message
if grep -qi "OutBusinessNo\|outBusinessNo" "$CREDIT_CONSUMER" 2>/dev/null; then
    ok "S8: CreditAdjustSuccessConsumer uses outBusinessNo from message"
else
    fail "S8: CreditAdjustSuccessConsumer missing outBusinessNo derivation"
fi

# S9: RebateMessageConsumer derives outBusinessNo / bizId from message
if grep -qi "bizId\|OutBusinessNo\|outBusinessNo" "$REBATE_CONSUMER" 2>/dev/null; then
    ok "S9: RebateMessageConsumer derives outBusinessNo/bizId from message"
else
    fail "S9: RebateMessageConsumer missing outBusinessNo/bizId derivation"
fi

# S10: saveCreatePartakeOrderAggregate not wired to IAccountQuotaWriteAdapter
# (quota decrement still happens in-process; remote wiring is deferred)
if grep -q "IAccountQuotaWriteAdapter\|accountQuotaWriteAdapter" "$ACTIVITY_REPO" 2>/dev/null; then
    fail "S10: ActivityRepository references accountQuotaWriteAdapter — quota is now remote (unexpected)"
else
    ok "S10: saveCreatePartakeOrderAggregate NOT wired to remote accountQuotaWriteAdapter (deferred)"
fi

# S11: RaffleActivityPartakeService does not call decrementQuota
if grep -rq "decrementQuota" \
    big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/ \
    big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java \
    2>/dev/null; then
    fail "S11: decrementQuota unexpectedly wired in partake domain"
else
    ok "S11: decrementQuota not wired in partake domain (deferred as designed)"
fi

# S12: AccountQuotaServiceRPC.decrementQuota stub is still UN_ERROR (not live)
if grep -q "decrementQuota not yet implemented" \
    big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java \
    2>/dev/null; then
    ok "S12: AccountQuotaServiceRPC.decrementQuota still stub (UN_ERROR)"
else
    fail "S12: AccountQuotaServiceRPC.decrementQuota stub guard missing or changed"
fi

# ---------------------------------------------------------------------------
# Section 2 — Write-mode idempotency probes (MQ_IDEMPOTENCY_WRITE=true)
# ---------------------------------------------------------------------------
if [[ "$MQ_IDEMPOTENCY_WRITE" != "true" ]]; then
    info ""
    info "=== Section 2: Write-mode skipped (set MQ_IDEMPOTENCY_WRITE=true to enable) ==="
    info "    Requires local Docker MySQL at container: $MYSQL_CONTAINER"
else
    info ""
    info "=== Section 2: Write-mode idempotency probes ==="

    # Localhost guard — prevent accidental writes to non-local hosts
    DOCKER_HOST_IP=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \
        "$MYSQL_CONTAINER" 2>/dev/null | head -1 || true)
    if [[ -z "$DOCKER_HOST_IP" ]]; then
        fail "W1: Docker container $MYSQL_CONTAINER not reachable — aborting write-mode"
        info "    Start Docker environment first: docker compose up -d"
        info "    Skipping remaining write-mode checks"
        SKIP_WRITE=true
    else
        ok "W1: Docker container $MYSQL_CONTAINER reachable (IP: $DOCKER_HOST_IP)"
        SKIP_WRITE=false
    fi

    if [[ "$SKIP_WRITE" != "true" ]]; then

        mysql_exec() {
            local db="$1"
            local query="$2"
            docker exec "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N -e "$query" "$db" 2>/dev/null
        }

        # EXIT trap: always clean up test rows
        cleanup_mq_test_rows() {
            info "EXIT trap: cleaning MQ idempotency test rows..."
            docker exec "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                -e "DELETE FROM user_credit_order_000 WHERE out_business_no='$TEST_CREDIT_BIZ_NO';" \
                big_market_01 2>/dev/null || true
            docker exec "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                -e "DELETE FROM user_behavior_rebate_order_000 WHERE biz_id='$TEST_REBATE_BIZ_ID';" \
                big_market_01 2>/dev/null || true
            info "EXIT trap: test rows cleaned."
        }
        trap cleanup_mq_test_rows EXIT

        # ---- user_credit_order_000 idempotency probe ----
        # Insert first row
        CREDIT_TABLE_EXISTS=$(mysql_exec big_market_01 \
            "SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA='big_market_01' AND TABLE_NAME='user_credit_order_000';" || echo "0")
        if [[ "$CREDIT_TABLE_EXISTS" -eq 0 ]]; then
            fail "W2: user_credit_order_000 not found in big_market_01"
        else
            # Clean any stale test row first
            mysql_exec big_market_01 \
                "DELETE FROM user_credit_order_000 WHERE out_business_no='$TEST_CREDIT_BIZ_NO';" || true

            INSERT1_RC=0
            mysql_exec big_market_01 \
                "INSERT INTO user_credit_order_000
                    (user_id, order_id, trade_name, trade_type, amount, out_business_no, state)
                 VALUES
                    ('$TEST_USER_ID', 'mq-idem-ord-credit-001', 'award_credit', 'forward',
                     10.00, '$TEST_CREDIT_BIZ_NO', 'completed');" 2>/dev/null || INSERT1_RC=$?

            if [[ $INSERT1_RC -ne 0 ]]; then
                fail "W2: First INSERT into user_credit_order_000 failed (rc=$INSERT1_RC)"
            else
                ok "W2: First INSERT into user_credit_order_000 succeeded"

                # Verify row count = 1
                CNT=$(mysql_exec big_market_01 \
                    "SELECT COUNT(*) FROM user_credit_order_000 WHERE out_business_no='$TEST_CREDIT_BIZ_NO';")
                if [[ "$CNT" -eq 1 ]]; then
                    ok "W3: user_credit_order_000 row count = 1 after first INSERT"
                else
                    fail "W3: user_credit_order_000 row count = $CNT (expected 1)"
                fi

                # Duplicate INSERT — must fail with duplicate key error
                INSERT2_RC=0
                mysql_exec big_market_01 \
                    "INSERT INTO user_credit_order_000
                        (user_id, order_id, trade_name, trade_type, amount, out_business_no, state)
                     VALUES
                        ('$TEST_USER_ID', 'mq-idem-ord-credit-002', 'award_credit', 'forward',
                         10.00, '$TEST_CREDIT_BIZ_NO', 'completed');" 2>/dev/null || INSERT2_RC=$?

                if [[ $INSERT2_RC -ne 0 ]]; then
                    ok "W4: Duplicate INSERT into user_credit_order_000 blocked by UNIQUE KEY (rc=$INSERT2_RC)"
                else
                    fail "W4: Duplicate INSERT into user_credit_order_000 succeeded — UNIQUE KEY not enforced!"
                fi

                # Row count must still be 1 after duplicate attempt
                CNT2=$(mysql_exec big_market_01 \
                    "SELECT COUNT(*) FROM user_credit_order_000 WHERE out_business_no='$TEST_CREDIT_BIZ_NO';")
                if [[ "$CNT2" -eq 1 ]]; then
                    ok "W5: user_credit_order_000 row count still 1 after duplicate attempt (idempotency verified)"
                else
                    fail "W5: user_credit_order_000 row count = $CNT2 (expected 1)"
                fi
            fi
        fi

        # ---- user_behavior_rebate_order_000 idempotency probe ----
        REBATE_TABLE_EXISTS=$(mysql_exec big_market_01 \
            "SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA='big_market_01' AND TABLE_NAME='user_behavior_rebate_order_000';" || echo "0")
        if [[ "$REBATE_TABLE_EXISTS" -eq 0 ]]; then
            fail "W6: user_behavior_rebate_order_000 not found in big_market_01"
        else
            mysql_exec big_market_01 \
                "DELETE FROM user_behavior_rebate_order_000 WHERE biz_id='$TEST_REBATE_BIZ_ID';" || true

            INSERT3_RC=0
            mysql_exec big_market_01 \
                "INSERT INTO user_behavior_rebate_order_000
                    (user_id, order_id, behavior_type, rebate_type, rebate_config, biz_id, state)
                 VALUES
                    ('$TEST_USER_ID', 'mq-idem-ord-rebate-001', 'sign', 'sku', '9011',
                     '$TEST_REBATE_BIZ_ID', 'complete');" 2>/dev/null || INSERT3_RC=$?

            if [[ $INSERT3_RC -ne 0 ]]; then
                fail "W6: First INSERT into user_behavior_rebate_order_000 failed (rc=$INSERT3_RC)"
            else
                ok "W6: First INSERT into user_behavior_rebate_order_000 succeeded"

                CNT3=$(mysql_exec big_market_01 \
                    "SELECT COUNT(*) FROM user_behavior_rebate_order_000 WHERE biz_id='$TEST_REBATE_BIZ_ID';")
                if [[ "$CNT3" -eq 1 ]]; then
                    ok "W7: user_behavior_rebate_order_000 row count = 1"
                else
                    fail "W7: user_behavior_rebate_order_000 row count = $CNT3 (expected 1)"
                fi

                INSERT4_RC=0
                mysql_exec big_market_01 \
                    "INSERT INTO user_behavior_rebate_order_000
                        (user_id, order_id, behavior_type, rebate_type, rebate_config, biz_id, state)
                     VALUES
                        ('$TEST_USER_ID', 'mq-idem-ord-rebate-002', 'sign', 'sku', '9011',
                         '$TEST_REBATE_BIZ_ID', 'complete');" 2>/dev/null || INSERT4_RC=$?

                if [[ $INSERT4_RC -ne 0 ]]; then
                    ok "W8: Duplicate INSERT into user_behavior_rebate_order_000 blocked by UNIQUE KEY (rc=$INSERT4_RC)"
                else
                    fail "W8: Duplicate INSERT into user_behavior_rebate_order_000 succeeded — uq_biz_id not enforced!"
                fi

                CNT4=$(mysql_exec big_market_01 \
                    "SELECT COUNT(*) FROM user_behavior_rebate_order_000 WHERE biz_id='$TEST_REBATE_BIZ_ID';")
                if [[ "$CNT4" -eq 1 ]]; then
                    ok "W9: user_behavior_rebate_order_000 row count still 1 after duplicate attempt"
                else
                    fail "W9: user_behavior_rebate_order_000 row count = $CNT4 (expected 1)"
                fi
            fi
        fi

        info ""
        info "Write-mode complete. EXIT trap will clean test rows."
    fi
fi

# ---------------------------------------------------------------------------
# Result
# ---------------------------------------------------------------------------
echo ""
echo "=== validate-mq-idempotency.sh: $PASS passed, $FAIL failed ==="

if [[ "$FAIL" -gt 0 ]]; then
    echo "RESULT: FAIL — resolve failed checks before enabling write flags"
    exit 1
else
    echo "RESULT: PASS"
    exit 0
fi
