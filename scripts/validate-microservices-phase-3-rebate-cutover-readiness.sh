#!/usr/bin/env bash
# validate-microservices-phase-3-rebate-cutover-readiness.sh
# Phase 3-E: dry-run cutover rehearsal for rebate-service.
# This script performs ONLY repo-only static checks. It does NOT connect to any external system,
# does NOT enable any traffic, and does NOT modify any flag.
#
# If all checks pass it prints the ordered cutover steps an operator must follow externally.
# If any required flag is currently true in repo files, the script exits non-zero as a safety gate.

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

# Paths
REBATE_APP="big-market-rebate-service/src/main/java/com/dyx/market/rebate/RebateServiceApplication.java"
REBATE_RPC="big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java"
LEGACY_RPC="big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java"
IREBATE="big-market-api/src/main/java/com/dyx/market/trigger/api/IRebateService.java"
REMOTE_CREATE="big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteCreateOrderAdapter.java"
REMOTE_READ="big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteReadAdapter.java"
MKT_YML="big-market-market-service/src/main/resources/application.yml"
DC="docker-compose.yml"
OUTBOX_DOC="docs/microservices-split-phase-3-rebate-outbox-ownership.md"

echo ""
echo "========================================================================"
echo "  Phase 3-E Rebate Cutover-Readiness Rehearsal (DRY-RUN — REPO ONLY)"
echo "  Repo: $ROOT"
echo "  !! This script does NOT connect to Nacos, DB, MQ, or any external system."
echo "  !! It does NOT enable any traffic flag."
echo "========================================================================"

echo ""
echo "-- [1] Rebate-service module and provider exist"
check_file "P3-CUT-1 rebate-service module/pom.xml exists" "big-market-rebate-service/pom.xml"
check_file "P3-CUT-2 RebateServiceApplication exists" "$REBATE_APP"
check_file "P3-CUT-3 rebate-service provider RebateServiceRPC exists" "$REBATE_RPC"

echo ""
echo "-- [2] IRebateService exposes both rebate() and isCalendarSignRebate()"
check_file "P3-CUT-4 IRebateService contract exists" "$IREBATE"
check_contains "P3-CUT-5 IRebateService declares rebate(...)" "$IREBATE" \
  "rebate\("
check_contains "P3-CUT-6 IRebateService declares isCalendarSignRebate(...)" "$IREBATE" \
  "isCalendarSignRebate"

echo ""
echo "-- [3] Both legacy and rebate-service providers implement both methods"
check_contains "P3-CUT-7 rebate-service RPC implements rebate()" "$REBATE_RPC" \
  "public Response.*rebate\("
check_contains "P3-CUT-8 rebate-service RPC implements isCalendarSignRebate()" "$REBATE_RPC" \
  "public Response.*isCalendarSignRebate\("
check_contains "P3-CUT-9 legacy RPC implements rebate()" "$LEGACY_RPC" \
  "public Response.*rebate\("
check_contains "P3-CUT-10 legacy RPC implements isCalendarSignRebate()" "$LEGACY_RPC" \
  "public Response.*isCalendarSignRebate\("

echo ""
echo "-- [4] Legacy provider can be disabled (gate present) but default remains true"
check_contains "P3-CUT-11 legacy RPC has @ConditionalOnProperty gate" "$LEGACY_RPC" \
  "@ConditionalOnProperty"
check_contains "P3-CUT-12 legacy RPC gate property is rebate.legacy-rpc-provider.enabled" "$LEGACY_RPC" \
  "rebate\.legacy-rpc-provider\.enabled"
check_contains "P3-CUT-13 legacy RPC gate matchIfMissing=true (default-on)" "$LEGACY_RPC" \
  "matchIfMissing[[:space:]]*=[[:space:]]*true"
check_contains "P3-CUT-14 market-service yml legacy provider default true" "$MKT_YML" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED:true"
check_contains "P3-CUT-15 docker-compose legacy provider default true" "$DC" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED.*:-true"

echo ""
echo "-- [5] Remote create-order adapter exists, has @DubboReference(check=false), defaults false"
check_file "P3-CUT-16 RebateRemoteCreateOrderAdapter exists" "$REMOTE_CREATE"
check_contains "P3-CUT-17 remote create-order has @DubboReference(check=false)" "$REMOTE_CREATE" \
  "@DubboReference.*check[[:space:]]*=[[:space:]]*false"
check_contains "P3-CUT-18 remote create-order defaults false in adapter" "$REMOTE_CREATE" \
  "remote-create-order\.enabled:false"
check_contains "P3-CUT-19 market-service yml remote-create-order default false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:false"
check_contains "P3-CUT-20 docker-compose remote-create-order default false" "$DC" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED.*:-false"

echo ""
echo "-- [6] Remote read adapter exists, has @DubboReference(check=false), defaults false"
check_file "P3-CUT-21 RebateRemoteReadAdapter exists" "$REMOTE_READ"
check_contains "P3-CUT-22 remote read has @DubboReference(check=false)" "$REMOTE_READ" \
  "@DubboReference.*check[[:space:]]*=[[:space:]]*false"
check_contains "P3-CUT-23 remote read defaults false in adapter" "$REMOTE_READ" \
  "remote-read\.enabled:false"
check_contains "P3-CUT-24 market-service yml remote-read default false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_READ_ENABLED:false"
check_contains "P3-CUT-25 docker-compose remote-read default false" "$DC" \
  "REBATE_SERVICE_REMOTE_READ_ENABLED.*:-false"

echo ""
echo "-- [7] SAFETY GATE: no remote rebate flag is currently hardcoded true in any config"
SAFETY_FAIL=0
while IFS= read -r f; do
  for pattern in \
    "rebate\.service\.remote-create-order\.enabled[[:space:]]*=[[:space:]]*true" \
    "rebate\.service\.remote-read\.enabled[[:space:]]*=[[:space:]]*true" \
    "rebate\.legacy-rpc-provider\.enabled[[:space:]]*=[[:space:]]*false"; do
    if grep -qE "$pattern" "$f" 2>/dev/null; then
      echo "  [SAFETY] flag change detected in $f: $pattern"
      SAFETY_FAIL=$((SAFETY_FAIL + 1))
    fi
  done
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$SAFETY_FAIL" -eq 0 ]; then
  pass "P3-CUT-26 SAFETY: no rebate remote flag is currently enabled (safe for dry-run)"
else
  fail "P3-CUT-26 SAFETY: $SAFETY_FAIL rebate flag(s) deviate from safe defaults — do not proceed with cutover"
fi

echo ""
echo "-- [8] Outbox ownership decision is documented"
check_file "P3-CUT-27 outbox ownership decision doc exists" "$OUTBOX_DOC"
check_contains "P3-CUT-28 outbox doc documents rebate-owned tables" "$OUTBOX_DOC" \
  "daily_behavior_rebate|user_behavior_rebate_order"
check_contains "P3-CUT-29 outbox doc documents shared task table coupling" "$OUTBOX_DOC" \
  "task"
check_contains "P3-CUT-30 outbox doc defines Phase 7-C proposed outbox" "$OUTBOX_DOC" \
  "7-C|rebate_task_outbox"

echo ""
echo "-- [9] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated/*" | grep -q .; then
  fail "P3-CUT-31 generated evidence files are tracked"
else
  pass "P3-CUT-31 generated evidence files are not tracked"
fi

echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "PASS: $PASS"
echo "FAIL: $FAIL"

if [ "$FAIL" -gt 0 ]; then
  echo "RESULT: FAIL — fix the above before rehearsing cutover"
  exit 1
fi

echo "RESULT: PASS — all repo-only pre-conditions verified"
echo ""
echo "========================================================================"
echo "  CUTOVER ORDER (DRY-RUN ONLY — DO NOT EXECUTE FROM THIS SCRIPT)"
echo "  These steps require external coordination outside this repo batch."
echo "========================================================================"
echo ""
echo "  Step 1. Deploy big-market-rebate-service to the staging Nacos registry."
echo "          Verify that IRebateService version 1.0 is registered by rebate-service"
echo "          by checking the Nacos service-list UI or CLI — NOT from this script."
echo ""
echo "  Step 2. Verify provider registration externally (Nacos console or dubbo-admin)."
echo "          Confirm both providers are visible: market-service (legacy) and rebate-service (new)."
echo "          Run staging smoke test: calendarSignRebate with local adapter still active (flag=false)."
echo ""
echo "  Step 3. Disable legacy provider on market-service."
echo "          Set: REBATE_LEGACY_RPC_PROVIDER_ENABLED=false on big-market-market-service."
echo "          Redeploy market-service. Verify only rebate-service is registered in Nacos."
echo ""
echo "  Step 4. Enable remote read first."
echo "          Set: REBATE_SERVICE_REMOTE_READ_ENABLED=true on big-market-market-service."
echo "          Smoke test: isCalendarSignRebate returns correct result via remote path."
echo "          Monitor logs and metrics. Rollback = REBATE_SERVICE_REMOTE_READ_ENABLED=false."
echo ""
echo "  Step 5. Enable remote create-order after read path is verified stable."
echo "          Set: REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true on big-market-market-service."
echo "          Smoke test: calendarSignRebate creates order via remote path."
echo "          Monitor rebate order counts and task outbox rows."
echo "          Rollback = REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=false."
echo ""
echo "  Step 6. Monitor and rollback if needed."
echo "          Any anomaly: flip the affected flag back to false (no DB rollback required)."
echo "          After 7 days stable: set REBATE_LEGACY_RPC_PROVIDER_ENABLED=false permanently."
echo "          After 30 days stable: remove legacy RebateServiceRPC from big-market-trigger."
echo ""
echo "  NOTE: No traffic is enabled by running this script. Steps 1-6 require"
echo "        human operator action in the staging and production environments."
echo "========================================================================"
exit 0
