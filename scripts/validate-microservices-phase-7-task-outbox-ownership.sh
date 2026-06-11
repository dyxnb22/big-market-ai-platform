#!/usr/bin/env bash
# validate-microservices-phase-7-task-outbox-ownership.sh
#
# Phase 7-B validator: generic task/outbox ownership decision and guardrails.
#
# This is a repo-only static check. It must not require DB, MQ, Redis, Docker,
# network, or external services.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0
BASE_REF="${PHASE7B_BASE_REF:-phase-7-strategy-activity-mapping-port}"

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

echo ""
echo "========================================================================"
echo "  Phase 7-B: Generic Task Outbox Ownership Validator"
echo "  Repo: $REPO_ROOT"
echo "  Baseline ref: $BASE_REF"
echo "========================================================================"

DOC="$REPO_ROOT/docs/microservices-split-phase-7-task-outbox-ownership.md"
DAO_DOC="$REPO_ROOT/docs/microservices-dao-ownership.md"
MASTER="$REPO_ROOT/docs/microservices-decomposition-master-plan.md"
PHASE6B="$REPO_ROOT/scripts/validate-microservices-phase-6-package-ownership-boundaries.sh"
PHASE7_ACCOUNT="$REPO_ROOT/scripts/validate-microservices-phase-7-account-boundary-prep.sh"
PHASE7_MAPPING="$REPO_ROOT/scripts/validate-microservices-phase-7-strategy-activity-mapping-boundary.sh"

# ── 1. Design doc exists and captures the decision ───────────────────────────
echo ""
echo "── 1. Design doc integrity ──"

if [[ -f "$DOC" ]]; then
  pass "Design doc exists: docs/microservices-split-phase-7-task-outbox-ownership.md"
else
  fail "Design doc missing: docs/microservices-split-phase-7-task-outbox-ownership.md"
fi

check_doc_keyword() {
  local label="$1" pattern="$2"
  if grep -qE "$pattern" "$DOC" 2>/dev/null; then
    pass "Design doc covers: $label"
  else
    fail "Design doc missing: $label"
  fi
}

check_doc_keyword "AL-8 BehaviorRebateRepository -> ITaskDao" "AL-8.*BehaviorRebateRepository.*ITaskDao"
check_doc_keyword "AL-9 CreditRepository -> ITaskDao" "AL-9.*CreditRepository.*ITaskDao"
check_doc_keyword "AL-10 AwardRepository -> ITaskDao" "AL-10.*AwardRepository.*ITaskDao"
check_doc_keyword "per-domain outbox decision" "per-domain.*outbox|per-domain.*task"
check_doc_keyword "shared task table blocks isolation" "shared.*task.*blocks|blocks.*isolation"
check_doc_keyword "rebate future table" "rebate_task_outbox"
check_doc_keyword "credit future table" "credit_trade_task_outbox"
check_doc_keyword "award future table" "award_dispatch_task_outbox"
check_doc_keyword "credit_award_task precedent" "credit_award_task"
check_doc_keyword "migration order" "Migration Order"
check_doc_keyword "compatibility strategy" "Compatibility Strategy"
check_doc_keyword "rollback strategy" "Rollback Strategy"
check_doc_keyword "validation gates" "Validation Gates"
check_doc_keyword "decision complete not runtime resolved" "decision-complete but not runtime-resolved|decision complete.*not runtime"

# ── 2. DAO ownership and master plan updated ─────────────────────────────────
echo ""
echo "── 2. Coordinating docs updated ──"

if grep -qE "AL-8.*decision complete|BehaviorRebateRepository.*decision complete" "$DAO_DOC" 2>/dev/null \
  && grep -qE "AL-9.*decision complete|CreditRepository.*decision complete" "$DAO_DOC" 2>/dev/null \
  && grep -qE "AL-10.*decision complete|AwardRepository.*decision complete" "$DAO_DOC" 2>/dev/null; then
  pass "DAO ownership doc marks AL-8/AL-9/AL-10 decision complete"
else
  fail "DAO ownership doc does not mark AL-8/AL-9/AL-10 decision complete"
fi

if grep -q "docs/microservices-split-phase-7-task-outbox-ownership.md" "$DAO_DOC" 2>/dev/null; then
  pass "DAO ownership doc links the Phase 7-B decision doc"
else
  fail "DAO ownership doc does not link the Phase 7-B decision doc"
fi

if grep -qE "7-B.*Done|7-B.*complete|Phase 7-B.*complete" "$MASTER" 2>/dev/null; then
  pass "Master plan marks Phase 7-B complete"
else
  fail "Master plan does not mark Phase 7-B complete"
fi

if grep -qE "AL-5|Phase 7-C" "$MASTER" 2>/dev/null; then
  pass "Master plan recommends a next batch"
else
  fail "Master plan does not recommend AL-5 or Phase 7-C as a next batch"
fi

# ── 3. Existing ITaskDao runtime couplings remain allowlisted ────────────────
echo ""
echo "── 3. Runtime coupling status unchanged ──"

check_source_reference() {
  local label="$1" file="$2" pattern="$3"
  if [[ ! -f "$file" ]]; then
    fail "$label — file missing: $file"
    return
  fi
  if grep -q "$pattern" "$file" 2>/dev/null; then
    pass "$label still present and allowlisted"
  else
    fail "$label missing unexpectedly — this batch should not change runtime task behavior"
  fi
}

INFRA_REPO="$REPO_ROOT/big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository"
check_source_reference "AL-8 BehaviorRebateRepository -> ITaskDao" "$INFRA_REPO/BehaviorRebateRepository.java" "ITaskDao"
check_source_reference "AL-9 CreditRepository -> ITaskDao" "$INFRA_REPO/CreditRepository.java" "ITaskDao"
check_source_reference "AL-10 AwardRepository -> ITaskDao" "$INFRA_REPO/AwardRepository.java" "ITaskDao"

# ── 4. No DDL applied outside proposed docs ──────────────────────────────────
echo ""
echo "── 4. No applied DDL or non-proposed SQL changes ──"

if git -C "$REPO_ROOT" rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  changed_files=$(git -C "$REPO_ROOT" diff --name-only "$BASE_REF"...HEAD 2>/dev/null)
  changed_files="${changed_files}"$'\n'"$(git -C "$REPO_ROOT" diff --name-only 2>/dev/null)"
  changed_files=$(printf '%s\n' "$changed_files" | sed '/^$/d' | sort -u)

  non_proposed_sql=$(printf '%s\n' "$changed_files" | grep -E '\.sql$' | grep -vE '^docs/sql/proposed-[^/]+\.sql$' || true)
  if [[ -z "$non_proposed_sql" ]]; then
    pass "No SQL files changed outside docs/sql/proposed-*.sql"
  else
    fail "SQL changed outside docs/sql/proposed-*.sql:"
    printf '%s\n' "$non_proposed_sql"
  fi

  mapper_changes=$(printf '%s\n' "$changed_files" | grep -E 'mapper.*\.xml$|_mapper\.xml$' || true)
  if [[ -z "$mapper_changes" ]]; then
    pass "No mapper XML files changed or moved since $BASE_REF"
  else
    fail "Mapper XML files changed or moved since $BASE_REF:"
    printf '%s\n' "$mapper_changes"
  fi
else
  fail "Baseline ref not found: $BASE_REF"
fi

non_proposed_ddl=$(grep -RInE '\b(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE)\b' \
  "$REPO_ROOT/docs" "$REPO_ROOT/scripts" \
  --include='*.sql' --include='*.md' --include='*.sh' 2>/dev/null \
  | grep -v '/docs/sql/proposed-' \
  | grep -v 'microservices-split-phase-5-activity-draw-saga-outbox.md' \
  | grep -v 'validate-microservices-phase-7-task-outbox-ownership.sh' \
  || true)

if [[ -z "$non_proposed_ddl" ]]; then
  pass "No new applied-DDL-looking statements found outside proposed docs"
else
  echo "[INFO] Existing non-proposed DDL-looking documentation references found; verifying Phase 7-B changed files only."
  phase7b_non_proposed_ddl=$(printf '%s\n' "$changed_files" \
    | grep -vE '^docs/sql/proposed-[^/]+\.sql$' \
    | while read -r file; do
        [[ -f "$REPO_ROOT/$file" ]] || continue
        grep -HnE '\b(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE)\b' "$REPO_ROOT/$file" 2>/dev/null || true
      done \
    | grep -v 'validate-microservices-phase-7-task-outbox-ownership.sh' \
    || true)
  if [[ -z "$phase7b_non_proposed_ddl" ]]; then
    pass "Phase 7-B changed files do not add applied-DDL statements"
  else
    fail "Phase 7-B changed files contain non-proposed DDL-looking statements:"
    printf '%s\n' "$phase7b_non_proposed_ddl"
  fi
fi

# ── 5. Remote / production flags remain disabled ─────────────────────────────
echo ""
echo "── 5. Remote / production flag defaults ──"

REMOTE_FLAGS=(
  "account.remote-read.enabled"
  "account.remote-write.enabled"
  "account.award-credit-outbox.enabled"
  "account.service.remote-quota-decrement.enabled"
  "rebate.remote-create-order.enabled"
  "rebate.service.remote-read.enabled"
  "strategy.service.remote-read.enabled"
  "strategy.service.remote-decision.enabled"
  "fulfillment.remote.enabled"
  "activity.service.remote-draw.enabled"
  "activity.service.remote-strategy-mapping.enabled"
  "award.service.remote-fulfillment.enabled"
)

RESOURCE_DIRS=(
  "$REPO_ROOT/big-market-account-service/src/main/resources"
  "$REPO_ROOT/big-market-market-service/src/main/resources"
  "$REPO_ROOT/big-market-message-job-service/src/main/resources"
  "$REPO_ROOT/big-market-rebate-service/src/main/resources"
  "$REPO_ROOT/big-market-strategy-service/src/main/resources"
  "$REPO_ROOT/big-market-activity-service/src/main/resources"
  "$REPO_ROOT/big-market-fulfillment-service/src/main/resources"
)

for flag in "${REMOTE_FLAGS[@]}"; do
  found=0
  for dir in "${RESOURCE_DIRS[@]}"; do
    cnt=$(grep -RInE "${flag}([[:space:]]*:|=)[[:space:]]*true" "$dir" \
      --include='*.yml' --include='*.yaml' --include='*.properties' \
      2>/dev/null | grep -cv '^[^:]*:[[:space:]]*#' || true)
    found=$((found + cnt))
  done
  dc_cnt=$(grep -nE "${flag}.*true" "$REPO_ROOT/docker-compose.yml" 2>/dev/null \
    | grep -cv '^[[:space:]]*#' || true)
  found=$((found + dc_cnt))

  if [[ "$found" -eq 0 ]]; then
    pass "Flag default safe: $flag"
  else
    fail "Flag appears enabled: $flag ($found match(es))"
  fi
done

# ── 6. Phase 7-A and Phase 6-B validators still pass ────────────────────────
echo ""
echo "── 6. Prior boundary validators ──"

run_child_validator() {
  local label="$1" script="$2" output="$3"
  if [[ ! -f "$script" ]]; then
    fail "$label missing: $script"
    return
  fi
  if bash "$script" > "$output" 2>&1; then
    pass "$label passed"
  else
    fail "$label FAILED — output follows"
    cat "$output"
  fi
}

run_child_validator "Phase 7-A strategy-activity mapping validator" "$PHASE7_MAPPING" "/tmp/phase7b_mapping_output.txt"
run_child_validator "Phase 7-A account boundary prep validator" "$PHASE7_ACCOUNT" "/tmp/phase7b_account_output.txt"
run_child_validator "Phase 6-B package ownership validator" "$PHASE6B" "/tmp/phase7b_phase6b_output.txt"

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED — Phase 7-B task outbox ownership decision complete"
  echo "        AL-8/AL-9/AL-10 are decision-complete but remain runtime allowlisted."
  echo "        Chosen path: per-domain outbox/task tables, following credit_award_task."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi
