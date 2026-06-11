#!/usr/bin/env bash
# validate-microservices-phase-3-rebate-dependency-narrowing.sh
# Phase 3-C: deterministic repo-only structural audit of rebate-service dependency narrowing.
# Checks module wiring, forbidden scan packages, forbidden provider imports, adapter gating,
# flag safety, and generated evidence tracking.

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
ROOT_POM="pom.xml"
REBATE_APP="big-market-rebate-service/src/main/java/com/dyx/market/rebate/RebateServiceApplication.java"
REBATE_POM="big-market-rebate-service/pom.xml"
REBATE_RPC="big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java"
LEGACY_RPC="big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java"
CONTROLLER="big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java"
REMOTE_CREATE="big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteCreateOrderAdapter.java"
REMOTE_READ="big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteReadAdapter.java"
MKT_YML="big-market-market-service/src/main/resources/application.yml"
DC="docker-compose.yml"

echo ""
echo "========================================================================"
echo "  Phase 3-C Rebate Dependency Narrowing Audit"
echo "  Repo: $ROOT"
echo "========================================================================"

echo ""
echo "-- [1] Module wiring"
check_contains "P3-DEP-1 root pom declares rebate-service module" "$ROOT_POM" \
  "<module>big-market-rebate-service</module>"
check_file "P3-DEP-2 rebate-service pom.xml exists" "$REBATE_POM"
check_file "P3-DEP-3 RebateServiceApplication exists" "$REBATE_APP"

echo ""
echo "-- [2] Rebate-service does NOT depend on big-market-trigger"
check_not_contains "P3-DEP-4 rebate-service pom does not declare big-market-trigger dependency" "$REBATE_POM" \
  "<artifactId>big-market-trigger</artifactId>"

echo ""
echo "-- [3] Rebate-service scan packages do not include trigger or job packages"
check_not_contains "P3-DEP-5 RebateServiceApplication does not scan trigger.http" "$REBATE_APP" \
  "trigger\.http"
check_not_contains "P3-DEP-6 RebateServiceApplication does not scan trigger.listener" "$REBATE_APP" \
  "trigger\.listener"
check_not_contains "P3-DEP-7 RebateServiceApplication does not scan trigger.job" "$REBATE_APP" \
  "trigger\.job"
check_not_contains "P3-DEP-8 RebateServiceApplication does not scan message.job" "$REBATE_APP" \
  "message\.job"

echo ""
echo "-- [4] Rebate-service scan packages include only rebate-domain-facing packages"
check_contains "P3-DEP-9 RebateServiceApplication scans com.dyx.market.rebate" "$REBATE_APP" \
  "com\.dyx\.market\.rebate"
check_contains "P3-DEP-10 RebateServiceApplication scans com.dyx.market.domain.rebate" "$REBATE_APP" \
  "com\.dyx\.market\.domain\.rebate"

echo ""
echo "-- [5] Rebate provider does not import activity/strategy/award/account/credit/fulfillment/auth/admin/chatbot domains"
for domain in activity strategy award account credit fulfillment auth admin chatbot; do
  check_not_contains "P3-DEP-11 rebate provider does not import domain.${domain}" "$REBATE_RPC" \
    "import com\.dyx\.market\.domain\.${domain}"
done

echo ""
echo "-- [6] RaffleActivityController uses IRebateOrderAdapter (not direct IBehaviorRebateService call) for write"
check_contains "P3-DEP-21 controller imports IRebateOrderAdapter" "$CONTROLLER" \
  "import com\.dyx\.market\.trigger\.adapter\.IRebateOrderAdapter"
check_contains "P3-DEP-22 controller injects rebateOrderAdapter" "$CONTROLLER" \
  "IRebateOrderAdapter rebateOrderAdapter"
check_contains "P3-DEP-23 calendarSignRebate calls rebateOrderAdapter.createOrder" "$CONTROLLER" \
  "rebateOrderAdapter\.createOrder"

echo ""
echo "-- [7] RaffleActivityController uses IRebateReadAdapter for isCalendarSignRebate read"
check_contains "P3-DEP-24 controller imports IRebateReadAdapter" "$CONTROLLER" \
  "import com\.dyx\.market\.trigger\.adapter\.IRebateReadAdapter"
check_contains "P3-DEP-25 controller injects rebateReadAdapter" "$CONTROLLER" \
  "IRebateReadAdapter rebateReadAdapter"
check_contains "P3-DEP-26 isCalendarSignRebate calls rebateReadAdapter.isCalendarSignRebate" "$CONTROLLER" \
  "rebateReadAdapter\.isCalendarSignRebate"
check_not_contains "P3-DEP-27 controller does not directly import IBehaviorRebateService" "$CONTROLLER" \
  "import com\.dyx\.market\.domain\.rebate\.service\.IBehaviorRebateService"
check_not_contains "P3-DEP-28 controller does not call behaviorRebateService.queryOrderByOutBusinessNo" "$CONTROLLER" \
  "behaviorRebateService\.queryOrderByOutBusinessNo"

echo ""
echo "-- [8] Remote adapters are flag-gated and default false"
check_contains "P3-DEP-29 remote create-order adapter has @DubboReference check=false" "$REMOTE_CREATE" \
  "@DubboReference.*check[[:space:]]*=[[:space:]]*false"
check_contains "P3-DEP-30 remote create-order adapter defaults false" "$REMOTE_CREATE" \
  "remote-create-order\.enabled:false"
check_contains "P3-DEP-31 remote read adapter has @DubboReference check=false" "$REMOTE_READ" \
  "@DubboReference.*check[[:space:]]*=[[:space:]]*false"
check_contains "P3-DEP-32 remote read adapter defaults false" "$REMOTE_READ" \
  "remote-read\.enabled:false"

echo ""
echo "-- [9] Legacy rebate provider gated by rebate.legacy-rpc-provider.enabled matchIfMissing=true"
check_contains "P3-DEP-33 legacy RPC has @ConditionalOnProperty" "$LEGACY_RPC" \
  "@ConditionalOnProperty"
check_contains "P3-DEP-34 legacy RPC uses rebate.legacy-rpc-provider.enabled" "$LEGACY_RPC" \
  "rebate\.legacy-rpc-provider\.enabled"
check_contains "P3-DEP-35 legacy RPC matchIfMissing=true" "$LEGACY_RPC" \
  "matchIfMissing[[:space:]]*=[[:space:]]*true"

echo ""
echo "-- [10] Docker-compose wires all three rebate flags with safe defaults"
check_contains "P3-DEP-36 docker-compose legacy provider default true" "$DC" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED.*:-true"
check_contains "P3-DEP-37 docker-compose remote-create-order default false" "$DC" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED.*:-false"
check_contains "P3-DEP-38 docker-compose remote-read default false" "$DC" \
  "REBATE_SERVICE_REMOTE_READ_ENABLED.*:-false"
check_contains "P3-DEP-39 market-service yml legacy provider default true" "$MKT_YML" \
  "REBATE_LEGACY_RPC_PROVIDER_ENABLED:true"
check_contains "P3-DEP-40 market-service yml remote-create-order default false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:false"
check_contains "P3-DEP-41 market-service yml remote-read default false" "$MKT_YML" \
  "REBATE_SERVICE_REMOTE_READ_ENABLED:false"

echo ""
echo "-- [11] Dangerous Phase 2/3 remote flags are NOT hardcoded true"
DFLAG_FAIL=0
while IFS= read -r f; do
  for pattern in \
    "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" \
    "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true" \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true" \
    "account\.service\.remote-quota-decrement\.enabled[[:space:]]*=[[:space:]]*true" \
    "rebate\.service\.remote-create-order\.enabled[[:space:]]*=[[:space:]]*true" \
    "rebate\.service\.remote-read\.enabled[[:space:]]*=[[:space:]]*true"; do
    if grep -qE "$pattern" "$f" 2>/dev/null; then
      echo "  [DANGER] $f : $pattern"
      DFLAG_FAIL=$((DFLAG_FAIL + 1))
    fi
  done
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$DFLAG_FAIL" -eq 0 ]; then
  pass "P3-DEP-42 no dangerous remote flag is hardcoded true"
else
  fail "P3-DEP-42 dangerous flag hardcoded true in $DFLAG_FAIL occurrence(s)"
fi

echo ""
echo "-- [12] Rebate mapper XMLs are limited to rebate-owned tables"
check_file "P3-DEP-43 daily_behavior_rebate_mapper.xml present in rebate-service" \
  "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/daily_behavior_rebate_mapper.xml"
check_file "P3-DEP-44 user_behavior_rebate_order_mapper.xml present in rebate-service" \
  "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/user_behavior_rebate_order_mapper.xml"
check_file "P3-DEP-45 task_mapper.xml present in rebate-service (shared outbox; kept explicitly)" \
  "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
FORBIDDEN_MAPPERS=0
for mapper in award activity strategy credit fulfillment raffle_activity_account raffle_activity_order; do
  if find "$ROOT/big-market-rebate-service/src/main/resources/mybatis" \
       -name "*${mapper}*mapper*" 2>/dev/null | grep -q .; then
    echo "  [WARN] forbidden mapper found for: $mapper"
    FORBIDDEN_MAPPERS=$((FORBIDDEN_MAPPERS + 1))
  fi
done
if [ "$FORBIDDEN_MAPPERS" -eq 0 ]; then
  pass "P3-DEP-46 no forbidden mapper XMLs in rebate-service"
else
  fail "P3-DEP-46 $FORBIDDEN_MAPPERS forbidden mapper(s) found in rebate-service"
fi

echo ""
echo "-- [13] docs/evidence/generated not tracked"
if git -C "$ROOT" ls-files "docs/evidence/generated/*" | grep -q .; then
  fail "P3-DEP-47 generated evidence files are tracked"
else
  pass "P3-DEP-47 generated evidence files are not tracked"
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
