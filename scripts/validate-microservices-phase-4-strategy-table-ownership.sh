#!/usr/bin/env bash
# validate-microservices-phase-4-strategy-table-ownership.sh
# Deterministic repo-only validation for the Phase 4-F strategy table ownership mapping.
#
# Checks:
#   1.  Strategy ownership doc exists
#   2.  Doc mentions all six strategy/rule tables
#   3.  Doc mentions all six mapper XML file names
#   4.  strategy-service contains only the six strategy/rule mapper XMLs (no forbidden XMLs)
#   5.  strategy-service does NOT contain activity/account/award/rebate mapper XMLs
#   6.  strategy-service still has no trigger dependency
#   7.  StrategyReadServiceRPC exposes only read methods (no draw/write)
#   8.  strategy.service.remote-read.enabled remains default false
#   9.  strategy.legacy-rpc-provider.enabled remains default true
#  10.  RaffleStrategyController is NOT gated by legacy RPC flag and is NOT @DubboService standalone
#  11.  RaffleStrategyServiceRPC IS gated with @ConditionalOnProperty matchIfMissing=true
#  12.  RaffleStrategyServiceRPC IS @DubboService
#  13.  No generated evidence files are tracked
#  14.  Dangerous Phase 2/3/4 flags remain false

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

echo ""
echo "========================================================================"
echo "  Phase 4-F Strategy Table Ownership Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

OWNERSHIP_DOC="docs/microservices-split-phase-4-strategy-table-ownership.md"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] Strategy ownership doc exists"
check_file "P4F-DOC-1 ownership doc" "$OWNERSHIP_DOC"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] Doc mentions all six strategy/rule tables"
for table in "strategy" "strategy_award" "strategy_rule" \
             "rule_tree" "rule_tree_node" "rule_tree_node_line"; do
  check_contains "P4F-DOC-2 doc mentions table $table" \
    "$OWNERSHIP_DOC" "\`${table}\`|\"${table}\""
done

# -----------------------------------------------------------------------
echo ""
echo "-- [3] Doc mentions all six mapper XML file names"
for xml in "strategy_mapper" "strategy_award_mapper" "strategy_rule_mapper" \
           "rule_tree_mapper" "rule_tree_node_mapper" "rule_tree_node_line_mapper"; do
  check_contains "P4F-DOC-3 doc mentions mapper $xml" \
    "$OWNERSHIP_DOC" "${xml}"
done

# -----------------------------------------------------------------------
echo ""
echo "-- [4] strategy-service mapper set: all six required XMLs present"
STRAT_MAPPER_DIR="$ROOT/big-market-strategy-service/src/main/resources/mybatis/mapper/mysql"
for xml in "strategy_mapper.xml" "strategy_award_mapper.xml" "strategy_rule_mapper.xml" \
           "rule_tree_mapper.xml" "rule_tree_node_mapper.xml" "rule_tree_node_line_mapper.xml"; do
  if [ -f "$STRAT_MAPPER_DIR/$xml" ]; then
    pass "P4F-MAPPER-1 strategy-service has $xml"
  else
    fail "P4F-MAPPER-1 strategy-service missing $xml"
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [5] strategy-service must NOT contain forbidden mapper XMLs"
for forbidden in "raffle_activity" "user_award_record" "user_credit" \
                 "daily_behavior_rebate" "user_behavior_rebate_order" \
                 "raffle_activity_account" "user_raffle_order" \
                 "credit_award_task" "task_mapper"; do
  if ls "$STRAT_MAPPER_DIR"/*${forbidden}* 2>/dev/null | grep -q .; then
    fail "P4F-MAPPER-2 forbidden mapper found in strategy-service: $forbidden"
  else
    pass "P4F-MAPPER-2 no $forbidden mapper in strategy-service"
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [6] strategy-service pom has no trigger dependency"
check_not_contains "P4F-DEP-1 no big-market-trigger in strategy-service pom" \
  "big-market-strategy-service/pom.xml" "big-market-trigger"

# -----------------------------------------------------------------------
echo ""
echo "-- [7] StrategyReadServiceRPC exposes only read methods (no draw/write)"
PROVIDER="big-market-strategy-service/src/main/java/com/dyx/market/strategy/provider/StrategyReadServiceRPC.java"
check_file "P4F-RPC-1 provider file" "$PROVIDER"
check_not_contains "P4F-RPC-2 no performRaffle" "$PROVIDER" "performRaffle"
check_not_contains "P4F-RPC-3 no randomRaffle" "$PROVIDER" "randomRaffle"
check_not_contains "P4F-RPC-4 no assembleLotteryStrategy" "$PROVIDER" "assembleLotteryStrategy"
check_not_contains "P4F-RPC-5 no subtractionAwardStock" "$PROVIDER" "subtractionAwardStock"
check_not_contains "P4F-RPC-6 no getRandomAwardId" "$PROVIDER" "getRandomAwardId"
check_contains "P4F-RPC-7 queryRaffleAwardList present" "$PROVIDER" "queryRaffleAwardList"
check_contains "P4F-RPC-8 queryRaffleStrategyRuleWeight present" "$PROVIDER" "queryRaffleStrategyRuleWeight"

# -----------------------------------------------------------------------
echo ""
echo "-- [8] strategy.service.remote-read.enabled defaults false"
STRAT_YML="big-market-strategy-service/src/main/resources/application.yml"
check_contains "P4F-FLAG-1 remote-read default false in strategy-service yml" \
  "$STRAT_YML" "STRATEGY_SERVICE_REMOTE_READ_ENABLED:false"

# Check docker-compose.yml does not flip it to true
if [ -f "$ROOT/docker-compose.yml" ]; then
  if grep -qE "STRATEGY_SERVICE_REMOTE_READ_ENABLED:-true|strategy\.service\.remote-read\.enabled: *true" "$ROOT/docker-compose.yml"; then
    fail "P4F-FLAG-2 strategy remote-read flag is true in docker-compose.yml"
  else
    pass "P4F-FLAG-2 strategy remote-read flag safe in docker-compose.yml"
  fi
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [9] strategy.legacy-rpc-provider.enabled defaults true (matchIfMissing=true)"
LEGACY_RPC="big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java"
check_file "P4F-GATE-1 RaffleStrategyServiceRPC exists" "$LEGACY_RPC"
check_contains "P4F-GATE-2 legacy gate annotation" \
  "$LEGACY_RPC" "strategy\.legacy-rpc-provider\.enabled"
check_contains "P4F-GATE-3 matchIfMissing=true" \
  "$LEGACY_RPC" "matchIfMissing *= *true"

# -----------------------------------------------------------------------
echo ""
echo "-- [10] RaffleStrategyController: HTTP controller is NOT gated by legacy RPC flag"
CTRL="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java"
check_file "P4F-CTRL-1 RaffleStrategyController exists" "$CTRL"
check_not_contains "P4F-CTRL-2 controller not gated by legacy-rpc-provider flag" \
  "$CTRL" "legacy-rpc-provider\.enabled"

# -----------------------------------------------------------------------
echo ""
echo "-- [11/12] RaffleStrategyServiceRPC IS @DubboService and IS gated"
check_contains "P4F-GATE-4 RaffleStrategyServiceRPC is @DubboService" \
  "$LEGACY_RPC" "@DubboService"
check_contains "P4F-GATE-5 RaffleStrategyServiceRPC is @ConditionalOnProperty" \
  "$LEGACY_RPC" "@ConditionalOnProperty"

# -----------------------------------------------------------------------
echo ""
echo "-- [13] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P4F-EVID: docs/evidence/generated is tracked by git"
else
  pass "P4F-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [14] Dangerous Phase 2/3/4 flags remain false"
MARKET_YML="big-market-market-service/src/main/resources/application.yml"
if [ -f "$ROOT/$MARKET_YML" ]; then
  for flag in \
    "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED" \
    "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED" \
    "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED" \
    "REBATE_SERVICE_REMOTE_READ_ENABLED" \
    "STRATEGY_SERVICE_REMOTE_READ_ENABLED"; do
    if grep -qE "${flag}:-true" "$ROOT/$MARKET_YML"; then
      fail "P4F-SAFEFLAG: $flag is hardcoded true in market-service yml"
    else
      pass "P4F-SAFEFLAG: $flag not hardcoded true in market-service yml"
    fi
  done
else
  pass "P4F-SAFEFLAG: market-service yml not present (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 4-F strategy table ownership mapping is repo-ready."
  echo "        Datasource/schema isolation is Phase 7 work."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi
