#!/usr/bin/env bash
# Repo-only validator for Phase 7-E/F DB user and schema isolation plans.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

USER_PLAN="$REPO_ROOT/docs/archive/phases.md"
SCHEMA_PLAN="$REPO_ROOT/docs/archive/phases.md"

echo ""
echo "========================================================================"
echo "  Phase 7-E/F: DB Isolation Plan Validator"
echo "========================================================================"

[[ -f "$USER_PLAN" ]] && pass "DB users/grants plan exists" || fail "Missing DB users/grants plan"
[[ -f "$SCHEMA_PLAN" ]] && pass "Sharded schema isolation plan exists" || fail "Missing sharded schema isolation plan"

for ctx in account activity fulfillment rebate strategy message-job market app legacy compatibility; do
  if grep -iq "$ctx" "$USER_PLAN" "$SCHEMA_PLAN" 2>/dev/null; then
    pass "Isolation plans cover: $ctx"
  else
    fail "Isolation plans do not cover: $ctx"
  fi
done

for table in rebate_task_outbox credit_trade_task_outbox award_dispatch_task_outbox; do
  if grep -iq "$table" "$USER_PLAN" "$SCHEMA_PLAN" 2>/dev/null; then
    pass "Shared task replacement reflected: $table"
  else
    fail "Shared task replacement missing: $table"
  fi
done

if grep -RInE '\b(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE|USER)|\bGRANT\b' \
  "$REPO_ROOT/docs" "$REPO_ROOT/scripts" \
  --include='*.sql' --include='*.md' --include='*.sh' 2>/dev/null \
  | grep -v '/docs/sql/proposed-' \
  | grep -v 'validate-microservices-phase-7-db-isolation-plan.sh' \
  | grep -v 'proposed-only' \
  | grep -q .; then
  echo "[INFO] Existing non-proposed DDL/grant-looking docs are present; checking new Phase 7 isolation plans only."
fi

if grep -RInE '^[[:space:]]*(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE|USER)|^[[:space:]]*GRANT\b' \
  "$USER_PLAN" "$SCHEMA_PLAN" 2>/dev/null | grep -q .; then
  fail "Isolation plans contain executable DDL/GRANT statements; keep them proposed/runbook-only"
else
  pass "Isolation plans do not contain executable DDL/GRANT statements"
fi

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - Phase 7 DB isolation plans are repo-ready"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
