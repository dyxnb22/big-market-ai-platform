#!/usr/bin/env bash
# Repo-only post-cutover cleanup gate validator.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INVENTORY="$REPO_ROOT/docs/microservices-legacy-cleanup-inventory.md"
INTAKE="$REPO_ROOT/docs/microservices-phase-8-external-evidence-intake.md"

PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

require_file() {
  local label="$1" file="$2"
  [[ -f "$file" ]] && pass "$label" || fail "$label missing: $file"
}

require_path() {
  local label="$1" rel="$2"
  [[ -e "$REPO_ROOT/$rel" ]] && pass "$label" || fail "$label missing: $rel"
}

require_text() {
  local label="$1" file="$2" pattern="$3"
  if grep -qE "$pattern" "$file" 2>/dev/null; then
    pass "$label"
  else
    fail "$label"
  fi
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
echo "  Post-Cutover Cleanup Gates Validator"
echo "========================================================================"

require_file "Legacy cleanup inventory exists" "$INVENTORY"
require_file "External evidence intake exists" "$INTAKE"

echo ""
echo "-- No cleanup completion markers without evidence --"
if [[ -f "$INVENTORY" ]] && grep -qiE "removable now|cleanup eligible: yes|removed after 30-day gate" "$INVENTORY" 2>/dev/null; then
  fail "Inventory marks cleanup complete/removable without external evidence"
else
  pass "Inventory keeps cleanup candidates not removable"
fi
if [[ -f "$INTAKE" ]] && grep -qiE "production cutover complete|external cutover complete|evidence status: complete|7-day stable evidence: complete|30-day removal evidence: complete" "$INTAKE" 2>/dev/null; then
  fail "Evidence intake marks external cutover or cleanup complete"
else
  pass "Evidence intake keeps missing evidence external-gated"
fi

echo ""
echo "-- Legacy provider defaults remain compatible --"
MARKET_YML="$REPO_ROOT/big-market-market-service/src/main/resources/application.yml"
require_text "Rebate legacy provider default true in application.yml" "$MARKET_YML" "REBATE_LEGACY_RPC_PROVIDER_ENABLED:true"
require_text "Strategy legacy provider default true in application.yml" "$MARKET_YML" "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:true"
require_text "Legacy rebate provider has matchIfMissing true" "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java" "matchIfMissing = true"
require_text "Legacy strategy provider has matchIfMissing true" "$REPO_ROOT/big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java" "matchIfMissing = true"

echo ""
echo "-- Default-local adapters remain present --"
for rel in \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountReadAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountCreditWriteAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountQuotaWriteAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAwardDispatchAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java; do
  require_path "Default-local adapter present: $rel" "$rel"
done

echo ""
echo "-- Mapper XML compatibility copies remain present --"
for module in big-market-app big-market-market-service big-market-message-job-service big-market-account-service; do
  require_path "$module mapper directory present" "$module/src/main/resources/mybatis/mapper/mysql"
  for mapper in \
    strategy_mapper.xml \
    strategy_rule_mapper.xml \
    raffle_activity_order_mapper.xml \
    award_mapper.xml \
    user_credit_account_mapper.xml \
    user_credit_order_mapper.xml \
    task_mapper.xml \
    user_behavior_rebate_order_mapper.xml; do
    require_path "$module mapper present: $mapper" "$module/src/main/resources/mybatis/mapper/mysql/$mapper"
  done
done
require_path "rebate-service task fallback mapper present" "big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml"
require_path "strategy-service strategy mapper present" "big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/strategy_mapper.xml"

if [[ -f "$INVENTORY" ]]; then
  require_text "Inventory documents mapper removal is 30-day gated" "$INVENTORY" "30-day removal gate"
fi

echo ""
echo "-- Shared task fallback adapters remain present --"
for rel in \
  big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalRebateTaskOutboxPort.java \
  big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalCreditTradeTaskOutboxPort.java \
  big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalAwardDispatchTaskOutboxPort.java \
  big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java; do
  require_path "Shared task fallback present: $rel" "$rel"
done

echo ""
echo "-- Production/remote/outbox flags remain default-off --"
RESOURCE_DIRS=("$REPO_ROOT"/big-market-*/src/main/resources)
COMPOSE_FILES=("$REPO_ROOT"/docker-compose*.yml "$REPO_ROOT"/docs/dev-ops/docker-compose*.yml)
assert_absent \
  "No service remote/outbox/cutover flag defaults true" \
  '(remote-[a-z-]+|[a-z-]+-outbox|cutover).*enabled:[[:space:]]*(true|\$\{[A-Z0-9_]+:true\})' \
  "${RESOURCE_DIRS[@]}"
assert_absent \
  "No env-backed REMOTE/OUTBOX/CUTOVER default true" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:true\}' \
  "${RESOURCE_DIRS[@]}"
assert_absent \
  "No docker compose REMOTE/OUTBOX/CUTOVER default true" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:-true\}' \
  "${COMPOSE_FILES[@]}"

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - post-cutover cleanup remains gated by external evidence"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
