#!/usr/bin/env bash
# validate-microservices-phase-3-rebate-read-adapter.sh
# Deterministic repo-only validation for Phase 3-A/B: rebate read adapter boundary.

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
echo "  Phase 3-A/B Rebate Read Adapter Boundary Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

IREAD_ADAPTER="big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IRebateReadAdapter.java"
LOCAL_READ_ADAPTER="big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java"
REMOTE_READ_ADAPTER="big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteReadAdapter.java"
CONTROLLER="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java"
IREBATE_SERVICE="big-market-api/src/main/java/com/dyx/market/trigger/api/IRebateService.java"
QUERY_DTO="big-market-api/src/main/java/com/dyx/market/trigger/api/dto/RebateOrderQueryRequestDTO.java"
LEGACY_RPC="big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java"
REBATE_RPC="big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java"
MKT_YML="big-market-market-service/src/main/resources/application.yml"
DC="docker-compose.yml"
DOC="docs/archive/phases.md"

echo ""
echo "-- [1] IRebateReadAdapter exists and declares isCalendarSignRebate"
check_file    "P3-READ-1 IRebateReadAdapter exists" "$IREAD_ADAPTER"
check_contains "P3-READ-2 IRebateReadAdapter declares isCalendarSignRebate" "$IREAD_ADAPTER" \
  "isCalendarSignRebate"

echo ""
echo "-- [2] LocalRebateReadAdapter exists, implements interface, delegates to queryOrderByOutBusinessNo"
check_file    "P3-READ-3 LocalRebateReadAdapter exists" "$LOCAL_READ_ADAPTER"
check_contains "P3-READ-4 LocalRebateReadAdapter implements IRebateReadAdapter" "$LOCAL_READ_ADAPTER" \
  "implements IRebateReadAdapter"
check_contains "P3-READ-5 LocalRebateReadAdapter delegates to queryOrderByOutBusinessNo" "$LOCAL_READ_ADAPTER" \
  "queryOrderByOutBusinessNo"
check_contains "P3-READ-6 LocalRebateReadAdapter has @ConditionalOnMissingBean(IRebateReadAdapter" "$LOCAL_READ_ADAPTER" \
  "@ConditionalOnMissingBean.*IRebateReadAdapter"

echo ""
echo "-- [3] RebateOrderQueryRequestDTO exists in big-market-api"
check_file    "P3-READ-7 RebateOrderQueryRequestDTO exists" "$QUERY_DTO"
check_contains "P3-READ-8 RebateOrderQueryRequestDTO has userId field" "$QUERY_DTO" \
  "userId"
check_contains "P3-READ-9 RebateOrderQueryRequestDTO has outBusinessNo field" "$QUERY_DTO" \
  "outBusinessNo"
check_contains "P3-READ-10 RebateOrderQueryRequestDTO implements Serializable" "$QUERY_DTO" \
  "Serializable"

echo ""
echo "-- [4] IRebateService declares isCalendarSignRebate"
check_contains "P3-READ-11 IRebateService declares isCalendarSignRebate" "$IREBATE_SERVICE" \
  "isCalendarSignRebate"
check_contains "P3-READ-12 IRebateService still declares rebate method" "$IREBATE_SERVICE" \
  "rebate\("

echo ""
echo "-- [5] Legacy RebateServiceRPC implements isCalendarSignRebate with ownership gate preserved"
check_contains "P3-READ-13 legacy RPC implements isCalendarSignRebate" "$LEGACY_RPC" \
  "isCalendarSignRebate"
check_contains "P3-READ-14 legacy RPC still has @ConditionalOnProperty ownership gate" "$LEGACY_RPC" \
  "rebate\.legacy-rpc-provider\.enabled"
check_contains "P3-READ-15 legacy RPC validates null request in read method" "$LEGACY_RPC" \
  "ILLEGAL_PARAMETER"
check_contains "P3-READ-16 legacy RPC validates appToken in read method" "$LEGACY_RPC" \
  "APP_TOKEN_ERROR"
check_contains "P3-READ-17 legacy RPC delegates queryOrderByOutBusinessNo in read method" "$LEGACY_RPC" \
  "queryOrderByOutBusinessNo"

echo ""
echo "-- [6] Rebate-service RebateServiceRPC implements isCalendarSignRebate"
check_contains "P3-READ-18 rebate-service RPC implements isCalendarSignRebate" "$REBATE_RPC" \
  "isCalendarSignRebate"
check_contains "P3-READ-19 rebate-service RPC validates null request in read method" "$REBATE_RPC" \
  "ILLEGAL_PARAMETER"
check_contains "P3-READ-20 rebate-service RPC validates appToken in read method" "$REBATE_RPC" \
  "APP_TOKEN_ERROR"
check_contains "P3-READ-21 rebate-service RPC delegates queryOrderByOutBusinessNo in read method" "$REBATE_RPC" \
  "queryOrderByOutBusinessNo"

echo ""
echo "-- [7] RebateRemoteReadAdapter exists, implements interface, has required structure"
check_file    "P3-READ-22 RebateRemoteReadAdapter exists" "$REMOTE_READ_ADAPTER"
check_contains "P3-READ-23 RebateRemoteReadAdapter implements IRebateReadAdapter" "$REMOTE_READ_ADAPTER" \
  "implements IRebateReadAdapter"
check_contains "P3-READ-24 RebateRemoteReadAdapter has @DubboReference(.*check.*false" "$REMOTE_READ_ADAPTER" \
  "@DubboReference.*check[[:space:]]*=[[:space:]]*false"
check_contains "P3-READ-25 RebateRemoteReadAdapter passes appId" "$REMOTE_READ_ADAPTER" \
  "\.appId\(appId\)"
check_contains "P3-READ-26 RebateRemoteReadAdapter passes appToken from appTokenMap" "$REMOTE_READ_ADAPTER" \
  "\.appToken\(appTokenMap\.get\(appId\)\)"
check_contains "P3-READ-27 RebateRemoteReadAdapter has local fallback via queryOrderByOutBusinessNo" "$REMOTE_READ_ADAPTER" \
  "queryOrderByOutBusinessNo"
check_contains "P3-READ-28 RebateRemoteReadAdapter remote-read flag defaults false" "$REMOTE_READ_ADAPTER" \
  "remote-read\.enabled:false"

echo ""
echo "-- [8] RaffleActivityController wires rebateReadAdapter and does NOT directly call behaviorRebateService"
check_contains "P3-READ-29 controller imports IRebateReadAdapter" "$CONTROLLER" \
  "import com\.dyx\.market\.trigger\.adapter\.IRebateReadAdapter"
check_contains "P3-READ-30 controller injects IRebateReadAdapter" "$CONTROLLER" \
  "IRebateReadAdapter rebateReadAdapter"
check_contains "P3-READ-31 controller calls rebateReadAdapter.isCalendarSignRebate" "$CONTROLLER" \
  "rebateReadAdapter\.isCalendarSignRebate"
check_not_contains "P3-READ-32 controller no longer directly calls behaviorRebateService.queryOrderByOutBusinessNo" "$CONTROLLER" \
  "behaviorRebateService\.queryOrderByOutBusinessNo"
check_not_contains "P3-READ-33 controller no longer imports IBehaviorRebateService" "$CONTROLLER" \
  "import com\.dyx\.market\.domain\.rebate\.service\.IBehaviorRebateService"

echo ""
echo "-- [9] Flag defaults: remote-read = false, remote-create-order = false, legacy provider = true"
check_contains "P3-READ-34 market-service yml remote-read.enabled defaults false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_READ_ENABLED:false"
check_contains "P3-READ-35 docker-compose remote-read defaults false" "$DC" \
  "REBATE_SERVICE_REMOTE_READ_ENABLED.*:-false"
check_contains "P3-READ-36 market-service yml remote-create-order still false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:false"
check_contains "P3-READ-37 docker-compose remote-create-order still false" "$DC" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED.*:-false"
check_contains "P3-READ-38 docker-compose legacy provider still true" "$DC" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED.*:-true"

echo ""
echo "-- [10] Dangerous Phase 2 flags remain false"
FLAG_FAIL=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true|ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true|ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true|account\.service\.remote-quota-decrement\.enabled[[:space:]]*=[[:space:]]*true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f"
    FLAG_FAIL=$((FLAG_FAIL + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$FLAG_FAIL" -eq 0 ]; then
  pass "P3-READ-39 no dangerous Phase 2 flag is hardcoded true"
else
  fail "P3-READ-39 dangerous flag hardcoded true in $FLAG_FAIL file(s)"
fi

echo ""
echo "-- [11] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated/*" | grep -q .; then
  fail "P3-READ-40 generated evidence files are tracked"
else
  pass "P3-READ-40 generated evidence files are not tracked"
fi

echo ""
echo "-- [12] Docs mention read adapter boundary and remaining blockers"
check_contains "P3-READ-41 doc mentions read adapter boundary" "$DOC" \
  "[Rr]ead [Aa]dapter|IRebateReadAdapter"
check_contains "P3-READ-42 doc mentions staging provider verification as blocker" "$DOC" \
  "[Ss]taging.*provider|provider.*[Ss]taging"
check_contains "P3-READ-43 doc mentions RebateMessageConsumer ownership as blocker" "$DOC" \
  "RebateMessageConsumer"
check_contains "P3-READ-44 doc mentions shared task outbox as blocker" "$DOC" \
  "[Ss]hared task outbox|task outbox"
check_contains "P3-READ-45 doc references read adapter validator" "$DOC" \
  "validate-microservices-phase-3-rebate-read-adapter"

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
