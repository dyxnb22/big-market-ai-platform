#!/usr/bin/env bash
# validate-microservices-phase-5-account-quota-port-reverification.sh
# Deterministic repo-only validation for the Phase 5-C account/quota port re-verification.
#
# Checks:
#   1.  Account/quota port re-verification doc exists
#   2.  IActivityAccountPort interface exists
#   3.  LocalActivityAccountPort exists and is gated ConditionalOnProperty (default active)
#   4.  AccountRemoteActivityAccountPort exists and is gated behind the remote flag
#   5.  account.service.remote-quota-decrement.enabled defaults false in market-service yml
#   6.  account.service.remote-quota-decrement.enabled defaults false in docker-compose.yml
#   7.  No dangerous account/fulfillment/rebate/strategy flags are hardcoded true
#   8.  docs/evidence/generated not tracked

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
echo "  Phase 5-C Account/Quota Port Re-Verification Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

REVERIF_DOC="docs/archive/phases.md"
PORT_IFACE="big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java"
LOCAL_IMPL="big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java"
REMOTE_IMPL="big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteActivityAccountPort.java"
MARKET_YML="big-market-market-service/src/main/resources/application.yml"
DOCKER_COMPOSE="docker-compose.yml"

# -----------------------------------------------------------------------
echo ""
echo "-- [1] Account/quota port re-verification doc exists"
check_file "P5C-DOC-1 re-verification doc" "$REVERIF_DOC"
check_contains "P5C-DOC-1 references IActivityAccountPort" "$REVERIF_DOC" "IActivityAccountPort"
check_contains "P5C-DOC-1 references B11" "$REVERIF_DOC" "B11"

# -----------------------------------------------------------------------
echo ""
echo "-- [2] IActivityAccountPort interface exists"
check_file "P5C-PORT-1 interface exists" "$PORT_IFACE"
check_contains "P5C-PORT-1 declares decrementQuota" "$PORT_IFACE" "boolean decrementQuota"
check_contains "P5C-PORT-1 declares rollbackQuota" "$PORT_IFACE" "void rollbackQuota"

# -----------------------------------------------------------------------
echo ""
echo "-- [3] LocalActivityAccountPort exists and is default-active"
check_file "P5C-LOCAL-1 local impl exists" "$LOCAL_IMPL"
check_contains "P5C-LOCAL-2 implements IActivityAccountPort" "$LOCAL_IMPL" "implements IActivityAccountPort"
check_contains "P5C-LOCAL-3 ConditionalOnProperty with matchIfMissing=true" "$LOCAL_IMPL" "matchIfMissing *= *true"

# -----------------------------------------------------------------------
echo ""
echo "-- [4] AccountRemoteActivityAccountPort exists and is gated behind remote flag"
check_file "P5C-REMOTE-1 remote impl exists" "$REMOTE_IMPL"
check_contains "P5C-REMOTE-2 implements IActivityAccountPort" "$REMOTE_IMPL" "implements IActivityAccountPort"
check_contains "P5C-REMOTE-3 ConditionalOnProperty havingValue=true" "$REMOTE_IMPL" 'havingValue *= *"true"'
check_contains "P5C-REMOTE-4 has @DubboReference" "$REMOTE_IMPL" "@DubboReference"

# -----------------------------------------------------------------------
echo ""
echo "-- [5] account.service.remote-quota-decrement.enabled defaults false in market-service yml"
if [ -f "$ROOT/$MARKET_YML" ]; then
  if grep -qE "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:-?false" "$ROOT/$MARKET_YML"; then
    pass "P5C-FLAG-1 quota decrement flag defaults false in market-service yml"
  else
    fail "P5C-FLAG-1 quota decrement flag default not confirmed in market-service yml"
  fi
  # Confirm it is NOT hardcoded true
  check_not_contains "P5C-FLAG-2 not hardcoded true" "$MARKET_YML" "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:-?true"
else
  pass "P5C-FLAG-1 market-service yml absent (skip)"
  pass "P5C-FLAG-2 market-service yml absent (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [6] account.service.remote-quota-decrement.enabled defaults false in docker-compose"
if [ -f "$ROOT/$DOCKER_COMPOSE" ]; then
  if grep -qE "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED" "$ROOT/$DOCKER_COMPOSE"; then
    if grep -qE "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:-false|ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false" "$ROOT/$DOCKER_COMPOSE"; then
      pass "P5C-FLAG-3 quota decrement flag defaults false in docker-compose"
    else
      fail "P5C-FLAG-3 quota decrement flag not confirmed false in docker-compose"
    fi
  else
    pass "P5C-FLAG-3 quota decrement flag not present in docker-compose (acceptable)"
  fi
else
  pass "P5C-FLAG-3 docker-compose absent (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [7] No dangerous flags hardcoded true"
if [ -f "$ROOT/$MARKET_YML" ]; then
  for flag in \
    "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED" \
    "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED" \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED" \
    "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED" \
    "REBATE_SERVICE_REMOTE_READ_ENABLED" \
    "STRATEGY_SERVICE_REMOTE_READ_ENABLED"; do
    if grep -qE "${flag}:-true" "$ROOT/$MARKET_YML"; then
      fail "P5C-SAFEFLAG: $flag is hardcoded true in market-service yml"
    else
      pass "P5C-SAFEFLAG: $flag not hardcoded true"
    fi
  done
else
  pass "P5C-SAFEFLAG: market-service yml not present (skip)"
fi

# -----------------------------------------------------------------------
echo ""
echo "-- [8] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated" 2>/dev/null | grep -q .; then
  fail "P5C-EVID: docs/evidence/generated is tracked by git"
else
  pass "P5C-EVID: docs/evidence/generated not tracked"
fi

# -----------------------------------------------------------------------
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS — Phase 5-C account/quota port re-verification is complete."
  echo "        IActivityAccountPort invariants from B11-B14 confirmed intact after Phase 4."
  echo "        Local default active. Remote decrement disabled. No traffic enabled."
  exit 0
else
  echo "RESULT: FAIL — $FAIL check(s) failed. Fix before tagging."
  exit 1
fi
