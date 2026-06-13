#!/usr/bin/env bash
# Repo-only Phase 8 external evidence readiness pack validator.
#
# Validates that the readiness pack
# (docs/microservices-phase-8.md) is present,
# complete, and consistent with the proposed DDL and flag defaults.
#
# Deterministic, repo-only, no DB/MQ/Docker/network.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

PACK="$REPO_ROOT/docs/microservices-phase-8.md"
PROPOSED_DDL_DIR="$REPO_ROOT/docs/sql"

echo ""
echo "========================================================================"
echo "  Phase 8 External Evidence Readiness Pack Validator"
echo "  Repo: $REPO_ROOT"
echo "========================================================================"

# ── Helpers ───────────────────────────────────────────────────────────────────
assert_pack_contains() {
  local label="$1" pattern="$2"
  if grep -qE "$pattern" "$PACK" 2>/dev/null; then
    pass "$label"
  else
    fail "$label — not found in readiness pack"
  fi
}

assert_pack_not_contains() {
  local label="$1" pattern="$2"
  if grep -qE "$pattern" "$PACK" 2>/dev/null; then
    fail "$label — forbidden pattern found: $pattern"
  else
    pass "$label"
  fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# Section 1: Pack document presence
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 1. Pack document presence ──"

if [[ -f "$PACK" ]]; then
  pass "Readiness pack exists"
else
  fail "Readiness pack missing: $PACK"
  echo ""
  echo "RESULT: FAILED — readiness pack not found"
  exit 1
fi

assert_pack_contains "Pack declares EXTERNAL-GATED status" "EXTERNAL-GATED"
assert_pack_contains "Pack references its own validator" "validate-microservices-phase-8-external-evidence-readiness-pack"
assert_pack_contains "Pack has a Purpose section" "## Purpose"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 2: Stakeholder sections
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 2. Stakeholder sections ──"

STAKEHOLDER_SECTIONS=(
  "DBA.*DDL Verification"
  "Ops.*Dubbo.*Provider|Ops.*XXL-Job|Ops.*MQ"
  "Engineering.*Staging Canary"
  "Oncall.*Dashboards.*Alerts|Oncall.*dashboards.*alerts"
  "Product.*GO/NO-GO"
)

for section_pat in "${STAKEHOLDER_SECTIONS[@]}"; do
  assert_pack_contains "Stakeholder section exists: $section_pat" "$section_pat"
done

# Verify each stakeholder has at least one checklist table
for stakeholder in DBA Ops Engineering Oncall Product; do
  assert_pack_contains "$stakeholder has EXTERNAL-GATED items" "${stakeholder}.*EXTERNAL-GATED"
done

# Verify every stakeholder has a signoff line
for stakeholder in "DBA-Staging" "Ops-Staging" "Engineering-Staging" "Oncall-Staging" "Product-Staging"; do
  assert_pack_contains "Signoff line for $stakeholder" "$stakeholder.*EXTERNAL-GATED"
done

# ═══════════════════════════════════════════════════════════════════════════════
# Section 3: High-risk flow coverage
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 3. High-risk flow coverage ──"

HIGH_RISK_FLOWS=(
  "Quota Decrement.*Rollback"
  "Credit Award Outbox Dispatch"
  "Award Fulfillment"
  "Rebate Create.*Read"
  "Credit Trade"
  "SKU Exchange"
  "Shared Task Fallback.*Per-Domain Outbox"
  "Strategy Read"
)

for flow_pat in "${HIGH_RISK_FLOWS[@]}"; do
  assert_pack_contains "High-risk flow covered: $flow_pat" "$flow_pat"
done

check_flow_mapping_section() {
  local label="$1" heading="$2" required_ids="$3"
  local section
  section=$(awk -v h="$heading" '
    $0 ~ "^### " h "$" { in_section=1; print; next }
    in_section && $0 ~ "^### " { exit }
    in_section { print }
  ' "$PACK")

  if [[ -z "$section" ]]; then
    fail "Flow mapping section missing: $label"
    return
  fi
  pass "Flow mapping section exists: $label"

  for owner in DBA Ops Engineering Oncall Product; do
    if printf '%s\n' "$section" | grep -q "| $owner |"; then
      pass "$label maps stakeholder: $owner"
    else
      fail "$label missing stakeholder mapping: $owner"
    fi
  done

  IFS=',' read -r -a ids <<< "$required_ids"
  for id in "${ids[@]}"; do
    if printf '%s\n' "$section" | grep -q "$id"; then
      pass "$label maps checklist id: $id"
    else
      fail "$label missing checklist id: $id"
    fi
  done
}

check_flow_mapping_section "Quota decrement / rollback" "2\\.1 Quota Decrement / Rollback" "DBA-3,OPS-1,ENG-3,ONC-1,PRD-6"
check_flow_mapping_section "Credit award outbox dispatch" "2\\.2 Credit Award Outbox Dispatch" "DBA-1,OPS-5,ENG-5,ONC-7,PRD-6"
check_flow_mapping_section "Award fulfillment" "2\\.3 Award Fulfillment" "DBA-9,OPS-2,ENG-4,ONC-3,PRD-2"
check_flow_mapping_section "Rebate create / read" "2\\.4 Rebate Create / Read" "DBA-5,OPS-3,ENG-6,ONC-4,PRD-3"
check_flow_mapping_section "Credit trade" "2\\.5 Credit Trade" "DBA-7,OPS-1,ENG-1,ONC-2,PRD-1"
check_flow_mapping_section "SKU exchange" "2\\.6 SKU Exchange" "DBA-11,OPS-1,ENG-2,ONC-6,PRD-6"
check_flow_mapping_section "Shared task fallback vs outbox" "2\\.7 Shared Task Fallback vs Per-Domain Outbox" "DBA-1,OPS-5,ENG-5,ONC-7,PRD-5"
check_flow_mapping_section "Strategy read" "2\\.8 Strategy Read" "DBA-11,OPS-4,ENG-8,ONC-5,PRD-4"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 4: Every flow references EXTERNAL-GATED owners
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 4. EXTERNAL-GATED owner references per flow ──"

# Count EXTERNAL-GATED occurrences by flow section
GATED_COUNT=$(grep -c "EXTERNAL-GATED" "$PACK" 2>/dev/null || echo 0)
if [[ "$GATED_COUNT" -ge 50 ]]; then
  pass "Readiness pack has $GATED_COUNT EXTERNAL-GATED references (expect >=50)"
else
  fail "Readiness pack has only $GATED_COUNT EXTERNAL-GATED references (expect >=50)"
fi

# Every checklist table must have the Status column with EXTERNAL-GATED
# Check that Status appears as a table column header
assert_pack_contains "Checklist tables have Status column header" '\| Status \|'
# Check that EXTERNAL-GATED appears in table rows (after a pipe, before a pipe)
assert_pack_contains "Checklist rows contain EXTERNAL-GATED values" '\| EXTERNAL-GATED \|'

# Ensure no flow claims a non-external owner
assert_pack_not_contains "No flow claims REPO-READY as evidence status" "Status[[:space:]]*\|[[:space:]]*REPO-READY"
assert_pack_not_contains "No flow claims LOCAL-ONLY as evidence status" "Status[[:space:]]*\|[[:space:]]*LOCAL-ONLY"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 5: Every proposed DDL file is referenced
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 5. Proposed DDL file references ──"

PROPOSED_DDL_FILES=(
  "proposed-credit-award-task-outbox.sql"
  "proposed-quota-decrement-ledger.sql"
  "proposed-rebate-task-outbox.sql"
  "proposed-credit-trade-task-outbox.sql"
  "proposed-award-dispatch-task-outbox.sql"
)

for ddl in "${PROPOSED_DDL_FILES[@]}"; do
  # Verify the DDL file exists on disk
  if [[ -f "$PROPOSED_DDL_DIR/$ddl" ]]; then
    pass "DDL file exists: $ddl"
  else
    fail "DDL file missing: $ddl"
  fi

  # Verify the DDL file is referenced in the readiness pack
  if grep -q "$ddl" "$PACK" 2>/dev/null; then
    pass "DDL referenced in pack: $ddl"
  else
    fail "DDL NOT referenced in pack: $ddl"
  fi
done

# Verify the proposed DDL coverage map section exists in the pack
assert_pack_contains "Pack has Proposed DDL Coverage Map section" "Proposed DDL Coverage Map"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 6: Every cutover flag remains default false
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 6. Cutover flag default safety ──"

RESOURCE_DIRS=("$REPO_ROOT"/big-market-*/src/main/resources)

CUTOVER_FLAGS=(
  "account.service.remote-credit-write.enabled"
  "account.service.remote-quota-write.enabled"
  "account.service.remote-quota-decrement.enabled"
  "account.fulfillment.remote-award.enabled"
  "account.award-credit-outbox.enabled"
  "rebate.service.remote-create-order.enabled"
  "rebate.service.remote-read.enabled"
  "strategy.service.remote-read.enabled"
)

for flag in "${CUTOVER_FLAGS[@]}"; do
  prop_pat="${flag//./\\.}"
  matches=$(grep -RInE "${prop_pat}.*:(.*true|\$\{[A-Z_]+:-?true\})" \
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

# Legacy provider flags must remain default true
echo ""
echo "── 6.2 Legacy provider flags (must remain default true) ──"

MARKET_RES="$REPO_ROOT/big-market-market-service/src/main/resources"
MESSAGE_JOB_YML="$REPO_ROOT/big-market-message-job-service/src/main/resources/application.yml"

# REBATE_LEGACY_RPC_PROVIDER_ENABLED should be true
if grep -RqE 'REBATE_LEGACY_RPC_PROVIDER_ENABLED:true' "$MARKET_RES" 2>/dev/null; then
  pass "REBATE_LEGACY_RPC_PROVIDER_ENABLED default true"
else
  fail "REBATE_LEGACY_RPC_PROVIDER_ENABLED not default true"
fi

# STRATEGY_LEGACY_RPC_PROVIDER_ENABLED should be true
if grep -RqE 'STRATEGY_LEGACY_RPC_PROVIDER_ENABLED:true' "$MARKET_RES" 2>/dev/null; then
  pass "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED default true"
else
  fail "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED not default true"
fi

# Verify the pack documents all cutover flags
echo ""
echo "── 6.3 Pack documents all cutover flags ──"

PACK_FLAGS=(
  "account\.service\.remote-credit-write\.enabled"
  "account\.service\.remote-quota-write\.enabled"
  "account\.service\.remote-quota-decrement\.enabled"
  "account\.fulfillment\.remote-award\.enabled"
  "account\.award-credit-outbox\.enabled"
  "rebate\.service\.remote-create-order\.enabled"
  "rebate\.service\.remote-read\.enabled"
  "strategy\.service\.remote-read\.enabled"
)

for flag_pat in "${PACK_FLAGS[@]}"; do
  assert_pack_contains "Pack documents flag: $flag_pat" "$flag_pat"
done

# Legacy provider flags documented
assert_pack_contains "Pack documents REBATE_LEGACY_RPC_PROVIDER_ENABLED" "REBATE_LEGACY_RPC_PROVIDER_ENABLED"
assert_pack_contains "Pack documents STRATEGY_LEGACY_RPC_PROVIDER_ENABLED" "STRATEGY_LEGACY_RPC_PROVIDER_ENABLED"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 7: No real-production-ready claim
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 7. No real production-ready claim ──"

FORBIDDEN_CLAIMS=(
  "production-ready"
  "production ready"
  "prod-ready"
  "cutover complete"
  "cutover.-.complete"
  "staging.-.complete"
  "real.*staging.*complete"
  "real.*production.*complete"
)

for claim_pat in "${FORBIDDEN_CLAIMS[@]}"; do
  if grep -qiE "$claim_pat" "$PACK" 2>/dev/null; then
    # Allow these phrases ONLY when qualified with "no" or "not" or "EXTERNAL-GATED"
    safe_count=$(grep -ciE "(no|not|never|EXTERNAL-GATED).*${claim_pat}|${claim_pat}.*(EXTERNAL-GATED|not|never)" "$PACK" 2>/dev/null || echo 0)
    total_count=$(grep -ciE "$claim_pat" "$PACK" 2>/dev/null || echo 0)
    if [[ "$safe_count" -eq "$total_count" ]]; then
      pass "Phrase '$claim_pat' only appears in safe/negated context"
    else
      fail "Phrase '$claim_pat' appears in non-safe context ($total_count total, $safe_count safe)"
    fi
  else
    pass "No forbidden claim: '$claim_pat'"
  fi
done

# Pack must have the standard disclaimer
assert_pack_contains "Pack disclaims production readiness" "No (real|remote|staging|production).*(cutover|DDL|readiness)"
assert_pack_contains "Pack states EXTERNAL-GATED in status header" "Status: repo-only"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 8: No executable DDL outside docs/sql/proposed-*.sql
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 8. DDL isolation ──"

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
# Section 9: GO/NO-GO decision matrix exists
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 9. GO/NO-GO decision matrix ──"

assert_pack_contains "Pack has GO/NO-GO Decision Matrix" "GO/NO-GO Decision Matrix"
assert_pack_contains "Staging GO criteria documented" "Staging GO criteria"
assert_pack_contains "Production GO criteria documented" "Production GO criteria"

for stakeholder in DBA Ops Engineering Oncall Product; do
  assert_pack_contains "$stakeholder row in GO/NO-GO matrix" "${stakeholder}.*EXTERNAL-GATED"
done

# ═══════════════════════════════════════════════════════════════════════════════
# Section 10: Cross-references to sibling docs
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 10. Cross-references to sibling documents ──"

SIBLING_REFS=(
  "microservices-phase-8-cutover-conflict-matrix"
  "microservices-phase-8-idempotency-rollback-matrix"
  "microservices-legacy-cleanup-inventory"
  "microservices-phase-8-external-evidence-intake"
  "microservices-phase-8-cutover-runbook"
)

for ref in "${SIBLING_REFS[@]}"; do
  assert_pack_contains "Pack cross-references: $ref" "$ref"
done

# Verify it references the aggregate gate script
assert_pack_contains "Pack references aggregate gate script" "validate-microservices-split-all-gates"

# ═══════════════════════════════════════════════════════════════════════════════
# Section 11: Unique key references in pack
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 11. Unique key references ──"

UNIQUE_KEYS=(
  "uq_award_order_id"
  "uq_user_activity_biz"
  "uq_user_message_id"
  "uq_out_business_no"
  "uq_biz_id"
  "uq_order_id"
  "uq_message_id"
)

for uk in "${UNIQUE_KEYS[@]}"; do
  if grep -q "$uk" "$PACK" 2>/dev/null; then
    pass "Unique key referenced in pack: $uk"
  else
    fail "Unique key NOT referenced in pack: $uk"
  fi
done

# ═══════════════════════════════════════════════════════════════════════════════
# Section 12: Execution order and cleanup clocks
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "── 12. Execution order and cleanup clocks ──"

assert_pack_contains "Pack documents execution order" "Execution Order"
assert_pack_contains "Pack references 7-day stable clock" "7-day stable"
assert_pack_contains "Pack references 30-day cleanup clock" "30-day"

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
  echo "RESULT: ALL CHECKS PASSED — Phase 8 external evidence readiness pack"
  echo "        is complete, consistent, and all external gates remain"
  echo "        EXTERNAL-GATED. Every proposed DDL is referenced, every"
  echo "        cutover flag defaults to false, and no production-ready"
  echo "        claim is made."
  exit 0
else
  echo "RESULT: $FAIL CHECK(S) FAILED — review output above"
  exit 1
fi
