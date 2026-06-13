#!/usr/bin/env bash
# validate-microservices-phase-3-rebate-adapter.sh
# Deterministic repo-only validation for the Phase 3 rebate adapter boundary.

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
echo "  Phase 3 Rebate Adapter Boundary Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

IFACE="big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IRebateOrderAdapter.java"
LOCAL="big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java"
REMOTE="big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteCreateOrderAdapter.java"
CTRL="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java"
MKT_YML="big-market-market-service/src/main/resources/application.yml"
DC="docker-compose.yml"
DOC="docs/archive/phases.md"

echo ""
echo "-- [1] Adapter interface"
check_file "P3-ADP-1 adapter interface exists" "$IFACE"
check_contains "P3-ADP-2 interface declares createOrder" "$IFACE" "List<String> createOrder"
check_contains "P3-ADP-3 interface is in trigger.adapter package" "$IFACE" "package com.dyx.market.trigger.adapter"

echo ""
echo "-- [2] Local adapter"
check_file "P3-ADP-4 local adapter exists" "$LOCAL"
check_contains "P3-ADP-5 local adapter implements interface" "$LOCAL" "implements IRebateOrderAdapter"
check_contains "P3-ADP-6 local adapter delegates to IBehaviorRebateService" "$LOCAL" "IBehaviorRebateService"
check_contains "P3-ADP-7 local adapter is conditional on missing bean" "$LOCAL" "@ConditionalOnMissingBean"
check_not_contains "P3-ADP-8 local adapter has no Dubbo reference" "$LOCAL" "@DubboReference"
check_not_contains "P3-ADP-9 local adapter has no flag" "$LOCAL" "remoteCreateOrder|remote-create-order"

echo ""
echo "-- [3] Controller wiring"
check_file "P3-ADP-10 controller exists" "$CTRL"
check_contains "P3-ADP-11 controller imports IRebateOrderAdapter" "$CTRL" "import com.dyx.market.trigger.adapter.IRebateOrderAdapter"
check_contains "P3-ADP-12 controller injects rebateOrderAdapter" "$CTRL" "IRebateOrderAdapter rebateOrderAdapter"
check_contains "P3-ADP-13 calendarSignRebate uses adapter createOrder" "$CTRL" "rebateOrderAdapter\.createOrder"
check_not_contains "P3-ADP-14 calendarSignRebate does not call behaviorRebateService.createOrder directly" \
  "$CTRL" "behaviorRebateService\.createOrder"

echo ""
echo "-- [4] Remote adapter (market-service)"
check_file "P3-ADP-15 remote adapter exists" "$REMOTE"
check_contains "P3-ADP-16 remote adapter implements interface" "$REMOTE" "implements IRebateOrderAdapter"
check_contains "P3-ADP-17 remote adapter has DubboReference for IRebateService" "$REMOTE" "@DubboReference"
check_contains "P3-ADP-18 remote adapter uses check=false" "$REMOTE" "check = false"
check_contains "P3-ADP-19 remote adapter guarded by flag" "$REMOTE" "remoteCreateOrderEnabled"
check_contains "P3-ADP-20 remote adapter has local fallback to IBehaviorRebateService" "$REMOTE" "IBehaviorRebateService"
check_contains "P3-ADP-21 remote adapter passes appId" "$REMOTE" "\\.appId\\(appId\\)"
check_contains "P3-ADP-22 remote adapter passes appToken from appTokenMap" "$REMOTE" "\\.appToken\\(appTokenMap\\.get\\(appId\\)\\)"
check_not_contains "P3-ADP-23 remote adapter flag is not hardcoded true" "$REMOTE" "remoteCreateOrderEnabled = true"

echo ""
echo "-- [5] Flag defaults"
check_contains "P3-ADP-24 market-service yml flag defaults false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:false"
check_contains "P3-ADP-25 market-service yml app id defaults to token-map key" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_APP_ID:chatgpt-data"
check_contains "P3-ADP-26 docker-compose flag defaults false" "$DC" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED.*:-false"

echo ""
echo "-- [6] rebate-service module still present"
check_contains "P3-ADP-27 root pom still registers rebate-service" "pom.xml" \
  "<module>big-market-rebate-service</module>"
check_file "P3-ADP-28 rebate-service application class still present" \
  "big-market-rebate-service/src/main/java/com/dyx/market/rebate/RebateServiceApplication.java"

echo ""
echo "-- [7] No forbidden scan added to rebate-service"
APP="big-market-rebate-service/src/main/java/com/dyx/market/rebate/RebateServiceApplication.java"
check_not_contains "P3-ADP-29 rebate-service does not scan trigger packages" "$APP" "com.dyx.market.trigger"
check_not_contains "P3-ADP-30 rebate-service does not scan job packages" "$APP" "trigger.job|trigger.listener"

echo ""
echo "-- [8] Dangerous Phase 2 flags remain false"
FLAG_FAIL=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true|ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true|ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true|account\.service\.remote-quota-decrement\.enabled[[:space:]]*=[[:space:]]*true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f"
    FLAG_FAIL=$((FLAG_FAIL + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$FLAG_FAIL" -eq 0 ]; then
  pass "P3-ADP-31 no dangerous Phase 2 flag is hardcoded true"
else
  fail "P3-ADP-31 dangerous flag hardcoded true in $FLAG_FAIL file(s)"
fi

echo ""
echo "-- [9] Docs mention adapter boundary and cutover blockers"
check_contains "P3-ADP-32 doc mentions adapter boundary" "$DOC" "rebate adapter boundary"
check_contains "P3-ADP-33 doc mentions duplicate provider risk" "$DOC" "[Dd]uplicate.*provider|provider.*[Dd]uplicate"
check_contains "P3-ADP-34 doc mentions shared task outbox" "$DOC" "task outbox"
check_contains "P3-ADP-35 doc mentions rebate adapter validator" "$DOC" "validate-microservices-phase-3-rebate-adapter"

echo ""
echo "-- [10] Generated evidence files not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated/*" | grep -q .; then
  fail "P3-ADP-36 generated evidence files are tracked"
else
  pass "P3-ADP-36 generated evidence files are not tracked"
fi

echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "PASS: $PASS"
echo "FAIL: $FAIL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT: PASS"
  exit 0
else
  echo "RESULT: FAIL"
  exit 1
fi
