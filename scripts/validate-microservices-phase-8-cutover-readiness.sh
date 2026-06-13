#!/usr/bin/env bash
# Repo-only validator for Phase 8 cutover readiness pack.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

RUNBOOK="$REPO_ROOT/docs/microservices-phase-8.md"
EVIDENCE="$REPO_ROOT/docs/evidence/phase-8-evidence-pack.md"
MASTER="$REPO_ROOT/docs/archive/microservices-history.md"

echo ""
echo "========================================================================"
echo "  Phase 8: Cutover Readiness Validator"
echo "========================================================================"

[[ -f "$RUNBOOK" ]] && pass "Phase 8 runbook exists" || fail "Phase 8 runbook missing"
[[ -f "$EVIDENCE" ]] && pass "Phase 8 evidence template exists" || fail "Phase 8 evidence template missing"

for service in account-service fulfillment-service rebate-service strategy-service; do
  if grep -q "$service" "$RUNBOOK" 2>/dev/null; then
    pass "Runbook covers $service"
  else
    fail "Runbook missing $service"
  fi
done

for ddl in proposed-credit-award-task-outbox.sql proposed-quota-decrement-ledger.sql proposed-rebate-task-outbox.sql proposed-credit-trade-task-outbox.sql proposed-award-dispatch-task-outbox.sql; do
  if grep -q "$ddl" "$RUNBOOK" 2>/dev/null; then
    pass "Runbook links $ddl"
  else
    fail "Runbook missing DDL link: $ddl"
  fi
done

for term in prerequisites "staging validation" "production canary" rollback "acceptance criteria" "7-day" "30-day" EXTERNAL-GATED; do
  if grep -iq "$term" "$RUNBOOK" 2>/dev/null; then
    pass "Runbook includes $term"
  else
    fail "Runbook missing $term"
  fi
done

if grep -RInE "(remote|outbox|cutover).*enabled([[:space:]]*:|=)[[:space:]]*true" \
  "$REPO_ROOT"/big-market-*/src/main/resources --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null \
  | grep -v '^[^:]*:[[:space:]]*#' | grep -q .; then
  fail "A production/remote/outbox flag appears default true"
else
  pass "No default production/remote/outbox flag is true"
fi

if grep -qiE "external.*complete|approved.*yes|production cutover complete" "$EVIDENCE" 2>/dev/null; then
  fail "Evidence template appears to pre-mark external approval/cutover complete"
else
  pass "Evidence template does not falsely mark external evidence complete"
fi

if grep -q "Phase 8.*repo readiness complete / external cutover gated" "$MASTER" 2>/dev/null; then
  pass "Master plan status is honest for Phase 8"
else
  fail "Master plan missing honest Phase 8 repo-readiness/external-gated status"
fi

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - Phase 8 repo readiness pack is complete"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
