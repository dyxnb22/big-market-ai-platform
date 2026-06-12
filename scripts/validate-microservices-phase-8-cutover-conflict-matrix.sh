#!/usr/bin/env bash
# Repo-only Phase 8 cutover conflict matrix validator.
#
# Validates that the cutover conflict matrix document
# (docs/microservices-phase-8-cutover-conflict-matrix.md) is present, complete,
# and consistent with the actual codebase: every referenced file/class/flag
# must exist, every row must document its external evidence gates, and every
# row must not claim premature cleanup eligibility.
#
# Deterministic, repo-only, no DB/MQ/Docker/network.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

MATRIX="$REPO_ROOT/docs/microservices-phase-8-cutover-conflict-matrix.md"
LEGACY_INVENTORY="$REPO_ROOT/docs/microservices-legacy-cleanup-inventory.md"

echo ""
echo "========================================================================"
echo "  Phase 8 Cutover Conflict Matrix Validator"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── Helpers ───────────────────────────────────────────────────────────────────
assert_file() {
  local label="$1" path="$2"
  if [[ -f "$REPO_ROOT/$path" ]]; then
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
    fail "$label — pattern not found in matrix: $pattern"
  fi
}

assert_file_contains() {
  local label="$1" path="$2" pattern="$3"
  if grep -qE "$pattern" "$REPO_ROOT/$path" 2>/dev/null; then
    pass "$label"
  else
    fail "$label — pattern not found in $path: $pattern"
  fi
}

assert_flag_default_false() {
  local label="$1" flag_prop="$2" search_dir="$3"
  local matches
  matches=$(grep -RInE "${flag_prop}.*:(.*true|\$\{[A-Z_]+:-true\})" "$search_dir" \
    --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null \
    | grep -v '/target/' | grep -v '^\s*#' || true)
  if [[ -z "$matches" ]]; then
    pass "$label"
  else
    fail "$label — flag appears enabled:"
    printf '%s\n' "$matches" | sed 's#^#       #'
  fi
}

