#!/usr/bin/env bash
# validate-microservices-phase-5-activity-draw-orchestration.sh
# Deterministic repo-only validation for the Phase 5-A activity/draw orchestration map.
#
# Checks:
#   1.  Phase 5 orchestration doc exists
#   2.  Doc mentions RaffleApplicationService
#   3.  Doc mentions the four draw-flow domains: strategy, activity/account quota, award, fulfillment/outbox
#   4.  Candidate adapters are documented: IStrategyDecisionAdapter, IActivityAccountPort,
#       IAwardFulfillmentPort, IDrawOutboxPort
#   5.  Non-goals are documented (no draw migration, no remote draw command, no activity-service now)
#   6.  No new big-market-activity-service module is added in this batch
#   7.  No new strategy.service.remote-decision.enabled flag is introduced
#   8.  No performRaffle / randomRaffle routing to a remote service is introduced
#   9.  strategy.service.remote-read.enabled default is NOT changed to true in any config
#  10.  Dangerous Phase 2/3/4 flags remain false
#  11.  docs/evidence/generated is not tracked

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
  if [ -f "$ROOT/$path" ] || [ -d "$ROOT/$path" ]; then
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
    # file absent = cannot contain the pattern = safe
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
echo "  Phase 5-A Activity/Draw Orchestration Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

ORCH_DOC="docs/microservices-split-phase-5-activity-draw-orchestration.md"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] Orchestration doc exists"
check_file "P5A-DOC-1 orchestration doc" "$ORCH_DOC"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] Doc documents RaffleApplicationService"
check_contains "P5A-DOC-2 RaffleApplicationService mentioned" \
  "$ORCH_DOC" "RaffleApplicationService"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] Doc covers the four draw-flow domains"
check_contains "P5A-DOC-3a strategy domain mentioned" \
  "$ORCH_DOC" "[Ss]trategy|IRaffleStrategy|performRaffle"
check_contains "P5A-DOC-3b activity/quota mentioned" \
  "$ORCH_DOC" "activity|quota|IRaffleActivityPartake|createOrder"
check_contains "P5A-DOC-3c award/fulfillment mentioned" \
  "$ORCH_DOC" "award|fulfillment|saveUserAwardRecord|IAwardService"
check_contains "P5A-DOC-3d outbox/task mentioned" \
  "$ORCH_DOC" "outbox|task|ITaskService|SendMessageTaskJob"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] Candidate adapters documented"
check_contains "P5A-DOC-4a IStrategyDecisionAdapter documented" \
  "$ORCH_DOC" "IStrategyDecisionAdapter"
check_contains "P5A-DOC-4b IActivityAccountPort documented" \
  "$ORCH_DOC" "IActivityAccountPort"
check_contains "P5A-DOC-4c IAwardFulfillmentPort documented" \
  "$ORCH_DOC" "IAwardFulfillmentPort"
check_contains "P5A-DOC-4d IDrawOutboxPort documented" \
  "$ORCH_DOC" "IDrawOutboxPort"

# -----------------------------------------------------------------------
echo ""
echo "-- [5] Non-goals documented"
check_contains "P5A-DOC-5a no draw execution migration non-goal" \
  "$ORCH_DOC" "[Nn]on-[Gg]oal|non.goal|not.*move|No draw"
check_contains "P5A-DOC-5b no activity-service scaffold non-goal" \
  "$ORCH_DOC" "activity.service.*not|No.*activity.service|not.*created|activity-service scaffold"
check_contains "P5A-DOC-5c no remote draw command non-goal" \
  "$ORCH_DOC" "remote.*draw|remote-decision|No remote draw"

# -----------------------------------------------------------------------
echo ""
echo "-- [6] No big-market-activity-service module added in this batch"
check_not_file "P5A-MOD-1 no activity-service module" \
  "big-market-activity-service"

# Confirm root pom does not register activity-service yet
ROOT_POM="pom.xml"
if [ -f "$ROOT/$ROOT_POM" ]; then
  if grep -qE "<module>big-market-activity-service</module>" "$ROOT/$ROOT_POM"; then
    fail "P5A-MOD-2 root pom registers activity-service (should not exist yet)"
  else
    pass "P5A-MOD-2 root pom does not register activity-service"
  fi
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [7] No strategy.service.remote-decision.enabled flag introduced in configs"
for cfg in \
  "big-market-market-service/src/main/resources/application.yml" \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "docker-compose.yml"; do
  if [ -f "$ROOT/$cfg" ]; then
    if grep -qE "remote-decision\.enabled|REMOTE_DECISION_ENABLED" "$ROOT/$cfg"; then
      fail "P5A-FLAG-1 remote-decision flag found in $cfg (must not exist in this batch)"
    else
      pass "P5A-FLAG-1 no remote-decision flag in $cfg"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [8] No performRaffle / randomRaffle remote routing introduced"
# Check that no remote decision adapter exists
REMOTE_DECISION_CANDIDATES=(
  "big-market-market-service/src/main/java"
  "big-market-strategy-service/src/main/java"
  "big-market-trigger/src/main/java"
)
for dir in "${REMOTE_DECISION_CANDIDATES[@]}"; do
  if [ -d "$ROOT/$dir" ]; then
    if grep -rqE "(performRaffle|randomRaffle).*[Rr]emote|[Rr]emote.*(performRaffle|randomRaffle)" "$ROOT/$dir" 2>/dev/null; then
      fail "P5A-DRAW-1 remote routing for performRaffle/randomRaffle found in $dir"
    else
      pass "P5A-DRAW-1 no remote performRaffle/randomRaffle routing in $dir"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [9] strategy.service.remote-read.enabled NOT changed to true"
for cfg in \
  "big-market-market-service/src/main/resources/application.yml" \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "docker-compose.yml"; do
  if [ -f "$ROOT/$cfg" ]; then
    if grep -qE "STRATEGY_SERVICE_REMOTE_READ_ENABLED:-true|strategy\.service\.remote-read\.enabled: *true" "$ROOT/$cfg"; then
      fail "P5A-FLAG-2 strategy remote-read flag is true in $cfg"
    else
      pass "P5A-FLAG-2 strategy remote-read flag safe in $cfg"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [10] Dangerous Phase 2/3/4 flags remain false"
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
      fail "P5A-SAFEFLAG: $flag is hardcoded true in market-service yml"
    else
      pass "P5A-SAFEFLAG: $flag not hardcoded true in market-service yml"
    fi
  done
else
  pass "P5A-SAFEFLAG: market-service yml not present (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [11] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P5A-EVID: docs/evidence/generated is tracked by git"
else
  pass "P5A-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 5-A activity/draw orchestration map is repo-ready."
  echo "        No draw execution was migrated. No remote flag was enabled."
  echo "        Phase 5-B draw-command boundary design is the recommended next step."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi
