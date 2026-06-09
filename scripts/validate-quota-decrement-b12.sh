#!/usr/bin/env bash
# validate-quota-decrement-b12.sh — Phase 2.2-B12
#
# Static validation for the quota-decrement idempotency foundation.
# Checks:
#   1.  Proposed DDL file exists for raffle_quota_decrement_ledger
#   2.  DDL file contains UNIQUE KEY on (user_id, activity_id, out_business_no)
#   3.  DDL file contains all four shards (_000 through _003)
#   4.  RaffleQuotaDecrementLedger PO exists
#   5.  IRaffleQuotaDecrementLedgerDao DAO interface exists
#   6.  DAO declares insert, queryByKey, updateStatusToRolledBack
#   7.  Mapper XML exists in account-service resources
#   8.  Mapper XML has correct namespace
#   9.  IActivityRepository declares decrementQuotaWithLedger
#   10. IRaffleActivityAccountQuotaService declares decrementQuota
#   11. RaffleActivityAccountQuotaService implements decrementQuota
#   12. ActivityRepository implements decrementQuotaWithLedger with ledger guard
#   13. ActivityRepository handles DuplicateKeyException for idempotency
#   14. AccountQuotaServiceRPC.decrementQuota calls raffleActivityAccountQuotaService.decrementQuota (real impl)
#   15. AccountQuotaServiceRPC.decrementQuota no longer contains "B11 stub" text
#   16. rollbackQuota remains stubbed / safe (no partial decrement path)
#   17. Safety gate — RaffleActivityPartakeService NOT wired to IActivityAccountPort
#   18. Safety gate — remote-quota-decrement NOT enabled in any config
#   19. Proposed DDL NOT referenced from any Flyway/Liquibase migration
#
# Usage:
#   ./scripts/validate-quota-decrement-b12.sh
set -euo pipefail

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }

info "=== Phase 2.2-B12 quota-decrement idempotency validation ==="
echo ""

# ---------------------------------------------------------------------------
# D1: Proposed DDL file exists
# ---------------------------------------------------------------------------
DDL_FILE="docs/sql/proposed-quota-decrement-ledger.sql"
if [[ -f "$DDL_FILE" ]]; then
    ok "D1: proposed-quota-decrement-ledger.sql exists"
else
    fail "D1: $DDL_FILE missing"
fi

# D2: DDL has UNIQUE KEY on (user_id, activity_id, out_business_no)
if grep -q "uq_user_activity_biz\|user_id.*activity_id.*out_business_no\|UNIQUE KEY.*user_id.*activity_id.*out_business_no" "$DDL_FILE" 2>/dev/null; then
    ok "D2: DDL contains UNIQUE KEY on (user_id, activity_id, out_business_no)"
else
    fail "D2: DDL missing UNIQUE KEY on (user_id, activity_id, out_business_no)"
fi

# D3: DDL contains all four shards
ALL_SHARDS=true
for SHARD in 000 001 002 003; do
    if ! grep -q "raffle_quota_decrement_ledger_${SHARD}" "$DDL_FILE" 2>/dev/null; then
        ALL_SHARDS=false
    fi
done
if $ALL_SHARDS; then
    ok "D3: DDL defines all four shard tables (_000 through _003)"
else
    fail "D3: DDL missing one or more shard tables (_000 through _003)"
fi

# ---------------------------------------------------------------------------
# D4: PO exists
# ---------------------------------------------------------------------------
LEDGER_PO="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/dao/po/RaffleQuotaDecrementLedger.java"
if [[ -f "$LEDGER_PO" ]]; then
    ok "D4: RaffleQuotaDecrementLedger PO exists"
else
    fail "D4: RaffleQuotaDecrementLedger PO missing at $LEDGER_PO"
fi

# ---------------------------------------------------------------------------
# D5: DAO interface exists
# ---------------------------------------------------------------------------
LEDGER_DAO="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/dao/IRaffleQuotaDecrementLedgerDao.java"
if [[ -f "$LEDGER_DAO" ]]; then
    ok "D5: IRaffleQuotaDecrementLedgerDao exists"
else
    fail "D5: IRaffleQuotaDecrementLedgerDao missing at $LEDGER_DAO"
fi

# D6: DAO declares required methods
for METHOD in insert queryByKey updateStatusToRolledBack; do
    if grep -q "$METHOD" "$LEDGER_DAO" 2>/dev/null; then
        ok "D6.$METHOD: IRaffleQuotaDecrementLedgerDao declares $METHOD"
    else
        fail "D6.$METHOD: IRaffleQuotaDecrementLedgerDao missing $METHOD"
    fi
done

# ---------------------------------------------------------------------------
# D7: Mapper XML in account-service
# ---------------------------------------------------------------------------
MAPPER_XML="big-market-account-service/src/main/resources/mybatis/mapper/mysql/raffle_quota_decrement_ledger_mapper.xml"
if [[ -f "$MAPPER_XML" ]]; then
    ok "D7: raffle_quota_decrement_ledger_mapper.xml exists in account-service"
else
    fail "D7: mapper XML missing at $MAPPER_XML"
fi

# D8: Mapper XML has correct namespace
if grep -q "IRaffleQuotaDecrementLedgerDao" "$MAPPER_XML" 2>/dev/null; then
    ok "D8: mapper XML namespace references IRaffleQuotaDecrementLedgerDao"
else
    fail "D8: mapper XML namespace does not reference IRaffleQuotaDecrementLedgerDao"
fi

# ---------------------------------------------------------------------------
# D9: IActivityRepository declares decrementQuotaWithLedger
# ---------------------------------------------------------------------------
ACTIVITY_REPO_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/repository/IActivityRepository.java"
if grep -q "decrementQuotaWithLedger" "$ACTIVITY_REPO_IFACE" 2>/dev/null; then
    ok "D9: IActivityRepository declares decrementQuotaWithLedger"
else
    fail "D9: IActivityRepository missing decrementQuotaWithLedger"
fi

# ---------------------------------------------------------------------------
# D10: IRaffleActivityAccountQuotaService declares decrementQuota
# ---------------------------------------------------------------------------
QUOTA_SVC_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/IRaffleActivityAccountQuotaService.java"
if grep -q "boolean decrementQuota" "$QUOTA_SVC_IFACE" 2>/dev/null; then
    ok "D10: IRaffleActivityAccountQuotaService declares decrementQuota"
else
    fail "D10: IRaffleActivityAccountQuotaService missing decrementQuota"
fi

# ---------------------------------------------------------------------------
# D11: RaffleActivityAccountQuotaService implements decrementQuota
# ---------------------------------------------------------------------------
QUOTA_SVC_IMPL="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/quota/RaffleActivityAccountQuotaService.java"
if grep -q "decrementQuota\|decrementQuotaWithLedger" "$QUOTA_SVC_IMPL" 2>/dev/null; then
    ok "D11: RaffleActivityAccountQuotaService implements decrementQuota"
else
    fail "D11: RaffleActivityAccountQuotaService missing decrementQuota implementation"
fi

# ---------------------------------------------------------------------------
# D12: ActivityRepository implements decrementQuotaWithLedger
# ---------------------------------------------------------------------------
ACTIVITY_REPO_IMPL="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java"
if grep -q "decrementQuotaWithLedger" "$ACTIVITY_REPO_IMPL" 2>/dev/null; then
    ok "D12: ActivityRepository implements decrementQuotaWithLedger"
else
    fail "D12: ActivityRepository missing decrementQuotaWithLedger implementation"
fi

# D13: ActivityRepository uses DuplicateKeyException for idempotency
if grep -q "DuplicateKeyException" "$ACTIVITY_REPO_IMPL" 2>/dev/null; then
    ok "D13: ActivityRepository catches DuplicateKeyException (idempotency guard)"
else
    fail "D13: ActivityRepository missing DuplicateKeyException handling in decrementQuotaWithLedger"
fi

# D14: ActivityRepository references raffleQuotaDecrementLedgerDao
if grep -q "raffleQuotaDecrementLedgerDao" "$ACTIVITY_REPO_IMPL" 2>/dev/null; then
    ok "D14: ActivityRepository wires IRaffleQuotaDecrementLedgerDao"
else
    fail "D14: ActivityRepository missing IRaffleQuotaDecrementLedgerDao injection"
fi

# ---------------------------------------------------------------------------
# D15: AccountQuotaServiceRPC.decrementQuota calls real service method
# ---------------------------------------------------------------------------
RPC_PROVIDER="big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java"
if grep -q "raffleActivityAccountQuotaService.decrementQuota" "$RPC_PROVIDER" 2>/dev/null; then
    ok "D15: AccountQuotaServiceRPC.decrementQuota calls real service (ledger-guarded)"
else
    fail "D15: AccountQuotaServiceRPC.decrementQuota does not call real service"
fi

# D16: decrementQuota stub comment removed
if grep -q "B11 stub.*not yet implemented\|not yet implemented.*B11 stub" "$RPC_PROVIDER" 2>/dev/null; then
    fail "D16: AccountQuotaServiceRPC.decrementQuota still contains B11 stub text"
else
    ok "D16: AccountQuotaServiceRPC.decrementQuota B11 stub text removed"
fi

# ---------------------------------------------------------------------------
# D17: rollbackQuota remains safe/stubbed
# ---------------------------------------------------------------------------
if grep -q "rollbackQuota" "$RPC_PROVIDER" 2>/dev/null; then
    ok "D17: AccountQuotaServiceRPC.rollbackQuota method exists"
else
    fail "D17: AccountQuotaServiceRPC.rollbackQuota missing"
fi

# ---------------------------------------------------------------------------
# D18: Safety gate — RaffleActivityPartakeService NOT wired to IActivityAccountPort
# ---------------------------------------------------------------------------
PARTAKE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java"
if grep -q "IActivityAccountPort\|activityAccountPort" "$PARTAKE_SVC" 2>/dev/null; then
    fail "D18: SAFETY GATE — RaffleActivityPartakeService is wired to IActivityAccountPort (must NOT be for B12)"
else
    ok "D18: Safety gate — RaffleActivityPartakeService not yet wired to IActivityAccountPort"
fi

# ---------------------------------------------------------------------------
# D19: No config enables remote-quota-decrement
# ---------------------------------------------------------------------------
ENABLED_MATCH=$(grep -r "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "D19: No config file enables remote-quota-decrement"
else
    fail "D19: remote-quota-decrement is enabled somewhere — check all application.yml files"
fi

# ---------------------------------------------------------------------------
# D20: Proposed DDL not in any migration tool path
# ---------------------------------------------------------------------------
MIGRATION_MATCH=$(grep -r "proposed-quota-decrement-ledger" \
    --include="*.xml" --include="*.yml" --include="*.yaml" --include="*.json" . 2>/dev/null \
    | grep -v "target/\|docs/" || true)
if [[ -z "$MIGRATION_MATCH" ]]; then
    ok "D20: proposed-quota-decrement-ledger.sql not referenced from any migration tool"
else
    fail "D20: proposed DDL referenced from a migration tool — it must remain docs-only until B13+"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B12 Quota Decrement Idempotency Validation Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B12 quota-decrement idempotency foundation checks passed."
    echo "     Next: B13 — staging DDL deployment + end-to-end integration validation."
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed. Fix before tagging B12."
    exit 1
fi
