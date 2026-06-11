#!/usr/bin/env bash
# validate-microservices-phase-3-rebate-provider-ownership.sh
# Deterministic repo-only validation for the Phase 3 rebate provider ownership gate.

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
echo "  Phase 3 Rebate Provider Ownership Gate Validator"
echo "  Repo: $ROOT"
echo "========================================================================"

LEGACY_RPC="big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java"
MKT_YML="big-market-market-service/src/main/resources/application.yml"
DC="docker-compose.yml"
DOC="docs/microservices-split-phase-3-next-extraction.md"
REBATE_APP="big-market-rebate-service/src/main/java/com/dyx/market/rebate/RebateServiceApplication.java"
REMOTE_ADP="big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteCreateOrderAdapter.java"

echo ""
echo "-- [1] Legacy RebateServiceRPC has @ConditionalOnProperty"
check_file "P3-OWN-1 legacy RebateServiceRPC exists" "$LEGACY_RPC"
check_contains "P3-OWN-2 legacy provider has @ConditionalOnProperty" "$LEGACY_RPC" \
  "@ConditionalOnProperty"
check_contains "P3-OWN-3 property name is rebate.legacy-rpc-provider.enabled" "$LEGACY_RPC" \
  'rebate\.legacy-rpc-provider\.enabled'
check_contains "P3-OWN-4 matchIfMissing=true (default-enabled behavior)" "$LEGACY_RPC" \
  "matchIfMissing[[:space:]]*=[[:space:]]*true"
check_contains "P3-OWN-5 ConditionalOnProperty import present" "$LEGACY_RPC" \
  "import org\.springframework\.boot\.autoconfigure\.condition\.ConditionalOnProperty"

echo ""
echo "-- [2] market-service config defaults legacy provider enabled"
check_contains "P3-OWN-6 market-service yml declares legacy-rpc-provider.enabled" "$MKT_YML" \
  "legacy-rpc-provider"
check_contains "P3-OWN-7 market-service yml defaults REBATE_LEGACY_RPC_PROVIDER_ENABLED:true" "$MKT_YML" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED:true"

echo ""
echo "-- [3] docker-compose wires legacy provider flag with safe default"
check_contains "P3-OWN-8 docker-compose sets REBATE_LEGACY_RPC_PROVIDER_ENABLED default true" "$DC" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED.*:-true"

echo ""
echo "-- [4] remote-create-order flag remains false (no traffic enabled)"
check_contains "P3-OWN-9 market-service yml remote-create-order still false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:false"
check_contains "P3-OWN-10 docker-compose remote-create-order still false" "$DC" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED.*:-false"
check_not_contains "P3-OWN-11 remote adapter flag not hardcoded true" "$REMOTE_ADP" \
  "remoteCreateOrderEnabled[[:space:]]*=[[:space:]]*true"

echo ""
echo "-- [5] big-market-rebate-service provider still present"
check_contains "P3-OWN-12 root pom still registers rebate-service" "pom.xml" \
  "<module>big-market-rebate-service</module>"
check_file "P3-OWN-13 rebate-service application class still present" "$REBATE_APP"

echo ""
echo "-- [6] RebateRemoteCreateOrderAdapter still passes appId and appToken"
check_contains "P3-OWN-14 remote adapter passes appId" "$REMOTE_ADP" \
  "\.appId\(appId\)"
check_contains "P3-OWN-15 remote adapter passes appToken from appTokenMap" "$REMOTE_ADP" \
  "\.appToken\(appTokenMap\.get\(appId\)\)"

echo ""
echo "-- [7] Dangerous Phase 2 flags remain false"
FLAG_FAIL=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true|ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true|ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true|account\.service\.remote-quota-decrement\.enabled[[:space:]]*=[[:space:]]*true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f"
    FLAG_FAIL=$((FLAG_FAIL + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$FLAG_FAIL" -eq 0 ]; then
  pass "P3-OWN-16 no dangerous Phase 2 flag is hardcoded true"
else
  fail "P3-OWN-16 dangerous flag hardcoded true in $FLAG_FAIL file(s)"
fi

echo ""
echo "-- [8] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated/*" | grep -q .; then
  fail "P3-OWN-17 generated evidence files are tracked"
else
  pass "P3-OWN-17 generated evidence files are not tracked"
fi

echo ""
echo "-- [9] Docs mention duplicate provider risk and cutover order"
check_contains "P3-OWN-18 doc mentions duplicate provider risk" "$DOC" \
  "[Dd]uplicate.*provider|provider.*[Dd]uplicate"
check_contains "P3-OWN-19 doc mentions future cutover order" "$DOC" \
  "[Cc]utover order"
check_contains "P3-OWN-20 doc mentions REBATE_LEGACY_RPC_PROVIDER_ENABLED=false step" "$DOC" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED=false"
check_contains "P3-OWN-21 doc mentions provider ownership validator" "$DOC" \
  "validate-microservices-phase-3-rebate-provider-ownership"

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
