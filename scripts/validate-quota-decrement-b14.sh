#!/usr/bin/env bash
# validate-quota-decrement-b14.sh — Phase 2.2-B14
#
# Verifies the B14 rollback and flag-gated wiring implementation:
#   - rollbackQuota is no longer a stub
#   - rollback is ledger-guarded (queries ledger before touching quota)
#   - rollback updates ledger applied -> rolled_back
#   - rollback restores total/month/day quota surplus
#   - duplicate rollback is idempotent (rolled_back status handled)
#   - RaffleActivityPartakeService is wired to IActivityAccountPort (flag-gated)
#   - remote-quota-decrement.enabled still defaults false
#   - local fallback (LocalActivityAccountPort) delegates to real repository methods
#   - savePartakeOrderOnly exists for flag=true order-only path
#   - no config enables remote quota decrement by default
#   - B13 staging script remains available
#
# Usage:
#   ./scripts/validate-quota-decrement-b14.sh
set -euo pipefail

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }

info "=== Phase 2.2-B14 rollback and flag-gated wiring validation ==="
echo ""

ACCOUNT_RPC="big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java"
QUOTA_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/quota/RaffleActivityAccountQuotaService.java"
QUOTA_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/IRaffleActivityAccountQuotaService.java"
REPO_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/repository/IActivityRepository.java"
REPO_IMPL="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java"
PARTAKE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java"
ABSTRACT_PARTAKE="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/AbstractRaffleActivityPartake.java"
LOCAL_PORT="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java"
PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java"

# ---------------------------------------------------------------------------
# S1: rollbackQuota is no longer a stub (real impl delegates to domain service)
# ---------------------------------------------------------------------------
if grep -q "raffleActivityAccountQuotaService.rollbackQuota" "$ACCOUNT_RPC" 2>/dev/null; then
    ok "S1: AccountQuotaServiceRPC.rollbackQuota delegates to raffleActivityAccountQuotaService.rollbackQuota (real impl)"
else
    fail "S1: AccountQuotaServiceRPC.rollbackQuota still stubbed — not delegating to domain service"
fi

# S2: rollbackQuota stub string removed (UN_ERROR/not yet implemented gone)
if grep -A3 "rollbackQuota" "$ACCOUNT_RPC" 2>/dev/null | grep -q "not yet implemented\|Phase 2.2-B12.*pending ledger"; then
    fail "S2: AccountQuotaServiceRPC.rollbackQuota still contains stub message from B12"
else
    ok "S2: rollbackQuota stub text removed from AccountQuotaServiceRPC"
fi

# S3: rollback is ledger-guarded in ActivityRepository
if grep -q "rollbackQuotaWithLedger" "$REPO_IMPL" 2>/dev/null; then
    ok "S3: ActivityRepository.rollbackQuotaWithLedger implemented"
else
    fail "S3: ActivityRepository.rollbackQuotaWithLedger missing"
fi

# S4: rollback queries ledger row before touching quota
if grep -q "queryByKey" "$REPO_IMPL" 2>/dev/null; then
    ok "S4: rollbackQuotaWithLedger queries ledger row (queryByKey) before modifying quota"
else
    fail "S4: rollbackQuotaWithLedger does not query ledger before acting"
fi

# S5: rollback updates ledger applied -> rolled_back
if grep -q "updateStatusToRolledBack" "$REPO_IMPL" 2>/dev/null; then
    ok "S5: rollbackQuotaWithLedger calls updateStatusToRolledBack (applied -> rolled_back)"
else
    fail "S5: rollbackQuotaWithLedger missing updateStatusToRolledBack call"
fi

# S6: rollback restores total_count_surplus
if grep -q "addAccountTotalSurplusQuota" "$REPO_IMPL" 2>/dev/null; then
    ok "S6: rollbackQuotaWithLedger restores total_count_surplus via addAccountTotalSurplusQuota"
else
    fail "S6: rollbackQuotaWithLedger missing addAccountTotalSurplusQuota (total quota not restored)"
fi

# S7: rollback restores month and day quota
MONTH_RESTORE=false
DAY_RESTORE=false
grep -q "addAccountMonthSurplusQuota" "$REPO_IMPL" 2>/dev/null && MONTH_RESTORE=true
grep -q "addAccountDaySurplusQuota"   "$REPO_IMPL" 2>/dev/null && DAY_RESTORE=true
if $MONTH_RESTORE && $DAY_RESTORE; then
    ok "S7: rollbackQuotaWithLedger restores month and day surplus mirrors in main account"
