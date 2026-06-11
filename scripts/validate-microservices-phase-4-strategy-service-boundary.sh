#!/usr/bin/env bash
# validate-microservices-phase-4-strategy-service-boundary.sh
# Deterministic repo-only validation for the Phase 4 strategy-service boundary.
#
# Checks:
#   1. Root pom registers big-market-strategy-service
#   2. big-market-strategy-service pom exists and does NOT depend on big-market-trigger
#   3. Application class exists and scans only allowed packages
#   4. Provider exists and implements IStrategyReadService
#   5. IStrategyReadService exists in big-market-api
#   6. Provider does not import forbidden domains
#   7. Provider does not expose draw execution or write/mutation methods
#   8. No trigger/job packages are scanned
#   9. No remote flags are true by default
#  10. Dangerous Phase 2/3 flags remain false
#  11. docs/evidence/generated is not tracked
#  12. Phase 4 doc exists and documents read-first/non-goals/blockers

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

check_dir_not_tracked() {
  local label="$1" dir="$2"
  if git -C "$ROOT" ls-files --error-unmatch "$dir" >/dev/null 2>&1; then
    fail "$label: $dir is tracked by git"
  else
    pass "$label: $dir not tracked"
  fi
}

echo ""
echo "========================================================================"
echo "  Phase 4 Strategy-Service Boundary Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] Module and Maven wiring"
check_file "P4-MOD-1 root pom" "pom.xml"
check_contains "P4-MOD-2 root pom registers strategy-service" \
  "pom.xml" "<module>big-market-strategy-service</module>"
check_file "P4-MOD-3 strategy-service pom" "big-market-strategy-service/pom.xml"
check_contains "P4-MOD-4 finalName" \
  "big-market-strategy-service/pom.xml" "<finalName>big-market-strategy-service</finalName>"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] strategy-service must NOT depend on big-market-trigger"
check_not_contains "P4-DEP-1 no trigger dependency" \
  "big-market-strategy-service/pom.xml" "big-market-trigger"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] Application class: scan boundary"
APP_CLASS="big-market-strategy-service/src/main/java/com/dyx/market/strategy/StrategyServiceApplication.java"
check_file "P4-APP-1 application class" "$APP_CLASS"
check_contains "P4-APP-2 scans strategy module" \
  "$APP_CLASS" "com\.dyx\.market\.strategy"
check_contains "P4-APP-3 scans strategy domain" \
  "$APP_CLASS" "com\.dyx\.market\.domain\.strategy"
check_contains "P4-APP-4 scans infrastructure" \
  "$APP_CLASS" "com\.dyx\.market\.infrastructure"
check_not_contains "P4-APP-5 does not scan trigger.http" \
  "$APP_CLASS" "trigger\.http"
check_not_contains "P4-APP-6 does not scan trigger.listener" \
  "$APP_CLASS" "trigger\.listener"
check_not_contains "P4-APP-7 does not scan trigger.job" \
  "$APP_CLASS" "trigger\.job"
check_not_contains "P4-APP-8 does not scan message.job" \
  "$APP_CLASS" "message\.job"
check_not_contains "P4-APP-9 does not scan trigger.rpc" \
  "$APP_CLASS" "trigger\.rpc"
check_not_contains "P4-APP-10 does not scan domain.activity" \
  "$APP_CLASS" "domain\.activity"
check_not_contains "P4-APP-11 does not scan domain.rebate" \
  "$APP_CLASS" "domain\.rebate"
check_not_contains "P4-APP-12 does not scan domain.credit" \
  "$APP_CLASS" "domain\.credit"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] Provider existence and contract"
PROVIDER="big-market-strategy-service/src/main/java/com/dyx/market/strategy/provider/StrategyReadServiceRPC.java"
check_file "P4-RPC-1 provider file" "$PROVIDER"
check_contains "P4-RPC-2 implements IStrategyReadService" \
  "$PROVIDER" "implements IStrategyReadService"
check_contains "P4-RPC-3 @DubboService annotation" \
  "$PROVIDER" "@DubboService"
check_contains "P4-RPC-4 queryRaffleAwardList method" \
  "$PROVIDER" "queryRaffleAwardList"
check_contains "P4-RPC-5 queryRaffleStrategyRuleWeight method" \
  "$PROVIDER" "queryRaffleStrategyRuleWeight"

# -----------------------------------------------------------------------
echo ""
echo "-- [5] Provider does not import forbidden domains"
check_not_contains "P4-RPC-6 no activity import" \
  "$PROVIDER" "domain\.activity"
check_not_contains "P4-RPC-7 no award import" \
  "$PROVIDER" "domain\.award"
check_not_contains "P4-RPC-8 no account import" \
  "$PROVIDER" "domain\.account"
check_not_contains "P4-RPC-9 no credit import" \
  "$PROVIDER" "domain\.credit"
check_not_contains "P4-RPC-10 no fulfillment import" \
  "$PROVIDER" "domain\.fulfillment"
check_not_contains "P4-RPC-11 no rebate import" \
  "$PROVIDER" "domain\.rebate"
check_not_contains "P4-RPC-12 no auth import" \
  "$PROVIDER" "domain\.auth"
check_not_contains "P4-RPC-13 no admin import" \
  "$PROVIDER" "domain\.admin"
check_not_contains "P4-RPC-14 no chatbot import" \
  "$PROVIDER" "domain\.chatbot"

# -----------------------------------------------------------------------
echo ""
echo "-- [6] Provider does not expose draw execution or write methods"
check_not_contains "P4-RPC-15 no performRaffle" \
  "$PROVIDER" "performRaffle"
check_not_contains "P4-RPC-16 no randomRaffle" \
  "$PROVIDER" "randomRaffle"
check_not_contains "P4-RPC-17 no assembleLotteryStrategy" \
  "$PROVIDER" "assembleLotteryStrategy"
check_not_contains "P4-RPC-18 no subtractionAwardStock" \
  "$PROVIDER" "subtractionAwardStock"
check_not_contains "P4-RPC-19 no getRandomAwardId" \
  "$PROVIDER" "getRandomAwardId"

# -----------------------------------------------------------------------
echo ""
echo "-- [7] IStrategyReadService exists in big-market-api"
API_IFACE="big-market-api/src/main/java/com/dyx/market/trigger/api/IStrategyReadService.java"
check_file "P4-API-1 IStrategyReadService" "$API_IFACE"
check_contains "P4-API-2 queryRaffleAwardList declared" \
  "$API_IFACE" "queryRaffleAwardList"
check_contains "P4-API-3 queryRaffleStrategyRuleWeight declared" \
  "$API_IFACE" "queryRaffleStrategyRuleWeight"
check_not_contains "P4-API-4 no performRaffle in contract" \
  "$API_IFACE" "performRaffle"
check_not_contains "P4-API-5 no randomRaffle in contract" \
  "$API_IFACE" "randomRaffle"

# -----------------------------------------------------------------------
echo ""
echo "-- [8] Dubbo scan limited to strategy provider package"
check_contains "P4-DUBBO-1 dubbo scan = strategy.provider" \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "base-packages: com\.dyx\.market\.strategy\.provider"
check_contains "P4-DUBBO-2 port 8089" \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "port: .*8089"
check_contains "P4-DUBBO-3 dubbo port 20884" \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "port: 20884"

# -----------------------------------------------------------------------
echo ""
echo "-- [9] Strategy mapper XMLs present (strategy-service owns these tables)"
check_file "P4-MAPPER-1 strategy_mapper.xml" \
  "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/strategy_mapper.xml"
check_file "P4-MAPPER-2 strategy_award_mapper.xml" \
  "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/strategy_award_mapper.xml"
check_file "P4-MAPPER-3 strategy_rule_mapper.xml" \
  "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/strategy_rule_mapper.xml"
check_file "P4-MAPPER-4 rule_tree_mapper.xml" \
  "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/rule_tree_mapper.xml"
check_file "P4-MAPPER-5 rule_tree_node_mapper.xml" \
  "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/rule_tree_node_mapper.xml"
check_file "P4-MAPPER-6 rule_tree_node_line_mapper.xml" \
  "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/rule_tree_node_line_mapper.xml"

# Ensure no rebate/activity/award mapper XMLs are present in strategy-service
echo ""
echo "-- [10] No forbidden mapper XMLs in strategy-service"
MAPPER_DIR="$ROOT/big-market-strategy-service/src/main/resources/mybatis/mapper/mysql"
for forbidden in "user_behavior_rebate_order" "daily_behavior_rebate" \
                 "raffle_activity" "user_award_record" "user_credit"; do
  if ls "$MAPPER_DIR"/*${forbidden}* 2>/dev/null | grep -q .; then
    fail "P4-MAPPER-forbidden: $forbidden mapper found in strategy-service"
  else
    pass "P4-MAPPER-clean: no $forbidden mapper in strategy-service"
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [11] Remote flag defaults false"
APP_YML="big-market-strategy-service/src/main/resources/application.yml"
check_contains "P4-FLAG-1 strategy remote-read default false" \
  "$APP_YML" "STRATEGY_SERVICE_REMOTE_READ_ENABLED:false"

# -----------------------------------------------------------------------
echo ""
echo "-- [12] Dangerous Phase 2/3 flags remain false (spot-check market-service)"
MARKET_YML="big-market-market-service/src/main/resources/application.yml"
if [ -f "$ROOT/$MARKET_YML" ]; then
  for flag in \
    "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED" \
    "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED" \
    "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED" \
    "REBATE_SERVICE_REMOTE_READ_ENABLED"; do
    # Dangerous if the value after :- is 'true'
    if grep -qE "${flag}:-true" "$ROOT/$MARKET_YML"; then
      fail "P4-SAFEFLAG: $flag is hardcoded true in $MARKET_YML"
    else
      pass "P4-SAFEFLAG: $flag not hardcoded true in $MARKET_YML"
    fi
  done
else
  pass "P4-SAFEFLAG: $MARKET_YML not present (skipping market-service flag check)"
fi

# Strategy remote flag must also not be hardcoded true in any config
for cfg in \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "big-market-market-service/src/main/resources/application.yml" \
  "docker-compose.yml"; do
  if [ -f "$ROOT/$cfg" ]; then
    if grep -qE "STRATEGY_SERVICE_REMOTE_READ_ENABLED:-true|strategy\.service\.remote-read\.enabled: *true" "$ROOT/$cfg"; then
      fail "P4-STRATFLAG: strategy remote-read flag is true in $cfg"
    else
      pass "P4-STRATFLAG: strategy remote-read flag safe in $cfg"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [13] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P4-EVID: docs/evidence/generated is tracked by git"
else
  pass "P4-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [14] Phase 4 boundary assessment doc"
PHASE4_DOC="docs/microservices-split-phase-4-strategy-service.md"
check_file "P4-DOC-1 phase4 doc" "$PHASE4_DOC"
check_contains "P4-DOC-2 documents read-first extraction" \
  "$PHASE4_DOC" "read.first|Read.first|read-first|Read-First"
check_contains "P4-DOC-3 documents non-goal (no draw migration)" \
  "$PHASE4_DOC" "Non-Goal|non-goal|Explicit Non|not.*move|must.*not.*move"
check_contains "P4-DOC-4 documents remaining blockers" \
  "$PHASE4_DOC" "[Bb]locker"
check_contains "P4-DOC-5 strategy.service.remote-read.enabled=false" \
  "$PHASE4_DOC" "strategy\.service\.remote-read\.enabled.*false"

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 4-A/B/C strategy-service boundary is repo-ready."
  echo "        Traffic cutover and adapter wiring remain Phase 4-D and later work."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi
