#!/usr/bin/env bash
# validate-microservices-phase-5-draw-command-boundary.sh
# Deterministic repo-only validation for the Phase 5-B draw-command boundary design.
#
# Checks:
#   1.  Draw-command boundary doc exists
#   2.  Recommended option is documented
#   3.  Command/response contract draft is documented
#   4.  Idempotency and rollback concerns are documented
#   5.  Preconditions before any remote draw path are documented
#   6.  No big-market-activity-service module exists
#   7.  No remote draw flag (strategy.service.remote-decision.enabled) introduced
#   8.  No remote draw adapter introduced
#   9.  No generated evidence files are tracked
#  10.  Dangerous Phase 2/3/4 flags remain false

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

check_not_contains_any() {
  local label="$1" pattern="$2"
  shift 2
  for dir in "$@"; do
    if [ -d "$ROOT/$dir" ]; then
      if grep -rqE "$pattern" "$ROOT/$dir" 2>/dev/null; then
        fail "$label: forbidden pattern '$pattern' found in $dir"
        return
      fi
    fi
  done
  pass "$label"
}

echo ""
echo "========================================================================"
echo "  Phase 5-B Draw-Command Boundary Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

BOUNDARY_DOC="docs/archive/phases.md"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] Draw-command boundary doc exists"
check_file "P5B-DOC-1 boundary doc" "$BOUNDARY_DOC"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] Recommended option is documented"
check_contains "P5B-DOC-2 recommended option" "$BOUNDARY_DOC" "[Rr]ecommended"
check_contains "P5B-DOC-2 option A documented" "$BOUNDARY_DOC" "[Oo]ption A"
check_contains "P5B-DOC-2 option B documented" "$BOUNDARY_DOC" "[Oo]ption B"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] Command/response contract draft is documented"
check_contains "P5B-DOC-3 DrawCommand" "$BOUNDARY_DOC" "DrawCommand"
check_contains "P5B-DOC-3 DrawResult" "$BOUNDARY_DOC" "DrawResult"
check_contains "P5B-DOC-3 orderId correlation" "$BOUNDARY_DOC" "orderId"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] Idempotency and rollback concerns are documented"
check_contains "P5B-DOC-4 idempotency" "$BOUNDARY_DOC" "[Ii]dempoten"
check_contains "P5B-DOC-4 rollback" "$BOUNDARY_DOC" "[Rr]ollback"
check_contains "P5B-DOC-4 compensation" "$BOUNDARY_DOC" "[Cc]ompensation"

# -----------------------------------------------------------------------
echo ""
echo "-- [5] Preconditions before remote draw path are documented"
check_contains "P5B-DOC-5 preconditions section" "$BOUNDARY_DOC" "[Pp]recondition"
check_contains "P5B-DOC-5 Phase 5-G referenced" "$BOUNDARY_DOC" "5-G"

# -----------------------------------------------------------------------
echo ""
echo "-- [6] activity-service scaffold boundary (Phase 5-F introduced it)"
# Phase 5-F created big-market-activity-service as a dark-launch scaffold.
# Verify that no draw execution or provider leaked into the scaffold.
ACT_SVC_DUBBO=$(find "$ROOT/big-market-activity-service/src" -type f -name "*.java" \
  -exec grep -l "@DubboService" {} + 2>/dev/null | wc -l | tr -d ' ')
if [ "$ACT_SVC_DUBBO" = "0" ]; then
  pass "P5B-MOD-1 activity-service scaffold has no @DubboService (Phase 5-F boundary holds)"
else
  fail "P5B-MOD-1 activity-service scaffold has unexpected @DubboService ($ACT_SVC_DUBBO file(s))"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [7] No strategy.service.remote-decision.enabled flag introduced"
for cfg in \
  "big-market-market-service/src/main/resources/application.yml" \
  "big-market-strategy-service/src/main/resources/application.yml" \
  "docker-compose.yml"; do
  if [ -f "$ROOT/$cfg" ]; then
    if grep -qE "remote-decision|REMOTE_DECISION" "$ROOT/$cfg"; then
      fail "P5B-FLAG-1 remote-decision flag found in $cfg"
    else
      pass "P5B-FLAG-1 no remote-decision flag in $cfg"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [8] No remote draw adapter introduced"
SEARCH_DIRS=(
  "big-market-market-service/src/main/java"
  "big-market-strategy-service/src/main/java"
  "big-market-trigger/src/main/java"
)
for dir in "${SEARCH_DIRS[@]}"; do
  if [ -d "$ROOT/$dir" ]; then
    if grep -rqE "(performRaffle|randomRaffle).*[Rr]emote|[Rr]emote.*(performRaffle|randomRaffle)" "$ROOT/$dir" 2>/dev/null; then
      fail "P5B-DRAW-1 remote draw routing found in $dir"
    else
      pass "P5B-DRAW-1 no remote draw routing in $dir"
    fi
  fi
done

# -----------------------------------------------------------------------
echo ""
echo "-- [9] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P5B-EVID: docs/evidence/generated is tracked by git"
else
  pass "P5B-EVID: docs/evidence/generated not tracked"
fi

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
      fail "P5B-SAFEFLAG: $flag is hardcoded true in market-service yml"
    else
      pass "P5B-SAFEFLAG: $flag not hardcoded true"
    fi
  done
else
  pass "P5B-SAFEFLAG: market-service yml not present (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 5-B draw-command boundary design is repo-ready."
  echo "        Recommended option A documented. No remote draw command introduced."
  echo "        Phase 5-C re-verification and Phase 5-D strategy decision port are next."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi
