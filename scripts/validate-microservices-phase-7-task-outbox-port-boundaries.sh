#!/usr/bin/env bash
# Repo-only validator for AL-8/AL-9/AL-10 direct repository -> ITaskDao cleanup.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

INFRA_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository"
INFRA_PORT="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port"
DOMAIN="$REPO_ROOT/big-market-domain/src/main/java/com/dyx/market/domain"

echo ""
echo "========================================================================"
echo "  Phase 7-C/D: Task Outbox Port Boundary Validator"
echo "========================================================================"

check_repo_boundary() {
  local al="$1" repo="$2" port="$3"
  local file="$INFRA_REPO/$repo.java"
  if grep -q "ITaskDao" "$file" 2>/dev/null; then
    fail "$al $repo still imports/uses ITaskDao"
  else
    pass "$al $repo no longer imports/uses ITaskDao"
  fi
  if grep -q "$port" "$file" 2>/dev/null; then
    pass "$al $repo uses $port"
  else
    fail "$al $repo does not use $port"
  fi
}

check_repo_boundary "AL-8" "BehaviorRebateRepository" "IRebateTaskOutboxPort"
check_repo_boundary "AL-9" "CreditRepository" "ICreditTradeTaskOutboxPort"
check_repo_boundary "AL-10" "AwardRepository" "IAwardDispatchTaskOutboxPort"

check_port_pair() {
  local label="$1" iface="$2" impl="$3"
  local iface_file
  iface_file=$(find "$DOMAIN" -name "$iface.java" ! -path '*/target/*' 2>/dev/null | head -1)
  local impl_file="$INFRA_PORT/$impl.java"
  [[ -f "$iface_file" ]] && pass "$label interface exists: $iface" || fail "$label interface missing: $iface"
  [[ -f "$impl_file" ]] && pass "$label local adapter exists: $impl" || fail "$label local adapter missing: $impl"
  if [[ -f "$impl_file" ]] && grep -q "ITaskDao" "$impl_file" && grep -q "taskDao.insert" "$impl_file" \
    && grep -q "updateTaskSendMessageCompleted" "$impl_file" && grep -q "updateTaskSendMessageFail" "$impl_file"; then
    pass "$label local adapter preserves legacy ITaskDao insert/completed/fail delegation"
  else
    fail "$label local adapter does not preserve legacy ITaskDao delegation"
  fi
}

check_port_pair "AL-8" "IRebateTaskOutboxPort" "LocalRebateTaskOutboxPort"
check_port_pair "AL-9" "ICreditTradeTaskOutboxPort" "LocalCreditTradeTaskOutboxPort"
check_port_pair "AL-10" "IAwardDispatchTaskOutboxPort" "LocalAwardDispatchTaskOutboxPort"

echo ""
echo "── Flag safety ──"
if grep -RInE "(remote|outbox|cutover).*enabled([[:space:]]*:|=)[[:space:]]*true" \
  "$REPO_ROOT"/big-market-*/src/main/resources --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null \
  | grep -v '^[^:]*:[[:space:]]*#' | grep -q .; then
  fail "A remote/outbox/cutover flag appears default true"
else
  pass "No production/default remote/outbox/cutover flag is enabled"
fi

echo ""
echo "── Coordinating validators/docs ──"
if bash "$REPO_ROOT/scripts/validate-microservices-phase-6-package-ownership-boundaries.sh" >/tmp/phase7_task_ports_phase6b.txt 2>&1; then
  pass "Phase 6-B package ownership validator passes with AL-8/AL-9/AL-10 resolved"
else
  fail "Phase 6-B package ownership validator failed"
  cat /tmp/phase7_task_ports_phase6b.txt
fi

DAO_DOC="$REPO_ROOT/docs/microservices-dao-ownership.md"
if grep -q "direct DAO coupling resolved" "$DAO_DOC" && grep -q "physical runtime table isolation remains Phase 8 external-gated" "$DAO_DOC"; then
  pass "DAO ownership doc distinguishes direct DAO cleanup from physical table cutover"
else
  fail "DAO ownership doc must distinguish direct DAO cleanup from external-gated physical cutover"
fi

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - AL-8/AL-9/AL-10 direct repository DAO couplings resolved"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