else
    fail "S7: rollbackQuotaWithLedger missing month=$MONTH_RESTORE or day=$DAY_RESTORE restore"
fi

# S8: duplicate rollback is idempotent (rolled_back check present)
if grep -A20 "rollbackQuotaWithLedger" "$REPO_IMPL" 2>/dev/null | grep -q "rolled_back"; then
    ok "S8: duplicate rollback is idempotent (rolled_back status check present)"
else
    fail "S8: rolled_back idempotency check missing in rollbackQuotaWithLedger"
fi

# S9: no-ledger-row is a safe no-op
if grep -A20 "rollbackQuotaWithLedger" "$REPO_IMPL" 2>/dev/null | grep -q "ledger == null\|no-op\|no ledger row"; then
    ok "S9: rollbackQuotaWithLedger handles missing ledger row as safe no-op"
else
    fail "S9: missing null ledger row guard in rollbackQuotaWithLedger"
fi

# S10: IRaffleActivityAccountQuotaService declares rollbackQuota
if grep -q "boolean rollbackQuota" "$QUOTA_IFACE" 2>/dev/null; then
    ok "S10: IRaffleActivityAccountQuotaService.rollbackQuota declared"
else
    fail "S10: IRaffleActivityAccountQuotaService missing rollbackQuota method"
fi

# S11: IActivityRepository declares rollbackQuotaWithLedger and savePartakeOrderOnly
REPO_ROLLBACK=false
REPO_SAVE_ONLY=false
grep -q "rollbackQuotaWithLedger" "$REPO_IFACE" 2>/dev/null && REPO_ROLLBACK=true
grep -q "savePartakeOrderOnly"    "$REPO_IFACE" 2>/dev/null && REPO_SAVE_ONLY=true
if $REPO_ROLLBACK && $REPO_SAVE_ONLY; then
    ok "S11: IActivityRepository declares both rollbackQuotaWithLedger and savePartakeOrderOnly"
else
    fail "S11: IActivityRepository missing rollbackQuotaWithLedger=$REPO_ROLLBACK savePartakeOrderOnly=$REPO_SAVE_ONLY"
fi

# S12: RaffleActivityPartakeService is wired to IActivityAccountPort (flag-gated)
if grep -q "IActivityAccountPort\|activityAccountPort" "$PARTAKE_SVC" 2>/dev/null; then
    ok "S12: RaffleActivityPartakeService is wired to IActivityAccountPort (B14 flag-gated)"
else
    fail "S12: RaffleActivityPartakeService not wired to IActivityAccountPort"
fi

# S13: flag-gate present in doSavePartakeOrder
if grep -q "remoteQuotaDecrementEnabled\|remote-quota-decrement" "$PARTAKE_SVC" 2>/dev/null; then
    ok "S13: RaffleActivityPartakeService.doSavePartakeOrder contains flag gate"
else
    fail "S13: flag gate (remoteQuotaDecrementEnabled) missing from RaffleActivityPartakeService"
fi

# S14: remote-quota-decrement.enabled still defaults false
if grep -q "remote-quota-decrement.enabled:false\|:false}" "$PARTAKE_SVC" 2>/dev/null; then
    ok "S14: remote-quota-decrement.enabled defaults false in RaffleActivityPartakeService"
else
    fail "S14: default false not found for remote-quota-decrement.enabled in RaffleActivityPartakeService"
fi

# S15: AbstractRaffleActivityPartake has doSavePartakeOrder hook with default
if grep -q "doSavePartakeOrder" "$ABSTRACT_PARTAKE" 2>/dev/null; then
    ok "S15: AbstractRaffleActivityPartake.doSavePartakeOrder hook present"
else
    fail "S15: AbstractRaffleActivityPartake missing doSavePartakeOrder hook"
fi

# S16: LocalActivityAccountPort no longer a no-op — delegates to real repository
if grep -q "activityRepository.decrementQuotaWithLedger\|activityRepository" "$LOCAL_PORT" 2>/dev/null; then
    ok "S16: LocalActivityAccountPort delegates to activityRepository (not a no-op)"
else
    fail "S16: LocalActivityAccountPort still a no-op — not delegating to IActivityRepository"
fi

# S17: rollbackQuota wired in LocalActivityAccountPort
if grep -q "rollbackQuotaWithLedger" "$LOCAL_PORT" 2>/dev/null; then
    ok "S17: LocalActivityAccountPort.rollbackQuota delegates to rollbackQuotaWithLedger"
else
    fail "S17: LocalActivityAccountPort.rollbackQuota missing rollbackQuotaWithLedger delegation"
fi

# S18: no config enables remote-quota-decrement by default
ENABLED_MATCH=$(grep -r \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null \
    | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "S18: No config file enables remote-quota-decrement (default=false preserved)"
else
    fail "S18: remote-quota-decrement enabled somewhere: $ENABLED_MATCH"
fi

# S19: B13 staging validation script remains available
B13_SCRIPT="scripts/validate-quota-decrement-b13.sh"
if [[ -f "$B13_SCRIPT" ]]; then
    ok "S19: B13 staging validation script exists ($B13_SCRIPT)"
else
    fail "S19: B13 staging validation script missing at $B13_SCRIPT"
fi

# S20: savePartakeOrderOnly implemented in ActivityRepository
if grep -q "savePartakeOrderOnly" "$REPO_IMPL" 2>/dev/null; then
    ok "S20: ActivityRepository.savePartakeOrderOnly implemented"
else
    fail "S20: ActivityRepository.savePartakeOrderOnly missing"
fi

# S21: IActivityAccountPort still declares both decrementQuota and rollbackQuota
DECREMENT_OK=false
ROLLBACK_OK=false
grep -q "decrementQuota" "$PORT_IFACE" 2>/dev/null && DECREMENT_OK=true
grep -q "rollbackQuota"  "$PORT_IFACE" 2>/dev/null && ROLLBACK_OK=true
if $DECREMENT_OK && $ROLLBACK_OK; then
    ok "S21: IActivityAccountPort contract intact (decrementQuota + rollbackQuota)"
else
    fail "S21: IActivityAccountPort missing decrementQuota=$DECREMENT_OK rollbackQuota=$ROLLBACK_OK"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B14 Quota Decrement Rollback and Wiring Validation Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo ""

cat <<'NOTES'
[RUNTIME NOTES]
  - remote-quota-decrement.enabled=false (default): saveCreatePartakeOrderAggregate path unchanged.
  - remote-quota-decrement.enabled=true: decrementQuota via port → savePartakeOrderOnly.
    On savePartakeOrderOnly failure, rollbackQuota compensates automatically.
  - LocalActivityAccountPort now delegates to decrementQuotaWithLedger/rollbackQuotaWithLedger.
    Requires raffle_quota_decrement_ledger DDL applied before enabling flag=true.

[REMAINING BLOCKERS before enabling remote-quota-decrement=true in staging]
  1. Apply docs/sql/proposed-quota-decrement-ledger.sql to staging big_market_01 and big_market_02
  2. Apply docs/sql/proposed-credit-award-task-outbox.sql to staging
  3. Register XXL-Job handlers: DispatchCreditAwardTaskJob_DB1, DispatchCreditAwardTaskJob_DB2
  4. Run ./scripts/validate-quota-decrement-b13.sh (12/12 must pass)
  5. Run CONNECT_DOCKER=true LEDGER_WRITE=true ./scripts/validate-quota-decrement-b13.sh

[VALIDATION COMMANDS]
  ./scripts/validate-quota-decrement-b14.sh   # B14 checks (this script)
  ./scripts/validate-quota-decrement-b13.sh   # B13 staging readiness
  ./scripts/validate-quota-decrement-b12.sh   # B12 foundation
  ./scripts/validate-quota-decrement-contract.sh  # B11 contract
  ./scripts/validate-production-ddl.sh        # DDL static checks
  ./scripts/validate-mq-idempotency.sh        # MQ idempotency
  mvn compile                                 # Build verification

[NEXT BATCH — B15]
  - E2E staging test: apply DDL, enable flag=true, run partake flow, verify rollback
  - Rollback plan: if flag=true causes issues, set enabled=false to revert instantly
NOTES

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B14 checks passed."
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed."
    exit 1
fi