assert_legacy_flag_default_true() {
  local label="$1" flag_prop="$2" search_dir="$3"
  local matches
  matches=$(grep -RInE "${flag_prop}.*:(.*true|\$\{[A-Z_]+:-true\})" "$search_dir" \
    --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null \
    | grep -v '/target/' || true)
  if [[ -n "$matches" ]]; then
    pass "$label"
  else
    fail "$label — legacy provider flag should default true but not found: $flag_prop"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# Section 1: Matrix document presence and structural completeness
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 1. Matrix document presence ──"

assert_file "Cutover conflict matrix exists" "docs/microservices-phase-8-cutover-conflict-matrix.md"

# Verify all 9 domain rows are present
DOMAINS=(
  "Account / Credit Write"
  "Account / Quota Write"
  "Account / Quota Decrement"
  "Fulfillment / Award Dispatch"
  "Fulfillment / Award.*Draw Hot Path"
  "Rebate / Create Order"
  "Rebate / Read"
  "Strategy / Read"
  "Shared Task.*Outbox Dispatcher"
)

for domain_pat in "${DOMAINS[@]}"; do
  assert_matrix_contains "Matrix covers: $domain_pat" "$domain_pat"
done

# Verify structural headers
for header in "Legacy path" "Future path" "Owning service" \
  "Flag that enables new path" "Flag that disables old path" \
  "Why both must not run simultaneously" "Current safe default" \
  "EXTERNAL-GATED"; do
  assert_matrix_contains "Matrix contains column/term: $header" "$header"
done

echo ""
echo "── 1.2 Gate language audit ──"

for gate_term in "EXTERNAL-GATED" "7-day stable" "30-day removal"; do
  count=$(grep -c "$gate_term" "$MATRIX" 2>/dev/null || echo 0)
  if [[ "$count" -ge 9 ]]; then
    pass "Gate term '$gate_term' appears $count times (expect >=9 rows)"
  else
    fail "Gate term '$gate_term' appears only $count times (expect >=9)"
  fi
done

# ═══════════════════════════════════════════════════════════════════════════════
# Section 2: Legacy adapter/port file presence
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 2. Legacy adapter/port file presence ──"

LEGACY_FILES=(
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountCreditWriteAdapter.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountQuotaWriteAdapter.java"
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAwardDispatchAdapter.java"
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalAwardFulfillmentPort.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java"
  "big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java"
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalRebateTaskOutboxPort.java"
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalCreditTradeTaskOutboxPort.java"
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalAwardDispatchTaskOutboxPort.java"
)

for f in "${LEGACY_FILES[@]}"; do
  assert_file "$(basename "$f")" "$f"
done

echo ""
echo "── 2.2 Future path file presence ──"

FUTURE_FILES=(
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteActivityAccountPort.java"
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/AccountRemoteCreditWriteAdapter.java"
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/AccountRemoteQuotaWriteAdapter.java"
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RemoteAwardDispatchAdapter.java"
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteCreditWriteAdapter.java"
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteQuotaWriteAdapter.java"
  "big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteCreateOrderAdapter.java"
  "big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteReadAdapter.java"
  "big-market-market-service/src/main/java/com/dyx/market/market/config/StrategyRemoteReadAdapter.java"
)

for f in "${FUTURE_FILES[@]}"; do
  assert_file "$(basename "$f")" "$f"
done

echo ""
echo "── 2.3 Adapter activation topology ──"

assert_file_contains \
  "LocalAccountCreditWriteAdapter is missing-bean fallback" \
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountCreditWriteAdapter.java" \
  '@ConditionalOnMissingBean\(IAccountCreditWriteAdapter\.class\)'

assert_file_contains \
  "Message-job credit remote adapter is flag-conditional" \
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/AccountRemoteCreditWriteAdapter.java" \
  'account\.service\.remote-credit-write\.enabled.*havingValue = "true"'

assert_file_contains \
  "Market credit wrapper uses internal remote flag" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteCreditWriteAdapter.java" \
  '@Value\("\$\{account\.service\.remote-credit-write\.enabled:false\}"\)'

assert_file_contains \
  "LocalAccountQuotaWriteAdapter is missing-bean fallback" \
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAccountQuotaWriteAdapter.java" \
  '@ConditionalOnMissingBean\(IAccountQuotaWriteAdapter\.class\)'

assert_file_contains \
  "Message-job quota remote adapter is flag-conditional" \
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/AccountRemoteQuotaWriteAdapter.java" \
  'account\.service\.remote-quota-write\.enabled.*havingValue = "true"'

assert_file_contains \
  "Market quota wrapper uses internal remote flag" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteQuotaWriteAdapter.java" \
  '@Value\("\$\{account\.service\.remote-quota-write\.enabled:false\}"\)'

assert_file_contains \
  "LocalActivityAccountPort is disabled when remote quota decrement is true" \
  "big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java" \
  'remote-quota-decrement\.enabled.*havingValue = "false".*matchIfMissing = true'

assert_file_contains \
  "Remote activity account port is flag-conditional" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteActivityAccountPort.java" \
  'remote-quota-decrement\.enabled.*havingValue = "true"'

assert_file_contains \
  "WriteAdapterLocalConfig registers remote award only when enabled" \
  "big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/WriteAdapterLocalConfig.java" \
  'account\.fulfillment\.remote-award\.enabled.*havingValue = "true"'

assert_file_contains \
  "Local award dispatch adapter is missing-bean fallback" \
  "big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalAwardDispatchAdapter.java" \
  '@ConditionalOnMissingBean\(IAwardDispatchAdapter\.class\)'

assert_file_contains \
  "Rebate create adapter uses internal remote flag" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteCreateOrderAdapter.java" \
  '@Value\("\$\{rebate\.service\.remote-create-order\.enabled:false\}"\)'

assert_file_contains \
  "Rebate read adapter uses internal remote flag" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/RebateRemoteReadAdapter.java" \
  '@Value\("\$\{rebate\.service\.remote-read\.enabled:false\}"\)'

assert_file_contains \
  "Strategy read adapter uses internal remote flag" \
  "big-market-market-service/src/main/java/com/dyx/market/market/config/StrategyRemoteReadAdapter.java" \
  '@Value\("\$\{strategy\.service\.remote-read\.enabled:false\}"\)'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 3: Flag default verification — remote flags must be default false
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 3. Remote/outbox flag defaults (must be false) ──"

RESOURCE_DIRS=("$REPO_ROOT"/big-market-*/src/main/resources)

assert_flag_default_false \
  "account.service.remote-credit-write.enabled default false" \
  'account\.service\.remote-credit-write\.enabled' \
  "${RESOURCE_DIRS[@]}"

assert_flag_default_false \
  "account.service.remote-quota-write.enabled default false" \
  'account\.service\.remote-quota-write\.enabled' \
  "${RESOURCE_DIRS[@]}"

assert_flag_default_false \
  "account.service.remote-quota-decrement.enabled default false" \
  'account\.service\.remote-quota-decrement\.enabled' \
  "${RESOURCE_DIRS[@]}"

assert_flag_default_false \
  "account.fulfillment.remote-award.enabled default false" \
  'account\.fulfillment\.remote-award\.enabled' \
  "${RESOURCE_DIRS[@]}"

assert_flag_default_false \
  "rebate.service.remote-create-order.enabled default false" \
  'rebate\.service\.remote-create-order\.enabled' \
  "${RESOURCE_DIRS[@]}"

assert_flag_default_false \
  "rebate.service.remote-read.enabled default false" \
  'rebate\.service\.remote-read\.enabled' \
  "${RESOURCE_DIRS[@]}"

assert_flag_default_false \
  "strategy.service.remote-read.enabled default false" \
  'strategy\.service\.remote-read\.enabled' \
  "${RESOURCE_DIRS[@]}"

assert_flag_default_false \
  "account.award-credit-outbox.enabled default false" \
  'account\.award-credit-outbox\.enabled' \
  "${RESOURCE_DIRS[@]}"

echo ""
echo "── 3.2 Legacy provider flags (must be default true until cutover) ──"

MARKET_RES="$REPO_ROOT/big-market-market-service/src/main/resources"

assert_legacy_flag_default_true \
  "rebate.legacy-rpc-provider.enabled default true" \
  'REBATE_LEGACY_RPC_PROVIDER_ENABLED' \
  "$MARKET_RES"

assert_legacy_flag_default_true \
  "strategy.legacy-rpc-provider.enabled default true" \
  'STRATEGY_LEGACY_RPC_PROVIDER_ENABLED' \
  "$MARKET_RES"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 4: Dual-provider mutual exclusion (legacy + remote never both true)
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 4. Dual-provider flag combination check ──"

# Rebate: REBATE_LEGACY_RPC_PROVIDER_ENABLED:-true must NOT coexist with
# REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:-true
REBATE_LEGACY_TRUE=$(grep -RcE 'REBATE_LEGACY_RPC_PROVIDER_ENABLED:-true' "$MARKET_RES" \
  --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null | \
  awk -F: '{s+=$NF} END {print s+0}' || echo 0)
REBATE_REMOTE_TRUE=$(grep -RcE 'REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:-true' "$MARKET_RES" \
  --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null | \
  awk -F: '{s+=$NF} END {print s+0}' || echo 0)

if [[ "$REBATE_LEGACY_TRUE" -gt 0 && "$REBATE_REMOTE_TRUE" -gt 0 ]]; then
  fail "Rebate legacy provider AND remote create-order both default true — dual-provider risk"
else
  pass "Rebate: legacy_provider_default_true=$REBATE_LEGACY_TRUE remote_create_default_true=$REBATE_REMOTE_TRUE (safe)"
fi

# Strategy: STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:-true must NOT coexist with
# STRATEGY_SERVICE_REMOTE_READ_ENABLED:-true
STRATEGY_LEGACY_TRUE=$(grep -RcE 'STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:-true' "$MARKET_RES" \
  --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null | \
  awk -F: '{s+=$NF} END {print s+0}' || echo 0)
STRATEGY_REMOTE_TRUE=$(grep -RcE 'STRATEGY_SERVICE_REMOTE_READ_ENABLED:-true' "$MARKET_RES" \
  --include='*.yml' --include='*.yaml' --include='*.properties' 2>/dev/null | \
  awk -F: '{s+=$NF} END {print s+0}' || echo 0)

if [[ "$STRATEGY_LEGACY_TRUE" -gt 0 && "$STRATEGY_REMOTE_TRUE" -gt 0 ]]; then
  fail "Strategy legacy provider AND remote read both default true — dual-provider risk"
else
  pass "Strategy: legacy_provider_default_true=$STRATEGY_LEGACY_TRUE remote_read_default_true=$STRATEGY_REMOTE_TRUE (safe)"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Section 5: Outbox / shared-task mutual exclusion
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 5. Outbox vs shared-task mutual exclusion ──"

MESSAGE_JOB_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"

if grep -qE 'ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:-true' "$MESSAGE_JOB_YML" 2>/dev/null; then
  if grep -qE 'job\.shared-task-fallback\.credit-award-disabled.*:.*true' "$MESSAGE_JOB_YML" 2>/dev/null; then
    pass "Outbox enabled + shared-task-fallback explicitly disabled — no dual-dispatch"
  else
    fail "Outbox enabled but shared-task-fallback.credit-award-disabled is not true"
  fi
else
  pass "Outbox not default-enabled; shared-task fallback remains safe"
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Section 6: Proposed DDL coverage for outbox tables
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 6. Proposed DDL coverage for outbox tables ──"

PROPOSED_DDL_DIR="$REPO_ROOT/docs/sql"
CREDIT_AWARD_DDL="docs/sql/proposed-credit-award-task-outbox.sql"
QUOTA_LEDGER_DDL="docs/sql/proposed-quota-decrement-ledger.sql"

OUTBOX_TABLES=(
  "credit_award_task"
  "raffle_quota_decrement_ledger"
  "rebate_task_outbox"
  "credit_trade_task_outbox"
  "award_dispatch_task_outbox"
)

for table in "${OUTBOX_TABLES[@]}"; do
  if grep -Riq "$table" "$PROPOSED_DDL_DIR" --include='*.sql' 2>/dev/null; then
    pass "Outbox table '$table' referenced in proposed DDL"
  else
    fail "Outbox table '$table' NOT found in proposed DDL — missing schema spec"
  fi
done

assert_file_contains \
  "Credit-award DDL has exact uq_award_order_id key" \
  "$CREDIT_AWARD_DDL" \
  'UNIQUE KEY `?uq_award_order_id`?.*`?user_id`?.*`?award_order_id`?'

assert_file_contains \
  "Quota ledger DDL has exact uq_user_activity_biz key" \
  "$QUOTA_LEDGER_DDL" \
  'UNIQUE KEY `?uq_user_activity_biz`?.*`?user_id`?.*`?activity_id`?.*`?out_business_no`?'

# ═══════════════════════════════════════════════════════════════════════════════
# Section 7: Matrix cross-references with legacy cleanup inventory
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 7. Consistency with legacy cleanup inventory ──"

if [[ -f "$LEGACY_INVENTORY" ]]; then
  pass "Legacy cleanup inventory exists"
else
  fail "Legacy cleanup inventory missing: $LEGACY_INVENTORY"
fi

# Every matrix row's legacy path should appear in the cleanup inventory
for legacy_label in \
  "LocalAccountCreditWriteAdapter" \
  "LocalAccountQuotaWriteAdapter" \
  "LocalActivityAccountPort" \
  "LocalAwardDispatchAdapter" \
  "LocalAwardFulfillmentPort" \
  "RebateServiceRPC" \
  "LocalRebateOrderAdapter" \
  "LocalRebateReadAdapter" \
  "RaffleStrategyServiceRPC" \
  "LocalStrategyReadAdapter" \
  "SendMessageTaskJob" \
  "LocalRebateTaskOutboxPort" \
  "LocalCreditTradeTaskOutboxPort" \
  "LocalAwardDispatchTaskOutboxPort"; do
  if grep -q "$legacy_label" "$LEGACY_INVENTORY" 2>/dev/null; then
    pass "Legacy inventory covers: $legacy_label"
  else
    fail "Legacy inventory missing: $legacy_label"
  fi
done

# ═══════════════════════════════════════════════════════════════════════════════
# Section 8: Idempotency key documentation
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 8. Idempotency key documentation ──"

assert_matrix_contains "Matrix documents credit_award_task idempotency key" 'uq_award_order_id.*user_id.*award_order_id|award_order_id.*uq_award_order_id'
assert_matrix_contains "Matrix documents quota decrement ledger idempotency key" 'uq_user_activity_biz.*user_id.*activity_id.*out_business_no|out_business_no.*uq_user_activity_biz'
assert_matrix_contains "Matrix documents dual-path idempotency gap" 'dual-path|dual.*path|both.*path'

if grep -q 'uk_user_activity_biz' "$MATRIX" 2>/dev/null; then
  fail "Matrix must not use stale quota key name uk_user_activity_biz"
else
  pass "Matrix does not use stale quota key name uk_user_activity_biz"
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
  echo "RESULT: ALL CHECKS PASSED — Phase 8 cutover conflict matrix is complete"
  echo "        Every legacy path is mapped to its future replacement with"
  echo "        explicit flag pairs, evidence gates, and dual-path risk documented."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi
