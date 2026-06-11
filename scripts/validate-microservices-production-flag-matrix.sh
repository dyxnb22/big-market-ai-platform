#!/usr/bin/env bash
# Repo-only production flag matrix validator.
#
# Ensures remote/outbox/cutover flags remain default false across service
# resources and docker compose files, and that the Phase 8 runbook documents
# enablement and rollback for each flag family.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

RUNBOOK="$REPO_ROOT/docs/microservices-phase-8-cutover-runbook.md"

echo ""
echo "========================================================================"
echo "  Microservices Production Flag Matrix Validator"
echo "========================================================================"

check_pattern_absent() {
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

check_runbook_terms() {
  local label="$1"
  shift
  local missing=0
  for term in "$@"; do
    if ! grep -q "$term" "$RUNBOOK" 2>/dev/null; then
      fail "$label missing runbook term: $term"
      missing=1
    fi
  done
  [[ "$missing" -eq 0 ]] && pass "$label runbook coverage"
}

echo ""
echo "── 1. Service resource defaults ──"
RESOURCE_DIRS=("$REPO_ROOT"/big-market-*/src/main/resources)
check_pattern_absent \
  "No service remote/outbox/cutover flag defaults true" \
  '(remote-[a-z-]+|[a-z-]+-outbox|cutover).*enabled:[[:space:]]*(true|\$\{[A-Z0-9_]+:true\})' \
  "${RESOURCE_DIRS[@]}"

check_pattern_absent \
  "No service env-backed REMOTE/OUTBOX/CUTOVER default true" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:true\}' \
  "${RESOURCE_DIRS[@]}"

echo ""
echo "── 2. Docker compose defaults ──"
COMPOSE_FILES=("$REPO_ROOT"/docker-compose*.yml "$REPO_ROOT"/docs/dev-ops/docker-compose*.yml)
check_pattern_absent \
  "No docker compose REMOTE/OUTBOX/CUTOVER default true" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:-true\}' \
  "${COMPOSE_FILES[@]}"

for env_name in \
  ACCOUNT_SERVICE_REMOTE_READ_ENABLED \
  ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED \
  ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED \
  ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED \
  ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED \
  ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED \
  REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED \
  REBATE_SERVICE_REMOTE_READ_ENABLED \
  STRATEGY_SERVICE_REMOTE_READ_ENABLED; do
  if grep -RIn "$env_name" "${COMPOSE_FILES[@]}" 2>/dev/null | grep -q ':-false'; then
    pass "$env_name defaults false in docker compose"
  else
    fail "$env_name missing docker compose default false"
  fi
done

echo ""
echo "── 3. Legacy provider flags remain compatibility defaults ──"
for env_name in REBATE_LEGACY_RPC_PROVIDER_ENABLED STRATEGY_LEGACY_RPC_PROVIDER_ENABLED; do
  if grep -RIn "$env_name" "$REPO_ROOT"/docker-compose*.yml 2>/dev/null | grep -q ':-true'; then
    pass "$env_name defaults true in docker compose until post-cutover cleanup"
  else
    fail "$env_name should default true until external cutover cleanup"
  fi
done

echo ""
echo "── 4. Phase 8 runbook flag enablement and rollback coverage ──"
if [[ -f "$RUNBOOK" ]]; then
  pass "Phase 8 runbook exists"
else
  fail "Phase 8 runbook missing"
fi

check_runbook_terms "account-service flags" \
  "account.remote-write.enabled=false" \
  "account.award-credit-outbox.enabled=false" \
  "quota decrement flags default false" \
  "set account write/outbox flags false"
check_runbook_terms "fulfillment-service flags" \
  "fulfillment.remote.enabled=false" \
  "account.award-credit-outbox.enabled=false" \
  "disable fulfillment remote and outbox flags"
check_runbook_terms "rebate-service flags" \
  "rebate.remote-create-order.enabled=false" \
  "rebate.service.remote-read.enabled=false" \
  "disable rebate remote flags"
check_runbook_terms "strategy-service flags" \
  "strategy.service.remote-read.enabled=false" \
  "strategy.service.remote-decision.enabled=false" \
  "disable strategy remote read flag"
check_runbook_terms "activity-service flags" \
  "activity.service.remote-draw.enabled=false" \
  "disable remote draw flag"

if grep -q 'EXTERNAL-GATED' "$RUNBOOK" 2>/dev/null && grep -qi 'Do not enable production, remote, or outbox flags by default' "$RUNBOOK" 2>/dev/null; then
  pass "Runbook keeps flag enablement external-gated"
else
  fail "Runbook missing external-gated no-default-enable rule"
fi

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - production flag matrix remains default-off"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
