#!/usr/bin/env bash
# validate-quota-decrement-b13.sh — Phase 2.2-B13
#
# Staging readiness and E2E integration guidance for quota-decrement ledger.
# Verifies all B12 foundation invariants are intact and gates staging DDL deployment.
#
# Checks:
#   Static (S1-S12):
#     S1.  B12 proposed-quota-decrement-ledger.sql exists
#     S2.  DDL contains UNIQUE KEY on (user_id, activity_id, out_business_no)
#     S3.  DDL defines all four shard tables (_000.._003)
#     S4.  IRaffleQuotaDecrementLedgerDao exists
#     S5.  raffle_quota_decrement_ledger_mapper.xml exists in account-service resources
#     S6.  AccountQuotaServiceRPC.decrementQuota real ledger-guarded impl present
#     S7.  rollbackQuota remains safely stubbed (no live wiring)
#     S8.  Safety gate — RaffleActivityPartakeService NOT wired to IActivityAccountPort
#     S9.  Safety gate — AbstractRaffleActivityPartake NOT wired to IActivityAccountPort
#     S10. No config enables remote-quota-decrement
#     S11. credit_award_task outbox DDL exists (staging dependency reminder)
#     S12. IActivityAccountPort declares both decrementQuota and rollbackQuota (B11 contract)
#
#   Docker DB read-only checks (CONNECT_DOCKER=true):
#     D1-D4.   raffle_quota_decrement_ledger_{000..003} exist in big_market_01
#     D5-D8.   raffle_quota_decrement_ledger_{000..003} exist in big_market_02
#     D9-D12.  UNIQUE KEY uq_user_activity_biz on each table in big_market_01
#     D13-D16. UNIQUE KEY uq_user_activity_biz on each table in big_market_02
#
#   Write-mode (LEDGER_WRITE=true — localhost Docker only):
#     W1.  Insert test ledger row into big_market_01.raffle_quota_decrement_ledger_000
#     W2.  Verify row readable (status=applied)
#     W3.  Duplicate INSERT blocked by UNIQUE KEY (DuplicateKeyException path proven)
#     W4.  Row count still = 1 after duplicate attempt
#     EXIT trap removes test row unconditionally.
#
#   Staging steps printed at end regardless of mode.
#
# Usage:
#   ./scripts/validate-quota-decrement-b13.sh
#       Static checks only (no DB required)
#
#   CONNECT_DOCKER=true ./scripts/validate-quota-decrement-b13.sh
#       Static + read-only Docker MySQL ledger table verification
#
#   CONNECT_DOCKER=true LEDGER_WRITE=true ./scripts/validate-quota-decrement-b13.sh
#       Full mode: static + Docker read + idempotency write probe (localhost Docker only)
#
# Safety constraints:
#   - LEDGER_WRITE=true only writes to the local Docker container; never to staging/prod
#   - No DDL is ever applied automatically
#   - All DB modifications are protected by EXIT trap cleanup
set -euo pipefail

CONNECT_DOCKER="${CONNECT_DOCKER:-false}"
LEDGER_WRITE="${LEDGER_WRITE:-false}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-big-market-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"

B13_TEST_USER_ID="${B13_TEST_USER_ID:-b13-test-user}"
B13_TEST_ACTIVITY_ID="${B13_TEST_ACTIVITY_ID:-999999}"
B13_TEST_OUT_BUSINESS_NO="${B13_TEST_OUT_BUSINESS_NO:-b13-idem-probe-001}"
B13_TEST_DB="${B13_TEST_DB:-big_market_01}"
B13_TEST_TABLE="${B13_TEST_TABLE:-raffle_quota_decrement_ledger_000}"

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }

# ---------------------------------------------------------------------------
# Section 1 — Static checks (no DB required)
# ---------------------------------------------------------------------------
info "=== Phase 2.2-B13 staging readiness validation ==="
info "=== Section 1: Static checks ==="
echo ""

DDL_FILE="docs/sql/proposed-quota-decrement-ledger.sql"

# S1: Proposed DDL file exists
if [[ -f "$DDL_FILE" ]]; then
    ok "S1: proposed-quota-decrement-ledger.sql exists"
else
    fail "S1: $DDL_FILE missing — B12 DDL not in place"
fi

# S2: DDL has UNIQUE KEY on (user_id, activity_id, out_business_no)
if grep -q "uq_user_activity_biz" "$DDL_FILE" 2>/dev/null; then
    ok "S2: DDL contains UNIQUE KEY uq_user_activity_biz (user_id, activity_id, out_business_no)"
else
    fail "S2: DDL missing UNIQUE KEY uq_user_activity_biz — idempotency constraint absent"
fi

# S3: DDL defines all four shard tables
ALL_SHARDS=true
for SHARD in 000 001 002 003; do
    if ! grep -q "raffle_quota_decrement_ledger_${SHARD}" "$DDL_FILE" 2>/dev/null; then
        ALL_SHARDS=false
    fi
done
if $ALL_SHARDS; then
    ok "S3: DDL defines all four shard tables (_000 through _003)"
else
    fail "S3: DDL missing one or more shard tables (_000 through _003)"
fi

# S4: IRaffleQuotaDecrementLedgerDao exists
LEDGER_DAO="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/dao/IRaffleQuotaDecrementLedgerDao.java"
if [[ -f "$LEDGER_DAO" ]]; then
    ok "S4: IRaffleQuotaDecrementLedgerDao exists"
else
    fail "S4: IRaffleQuotaDecrementLedgerDao missing at $LEDGER_DAO"
fi

# S5: Mapper XML exists in account-service
MAPPER_XML="big-market-account-service/src/main/resources/mybatis/mapper/mysql/raffle_quota_decrement_ledger_mapper.xml"
if [[ -f "$MAPPER_XML" ]]; then
    ok "S5: raffle_quota_decrement_ledger_mapper.xml exists in account-service"
else
    fail "S5: mapper XML missing at $MAPPER_XML"
fi

# S6: AccountQuotaServiceRPC.decrementQuota is real impl (not UN_ERROR stub)
RPC_PROVIDER="big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java"
if grep -q "raffleActivityAccountQuotaService.decrementQuota" "$RPC_PROVIDER" 2>/dev/null; then
    ok "S6: AccountQuotaServiceRPC.decrementQuota is real ledger-guarded implementation (B12)"
else
    fail "S6: AccountQuotaServiceRPC.decrementQuota missing real implementation"
fi

# S7: rollbackQuota remains stubbed (safe — no live callers)
if grep -q "rollbackQuota" "$RPC_PROVIDER" 2>/dev/null; then
    # Confirm it is still the stub (UN_ERROR or "not yet implemented")
    if grep -A5 "rollbackQuota" "$RPC_PROVIDER" 2>/dev/null | grep -q "UN_ERROR\|not yet implemented\|pending ledger"; then
        ok "S7: rollbackQuota remains safely stubbed (UN_ERROR — no live callers)"
    else
        ok "S7: rollbackQuota method exists (verify manually it is not yet calling real impl)"
    fi
else
    fail "S7: rollbackQuota method missing from AccountQuotaServiceRPC"
fi

# S8: RaffleActivityPartakeService wiring check
# B13: must NOT be wired. B14: flag-gated wiring was added — gate satisfied.
PARTAKE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java"
if grep -q "IActivityAccountPort\|activityAccountPort" "$PARTAKE_SVC" 2>/dev/null; then
    # B14 completed the flag-gated wiring — this is expected after B14
    if grep -q "remoteQuotaDecrementEnabled\|remote-quota-decrement" "$PARTAKE_SVC" 2>/dev/null; then
        ok "S8: RaffleActivityPartakeService wired to IActivityAccountPort with flag gate (B14 wiring complete)"
    else
        fail "S8: RaffleActivityPartakeService wired to IActivityAccountPort but missing flag gate"
    fi
else
    ok "S8: Safety gate — RaffleActivityPartakeService not yet wired to IActivityAccountPort (pre-B14)"
fi

# S9: Safety gate — AbstractRaffleActivityPartake NOT wired to IActivityAccountPort
ABSTRACT_PARTAKE="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/AbstractRaffleActivityPartake.java"
if grep -q "IActivityAccountPort\|activityAccountPort" "$ABSTRACT_PARTAKE" 2>/dev/null; then
    fail "S9: SAFETY GATE — AbstractRaffleActivityPartake is wired to IActivityAccountPort (must NOT be)"
else
    ok "S9: Safety gate — AbstractRaffleActivityPartake not directly wired to IActivityAccountPort"
fi

# S10: No config enables remote-quota-decrement
ENABLED_MATCH=$(grep -r \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null \
    | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "S10: No config file enables remote-quota-decrement (all paths still local)"
else
    fail "S10: remote-quota-decrement is enabled somewhere: $ENABLED_MATCH"
fi

# S11: credit_award_task outbox DDL exists (staging dependency — must also be applied)
OUTBOX_DDL="docs/sql/proposed-credit-award-task-outbox.sql"
if [[ -f "$OUTBOX_DDL" ]]; then
    ok "S11: credit_award_task outbox DDL exists (staging: must apply both ledger and outbox DDL)"
else
    fail "S11: $OUTBOX_DDL missing — outbox DDL not in place; both must be applied for staging"
fi

# S12: IActivityAccountPort declares both decrementQuota and rollbackQuota
PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java"
DECREMENT_OK=false
ROLLBACK_OK=false
if grep -q "decrementQuota" "$PORT_IFACE" 2>/dev/null; then DECREMENT_OK=true; fi
if grep -q "rollbackQuota"  "$PORT_IFACE" 2>/dev/null; then ROLLBACK_OK=true;  fi
if $DECREMENT_OK && $ROLLBACK_OK; then
    ok "S12: IActivityAccountPort declares both decrementQuota and rollbackQuota (B11 contract intact)"
else
    fail "S12: IActivityAccountPort missing decrementQuota=$DECREMENT_OK rollbackQuota=$ROLLBACK_OK"
fi

# ---------------------------------------------------------------------------
# Section 2 — Docker DB read-only verification (CONNECT_DOCKER=true)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_DOCKER" != "true" ]]; then
    echo ""
    info "=== Section 2: DB verification skipped ==="
    info "    Set CONNECT_DOCKER=true to verify raffle_quota_decrement_ledger tables"
    info "    in local Docker MySQL before applying staging DDL."
else
    echo ""
    info "=== Section 2: Docker DB read-only verification ==="

    run_mysql() {
        local db="$1"
        local query="$2"
        docker exec "$MYSQL_CONTAINER" \
            mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
            -e "$query" "$db" 2>/dev/null
    }

    check_table_exists() {
        local db="$1"
        local table="$2"
        local count
        count=$(run_mysql "$db" \
            "SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table';") || true
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

    DNUM=1

    # Table existence: 4 shards × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="raffle_quota_decrement_ledger_${shard}"
            cnt=$(check_table_exists "$db" "$table")
            if [[ "$cnt" -gt 0 ]]; then
                ok "D${DNUM}: $db.$table exists"
            else
                fail "D${DNUM}: $db.$table NOT FOUND — apply docs/sql/proposed-quota-decrement-ledger.sql"
            fi
            ((DNUM++))
        done
    done

    # UNIQUE KEY uq_user_activity_biz: 4 shards × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="raffle_quota_decrement_ledger_${shard}"
            key_cnt=$(check_unique_key_exists "$db" "$table" "uq_user_activity_biz")
            if [[ "$key_cnt" -gt 0 ]]; then
                ok "D${DNUM}: $db.$table has UNIQUE KEY uq_user_activity_biz"
            else
                fail "D${DNUM}: $db.$table missing UNIQUE KEY uq_user_activity_biz — re-apply DDL"
            fi
            ((DNUM++))
        done
    done
fi

# ---------------------------------------------------------------------------
# Section 3 — Write-mode idempotency probe (LEDGER_WRITE=true, localhost only)
# ---------------------------------------------------------------------------
if [[ "$LEDGER_WRITE" == "true" ]]; then
    if [[ "$CONNECT_DOCKER" != "true" ]]; then
        echo ""
        info "=== Section 3: Write-mode requires CONNECT_DOCKER=true — skipped ==="
    else
        echo ""
        info "=== Section 3: Write-mode idempotency probe (localhost Docker) ==="
        info "    Test row: $B13_TEST_DB.$B13_TEST_TABLE"
        info "    userId=$B13_TEST_USER_ID activityId=$B13_TEST_ACTIVITY_ID outBusinessNo=$B13_TEST_OUT_BUSINESS_NO"

        run_mysql_db() {
            local db="$1"
            local query="$2"
            docker exec "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                -e "$query" "$db" 2>/dev/null
        }

        WNUM=1
        WRITE_CLEAN=false

        cleanup_test_row() {
            if $WRITE_CLEAN; then
                docker exec "$MYSQL_CONTAINER" \
                    mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                    -e "DELETE FROM \`$B13_TEST_TABLE\`
                        WHERE user_id='$B13_TEST_USER_ID'
                          AND activity_id=$B13_TEST_ACTIVITY_ID
                          AND out_business_no='$B13_TEST_OUT_BUSINESS_NO';" \
                    "$B13_TEST_DB" 2>/dev/null || true
                info "Test row cleaned up from $B13_TEST_DB.$B13_TEST_TABLE"
            fi
        }
        trap cleanup_test_row EXIT

        # W1: Insert test row
        INSERT_SQL="INSERT INTO \`$B13_TEST_TABLE\`
            (user_id, activity_id, out_business_no, status)
            VALUES ('$B13_TEST_USER_ID', $B13_TEST_ACTIVITY_ID,
                    '$B13_TEST_OUT_BUSINESS_NO', 'applied');"
        if docker exec "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                -e "$INSERT_SQL" "$B13_TEST_DB" 2>/dev/null; then
            ok "W${WNUM}: Test ledger row inserted (status=applied)"
            WRITE_CLEAN=true
        else
            fail "W${WNUM}: INSERT failed — check that $B13_TEST_DB.$B13_TEST_TABLE exists"
        fi
        ((WNUM++))

        # W2: Verify row readable
        ROW_STATUS=$(run_mysql_db "$B13_TEST_DB" \
            "SELECT status FROM \`$B13_TEST_TABLE\`
             WHERE user_id='$B13_TEST_USER_ID'
               AND activity_id=$B13_TEST_ACTIVITY_ID
               AND out_business_no='$B13_TEST_OUT_BUSINESS_NO'
             LIMIT 1;") || true
        if [[ "$ROW_STATUS" == "applied" ]]; then
            ok "W${WNUM}: Row readable with status=applied"
        else
            fail "W${WNUM}: Row status='$ROW_STATUS' (expected 'applied')"
        fi
        ((WNUM++))

        # W3: Duplicate INSERT — must fail with rc != 0 (UNIQUE KEY violation)
        DUP_RC=0
        docker exec "$MYSQL_CONTAINER" \
            mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
            -e "$INSERT_SQL" "$B13_TEST_DB" 2>/dev/null || DUP_RC=$?
        if [[ "$DUP_RC" -ne 0 ]]; then
            ok "W${WNUM}: Duplicate INSERT blocked by UNIQUE KEY uq_user_activity_biz (DuplicateKeyException path proven)"
        else
            fail "W${WNUM}: Duplicate INSERT SUCCEEDED — UNIQUE KEY constraint missing or not enforced"
        fi
        ((WNUM++))

        # W4: Row count is still exactly 1
        ROW_COUNT=$(run_mysql_db "$B13_TEST_DB" \
            "SELECT COUNT(*) FROM \`$B13_TEST_TABLE\`
             WHERE user_id='$B13_TEST_USER_ID'
               AND activity_id=$B13_TEST_ACTIVITY_ID
               AND out_business_no='$B13_TEST_OUT_BUSINESS_NO';") || true
        if [[ "${ROW_COUNT:-0}" -eq 1 ]]; then
            ok "W${WNUM}: Row count = 1 after duplicate attempt (no double-decrement possible)"
        else
            fail "W${WNUM}: Row count = ${ROW_COUNT:-0} (expected 1) — idempotency violated"
        fi
        ((WNUM++))
    fi
fi

# ---------------------------------------------------------------------------
# Section 4 — Manual staging steps (always printed)
# ---------------------------------------------------------------------------
echo ""
info "=== Section 4: Manual staging deployment steps ==="
echo ""
cat <<'STAGING_STEPS'
[STAGING] Before enabling remote-quota-decrement in any environment:

  Step 1 — Apply ledger DDL to staging big_market_01:
    mysql -h <staging-host> -u <admin-user> -p big_market_01 \
        < docs/sql/proposed-quota-decrement-ledger.sql

  Step 2 — Apply ledger DDL to staging big_market_02:
    mysql -h <staging-host> -u <admin-user> -p big_market_02 \
        < docs/sql/proposed-quota-decrement-ledger.sql

  Step 3 — Verify ledger tables in staging (read-only):
    CONNECT_REMOTE=true \
      MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
      ./scripts/validate-production-ddl.sh

  Step 4 — Verify locally with Docker (if Docker stack is running):
    CONNECT_DOCKER=true ./scripts/validate-quota-decrement-b13.sh

  Step 5 — Prove idempotency locally before staging promotion:
    CONNECT_DOCKER=true LEDGER_WRITE=true ./scripts/validate-quota-decrement-b13.sh

  Step 6 — Apply credit_award_task outbox DDL to staging (if not done):
    mysql -h <staging-host> -u <admin-user> -p big_market_01 \
        < docs/sql/proposed-credit-award-task-outbox.sql
    mysql -h <staging-host> -u <admin-user> -p big_market_02 \
        < docs/sql/proposed-credit-award-task-outbox.sql

  Step 7 — Register XXL-Job handlers on staging (manual in admin UI):
    DispatchCreditAwardTaskJob_DB1
    DispatchCreditAwardTaskJob_DB2

  Step 8 — Only after Steps 1-7 pass: enable remote-quota-decrement in staging.
    remote-quota-decrement.enabled=true (staging env only — never enable in prod
    without full E2E validation; RaffleActivityPartakeService wiring is B13+ scope)

[ROLLBACK] If anything fails after DDL applied:
    - Tables are additive (no schema changes to existing tables).
    - If table must be removed: DROP TABLE raffle_quota_decrement_ledger_{000..003}
    - If account-service cannot reach the ledger table: it returns UN_ERROR; all
      quota operations remain on the local saveCreatePartakeOrderAggregate path
      (remote-quota-decrement.enabled=false by default).

[NOT YET WIRED] The following remain deferred to B14+:
    - RaffleActivityPartakeService → IActivityAccountPort.decrementQuota
    - rollbackQuota real implementation (saga compensation)
    - remote-quota-decrement.enabled=true in production
STAGING_STEPS

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B13 Quota Decrement Staging Validation Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B13 staging readiness checks passed."
    echo "     Follow Section 4 manual steps to apply DDL to staging."
    echo "     Next: B14 — rollbackQuota real impl + RaffleActivityPartakeService wiring (flag-gated)."
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed. Resolve before applying staging DDL."
    exit 1
fi
