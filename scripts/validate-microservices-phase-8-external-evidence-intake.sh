#!/usr/bin/env bash
# Repo-only validator for Phase 8 external evidence intake.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INTAKE="$REPO_ROOT/docs/microservices-phase-8.md"
RUNBOOK="$REPO_ROOT/docs/microservices-phase-8.md"
COMPLETION="$REPO_ROOT/docs/archive/microservices-history.md"

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

echo ""
echo "========================================================================"
echo "  Phase 8 External Evidence Intake Validator"
echo "========================================================================"

require_file "External evidence intake doc exists" "$INTAKE"
require_file "Phase 8 runbook exists" "$RUNBOOK"
require_file "Completion index exists" "$COMPLETION"

if [[ -f "$INTAKE" ]]; then
  echo ""
  echo "-- Required team sections --"
  for section in "DBA Gates" "Ops Gates" "Engineering Gates" "Oncall Gates" "Product Gates"; do
    require_text "Section present: $section" "$INTAKE" "^## $section"
  done

  echo ""
  echo "-- Service cutover placeholders --"
  for service in account-service fulfillment-service rebate-service strategy-service activity-service; do
    require_text "$service cutover placeholder exists" "$INTAKE" "^### $service cutover evidence"
    require_text "$service placeholder is EXTERNAL-GATED" "$INTAKE" "$service cutover evidence|EXTERNAL-GATED"
  done

  echo ""
  echo "-- External-gated posture --"
  for team in DBA Ops Engineering Oncall Product; do
    require_text "$team evidence placeholders marked EXTERNAL-GATED" "$INTAKE" "$team .*EXTERNAL-GATED|$team evidence.*EXTERNAL-GATED|Owning team.*$team"
  done

  for ddl in \
    proposed-credit-award-task-outbox.sql \
    proposed-quota-decrement-ledger.sql \
    proposed-rebate-task-outbox.sql \
    proposed-credit-trade-task-outbox.sql \
    proposed-award-dispatch-task-outbox.sql; do
    require_text "DDL mapped: $ddl" "$INTAKE" "$ddl"
  done

  for flag in \
    ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED \
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED \
    REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED \
    REBATE_SERVICE_REMOTE_READ_ENABLED \
    STRATEGY_SERVICE_REMOTE_READ_ENABLED \
    STRATEGY_LEGACY_RPC_PROVIDER_ENABLED \
    REBATE_LEGACY_RPC_PROVIDER_ENABLED; do
    require_text "Flag mapped: $flag" "$INTAKE" "$flag"
  done

  if grep -qiE "(evidence status|DBA evidence|Ops evidence|Engineering evidence|Oncall evidence|Product evidence).*(complete|approved|done|passed)" "$INTAKE" 2>/dev/null; then
    fail "Evidence placeholders must not be falsely marked complete"
  else
    pass "No evidence placeholder is marked complete"
  fi

  if grep -qiE "production cutover (is )?complete|external cutover (is )?complete|cleanup eligible: yes" "$INTAKE" 2>/dev/null; then
    fail "Intake doc appears to claim external cutover/cleanup completion"
  else
    pass "Intake doc does not claim external cutover completion"
  fi
fi

echo ""
echo "-- Cross-links --"
require_text "Runbook links to intake doc" "$RUNBOOK" "docs/microservices-phase-8\\.md"
require_text "Completion index links to intake doc" "$COMPLETION" "docs/microservices-phase-8\\.md"

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - Phase 8 external evidence intake is repo-ready and external-gated"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
