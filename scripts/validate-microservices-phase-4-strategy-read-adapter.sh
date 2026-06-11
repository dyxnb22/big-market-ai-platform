#!/usr/bin/env bash
# validate-microservices-phase-4-strategy-read-adapter.sh
# Deterministic repo-only validation for the Phase 4-D strategy read adapter boundary.
#
# Checks:
#   1.  IStrategyReadAdapter exists and exposes the two read methods
#   2.  LocalStrategyReadAdapter exists and is @ConditionalOnMissingBean
#   3.  LocalStrategyReadAdapter has no DubboReference
#   4.  LocalStrategyReadAdapter uses account participation count methods
#   5.  RaffleStrategyController read endpoints route through IStrategyReadAdapter
#   6.  RaffleStrategyController draw/write endpoints (randomRaffle, strategyArmory) are untouched
#   7.  Legacy RPC provider has the provider gate with matchIfMissing=true; HTTP controller is not gated
#   8.  RaffleStrategyController no longer directly calls IRaffleAward/IRaffleRule in read bodies
#   9.  StrategyRemoteReadAdapter exists, uses @DubboReference(check=false), defaults false, falls back local
#  10.  strategy.service.remote-read.enabled defaults false in market-service application.yml
#  11.  strategy.service.remote-read.enabled env var defaults false in docker-compose.yml
#  12.  strategy.legacy-rpc-provider.enabled defaults true in market-service application.yml
#  13.  STRATEGY_LEGACY_RPC_PROVIDER_ENABLED defaults true in docker-compose.yml
#  14.  Dangerous Phase 2/3/4 flags remain false in all configs
#  15.  No generated evidence files tracked in git

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

check_file() {
  local label="$1" path="$2"
  if [ -f "$ROOT/$path" ]; then
    pass "$label: $path"
  else
    fail "$label: missing $path"
  fi
}

check_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file missing $path"
    return
  fi
  if grep -qE "$pattern" "$ROOT/$path"; then
    pass "$label"
  else
    fail "$label: pattern not found in $path: $pattern"
  fi
}

check_not_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file missing $path"
    return
  fi
  if grep -qE "$pattern" "$ROOT/$path"; then
    fail "$label: forbidden pattern found in $path: $pattern"
  else
    pass "$label"
  fi
}

check_contains_any() {
  local label="$1" path="$2"
  shift 2
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file missing $path"
    return
  fi
  for pattern in "$@"; do
    if grep -qE "$pattern" "$ROOT/$path"; then
      pass "$label"
      return
    fi
  done
  fail "$label: none of the patterns found in $path"
}

ISTRA="big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IStrategyReadAdapter.java"
LSTRA="big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java"
CTRL="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java"
RPC="big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java"
SREM="big-market-market-service/src/main/java/com/dyx/market/market/config/StrategyRemoteReadAdapter.java"
APPL="big-market-market-service/src/main/resources/application.yml"
DC="docker-compose.yml"

echo "=== Phase 4-D Strategy Read Adapter Validation ==="
echo ""

# 1. IStrategyReadAdapter exists and exposes the two read methods
check_file "1a. IStrategyReadAdapter exists" "$ISTRA"
check_contains "1b. IStrategyReadAdapter: queryRaffleAwardList" "$ISTRA" "queryRaffleAwardList"
check_contains "1c. IStrategyReadAdapter: queryRaffleStrategyRuleWeight" "$ISTRA" "queryRaffleStrategyRuleWeight"

# 2. LocalStrategyReadAdapter exists and is @ConditionalOnMissingBean
check_file "2a. LocalStrategyReadAdapter exists" "$LSTRA"
check_contains "2b. LocalStrategyReadAdapter @ConditionalOnMissingBean" "$LSTRA" "@ConditionalOnMissingBean"

# 3. LocalStrategyReadAdapter has no DubboReference
check_not_contains "3. LocalStrategyReadAdapter no DubboReference" "$LSTRA" "@DubboReference"

# 4. LocalStrategyReadAdapter uses account participation count methods
check_contains "4a. LocalStrategyReadAdapter uses dayPartakeCount" "$LSTRA" "queryRaffleActivityAccountDayPartakeCount"
check_contains "4b. LocalStrategyReadAdapter uses totalUseCount" "$LSTRA" "queryRaffleActivityAccountPartakeCount"

# 5. RaffleStrategyController read endpoints route through IStrategyReadAdapter
check_contains "5a. RaffleStrategyController injects IStrategyReadAdapter" "$CTRL" "IStrategyReadAdapter"
check_contains "5b. queryRaffleAwardList routes through adapter" "$CTRL" "strategyReadAdapter\.queryRaffleAwardList"
check_contains "5c. queryRaffleStrategyRuleWeight routes through adapter" "$CTRL" "strategyReadAdapter\.queryRaffleStrategyRuleWeight"

# 6. Draw/write endpoints (randomRaffle, strategyArmory) are untouched
check_contains "6a. randomRaffle still uses raffleStrategy.performRaffle" "$CTRL" "raffleStrategy\.performRaffle"
check_contains "6b. strategyArmory still uses strategyArmory.assembleLotteryStrategy" "$CTRL" "strategyArmory\.assembleLotteryStrategy"

# 7. Legacy RPC provider has provider gate with matchIfMissing=true; HTTP controller remains always registered
check_file "7a. legacy RaffleStrategyServiceRPC exists" "$RPC"
check_contains "7b. legacy RPC has @DubboService" "$RPC" "@DubboService"
check_contains "7c. legacy RPC provider gate ConditionalOnProperty" "$RPC" "strategy\.legacy-rpc-provider\.enabled"
check_contains "7d. legacy RPC provider gate matchIfMissing=true" "$RPC" "matchIfMissing\s*=\s*true"
check_not_contains "7e. HTTP controller is not gated by strategy legacy flag" "$CTRL" "strategy\.legacy-rpc-provider\.enabled"
check_not_contains "7f. HTTP controller is not a Dubbo provider" "$CTRL" "@DubboService"

# 8. Controller no longer directly calls IRaffleAward/IRaffleRule in read bodies
# (The field injection should be gone; draw path is untouched)
check_not_contains "8a. RaffleStrategyController no IRaffleAward field" "$CTRL" "@Resource[^;]*IRaffleAward|IRaffleAward raffleAward"
check_not_contains "8b. RaffleStrategyController no IRaffleRule field" "$CTRL" "@Resource[^;]*IRaffleRule|IRaffleRule raffleRule"

# 9. StrategyRemoteReadAdapter exists, uses @DubboReference(check=false), defaults false, has local fallback
check_file "9a. StrategyRemoteReadAdapter exists" "$SREM"
check_contains "9b. StrategyRemoteReadAdapter @DubboReference check=false" "$SREM" "check\s*=\s*false"
check_contains "9c. StrategyRemoteReadAdapter strategy.service.remote-read.enabled default false" "$SREM" "remote-read\.enabled:false"
check_contains "9d. StrategyRemoteReadAdapter has local fallback method" "$SREM" "localQuery"

# 10. strategy.service.remote-read.enabled defaults false in application.yml
check_contains "10. strategy.service.remote-read.enabled:false in application.yml" "$APPL" "STRATEGY_SERVICE_REMOTE_READ_ENABLED:false"

# 11. STRATEGY_SERVICE_REMOTE_READ_ENABLED defaults false in docker-compose.yml
check_contains "11. STRATEGY_SERVICE_REMOTE_READ_ENABLED:-false in docker-compose.yml" "$DC" "STRATEGY_SERVICE_REMOTE_READ_ENABLED.*:-false"

# 12. strategy.legacy-rpc-provider.enabled defaults true in application.yml
check_contains "12. STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:true in application.yml" "$APPL" "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:true"

# 13. STRATEGY_LEGACY_RPC_PROVIDER_ENABLED defaults true in docker-compose.yml
check_contains "13. STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:-true in docker-compose.yml" "$DC" "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED.*:-true"

# 14. Dangerous Phase 2/3/4 flags remain false
check_not_contains "14a. ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED not true" "$DC" "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED[^-].*true"
check_not_contains "14b. ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED not true" "$DC" "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED[^-].*true"
check_not_contains "14c. REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED not true" "$DC" "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED.*:-true"
check_not_contains "14d. REBATE_SERVICE_REMOTE_READ_ENABLED not true" "$DC" "REBATE_SERVICE_REMOTE_READ_ENABLED.*:-true"
check_not_contains "14e. STRATEGY_SERVICE_REMOTE_READ_ENABLED not true" "$DC" "STRATEGY_SERVICE_REMOTE_READ_ENABLED.*:-true"

# 15. No generated evidence files tracked in git
if git -C "$ROOT" ls-files --error-unmatch docs/evidence/generated 2>/dev/null | grep -q .; then
  fail "15. docs/evidence/generated tracked in git"
else
  pass "15. docs/evidence/generated not tracked in git"
fi

echo ""
echo "=== Results: PASS=$PASS FAIL=$FAIL ==="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
