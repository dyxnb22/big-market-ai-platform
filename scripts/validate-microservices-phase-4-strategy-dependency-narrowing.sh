#!/usr/bin/env bash
# validate-microservices-phase-4-strategy-dependency-narrowing.sh
# Deterministic repo-only validation for Phase 4-E strategy dependency narrowing.
#
# Checks:
#   1.  big-market-strategy-service does NOT depend on big-market-trigger
#   2.  StrategyReadServiceRPC does not import forbidden domains (activity, credit, rebate, award)
#   3.  StrategyReadServiceRPC does not expose draw/write/mutation methods
#   4.  StrategyReadServiceRPC uses IStrategyAccountParticipationPort (not hardcoded 0)
#   5.  IStrategyAccountParticipationPort exists in strategy-service port package
#   6.  LocalStrategyAccountParticipationPort exists and uses @DubboReference(check=false)
#   7.  LocalStrategyAccountParticipationPort does not import trigger.adapter or trigger.http
#   8.  strategy-service application scans only allowed packages (not trigger, not http)
#   9.  IStrategyReadService in big-market-api exposes exactly the two read methods
#  10.  No draw/write methods on IStrategyReadService
#  11.  strategy.service.remote-read.enabled defaults false everywhere it is declared
#  12.  Legacy strategy RPC provider is gated without gating the HTTP controller
#  13.  No generated evidence files tracked in git

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

SPOM="big-market-strategy-service/pom.xml"
RPC="big-market-strategy-service/src/main/java/com/dyx/market/strategy/provider/StrategyReadServiceRPC.java"
IPORT="big-market-strategy-service/src/main/java/com/dyx/market/strategy/port/IStrategyAccountParticipationPort.java"
LPORT="big-market-strategy-service/src/main/java/com/dyx/market/strategy/port/LocalStrategyAccountParticipationPort.java"
APP="big-market-strategy-service/src/main/java/com/dyx/market/strategy/StrategyServiceApplication.java"
ISVC="big-market-api/src/main/java/com/dyx/market/trigger/api/IStrategyReadService.java"
CTRL="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java"
LEGACY_RPC="big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java"

echo "=== Phase 4-E Strategy Dependency Narrowing Validation ==="
echo ""

# 1. strategy-service does NOT depend on big-market-trigger
check_not_contains "1. strategy-service pom does not import big-market-trigger" "$SPOM" "big-market-trigger"

# 2. StrategyReadServiceRPC does not import forbidden domains
check_not_contains "2a. RPC no activity domain import" "$RPC" "import.*domain\.activity"
check_not_contains "2b. RPC no credit domain import" "$RPC" "import.*domain\.credit"
check_not_contains "2c. RPC no rebate domain import" "$RPC" "import.*domain\.rebate"
check_not_contains "2d. RPC no award domain import" "$RPC" "import.*domain\.award"

# 3. StrategyReadServiceRPC does not expose draw/write/mutation methods
check_not_contains "3a. RPC no performRaffle" "$RPC" "performRaffle"
check_not_contains "3b. RPC no randomRaffle" "$RPC" "randomRaffle"
check_not_contains "3c. RPC no assembleLotteryStrategy" "$RPC" "assembleLotteryStrategy"
check_not_contains "3d. RPC no subtractionAwardStock" "$RPC" "subtractionAwardStock"
check_not_contains "3e. RPC no strategyArmory" "$RPC" "strategyArmory"

# 4. StrategyReadServiceRPC uses IStrategyAccountParticipationPort (not hardcoded 0)
check_contains "4a. RPC uses IStrategyAccountParticipationPort" "$RPC" "IStrategyAccountParticipationPort"
check_contains "4b. RPC calls dayPartakeCount via port" "$RPC" "queryRaffleActivityAccountDayPartakeCount"
check_contains "4c. RPC calls totalUseCount via port" "$RPC" "queryRaffleActivityAccountPartakeCount"

# 5. IStrategyAccountParticipationPort exists
check_file "5. IStrategyAccountParticipationPort exists" "$IPORT"

# 6. LocalStrategyAccountParticipationPort exists and uses @DubboReference(check=false)
check_file "6a. LocalStrategyAccountParticipationPort exists" "$LPORT"
check_contains "6b. LocalStrategyAccountParticipationPort @DubboReference check=false" "$LPORT" "check\s*=\s*false"
check_contains "6c. LocalStrategyAccountParticipationPort uses IAccountQuotaService" "$LPORT" "IAccountQuotaService"

# 7. LocalStrategyAccountParticipationPort does not import trigger.adapter or trigger.http
check_not_contains "7a. port no trigger.adapter import" "$LPORT" "import.*trigger\.adapter"
check_not_contains "7b. port no trigger.http import" "$LPORT" "import.*trigger\.http"

# 8. Strategy-service application scans only allowed packages
check_file "8a. StrategyServiceApplication exists" "$APP"
check_not_contains "8b. application does not scan trigger package" "$APP" "com\.dyx\.market\.trigger"
check_not_contains "8c. application does not scan market\.market package" "$APP" "com\.dyx\.market\.market"

# 9. IStrategyReadService exposes exactly the two read methods
check_file "9a. IStrategyReadService exists" "$ISVC"
check_contains "9b. IStrategyReadService: queryRaffleAwardList" "$ISVC" "queryRaffleAwardList"
check_contains "9c. IStrategyReadService: queryRaffleStrategyRuleWeight" "$ISVC" "queryRaffleStrategyRuleWeight"

# 10. IStrategyReadService has no draw/write methods
check_not_contains "10a. IStrategyReadService no performRaffle" "$ISVC" "performRaffle"
check_not_contains "10b. IStrategyReadService no randomRaffle" "$ISVC" "randomRaffle"
check_not_contains "10c. IStrategyReadService no assembleLotteryStrategy" "$ISVC" "assembleLotteryStrategy"

# 11. strategy.service.remote-read.enabled defaults false everywhere declared
check_contains "11a. remote-read default false in application.yml" \
  "big-market-market-service/src/main/resources/application.yml" \
  "STRATEGY_SERVICE_REMOTE_READ_ENABLED:false"
check_contains "11b. remote-read default false in docker-compose.yml" \
  "docker-compose.yml" \
  "STRATEGY_SERVICE_REMOTE_READ_ENABLED.*:-false"

# 12. Legacy strategy RPC provider is gated without gating HTTP controller
check_file "12a. legacy RaffleStrategyServiceRPC exists" "$LEGACY_RPC"
check_contains "12b. legacy RPC has @DubboService" "$LEGACY_RPC" "@DubboService"
check_contains "12c. legacy RPC gate defaults enabled" "$LEGACY_RPC" "strategy\.legacy-rpc-provider\.enabled"
check_contains "12d. legacy RPC gate matchIfMissing=true" "$LEGACY_RPC" "matchIfMissing\s*=\s*true"
check_not_contains "12e. HTTP controller not gated by legacy RPC flag" "$CTRL" "strategy\.legacy-rpc-provider\.enabled"
check_not_contains "12f. HTTP controller not exported as Dubbo provider" "$CTRL" "@DubboService"

# 13. No generated evidence files tracked in git
if git -C "$ROOT" ls-files --error-unmatch docs/evidence/generated 2>/dev/null | grep -q .; then
  fail "13. docs/evidence/generated tracked in git"
else
  pass "13. docs/evidence/generated not tracked in git"
fi

echo ""
echo "=== Results: PASS=$PASS FAIL=$FAIL ==="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
