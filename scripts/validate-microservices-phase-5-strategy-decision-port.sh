#!/usr/bin/env bash
# validate-microservices-phase-5-strategy-decision-port.sh
# Deterministic repo-only validation for the Phase 5-D local strategy decision port.
#
# Checks:
#   1.  IStrategyDecisionPort interface exists in domain activity adapter port package
#   2.  LocalStrategyDecisionPort implementation exists in infrastructure adapter port
#   3.  LocalStrategyDecisionPort delegates to IRaffleStrategy.performRaffle
#   4.  LocalStrategyDecisionPort has no @DubboReference
#   5.  No remote strategy decision implementation exists
#   6.  RaffleApplicationService uses IStrategyDecisionPort (not directly IRaffleStrategy)
#   7.  RaffleApplicationService.executeDraw still calls performRaffle through the port
#   8.  RaffleStrategyController.randomRaffle still uses IRaffleStrategy directly (unchanged)
#   9.  No strategy.service.remote-decision.enabled flag in configs
#  10.  No big-market-activity-service module exists
#  11.  Dangerous flags remain false
#  12.  docs/evidence/generated not tracked

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

check_not_file() {
  local label="$1" path="$2"
  if [ -f "$ROOT/$path" ]; then
    fail "$label: $path should not exist"
  else
    pass "$label: $path correctly absent"
  fi
}

check_not_dir() {
  local label="$1" path="$2"
  if [ -d "$ROOT/$path" ]; then
    fail "$label: $path should not exist yet"
  else
    pass "$label: $path correctly absent"
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
    pass "$label: file $path absent (no forbidden pattern)"
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
echo "  Phase 5-D Strategy Decision Port Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IStrategyDecisionPort.java"
LOCAL_IMPL="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalStrategyDecisionPort.java"
RAFFLE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java"
STRATEGY_CTRL="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] IStrategyDecisionPort interface exists"
check_file "P5D-PORT-1 interface" "$PORT_IFACE"
check_contains "P5D-PORT-1 interface declares performRaffle" "$PORT_IFACE" "RaffleAwardEntity performRaffle"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] LocalStrategyDecisionPort implementation exists"
check_file "P5D-IMPL-1 local impl" "$LOCAL_IMPL"
check_contains "P5D-IMPL-1 implements interface" "$LOCAL_IMPL" "implements IStrategyDecisionPort"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] LocalStrategyDecisionPort delegates to IRaffleStrategy.performRaffle"
check_contains "P5D-IMPL-2 references IRaffleStrategy" "$LOCAL_IMPL" "IRaffleStrategy"
check_contains "P5D-IMPL-2 calls raffleStrategy.performRaffle" "$LOCAL_IMPL" "raffleStrategy\.performRaffle"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] LocalStrategyDecisionPort has no @DubboReference"
check_not_contains "P5D-IMPL-3 no DubboReference" "$LOCAL_IMPL" "@DubboReference"

# -----------------------------------------------------------------------
echo ""
echo "-- [5] No remote strategy decision implementation"
REMOTE_IMPL="big-market-market-service/src/main/java/com/dyx/market/market/config/StrategyRemoteDecisionPort.java"
check_not_file "P5D-REMOTE-1 no remote decision impl" "$REMOTE_IMPL"

# Also grep broadly for any class implementing IStrategyDecisionPort in market-service config
MARKET_CONFIG_DIR="big-market-market-service/src/main/java"
if [ -d "$ROOT/$MARKET_CONFIG_DIR" ]; then
  REMOTE_COUNT=$(grep -rE "implements IStrategyDecisionPort" "$ROOT/$MARKET_CONFIG_DIR" 2>/dev/null | wc -l)
  if [ "$REMOTE_COUNT" -gt 0 ]; then
    fail "P5D-REMOTE-2 IStrategyDecisionPort implemented in market-service (unexpected remote impl)"
  else
    pass "P5D-REMOTE-2 no IStrategyDecisionPort implementation in market-service"
  fi
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [6] RaffleApplicationService uses IStrategyDecisionPort, not IRaffleStrategy directly"
check_contains "P5D-SVC-1 imports IStrategyDecisionPort" "$RAFFLE_SVC" "import.*IStrategyDecisionPort"
check_not_contains "P5D-SVC-2 no IRaffleStrategy import" "$RAFFLE_SVC" "import.*IRaffleStrategy"
check_contains "P5D-SVC-3 injects strategyDecisionPort" "$RAFFLE_SVC" "IStrategyDecisionPort strategyDecisionPort"

# -----------------------------------------------------------------------
echo ""
echo "-- [7] RaffleApplicationService.executeDraw still calls performRaffle through the port"
check_contains "P5D-SVC-4 calls strategyDecisionPort.performRaffle" "$RAFFLE_SVC" "strategyDecisionPort\.performRaffle"

# -----------------------------------------------------------------------
echo ""
echo "-- [8] RaffleStrategyController.randomRaffle remains unchanged (still uses IRaffleStrategy)"
if [ -f "$ROOT/$STRATEGY_CTRL" ]; then
  check_contains "P5D-CTRL-1 controller still imports IRaffleStrategy" "$STRATEGY_CTRL" "import.*IRaffleStrategy"
  check_contains "P5D-CTRL-2 randomRaffle still calls raffleStrategy.performRaffle" "$STRATEGY_CTRL" "raffleStrategy\.performRaffle"
else
  pass "P5D-CTRL-1 controller not present (skip)"
  pass "P5D-CTRL-2 controller not present (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [9] No strategy.service.remote-decision.enabled flag in configs"
for cfg in \
  "big-market-market-service/src/main/resources/application.yml" \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "docker-compose.yml"; do
  if [ -f "$ROOT/$cfg" ]; then
    if grep -qE "remote-decision|REMOTE_DECISION" "$ROOT/$cfg"; then
      fail "P5D-FLAG-1 remote-decision flag found in $cfg"
    else
      pass "P5D-FLAG-1 no remote-decision flag in $cfg"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [10] No big-market-activity-service module"
check_not_dir "P5D-MOD-1 activity-service absent" "big-market-activity-service"

# -----------------------------------------------------------------------
echo ""
echo "-- [11] Dangerous Phase 2/3/4/5 flags remain false"
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
      fail "P5D-SAFEFLAG: $flag is hardcoded true in market-service yml"
    else
      pass "P5D-SAFEFLAG: $flag not hardcoded true"
    fi
  done
else
  pass "P5D-SAFEFLAG: market-service yml not present (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [12] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P5D-EVID: docs/evidence/generated is tracked by git"
else
  pass "P5D-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 5-D strategy decision port boundary is repo-ready."
  echo "        IStrategyDecisionPort (local) introduced. RaffleApplicationService updated."
  echo "        All draw execution remains in-process. No remote flag introduced."
  echo "        Phase 5-E award fulfillment port is the recommended next step."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi
