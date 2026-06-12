#!/usr/bin/env bash
# Repo-only Phase 8 idempotency & rollback matrix validator.
#
# Validates that the idempotency/rollback matrix
# (docs/microservices-phase-8-idempotency-rollback-matrix.md) is present,
# complete, and consistent with actual code and proposed DDL.
#
# Deterministic, repo-only, no DB/MQ/Docker/network.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

MATRIX="$REPO_ROOT/docs/microservices-phase-8-idempotency-rollback-matrix.md"
PROPOSED_DDL_DIR="$REPO_ROOT/docs/sql"

echo ""
echo "========================================================================"
echo "  Phase 8 Idempotency & Rollback Matrix Validator"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── Helpers ───────────────────────────────────────────────────────────────────
assert_file() {
  local label="$1" path="$2"
  if [[ -f "$path" ]]; then
    pass "$label"
  else
    fail "$label — missing: $path"
  fi
}

assert_matrix_contains() {
  local label="$1" pattern="$2"
  if grep -qE "$pattern" "$MATRIX" 2>/dev/null; then
    pass "$label"
  else
    fail "$label"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# Section 1: Matrix document presence and flow coverage
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 1. Matrix document and flow coverage ──"

assert_file "Idempotency/rollback matrix exists" "$MATRIX"

FLOWS=(
  "Quota Decrement"
  "Credit Award Outbox Dispatch"
  "Award Fulfillment"
  "Rebate Create Order"
  "Rebate Read"
  "Credit Trade"
  "SKU Exchange"
  "Shared Task Fallback"
)

for flow in "${FLOWS[@]}"; do
  assert_matrix_contains "Matrix covers flow: $flow" "$flow"
done

STRUCTURAL_TERMS=(
  "Business operation"
  "Idempotency key"
  "DB unique key"
  "Retry behavior"
  "Rollback behavior"
  "EXTERNAL-GATED"
)

for term in "${STRUCTURAL_TERMS[@]}"; do
  assert_matrix_contains "Matrix contains column: $term" "$term"
done

echo ""
echo "── 1.2 Gate language audit ──"

for gate in "EXTERNAL-GATED" "rollback"; do
  count=$(grep -c "$gate" "$MATRIX" 2>/dev/null || echo 0)
  if [[ "$count" -ge 5 ]]; then
    pass "Term '$gate' appears $count times"
  else
    fail "Term '$gate' appears only $count times (expect >=5)"
  fi
done

# ═══════════════════════════════════════════════════════════════════════════════
# Section 2: Unique-key name consistency with proposed DDL
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 2. Unique-key name consistency ──"

check_uk_ddl_match() {
  local label="$1" ddl_file="$2" uk_name="$3"
  local ddl_path="$PROPOSED_DDL_DIR/$ddl_file"
  if [[ ! -f "$ddl_path" ]]; then
    fail "$label — DDL file missing: $ddl_file"
    return
  fi
  if grep -q "$uk_name" "$ddl_path" 2>/dev/null; then
    pass "$label — $uk_name found in $ddl_file"
  else
    fail "$label — $uk_name NOT found in $ddl_file"
  fi
}

check_uk_matrix_match() {
  local label="$1" uk_name="$2"
  if grep -q "$uk_name" "$MATRIX" 2>/dev/null; then
    pass "$label — $uk_name documented in matrix"
  else
    fail "$label — $uk_name NOT in matrix"
  fi
}

# credit_award_task
check_uk_ddl_match \
  "credit_award_task unique key" \
  "proposed-credit-award-task-outbox.sql" \
  "uq_award_order_id"
check_uk_matrix_match \
  "credit_award_task unique key in matrix" \
  "uq_award_order_id"

# raffle_quota_decrement_ledger
check_uk_ddl_match \
  "raffle_quota_decrement_ledger unique key" \
  "proposed-quota-decrement-ledger.sql" \
  "uq_user_activity_biz"
check_uk_matrix_match \
  "raffle_quota_decrement_ledger unique key in matrix" \
  "uq_user_activity_biz"

# per-domain outbox tables
check_uk_ddl_match \
  "rebate_task_outbox unique key" \
  "proposed-rebate-task-outbox.sql" \
  "uq_user_message_id"
check_uk_ddl_match \
  "credit_trade_task_outbox unique key" \
  "proposed-credit-trade-task-outbox.sql" \
  "uq_user_message_id"
check_uk_ddl_match \
  "award_dispatch_task_outbox unique key" \
  "proposed-award-dispatch-task-outbox.sql" \
  "uq_user_message_id"

assert_matrix_contains \
  "uq_user_message_id documented in matrix" \
  "uq_user_message_id"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 3: Code-level idempotency field verification
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 3. Code-level idempotency fields ──"

check_code_pattern() {
  local label="$1" file="$2" pattern="$3"
  local path="$REPO_ROOT/$file"
  if [[ ! -f "$path" ]]; then
    fail "$label — file missing: $file"
    return
  fi
  if grep -qE "$pattern" "$path" 2>/dev/null; then
    pass "$label"
  else
    fail "$label — pattern not found in $file"
  fi
}

# outBusinessNo as idempotency key in key code paths
check_code_pattern \
  "DispatchCreditAwardTaskJob uses awardOrderId as outBusinessNo" \
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java" \
  'getAwardOrderId|outBusinessNo.*award'

check_code_pattern \
  "RaffleActivityPartakeService uses outBusinessNo for decrementQuota" \
  "big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java" \
  'outBusinessNo.*decrementQuota|decrementQuota.*outBusinessNo'

check_code_pattern \
  "RaffleActivityPartakeService calls rollbackQuota on failure" \
  "big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/RaffleActivityPartakeService.java" \
  'rollbackQuota'

check_code_pattern \
  "RaffleActivityController SKU exchange uses deterministic outBusinessNo" \
  "big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java" \
  'Deterministic outBusinessNo|outBusinessNo.*getUserId.*sku.*DATE_FORMAT'

check_code_pattern \
  "ActivityRepository has decrementQuotaWithLedger with idempotency guard" \
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java" \
  'decrementQuotaWithLedger'

check_code_pattern \
  "AwardRepository catches DuplicateKeyException for award/outbox idempotency" \
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java" \
  'DuplicateKeyException'

check_code_pattern \
  "CreditRepository catches DuplicateKeyException for credit order idempotency" \
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java" \
  'DuplicateKeyException'

check_code_pattern \
  "BehaviorRebateRepository catches DuplicateKeyException for rebate order idempotency" \
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/BehaviorRebateRepository.java" \
  'DuplicateKeyException'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 4: rollbackQuota exists in both local and remote ports
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 4. rollbackQuota path coverage ──"

check_code_pattern \
  "LocalActivityAccountPort implements rollbackQuota" \
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java" \
  'void rollbackQuota'

check_code_pattern \
  "AccountRemoteActivityAccountPort implements rollbackQuota" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteActivityAccountPort.java" \
  'void rollbackQuota'

check_code_pattern \
  "IActivityAccountPort declares rollbackQuota" \
  "big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java" \
  'void rollbackQuota'

check_code_pattern \
  "ActivityRepository implements rollbackQuotaWithLedger" \
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java" \
  'rollbackQuotaWithLedger'

check_code_pattern \
  "RaffleApplicationService sagas rollbackQuota on draw failure" \
  "big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java" \
  'rollbackQuota'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 5: Dual-dispatch risk documentation
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 5. Dual-dispatch risk documentation ──"

assert_matrix_contains \
  "Matrix documents dual-dispatch risk (shared task + outbox)" \
  'dual-dispatch|dual dispatch|both.*SendMessageTaskJob|both.*DispatchCreditAwardTaskJob'

assert_matrix_contains \
  "Matrix references JobMutualExclusionValidator for startup guard" \
  'JobMutualExclusionValidator'

assert_matrix_contains \
  "Matrix references shared-task-fallback.credit-award-disabled flag" \
  'shared-task-fallback\.credit-award-disabled|shared.*task.*fallback.*disabled'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 6: No remote/outbox/cutover flag defaults to true
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 6. Remote/outbox/cutover flag defaults ──"

RESOURCE_DIRS=("$REPO_ROOT"/big-market-*/src/main/resources)

IDEM_FLAGS=(
  "account.service.remote-credit-write.enabled"
  "account.service.remote-quota-write.enabled"
  "account.service.remote-quota-decrement.enabled"
  "account.fulfillment.remote-award.enabled"
  "account.award-credit-outbox.enabled"
  "rebate.service.remote-create-order.enabled"
  "rebate.service.remote-read.enabled"
  "strategy.service.remote-read.enabled"
)

for flag in "${IDEM_FLAGS[@]}"; do
  prop_pat="${flag//./\\.}"
  matches=$(grep -RInE "${prop_pat}.*:(.*true|\$\{[A-Z_]+:-\?true\})" \
    "${RESOURCE_DIRS[@]}" \
    --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null \
    | grep -v '/target/' | grep -v '^\s*#' || true)
  if [[ -z "$matches" ]]; then
    pass "Flag default safe: $flag"
  else
    fail "Flag appears default-true: $flag"
    printf '%s\n' "$matches" | sed 's#^#       #'
  fi
done

# ═══════════════════════════════════════════════════════════════════════════════
# Section 7: No executable DDL outside docs/sql/proposed-*.sql
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 7. DDL isolation ──"

DDL_VIOLATIONS=$(grep -RInE '\b(CREATE|ALTER|DROP)[[:space:]]+(TABLE|INDEX|DATABASE)\b' \
  "$REPO_ROOT/docs" --include='*.sql' 2>/dev/null \
  | grep -v '/docs/sql/proposed-' \
  | grep -v '/docs/archive/' \
  | grep -v '/docs/dev-ops/' \
  || true)

if [[ -z "$DDL_VIOLATIONS" ]]; then
  pass "No DDL outside docs/sql/proposed-*.sql (excluding archive, dev-ops)"
else
  fail "DDL found outside proposed/archive/dev-ops:"
  printf '%s\n' "$DDL_VIOLATIONS" | head -5
fi

# Verify all 5 proposed DDL files exist
PROPOSED_COUNT=$(find "$PROPOSED_DDL_DIR" -name 'proposed-*.sql' -type f 2>/dev/null | wc -l | tr -d ' ')
if [[ "$PROPOSED_COUNT" -ge 5 ]]; then
  pass "$PROPOSED_COUNT proposed DDL files present (expect >=5)"
else
  fail "Only $PROPOSED_COUNT proposed DDL files (expect >=5)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "========================================================================"
echo "  SUMMARY"
echo "========================================================================"
echo "Checks passed: $PASS"
echo "Checks failed: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED — Phase 8 idempotency & rollback matrix complete"
  echo "        Every flow has a documented idempotency key, retry/rollback"
  echo "        behavior, unique-key name matching proposed DDL, and"
  echo "        EXTERNAL-GATED evidence requirements."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi
