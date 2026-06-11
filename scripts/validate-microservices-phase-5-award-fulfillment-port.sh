#!/usr/bin/env bash
# validate-microservices-phase-5-award-fulfillment-port.sh
# Deterministic repo-only validation for the Phase 5-E local award fulfillment port.
#
# Checks:
#   1.  IAwardFulfillmentPort exists in domain activity adapter/port package
#   2.  LocalAwardFulfillmentPort exists in infrastructure adapter/port package
#   3.  LocalAwardFulfillmentPort delegates to IAwardService.saveUserAwardRecord
#   4.  LocalAwardFulfillmentPort has no @DubboReference
#   5.  No remote award fulfillment implementation was added
#   6.  RaffleApplicationService imports/injects IAwardFulfillmentPort
#   7.  RaffleApplicationService no longer directly imports/injects IAwardService
#   8.  Draw flow still calls strategyDecisionPort.performRaffle and createOrder
#   9.  No activity-service module was added
#  10.  No remote draw/award fulfillment flag was introduced
#  11.  Existing dangerous flags remain false/default-safe
#  12.  docs/evidence/generated is not tracked
#  13.  Phase 5 docs and master plan mention Phase 5-E and the port boundary

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
echo "  Phase 5-E Award Fulfillment Port Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IAwardFulfillmentPort.java"
LOCAL_IMPL="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalAwardFulfillmentPort.java"
RAFFLE_SVC="big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java"
ORCH_DOC="docs/microservices-split-phase-5-activity-draw-orchestration.md"
BOUNDARY_DOC="docs/microservices-split-phase-5-draw-command-boundary.md"
MASTER_PLAN="docs/microservices-decomposition-master-plan.md"
MARKET_YML="big-market-market-service/src/main/resources/application.yml"
MSGJOB_YML="big-market-message-job-service/src/main/resources/application.yml"
APP_YML="big-market-app/src/main/resources/application-dev.yml"
DOCKER_COMPOSE="docker-compose.yml"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] IAwardFulfillmentPort interface exists"
check_file "P5E-PORT-1 interface" "$PORT_IFACE"
check_contains "P5E-PORT-1 declares saveUserAwardRecord" "$PORT_IFACE" "void saveUserAwardRecord"
check_contains "P5E-PORT-1 uses UserAwardRecordEntity" "$PORT_IFACE" "UserAwardRecordEntity"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] LocalAwardFulfillmentPort implementation exists"
check_file "P5E-IMPL-1 local impl" "$LOCAL_IMPL"
check_contains "P5E-IMPL-1 implements interface" "$LOCAL_IMPL" "implements IAwardFulfillmentPort"
check_contains "P5E-IMPL-1 is component" "$LOCAL_IMPL" "@Component"
check_contains "P5E-IMPL-1 default local bean" "$LOCAL_IMPL" "@ConditionalOnMissingBean\\(IAwardFulfillmentPort.class\\)"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] LocalAwardFulfillmentPort delegates to IAwardService.saveUserAwardRecord"
check_contains "P5E-IMPL-2 references IAwardService" "$LOCAL_IMPL" "IAwardService"
check_contains "P5E-IMPL-2 calls awardService.saveUserAwardRecord" "$LOCAL_IMPL" "awardService\\.saveUserAwardRecord"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] LocalAwardFulfillmentPort has no @DubboReference"
check_not_contains "P5E-IMPL-3 no DubboReference" "$LOCAL_IMPL" "@DubboReference"

# -----------------------------------------------------------------------
echo ""
echo "-- [5] No remote award fulfillment implementation"
check_not_file "P5E-REMOTE-1 no RemoteAwardFulfillmentPort in market-service" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/RemoteAwardFulfillmentPort.java"
check_not_file "P5E-REMOTE-2 no AwardRemoteFulfillmentPort in market-service" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AwardRemoteFulfillmentPort.java"

IMPL_COUNT=$(find \
  "$ROOT/big-market-domain/src/main/java" \
  "$ROOT/big-market-infrastructure/src/main/java" \
  "$ROOT/big-market-market-service/src/main/java" \
  "$ROOT/big-market-message-job-service/src/main/java" \
  "$ROOT/big-market-fulfillment-service/src/main/java" \
  -type f -name "*.java" -exec grep -l "implements IAwardFulfillmentPort" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$IMPL_COUNT" = "1" ]; then
  pass "P5E-REMOTE-3 only LocalAwardFulfillmentPort implements IAwardFulfillmentPort"
else
  fail "P5E-REMOTE-3 expected 1 IAwardFulfillmentPort implementation, found $IMPL_COUNT"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [6] RaffleApplicationService uses IAwardFulfillmentPort"
check_contains "P5E-SVC-1 imports IAwardFulfillmentPort" "$RAFFLE_SVC" "import.*IAwardFulfillmentPort"
check_contains "P5E-SVC-2 injects awardFulfillmentPort" "$RAFFLE_SVC" "IAwardFulfillmentPort awardFulfillmentPort"
check_contains "P5E-SVC-3 calls awardFulfillmentPort.saveUserAwardRecord" "$RAFFLE_SVC" "awardFulfillmentPort\\.saveUserAwardRecord"

# -----------------------------------------------------------------------
echo ""
echo "-- [7] RaffleApplicationService no longer directly uses IAwardService"
check_not_contains "P5E-SVC-4 no IAwardService import" "$RAFFLE_SVC" "import.*IAwardService"
check_not_contains "P5E-SVC-5 no IAwardService injection" "$RAFFLE_SVC" "IAwardService awardService"
check_not_contains "P5E-SVC-6 no direct awardService.saveUserAwardRecord" "$RAFFLE_SVC" "awardService\\.saveUserAwardRecord"

# -----------------------------------------------------------------------
echo ""
echo "-- [8] Existing draw flow behavior remains in-process"
check_contains "P5E-SVC-7 strategy decision unchanged" "$RAFFLE_SVC" "strategyDecisionPort\\.performRaffle"
check_contains "P5E-SVC-8 createOrder unchanged" "$RAFFLE_SVC" "raffleActivityPartakeService\\.createOrder"

# -----------------------------------------------------------------------
echo ""
echo "-- [9] No big-market-activity-service module"
check_not_dir "P5E-MOD-1 activity-service absent" "big-market-activity-service"
if [ -f "$ROOT/pom.xml" ]; then
  check_not_contains "P5E-MOD-2 root pom does not register activity-service" \
    "pom.xml" "<module>big-market-activity-service</module>"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [10] No remote draw/award fulfillment flag introduced"
for cfg in "$MARKET_YML" "$MSGJOB_YML" "$APP_YML" "$DOCKER_COMPOSE"; do
  rel="${cfg#$ROOT/}"
  if [ -f "$ROOT/$rel" ]; then
    if grep -qE "award\.service\.remote-fulfillment|REMOTE_FULFILLMENT|remote-fulfillment|strategy\.service\.remote-decision|REMOTE_DECISION" "$ROOT/$rel"; then
      fail "P5E-FLAG-1 forbidden remote draw/award fulfillment flag found in $rel"
    else
      pass "P5E-FLAG-1 no new remote draw/award fulfillment flag in $rel"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [11] Existing dangerous flags remain false/default-safe"
for cfg in "$MARKET_YML" "$MSGJOB_YML" "$APP_YML" "$DOCKER_COMPOSE"; do
  rel="${cfg#$ROOT/}"
  if [ -f "$ROOT/$rel" ]; then
    for flag in \
      "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED" \
      "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" \
      "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED" \
      "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED" \
      "REBATE_SERVICE_REMOTE_READ_ENABLED" \
      "STRATEGY_SERVICE_REMOTE_READ_ENABLED" \
      "STRATEGY_SERVICE_REMOTE_DECISION_ENABLED"; do
      if grep -qE "${flag}(:-|=|: *)true" "$ROOT/$rel"; then
        fail "P5E-SAFEFLAG: $flag is true in $rel"
      else
        pass "P5E-SAFEFLAG: $flag not true in $rel"
      fi
    done
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [12] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P5E-EVID: docs/evidence/generated is tracked by git"
else
  pass "P5E-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [13] Phase 5 docs and master plan mention Phase 5-E award fulfillment port"
check_contains "P5E-DOC-1 orchestration doc mentions Phase 5-E" "$ORCH_DOC" "Phase 5-E"
check_contains "P5E-DOC-2 orchestration doc mentions port" "$ORCH_DOC" "IAwardFulfillmentPort"
check_contains "P5E-DOC-3 boundary doc mentions award fulfillment port" "$BOUNDARY_DOC" "IAwardFulfillmentPort"
check_contains "P5E-DOC-4 boundary doc blocks remote until Phase 5-G" "$BOUNDARY_DOC" "[Rr]emote award fulfillment.*Phase 5-G|Phase 5-G.*remote award fulfillment"
check_contains "P5E-DOC-5 master plan marks Phase 5-E done" "$MASTER_PLAN" "5-E.*IAwardFulfillmentPort.*Done|5-E local award fulfillment port introduced"
check_contains "P5E-DOC-6 master plan identifies 5-F/5-G next" "$MASTER_PLAN" "Phase 5-F.*Phase 5-G|5-F.*5-G"

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 5-E award fulfillment port boundary is repo-ready."
  echo "        Local port introduced. RaffleApplicationService updated."
  echo "        Award persistence remains in-process. No remote flag introduced."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi
