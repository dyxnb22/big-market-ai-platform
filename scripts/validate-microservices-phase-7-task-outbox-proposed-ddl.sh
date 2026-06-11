#!/usr/bin/env bash
# Repo-only validator for Phase 7-C proposed per-domain task outbox DDL.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

echo ""
echo "========================================================================"
echo "  Phase 7-C: Per-Domain Task Outbox Proposed DDL Validator"
echo "========================================================================"

TABLES=("rebate_task_outbox" "credit_trade_task_outbox" "award_dispatch_task_outbox")
FILES=("docs/sql/proposed-rebate-task-outbox.sql" "docs/sql/proposed-credit-trade-task-outbox.sql" "docs/sql/proposed-award-dispatch-task-outbox.sql")

for i in "${!TABLES[@]}"; do
  table="${TABLES[$i]}"
  rel="${FILES[$i]}"
  file="$REPO_ROOT/$rel"
  if [[ -f "$file" ]]; then
    pass "Proposed SQL exists: $rel"
  else
    fail "Missing proposed SQL: $rel"
    continue
  fi

  for shard in 000 001 002 003; do
    if grep -q "${table}_${shard}" "$file"; then
      pass "$rel contains ${table}_${shard}"
    else
      fail "$rel missing ${table}_${shard}"
    fi
  done

  for pattern in "PROPOSED ONLY" "DO NOT run" "UNIQUE KEY.*message_id" "state" "retry_count" "topic" "message" "create_time" "update_time"; do
    if grep -qE "$pattern" "$file"; then
      pass "$rel contains required pattern: $pattern"
    else
      fail "$rel missing required pattern: $pattern"
    fi
  done
done

echo ""
echo "── Non-proposed SQL guard ──"
for table in rebate_task_outbox credit_trade_task_outbox award_dispatch_task_outbox; do
  hits=$(grep -RIn "$table" "$REPO_ROOT" \
    --include='*.sql' --include='*.xml' 2>/dev/null \
    | grep -v "/docs/sql/proposed-" || true)
  if [[ -z "$hits" ]]; then
    pass "$table appears only in proposed SQL/XML paths"
  else
    fail "$table appears outside proposed SQL:"
    printf '%s\n' "$hits"
  fi
done

echo ""
echo "── Documentation references ──"
for table in rebate_task_outbox credit_trade_task_outbox award_dispatch_task_outbox; do
  if grep -RIn "$table" "$REPO_ROOT/docs" --include='*.md' 2>/dev/null | grep -q .; then
    pass "Docs reference $table"
  else
    fail "Docs do not reference $table"
  fi
done

echo ""
echo "── Phase 7-B ownership validator ──"
if bash "$REPO_ROOT/scripts/validate-microservices-phase-7-task-outbox-ownership.sh" >/tmp/phase7c_task_outbox_ownership.txt 2>&1; then
  pass "Phase 7-B task outbox ownership validator passes"
else
  fail "Phase 7-B task outbox ownership validator failed"
  cat /tmp/phase7c_task_outbox_ownership.txt
fi

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - Phase 7-C proposed DDL is repo-ready"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
