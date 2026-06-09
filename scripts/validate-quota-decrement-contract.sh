#!/usr/bin/env bash
# validate-quota-decrement-contract.sh — Phase 2.2-B11
#
# Static validation for the quota-decrement domain port contract.
# Checks:
#   1. IActivityAccountPort interface exists with decrementQuota + rollbackQuota
#   2. LocalActivityAccountPort implements IActivityAccountPort (local no-op)
#   3. AccountRemoteActivityAccountPort implements IActivityAccountPort (remote stub)
#   4. AccountQuotaRollbackRequestDTO exists in big-market-api
#   5. IAccountQuotaService declares both decrementQuota and rollbackQuota
#   6. AccountQuotaServiceRPC implements rollbackQuota stub
#   7. Feature flag remote-quota-decrement.enabled=false in market-service yml
#   8. RaffleActivityPartakeService NOT wired to IActivityAccountPort (safety gate)
#   9. Remote flag NOT enabled in any application.yml
#
# Usage:
#   ./scripts/validate-quota-decrement-contract.sh
set -euo pipefail

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }

info "=== Phase 2.2-B11 quota-decrement contract validation ==="
echo ""

# ---------------------------------------------------------------------------
# C1: IActivityAccountPort interface exists
# ---------------------------------------------------------------------------
PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java"
if [[ -f "$PORT_IFACE" ]]; then
    ok "C1: IActivityAccountPort interface file exists"
else
    fail "C1: IActivityAccountPort missing at $PORT_IFACE"
fi

# C2: IActivityAccountPort declares decrementQuota
if grep -q "decrementQuota" "$PORT_IFACE" 2>/dev/null; then
    ok "C2: IActivityAccountPort declares decrementQuota"
else
    fail "C2: IActivityAccountPort missing decrementQuota"
fi

# C3: IActivityAccountPort declares rollbackQuota
if grep -q "rollbackQuota" "$PORT_IFACE" 2>/dev/null; then
    ok "C3: IActivityAccountPort declares rollbackQuota"
else
    fail "C3: IActivityAccountPort missing rollbackQuota"
fi

# ---------------------------------------------------------------------------
# C4: LocalActivityAccountPort (local no-op impl)
# ---------------------------------------------------------------------------
LOCAL_PORT="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java"
if [[ -f "$LOCAL_PORT" ]]; then
    ok "C4: LocalActivityAccountPort file exists"
else
    fail "C4: LocalActivityAccountPort missing at $LOCAL_PORT"
fi

# C5: LocalActivityAccountPort implements IActivityAccountPort
if grep -q "implements IActivityAccountPort" "$LOCAL_PORT" 2>/dev/null; then
    ok "C5: LocalActivityAccountPort implements IActivityAccountPort"
else
    fail "C5: LocalActivityAccountPort does not implement IActivityAccountPort"
fi

# C6: LocalActivityAccountPort has @ConditionalOnMissingBean
if grep -q "ConditionalOnMissingBean" "$LOCAL_PORT" 2>/dev/null; then
    ok "C6: LocalActivityAccountPort uses @ConditionalOnMissingBean"
else
    fail "C6: LocalActivityAccountPort missing @ConditionalOnMissingBean"
fi

# ---------------------------------------------------------------------------
# C7: AccountRemoteActivityAccountPort (remote stub)
# ---------------------------------------------------------------------------
REMOTE_PORT="big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteActivityAccountPort.java"
if [[ -f "$REMOTE_PORT" ]]; then
    ok "C7: AccountRemoteActivityAccountPort file exists"
else
    fail "C7: AccountRemoteActivityAccountPort missing at $REMOTE_PORT"
fi

# C8: Remote port implements IActivityAccountPort
if grep -q "implements IActivityAccountPort" "$REMOTE_PORT" 2>/dev/null; then
    ok "C8: AccountRemoteActivityAccountPort implements IActivityAccountPort"
else
    fail "C8: AccountRemoteActivityAccountPort does not implement IActivityAccountPort"
fi

# C9: Remote port gated behind @ConditionalOnProperty
if grep -q "ConditionalOnProperty" "$REMOTE_PORT" 2>/dev/null && \
   grep -q "remote-quota-decrement" "$REMOTE_PORT" 2>/dev/null; then
    ok "C9: AccountRemoteActivityAccountPort gated by ConditionalOnProperty(remote-quota-decrement)"
else
    fail "C9: AccountRemoteActivityAccountPort not properly gated"
fi

# ---------------------------------------------------------------------------
# C10: AccountQuotaRollbackRequestDTO in big-market-api
# ---------------------------------------------------------------------------
ROLLBACK_DTO="big-market-api/src/main/java/com/dyx/market/trigger/api/dto/AccountQuotaRollbackRequestDTO.java"
if [[ -f "$ROLLBACK_DTO" ]]; then
    ok "C10: AccountQuotaRollbackRequestDTO exists"
else
    fail "C10: AccountQuotaRollbackRequestDTO missing at $ROLLBACK_DTO"
fi

# ---------------------------------------------------------------------------
# C11: IAccountQuotaService declares rollbackQuota
# ---------------------------------------------------------------------------
QUOTA_API="big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountQuotaService.java"
if grep -q "rollbackQuota" "$QUOTA_API" 2>/dev/null; then
    ok "C11: IAccountQuotaService declares rollbackQuota"
else
    fail "C11: IAccountQuotaService missing rollbackQuota"
fi

# C12: IAccountQuotaService declares decrementQuota
if grep -q "decrementQuota" "$QUOTA_API" 2>/dev/null; then
    ok "C12: IAccountQuotaService declares decrementQuota"
else
    fail "C12: IAccountQuotaService missing decrementQuota"
fi

# ---------------------------------------------------------------------------
# C13: AccountQuotaServiceRPC implements rollbackQuota stub
# ---------------------------------------------------------------------------
RPC_PROVIDER="big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java"
if grep -q "rollbackQuota" "$RPC_PROVIDER" 2>/dev/null; then
    ok "C13: AccountQuotaServiceRPC implements rollbackQuota"
else
    fail "C13: AccountQuotaServiceRPC missing rollbackQuota implementation"
fi

# ---------------------------------------------------------------------------
# C14: Feature flag default=false in market-service yml
# ---------------------------------------------------------------------------
MARKET_YML="big-market-market-service/src/main/resources/application.yml"
if grep -q "remote-quota-decrement" "$MARKET_YML" 2>/dev/null; then
    ok "C14: remote-quota-decrement flag present in market-service application.yml"
else
    fail "C14: remote-quota-decrement flag missing from market-service application.yml"
fi

if grep -q "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:false" "$MARKET_YML" 2>/dev/null; then
    ok "C15: remote-quota-decrement defaults to false"
else
    fail "C15: remote-quota-decrement not defaulting to false"
fi

# ---------------------------------------------------------------------------
# C16: Safety gate — RaffleActivityPartakeService NOT wired to IActivityAccountPort
# ---------------------------------------------------------------------------
PARTAKE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java"
if grep -q "IActivityAccountPort\|activityAccountPort" "$PARTAKE_SVC" 2>/dev/null; then
    fail "C16: SAFETY GATE — RaffleActivityPartakeService is wired to IActivityAccountPort (must NOT be for B11)"
else
    ok "C16: Safety gate — RaffleActivityPartakeService not yet wired to IActivityAccountPort"
fi

# C17: AbstractRaffleActivityPartake NOT wired to IActivityAccountPort
ABSTRACT_PARTAKE="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/AbstractRaffleActivityPartake.java"
if grep -q "IActivityAccountPort\|activityAccountPort" "$ABSTRACT_PARTAKE" 2>/dev/null; then
    fail "C17: SAFETY GATE — AbstractRaffleActivityPartake is wired to IActivityAccountPort (must NOT be for B11)"
else
    ok "C17: Safety gate — AbstractRaffleActivityPartake not yet wired to IActivityAccountPort"
fi

# ---------------------------------------------------------------------------
# C18: No application.yml has remote-quota-decrement=true
# ---------------------------------------------------------------------------
ENABLED_MATCH=$(grep -r "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "C18: No config file enables remote-quota-decrement"
else
    fail "C18: remote-quota-decrement is enabled somewhere — check all application.yml files"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B11 Contract Validation Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B11 quota-decrement contract checks passed."
    echo "     Next: B12 — implement account-service idempotency ledger DDL + server logic."
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed. Fix before tagging B11."
    exit 1
fi
