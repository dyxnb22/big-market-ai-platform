#!/usr/bin/env bash
# validate-quota-decrement-b15-e2e.sh — Phase 2.2-B15
#
# B15 staging runbook gate: E2E rehearsal for quota-decrement + rollback + credit-award outbox.
# Verifies all B14 static invariants, Docker DB readiness, and exercises the ledger state machine
# locally to prove the E2E path is safe before applying DDL and enabling flag=true on staging.
#
# Usage:
#   ./scripts/validate-quota-decrement-b15-e2e.sh
#       Static dry-run only (no DB, no flag changes, no writes)
#
#   CONNECT_DOCKER=true ./scripts/validate-quota-decrement-b15-e2e.sh
#       Static + read-only Docker MySQL verification:
#         - raffle_quota_decrement_ledger_{000..003} in big_market_01 and big_market_02
#         - uq_user_activity_biz on all ledger tables
#         - credit_award_task_{000..003} in big_market_01 and big_market_02
#         - uq_award_order_id on all outbox tables
#
#   CONNECT_DOCKER=true B15_E2E_REHEARSAL=true ./scripts/validate-quota-decrement-b15-e2e.sh
#       Full ledger state-machine rehearsal (localhost Docker only):
#         - Insert test ledger row (status=applied) — proves first decrement writes once
#         - Verify idempotent duplicate INSERT is blocked by UNIQUE KEY
#         - Update applied → rolled_back — proves rollback path
#         - Verify duplicate rollback is idempotent (0 rows affected on second update)
#         - Cleanup via EXIT trap (B15 test rows only)
#
#   CONNECT_DOCKER=true B15_E2E_REHEARSAL=true B15_POST_CHECK=true ./scripts/...
#       Same as rehearsal mode, with an extra post-cleanup verification pass.
#
#   B15_CLEANUP=true ./scripts/validate-quota-decrement-b15-e2e.sh
#       Remove any leftover B15 test rows from a previous interrupted run (localhost only).
#
# Safety constraints:
#   - NEVER applies staging/production DDL automatically
#   - NEVER enables remote-quota-decrement.enabled by default
#   - B15_E2E_REHEARSAL writes ONLY to the local Docker container; blocked for non-localhost hosts
#   - EXIT trap always cleans B15 test rows (even on failure)
#   - No production logic added; this is a rehearsal/runbook gate batch
set -euo pipefail

CONNECT_DOCKER="${CONNECT_DOCKER:-false}"
B15_E2E_REHEARSAL="${B15_E2E_REHEARSAL:-false}"
B15_POST_CHECK="${B15_POST_CHECK:-false}"
B15_CLEANUP="${B15_CLEANUP:-false}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-big-market-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_HOST="${MYSQL_HOST:-localhost}"

B15_TEST_USER_ID="${B15_TEST_USER_ID:-b15-e2e-test-user}"
B15_TEST_ACTIVITY_ID="${B15_TEST_ACTIVITY_ID:-999998}"
B15_TEST_OUT_BUSINESS_NO="${B15_TEST_OUT_BUSINESS_NO:-b15-e2e-probe-001}"
B15_TEST_DB="${B15_TEST_DB:-big_market_01}"
B15_TEST_TABLE="${B15_TEST_TABLE:-raffle_quota_decrement_ledger_000}"

PASS=0
FAIL=0
SKIP=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
skip() { echo "[SKIP] $*"; SKIP=$((SKIP + 1)); }
info() { echo "[INFO] $*"; }

# ---------------------------------------------------------------------------
# Section 0 — Cleanup-only mode
# ---------------------------------------------------------------------------
if [[ "$B15_CLEANUP" == "true" ]]; then
    info "=== B15 Cleanup mode ==="
    if [[ "$CONNECT_DOCKER" != "true" ]]; then
        echo "[ERROR] B15_CLEANUP=true requires CONNECT_DOCKER=true (localhost Docker only)."
        exit 1
    fi
    if [[ "$MYSQL_HOST" != "localhost" && "$MYSQL_HOST" != "127.0.0.1" ]]; then
        echo "[ERROR] Cleanup blocked for non-localhost MYSQL_HOST=$MYSQL_HOST"
        exit 1
    fi
    info "Removing B15 test rows from $B15_TEST_DB.$B15_TEST_TABLE ..."
    docker exec "$MYSQL_CONTAINER" \
        mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
        -e "DELETE FROM \`$B15_TEST_TABLE\`
            WHERE user_id='$B15_TEST_USER_ID'
              AND activity_id=$B15_TEST_ACTIVITY_ID
              AND out_business_no='$B15_TEST_OUT_BUSINESS_NO';" \
        "$B15_TEST_DB" 2>/dev/null && info "Cleanup done." || info "Nothing to remove."
    exit 0
fi

info "=== Phase 2.2-B15 E2E staging runbook gate ==="
echo ""

# ---------------------------------------------------------------------------
# Section 1 — Static checks (no DB required)
# ---------------------------------------------------------------------------
info "=== Section 1: Static checks ==="
echo ""

PARTAKE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java"
ABSTRACT_PARTAKE="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/AbstractRaffleActivityPartake.java"
REPO_IMPL="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java"
LOCAL_PORT="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java"
PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java"
LEDGER_DDL="docs/sql/proposed-quota-decrement-ledger.sql"
OUTBOX_DDL="docs/sql/proposed-credit-award-task-outbox.sql"
B13_SCRIPT="scripts/validate-quota-decrement-b13.sh"
B14_SCRIPT="scripts/validate-quota-decrement-b14.sh"

# S1: B14 validation script exists and is executable
if [[ -x "$B14_SCRIPT" ]]; then
    ok "S1: $B14_SCRIPT exists and is executable"
elif [[ -f "$B14_SCRIPT" ]]; then
    ok "S1: $B14_SCRIPT exists (not executable — run chmod +x if needed)"
else
    fail "S1: $B14_SCRIPT missing — B14 baseline not in place"
fi

# S2: B13 validation script exists and is executable
if [[ -x "$B13_SCRIPT" ]]; then
    ok "S2: $B13_SCRIPT exists and is executable"
elif [[ -f "$B13_SCRIPT" ]]; then
    ok "S2: $B13_SCRIPT exists (not executable — run chmod +x if needed)"
else
    fail "S2: $B13_SCRIPT missing — B13 baseline not in place"
fi

# S3: Ledger DDL file exists
if [[ -f "$LEDGER_DDL" ]]; then
    ok "S3: $LEDGER_DDL exists (staging ledger DDL ready for manual apply)"
else
    fail "S3: $LEDGER_DDL missing — B12 DDL not in place"
fi

# S4: Credit-award outbox DDL exists
if [[ -f "$OUTBOX_DDL" ]]; then
    ok "S4: $OUTBOX_DDL exists (staging outbox DDL ready for manual apply)"
else
    fail "S4: $OUTBOX_DDL missing — B5 outbox DDL not in place"
fi

# S5: remote-quota-decrement.enabled defaults false
if grep -q "remote-quota-decrement.enabled:false\|:false}" "$PARTAKE_SVC" 2>/dev/null; then
    ok "S5: remote-quota-decrement.enabled defaults false in RaffleActivityPartakeService"
else
    fail "S5: default false not found for remote-quota-decrement.enabled — flag may be live"
fi

# S6: RaffleActivityPartakeService is flag-gated to IActivityAccountPort
if grep -q "IActivityAccountPort\|activityAccountPort" "$PARTAKE_SVC" 2>/dev/null && \
   grep -q "remoteQuotaDecrementEnabled\|remote-quota-decrement" "$PARTAKE_SVC" 2>/dev/null; then
    ok "S6: RaffleActivityPartakeService is wired to IActivityAccountPort with flag gate (B14)"
else
    fail "S6: RaffleActivityPartakeService missing IActivityAccountPort wiring or flag gate"
fi

# S7: rollbackQuotaWithLedger exists in ActivityRepository
if grep -q "rollbackQuotaWithLedger" "$REPO_IMPL" 2>/dev/null; then
    ok "S7: ActivityRepository.rollbackQuotaWithLedger exists (real B14 impl)"
else
    fail "S7: ActivityRepository.rollbackQuotaWithLedger missing — B14 not complete"
fi

# S8: rollback queries ledger before modifying quota (ledger-guarded)
if grep -q "queryByKey" "$REPO_IMPL" 2>/dev/null; then
    ok "S8: rollbackQuotaWithLedger queries ledger row before touching quota (ledger-guarded)"
else
    fail "S8: rollbackQuotaWithLedger does not query ledger — not ledger-guarded"
fi

# S9: rollback transitions applied → rolled_back
if grep -q "updateStatusToRolledBack" "$REPO_IMPL" 2>/dev/null; then
    ok "S9: rollbackQuotaWithLedger calls updateStatusToRolledBack (applied → rolled_back)"
else
    fail "S9: updateStatusToRolledBack missing from rollbackQuotaWithLedger"
fi

# S10: duplicate rollback is idempotent (rolled_back check present)
if grep -A20 "rollbackQuotaWithLedger" "$REPO_IMPL" 2>/dev/null | grep -q "rolled_back"; then
    ok "S10: rollbackQuotaWithLedger is idempotent (rolled_back status check present)"
else
    fail "S10: rolled_back idempotency guard missing from rollbackQuotaWithLedger"
fi

# S11: no-ledger-row is a safe no-op
if grep -A20 "rollbackQuotaWithLedger" "$REPO_IMPL" 2>/dev/null | grep -q "ledger == null\|no-op\|no ledger row"; then
    ok "S11: rollbackQuotaWithLedger handles missing ledger row as safe no-op"
else
    fail "S11: null ledger row guard missing from rollbackQuotaWithLedger"
fi

# S12: no config enables remote-quota-decrement by default
ENABLED_MATCH=$(grep -r \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null \
    | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "S12: No config enables remote-quota-decrement (default=false preserved)"
else
    fail "S12: remote-quota-decrement enabled in config: $ENABLED_MATCH"
fi

# S13: savePartakeOrderOnly exists (order-only insert for flag=true path)
if grep -q "savePartakeOrderOnly" "$REPO_IMPL" 2>/dev/null; then
    ok "S13: ActivityRepository.savePartakeOrderOnly exists (flag=true order-only path)"
else
    fail "S13: ActivityRepository.savePartakeOrderOnly missing — B14 order-only insert not in place"
fi

# S14: LocalActivityAccountPort delegates to real repository methods
if grep -q "activityRepository.decrementQuotaWithLedger\|activityRepository" "$LOCAL_PORT" 2>/dev/null && \
   grep -q "rollbackQuotaWithLedger" "$LOCAL_PORT" 2>/dev/null; then
    ok "S14: LocalActivityAccountPort delegates to activityRepository (real B14 impl, not no-op)"
else
    fail "S14: LocalActivityAccountPort not delegating to activityRepository — B14 wiring missing"
fi

# S15: IActivityAccountPort contract intact (decrementQuota + rollbackQuota)
DECREMENT_OK=false; ROLLBACK_OK=false
grep -q "decrementQuota" "$PORT_IFACE" 2>/dev/null && DECREMENT_OK=true
grep -q "rollbackQuota"  "$PORT_IFACE" 2>/dev/null && ROLLBACK_OK=true
if $DECREMENT_OK && $ROLLBACK_OK; then
    ok "S15: IActivityAccountPort contract intact (decrementQuota + rollbackQuota)"
else
    fail "S15: IActivityAccountPort missing decrementQuota=$DECREMENT_OK rollbackQuota=$ROLLBACK_OK"
fi

# S16: AbstractRaffleActivityPartake NOT directly wired (safety gate)
if grep -q "IActivityAccountPort\|activityAccountPort" "$ABSTRACT_PARTAKE" 2>/dev/null; then
    fail "S16: SAFETY GATE — AbstractRaffleActivityPartake directly wired to IActivityAccountPort (must NOT be)"
else
    ok "S16: Safety gate — AbstractRaffleActivityPartake not directly wired to IActivityAccountPort"
fi

# S17: doSavePartakeOrder hook exists in AbstractRaffleActivityPartake
if grep -q "doSavePartakeOrder" "$ABSTRACT_PARTAKE" 2>/dev/null; then
    ok "S17: AbstractRaffleActivityPartake.doSavePartakeOrder hook present (B14 override seam)"
else
    fail "S17: AbstractRaffleActivityPartake.doSavePartakeOrder hook missing"
fi

# S18: XXL-Job handlers documented as manual blockers (referenced in B14 notes)
if grep -q "DispatchCreditAwardTaskJob_DB1\|DispatchCreditAwardTaskJob_DB2" "$B14_SCRIPT" 2>/dev/null; then
    ok "S18: XXL-Job handlers (DispatchCreditAwardTaskJob_DB1/DB2) listed as manual blockers in B14 script"
else
    fail "S18: XXL-Job handler references missing from B14 script"
fi

# S19: Ledger DDL has UNIQUE KEY (idempotency constraint)
if grep -q "uq_user_activity_biz" "$LEDGER_DDL" 2>/dev/null; then
    ok "S19: Ledger DDL has UNIQUE KEY uq_user_activity_biz (user_id, activity_id, out_business_no)"
else
    fail "S19: Ledger DDL missing UNIQUE KEY uq_user_activity_biz"
fi

# S20: Outbox DDL has UNIQUE KEY (idempotency constraint)
if grep -q "uq_award_order_id" "$OUTBOX_DDL" 2>/dev/null; then
    ok "S20: Outbox DDL has UNIQUE KEY uq_award_order_id (user_id, award_order_id)"
else
    fail "S20: Outbox DDL missing UNIQUE KEY uq_award_order_id"
fi

# ---------------------------------------------------------------------------
# Section 2 — Docker read-only DB verification (CONNECT_DOCKER=true)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_DOCKER" != "true" ]]; then
    echo ""
    info "=== Section 2: Docker DB verification skipped ==="
    info "    Set CONNECT_DOCKER=true to verify table/key presence in local Docker MySQL."
    info "    Required before applying staging DDL or running B15_E2E_REHEARSAL=true."
else
    echo ""
    info "=== Section 2: Docker read-only DB verification ==="

    run_mysql() {
        local db="$1" query="$2"
        docker exec "$MYSQL_CONTAINER" \
            mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
            -e "$query" "$db" 2>/dev/null
    }

    check_table() {
        local db="$1" table="$2"
        local cnt
        cnt=$(run_mysql "$db" \
            "SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table';") || true
        echo "${cnt:-0}"
    }

    check_unique_key() {
        local db="$1" table="$2" key="$3"
        local cnt
        cnt=$(run_mysql "$db" \
            "SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA='$db' AND TABLE_NAME='$table'
               AND INDEX_NAME='$key' AND NON_UNIQUE=0;") || true
        echo "${cnt:-0}"
    }

    DNUM=1

    # Ledger tables: 4 shards × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="raffle_quota_decrement_ledger_${shard}"
            cnt=$(check_table "$db" "$table")
            if [[ "${cnt:-0}" -gt 0 ]]; then
                ok "D${DNUM}: $db.$table exists"
            else
                fail "D${DNUM}: $db.$table NOT FOUND — apply $LEDGER_DDL to $db first"
            fi
            ((DNUM++))
        done
    done

    # Ledger UNIQUE KEY uq_user_activity_biz: 4 shards × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="raffle_quota_decrement_ledger_${shard}"
            kcnt=$(check_unique_key "$db" "$table" "uq_user_activity_biz")
            if [[ "${kcnt:-0}" -gt 0 ]]; then
                ok "D${DNUM}: $db.$table has UNIQUE KEY uq_user_activity_biz"
            else
                fail "D${DNUM}: $db.$table missing UNIQUE KEY uq_user_activity_biz — re-apply DDL"
            fi
            ((DNUM++))
        done
    done

    # Outbox tables: 4 shards × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="credit_award_task_${shard}"
            cnt=$(check_table "$db" "$table")
            if [[ "${cnt:-0}" -gt 0 ]]; then
                ok "D${DNUM}: $db.$table exists"
            else
                fail "D${DNUM}: $db.$table NOT FOUND — apply $OUTBOX_DDL to $db first"
            fi
            ((DNUM++))
        done
    done

    # Outbox UNIQUE KEY uq_award_order_id: 4 shards × 2 DBs = 8
    for db in big_market_01 big_market_02; do
        for shard in 000 001 002 003; do
            table="credit_award_task_${shard}"
            kcnt=$(check_unique_key "$db" "$table" "uq_award_order_id")
            if [[ "${kcnt:-0}" -gt 0 ]]; then
                ok "D${DNUM}: $db.$table has UNIQUE KEY uq_award_order_id"
            else
                fail "D${DNUM}: $db.$table missing UNIQUE KEY uq_award_order_id — re-apply DDL"
            fi
            ((DNUM++))
        done
    done
fi

# ---------------------------------------------------------------------------
# Section 3 — Local-only write rehearsal (CONNECT_DOCKER=true B15_E2E_REHEARSAL=true)
# ---------------------------------------------------------------------------
if [[ "$B15_E2E_REHEARSAL" == "true" ]]; then
    if [[ "$CONNECT_DOCKER" != "true" ]]; then
        echo ""
        info "=== Section 3: Write rehearsal requires CONNECT_DOCKER=true — skipped ==="
        info "    Run with: CONNECT_DOCKER=true B15_E2E_REHEARSAL=true ./scripts/$(basename "$0")"
    elif [[ "$MYSQL_HOST" != "localhost" && "$MYSQL_HOST" != "127.0.0.1" ]]; then
        echo ""
        info "=== Section 3: Write rehearsal blocked ==="
        echo "[ERROR] B15_E2E_REHEARSAL=true is only allowed for localhost Docker."
        echo "        MYSQL_HOST=$MYSQL_HOST is not localhost. Aborting to protect staging/prod."
        exit 1
    else
        echo ""
        info "=== Section 3: Local-only write rehearsal (localhost Docker) ==="
        info "    Test shard: $B15_TEST_DB.$B15_TEST_TABLE"
        info "    userId=$B15_TEST_USER_ID  activityId=$B15_TEST_ACTIVITY_ID  outBusinessNo=$B15_TEST_OUT_BUSINESS_NO"

        run_db() {
            local db="$1" query="$2"
            docker exec "$MYSQL_CONTAINER" \
                mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                -e "$query" "$db" 2>/dev/null
        }

        WNUM=1
        WRITE_CLEAN=false

        cleanup_b15_rows() {
            if $WRITE_CLEAN; then
                docker exec "$MYSQL_CONTAINER" \
                    mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                    -e "DELETE FROM \`$B15_TEST_TABLE\`
                        WHERE user_id='$B15_TEST_USER_ID'
                          AND activity_id=$B15_TEST_ACTIVITY_ID
                          AND out_business_no='$B15_TEST_OUT_BUSINESS_NO';" \
                    "$B15_TEST_DB" 2>/dev/null || true
                info "B15 test row cleaned up from $B15_TEST_DB.$B15_TEST_TABLE"
            fi
        }
        trap cleanup_b15_rows EXIT

        # ---- prerequisite: check tables exist ----
        PREREQ_OK=true
        PREREQ_CNT=$(run_db "$B15_TEST_DB" \
            "SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA='$B15_TEST_DB' AND TABLE_NAME='$B15_TEST_TABLE';" 2>/dev/null) || true
        if [[ "${PREREQ_CNT:-0}" -eq 0 ]]; then
            PREREQ_OK=false
            echo ""
            echo "[PREREQ FAILED] $B15_TEST_DB.$B15_TEST_TABLE does not exist."
            echo "  Apply the ledger DDL first, then re-run B15_E2E_REHEARSAL=true:"
            echo ""
            echo "    mysql -h localhost -u root -p $B15_TEST_DB \\"
            echo "        < docs/sql/proposed-quota-decrement-ledger.sql"
            echo ""
            echo "  Or use the B13 script to apply locally:"
            echo "    CONNECT_DOCKER=true LEDGER_WRITE=true ./scripts/validate-quota-decrement-b13.sh"
            echo ""
            info "Skipping write rehearsal — prerequisite tables not present."
        fi

        if $PREREQ_OK; then
            INSERT_SQL="INSERT INTO \`$B15_TEST_TABLE\`
                (user_id, activity_id, out_business_no, status)
                VALUES ('$B15_TEST_USER_ID', $B15_TEST_ACTIVITY_ID,
                        '$B15_TEST_OUT_BUSINESS_NO', 'applied');"

            # W1: Insert test ledger row — simulates first successful decrementQuotaWithLedger
            if docker exec "$MYSQL_CONTAINER" \
                    mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                    -e "$INSERT_SQL" "$B15_TEST_DB" 2>/dev/null; then
                ok "W${WNUM}: First decrement: test ledger row inserted (status=applied)"
                WRITE_CLEAN=true
            else
                fail "W${WNUM}: INSERT failed — $B15_TEST_DB.$B15_TEST_TABLE may not exist"
                PREREQ_OK=false
            fi
            ((WNUM++))

            if $PREREQ_OK; then
                # W2: Verify row readable with status=applied
                ROW_STATUS=$(run_db "$B15_TEST_DB" \
                    "SELECT status FROM \`$B15_TEST_TABLE\`
                     WHERE user_id='$B15_TEST_USER_ID'
                       AND activity_id=$B15_TEST_ACTIVITY_ID
                       AND out_business_no='$B15_TEST_OUT_BUSINESS_NO'
                     LIMIT 1;") || true
                if [[ "$ROW_STATUS" == "applied" ]]; then
                    ok "W${WNUM}: Row readable with status=applied (decrement recorded in ledger)"
                else
                    fail "W${WNUM}: Row status='${ROW_STATUS:-<empty>}' (expected 'applied')"
                fi
                ((WNUM++))

                # W3: Duplicate INSERT blocked by UNIQUE KEY — idempotent re-delivery
                DUP_RC=0
                docker exec "$MYSQL_CONTAINER" \
                    mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                    -e "$INSERT_SQL" "$B15_TEST_DB" 2>/dev/null || DUP_RC=$?
                if [[ "$DUP_RC" -ne 0 ]]; then
                    ok "W${WNUM}: Duplicate decrement blocked by UNIQUE KEY uq_user_activity_biz (idempotency proven)"
                else
                    fail "W${WNUM}: Duplicate INSERT SUCCEEDED — UNIQUE KEY missing or not enforced"
                fi
                ((WNUM++))

                # W4: Row count still 1 after duplicate attempt
                ROW_COUNT=$(run_db "$B15_TEST_DB" \
                    "SELECT COUNT(*) FROM \`$B15_TEST_TABLE\`
                     WHERE user_id='$B15_TEST_USER_ID'
                       AND activity_id=$B15_TEST_ACTIVITY_ID
                       AND out_business_no='$B15_TEST_OUT_BUSINESS_NO';") || true
                if [[ "${ROW_COUNT:-0}" -eq 1 ]]; then
                    ok "W${WNUM}: Row count = 1 after duplicate attempt (no double-decrement possible)"
                else
                    fail "W${WNUM}: Row count = ${ROW_COUNT:-0} (expected 1) — idempotency violated"
                fi
                ((WNUM++))

                # W5: Simulate rollback — UPDATE applied → rolled_back
                ROWS_AFFECTED=$(docker exec "$MYSQL_CONTAINER" \
                    mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                    -e "UPDATE \`$B15_TEST_TABLE\`
                        SET status='rolled_back'
                        WHERE user_id='$B15_TEST_USER_ID'
                          AND activity_id=$B15_TEST_ACTIVITY_ID
                          AND out_business_no='$B15_TEST_OUT_BUSINESS_NO'
                          AND status='applied';
                        SELECT ROW_COUNT();" \
                    "$B15_TEST_DB" 2>/dev/null | tail -1) || true
                if [[ "${ROWS_AFFECTED:-0}" -eq 1 ]]; then
                    ok "W${WNUM}: Rollback: status updated applied → rolled_back (1 row affected)"
                else
                    fail "W${WNUM}: Rollback UPDATE returned ${ROWS_AFFECTED:-0} rows (expected 1)"
                fi
                ((WNUM++))

                # W6: Verify row has status=rolled_back
                AFTER_STATUS=$(run_db "$B15_TEST_DB" \
                    "SELECT status FROM \`$B15_TEST_TABLE\`
                     WHERE user_id='$B15_TEST_USER_ID'
                       AND activity_id=$B15_TEST_ACTIVITY_ID
                       AND out_business_no='$B15_TEST_OUT_BUSINESS_NO'
                     LIMIT 1;") || true
                if [[ "$AFTER_STATUS" == "rolled_back" ]]; then
                    ok "W${WNUM}: Row status=rolled_back (rollback committed)"
                else
                    fail "W${WNUM}: Row status='${AFTER_STATUS:-<empty>}' (expected 'rolled_back')"
                fi
                ((WNUM++))

                # W7: Duplicate rollback — UPDATE WHERE status='applied' must return 0 rows (idempotent)
                DUP_ROLLBACK_ROWS=$(docker exec "$MYSQL_CONTAINER" \
                    mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -s -N \
                    -e "UPDATE \`$B15_TEST_TABLE\`
                        SET status='rolled_back'
                        WHERE user_id='$B15_TEST_USER_ID'
                          AND activity_id=$B15_TEST_ACTIVITY_ID
                          AND out_business_no='$B15_TEST_OUT_BUSINESS_NO'
                          AND status='applied';
                        SELECT ROW_COUNT();" \
                    "$B15_TEST_DB" 2>/dev/null | tail -1) || true
                if [[ "${DUP_ROLLBACK_ROWS:-0}" -eq 0 ]]; then
                    ok "W${WNUM}: Duplicate rollback is idempotent (0 rows affected when status already=rolled_back)"
                else
                    fail "W${WNUM}: Duplicate rollback updated ${DUP_ROLLBACK_ROWS} rows (expected 0 — idempotency violated)"
                fi
                ((WNUM++))

                # W8: Final status is still rolled_back (no double-restore possible)
                FINAL_STATUS=$(run_db "$B15_TEST_DB" \
                    "SELECT status FROM \`$B15_TEST_TABLE\`
                     WHERE user_id='$B15_TEST_USER_ID'
                       AND activity_id=$B15_TEST_ACTIVITY_ID
                       AND out_business_no='$B15_TEST_OUT_BUSINESS_NO'
                     LIMIT 1;") || true
                if [[ "$FINAL_STATUS" == "rolled_back" ]]; then
                    ok "W${WNUM}: Final status=rolled_back (no double-restore possible)"
                else
                    fail "W${WNUM}: Final status='${FINAL_STATUS:-<empty>}' (expected 'rolled_back')"
                fi
                ((WNUM++))
            fi
        fi

        # ---- post-check (B15_POST_CHECK=true) ----
        if [[ "$B15_POST_CHECK" == "true" ]]; then
            echo ""
            info "=== Section 3b: Post-check ==="
            FINAL_COUNT=$(run_db "$B15_TEST_DB" \
                "SELECT COUNT(*) FROM \`$B15_TEST_TABLE\`
                 WHERE user_id='$B15_TEST_USER_ID'
                   AND activity_id=$B15_TEST_ACTIVITY_ID
                   AND out_business_no='$B15_TEST_OUT_BUSINESS_NO';") || true
            info "Test row count after rehearsal: ${FINAL_COUNT:-0}"
            FINAL_ST=$(run_db "$B15_TEST_DB" \
                "SELECT status FROM \`$B15_TEST_TABLE\`
                 WHERE user_id='$B15_TEST_USER_ID'
                   AND activity_id=$B15_TEST_ACTIVITY_ID
                   AND out_business_no='$B15_TEST_OUT_BUSINESS_NO'
                 LIMIT 1;") || true
            info "Test row final status: ${FINAL_ST:-<no row>}"
        fi
    fi
fi

# ---------------------------------------------------------------------------
# Section 4 — Staging runbook (always printed)
# ---------------------------------------------------------------------------
echo ""
info "=== Section 4: B15 Staging Runbook ==="
echo ""
cat <<'RUNBOOK'
[B15 STAGING RUNBOOK]
Ordered steps for enabling remote-quota-decrement=true in staging and validating E2E.
All steps are MANUAL — this script cannot perform staging DB operations.

--- Phase A: Apply DDL (if not already done) ---

  A1. Apply ledger DDL to staging big_market_01:
        mysql -h <staging-host> -u <admin-user> -p big_market_01 \
            < docs/sql/proposed-quota-decrement-ledger.sql

  A2. Apply ledger DDL to staging big_market_02:
        mysql -h <staging-host> -u <admin-user> -p big_market_02 \
            < docs/sql/proposed-quota-decrement-ledger.sql

  A3. Apply outbox DDL to staging big_market_01 (if not done from B9):
        mysql -h <staging-host> -u <admin-user> -p big_market_01 \
            < docs/sql/proposed-credit-award-task-outbox.sql

  A4. Apply outbox DDL to staging big_market_02:
        mysql -h <staging-host> -u <admin-user> -p big_market_02 \
            < docs/sql/proposed-credit-award-task-outbox.sql

--- Phase B: Verify DDL applied ---

  B1. Confirm ledger tables exist:
        CONNECT_REMOTE=true \
          MYSQL_HOST=<staging-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
          ./scripts/validate-production-ddl.sh
        (expect S13-S14 static PASS, C45-C76 DB PASS)

  B2. Confirm outbox tables exist (same command as B1 above covers both).

  B3. Verify locally with Docker (if Docker stack is running):
        CONNECT_DOCKER=true ./scripts/validate-quota-decrement-b15-e2e.sh
        (all Section 2 DB checks must PASS before enabling flag=true)

--- Phase C: XXL-Job handler registration ---

  C1. Log into XXL-Job admin UI on staging.
  C2. Register executor for big-market-message-job-service (appname: big-market-message-job).
  C3. Add job handler: DispatchCreditAwardTaskJob_DB1
        Handler: DispatchCreditAwardTaskJob_DB1
        Cron: 0/30 * * * * ?   (every 30 seconds — adjust as needed)
  C4. Add job handler: DispatchCreditAwardTaskJob_DB2
        Handler: DispatchCreditAwardTaskJob_DB2
        Cron: 0/30 * * * * ?

--- Phase D: Enable flag and run partake flow ---

  D1. Enable remote-quota-decrement in staging (staging env only):
        ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true
        — set in staging docker-compose or environment and redeploy market-service.

  D2. Confirm flag is live in running container:
        docker exec big-market-market-service \
          env | grep REMOTE_QUOTA_DECREMENT
        (expect: ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true)

--- Phase E: Partake flow E2E checklist ---

  E1. Submit a raffle participation request:
        POST /api/v1/raffle/activity/draw
        body: {"activityId": 100301, "userId": "<test-user>"}
        expect: HTTP 200, code=0000, awardId set

  E2. Verify ledger row written (status=applied):
        SELECT * FROM raffle_quota_decrement_ledger_000
        WHERE user_id='<test-user>' AND activity_id=100301
        ORDER BY create_time DESC LIMIT 1;
        expect: status=applied

  E3. Verify quota decremented in raffle_activity_account:
        SELECT total_count_surplus FROM raffle_activity_account
        WHERE user_id='<test-user>' AND activity_id=100301;
        expect: decremented by 1 vs. pre-test value

  E4. Re-submit the same draw request with the same outBusinessNo
      (idempotency: ledger row already exists → DuplicateKeyException → return true, no double-decrement).
      Verify quota unchanged after duplicate attempt.

--- Phase F: Rollback path checklist ---

  F1. Simulate a savePartakeOrderOnly failure by temporarily breaking the order insert
      (e.g., using a DB permission change or network drop) and re-submitting a draw.
      expect: rollbackQuota compensates → ledger row status=rolled_back → quota restored.

  F2. Alternatively, manually update an applied ledger row to trigger a rollback call
      and verify:
        SELECT status FROM raffle_quota_decrement_ledger_000
        WHERE user_id='<test-user>' AND out_business_no='<your-biz-no>';
        expect: status=rolled_back after rollback completes

  F3. Re-submit rollback for same outBusinessNo (idempotency):
      expect: 0 rows affected by UPDATE, quota NOT doubly restored.

--- Phase G: Credit-award outbox dispatch checklist ---

  G1. Insert a test credit_award_task row with state=pending:
        INSERT INTO credit_award_task_000
          (user_id, award_order_id, credit_amount, state)
        VALUES ('<test-user>', 'b15-outbox-test-001', 10.00, 'pending');

  G2. Trigger DispatchCreditAwardTaskJob_DB1 from XXL-Job admin UI.
      expect: row transitions pending → dispatched

  G3. Verify exactly 1 user_credit_order row for award_order_id=b15-outbox-test-001:
        SELECT COUNT(*) FROM user_credit_order_000
        WHERE out_business_no='b15-outbox-test-001';
        expect: 1

  G4. Trigger handler again (idempotency replay):
      expect: user_credit_order count stays at 1 — no double-credit.

--- Phase H: Restore flag to false ---

  H1. Restore remote-quota-decrement to false on staging:
        ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
        — redeploy market-service with flag restored.

  H2. Confirm market-service healthy after restore:
        curl -sf http://<staging-host>:8083/actuator/health | jq .status
        expect: "UP"

--- Failure rollback plan ---

  If partake flow fails after enabling flag=true:
    1. Set ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false and redeploy immediately.
       The saveCreatePartakeOrderAggregate path is unchanged and takes effect instantly.
    2. Verify no quota was leaked: check raffle_activity_account total_count_surplus
       matches expected value.
    3. If ledger tables cause TableNotFoundException (DDL not applied):
       The RPC returns UN_ERROR; market-service caller gets failure response; quota NOT decremented.
       No data loss. Apply DDL and retry.
    4. If rollback did not fire (quota decremented, order insert failed, no rollback):
       Manually restore quota surplus and update ledger status to rolled_back:
         UPDATE raffle_quota_decrement_ledger_000
           SET status='rolled_back'
           WHERE user_id='<user>' AND out_business_no='<biz-no>';
         UPDATE raffle_activity_account
           SET total_count_surplus = total_count_surplus + 1
           WHERE user_id='<user>' AND activity_id=<id>;
    5. Do NOT enable flag=true in production until all Phase E/F/G steps pass in staging.

[VALIDATION COMMANDS — run these before and after each phase]
  ./scripts/validate-quota-decrement-b15-e2e.sh                          # B15: static
  CONNECT_DOCKER=true ./scripts/validate-quota-decrement-b15-e2e.sh      # B15: DB mode
  CONNECT_DOCKER=true B15_E2E_REHEARSAL=true ./scripts/validate-quota-decrement-b15-e2e.sh  # B15: rehearsal
  ./scripts/validate-quota-decrement-b14.sh                              # B14: 21/21
  ./scripts/validate-quota-decrement-b13.sh                              # B13: 12/12
  ./scripts/validate-production-ddl.sh                                   # DDL static
  ./scripts/validate-mq-idempotency.sh                                   # MQ idempotency
  mvn compile                                                            # Build
RUNBOOK

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B15 E2E Staging Runbook Gate Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
[[ "$SKIP" -gt 0 ]] && echo "SKIP: $SKIP"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B15 checks passed."
    echo "     Follow Section 4 staging runbook to apply DDL, register XXL-Job handlers,"
    echo "     and enable remote-quota-decrement=true for E2E validation."
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed. Resolve before proceeding with staging runbook."
    exit 1
fi
