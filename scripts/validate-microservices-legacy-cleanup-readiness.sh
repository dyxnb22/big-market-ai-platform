#!/usr/bin/env bash
# Repo-only validator for legacy cleanup readiness inventory.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INVENTORY="$REPO_ROOT/docs/microservices-legacy-cleanup-inventory.md"

PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

require_file() {
  local label="$1" file="$2"
  [[ -f "$file" ]] && pass "$label" || fail "$label missing: $file"
}

require_text() {
  local label="$1" file="$2" pattern="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    pass "$label"
  else
    fail "$label"
  fi
}

require_path() {
  local label="$1" rel="$2"
  [[ -e "$REPO_ROOT/$rel" ]] && pass "$label" || fail "$label missing: $rel"
}

assert_absent() {
  local label="$1" pattern="$2"
  shift 2
  local matches
  matches=$(grep -RInE "$pattern" "$@" 2>/dev/null | grep -v '/target/' || true)
  if [[ -z "$matches" ]]; then
    pass "$label"
  else
    fail "$label"
    printf '%s\n' "$matches" | sed 's#^#       #'
  fi
}

echo ""
echo "========================================================================"
echo "  Legacy Cleanup Readiness Validator"
echo "========================================================================"

require_file "Legacy cleanup inventory exists" "$INVENTORY"

if [[ -f "$INVENTORY" ]]; then
  echo ""
  echo "-- Inventory sections --"
  for section in \
    "Legacy RPC Providers" \
    "Default-Local Adapters" \
    "Local Fallback Ports" \
    "Shared Mapper Compatibility Copies" \
    "Generic Task and Outbox Fallbacks" \
    "Current Cleanup Eligibility"; do
    require_text "Section present: $section" "$INVENTORY" "^## $section"
  done

  echo ""
  echo "-- Known legacy providers listed --"
  for item in RebateServiceRPC RaffleStrategyServiceRPC RaffleActivityController ErpOperateController; do
    require_text "Inventory lists provider surface: $item" "$INVENTORY" "$item"
  done

  echo ""
  echo "-- Known local adapters listed --"
  for item in \
    LocalAccountReadAdapter \
    LocalAccountCreditWriteAdapter \
    LocalAccountQuotaWriteAdapter \
    LocalAwardDispatchAdapter \
    LocalRebateOrderAdapter \
    LocalRebateReadAdapter \
    LocalStrategyReadAdapter; do
    require_text "Inventory lists local adapter: $item" "$INVENTORY" "$item"
  done
  for rel in \
    big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountReadAdapter.java \
    big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountCreditWriteAdapter.java \
    big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountQuotaWriteAdapter.java \
    big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAwardDispatchAdapter.java \
    big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java \
    big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java \
    big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java; do
    require_path "Local adapter still present: $rel" "$rel"
  done

  echo ""
  echo "-- Known local fallback ports listed --"
  for item in \
    LocalActivityAccountPort \
    LocalStrategyActivityAccountPort \
    LocalStrategyActivityMappingPort \
    LocalStrategyDecisionPort \
    LocalAwardFulfillmentPort \
    LocalAwardCreditWritePort \
    LocalCreditAwardTaskDispatchPort \
    LocalAwardActivityOrderPort \
    LocalDrawOutboxPort; do
    require_text "Inventory lists local port: $item" "$INVENTORY" "$item"
    require_path "Local port still present: $item" "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/$item.java"
  done

  echo ""
  echo "-- Shared mapper copies listed or exempted --"
  for module in big-market-app big-market-market-service big-market-message-job-service big-market-account-service big-market-rebate-service big-market-strategy-service; do
    require_text "Inventory lists mapper compatibility for $module" "$INVENTORY" "$module/src/main/resources/mybatis/mapper/mysql"
  done
  require_text "Inventory explicitly describes fulfillment-service mapper ownership" "$INVENTORY" "big-market-fulfillment-service.*service-owned"

  for mapper in \
    strategy_rule_mapper.xml \
    rule_tree_node_line_mapper.xml \
    raffle_activity_order_mapper.xml \
    raffle_activity_sku_mapper.xml \
    award_mapper.xml \
    user_credit_account_mapper.xml \
    user_award_record_mapper.xml \
    daily_behavior_rebate_mapper.xml \
    task_mapper.xml \
    credit_award_task_mapper.xml; do
    require_text "Inventory lists or covers mapper: $mapper" "$INVENTORY" "$mapper|mapper/mysql/\\*\\.xml|compatibility set"
  done

  echo ""
  echo "-- Cleanup candidates have evidence gates --"
  for term in "External evidence required before disabling" "7-day stable gate" "30-day removal gate" "EXTERNAL-GATED"; do
    require_text "Inventory contains gate term: $term" "$INVENTORY" "$term"
  done

  if grep -qiE "removable now|cleanup eligible: yes|30 clean days: complete|7 clean days: complete" "$INVENTORY" 2>/dev/null; then
    fail "Inventory must not mark cleanup candidates removable"
  else
    pass "Inventory does not mark any cleanup candidate removable"
  fi
fi

echo ""
echo "-- Legacy provider defaults and production flag defaults --"
MARKET_YML="$REPO_ROOT/big-market-market-service/src/main/resources/application.yml"
COMPOSE_FILES=("$REPO_ROOT"/docker-compose*.yml "$REPO_ROOT"/docs/dev-ops/docker-compose*.yml)
require_text "Rebate legacy provider application default remains true" "$MARKET_YML" "REBATE_LEGACY_RPC_PROVIDER_ENABLED:true"
require_text "Strategy legacy provider application default remains true" "$MARKET_YML" "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:true"
if grep -RIn "REBATE_LEGACY_RPC_PROVIDER_ENABLED" "${COMPOSE_FILES[@]}" 2>/dev/null | grep -q ':-true'; then
  pass "Rebate legacy provider compose default remains true"
else
  fail "Rebate legacy provider compose default should remain true"
fi
if grep -RIn "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED" "${COMPOSE_FILES[@]}" 2>/dev/null | grep -q ':-true'; then
  pass "Strategy legacy provider compose default remains true"
else
  fail "Strategy legacy provider compose default should remain true"
fi

RESOURCE_DIRS=("$REPO_ROOT"/big-market-*/src/main/resources)
assert_absent \
  "No production/remote/outbox flag defaults true in service resources" \
  '(remote-[a-z-]+|[a-z-]+-outbox|cutover).*enabled:[[:space:]]*(true|\$\{[A-Z0-9_]+:true\})' \
  "${RESOURCE_DIRS[@]}"
assert_absent \
  "No env-backed REMOTE/OUTBOX/CUTOVER default true in service resources" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:true\}' \
  "${RESOURCE_DIRS[@]}"
assert_absent \
  "No docker compose REMOTE/OUTBOX/CUTOVER default true" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:-true\}' \
  "${COMPOSE_FILES[@]}"

echo ""
echo "-- Local fallback paths remain present --"
for rel in \
  big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountReadAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java; do
  require_path "Fallback path still present: $rel" "$rel"
done

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - legacy cleanup remains evidence-gated"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
