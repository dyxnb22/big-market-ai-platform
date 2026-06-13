#!/usr/bin/env bash
# Repo-only cross-document consistency gate for Phase 8 staging evidence.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAGING="$REPO_ROOT/docs/evidence/phase-8-evidence-pack.md"
STAGING_INTAKE="$REPO_ROOT/docs/evidence/phase-8-evidence-pack.md"
PRODUCTION="$REPO_ROOT/docs/evidence/phase-8-evidence-pack.md"
GONOGO="$REPO_ROOT/docs/evidence/phase-8-evidence-pack.md"
RUNBOOK="$REPO_ROOT/docs/microservices-phase-8.md"
INTAKE="$REPO_ROOT/docs/microservices-phase-8.md"
COMPLETION="$REPO_ROOT/docs/archive/microservices-history.md"
AGGREGATE="$REPO_ROOT/scripts/validate-microservices-split-all-gates.sh"

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

assert_absent() {
  local label="$1" file="$2" pattern="$3"
  local matches
  matches=$(grep -nEi "$pattern" "$file" 2>/dev/null || true)
  if [[ -z "$matches" ]]; then
    pass "$label"
  else
    fail "$label"
    printf '%s\n' "$matches" | sed 's#^#       #'
  fi
}

field_line_has_gated_value() {
  local file="$1" field="$2"
  grep -qE "\\| ${field//./\\.} \\|[^|]+\\|[[:space:]]*EXTERNAL-GATED[[:space:]]*\\|" "$file" 2>/dev/null
}

field_range_present() {
  local file="$1" from="$2" to="$3"
  grep -qE "${from//./\\.}[[:space:]]+through[[:space:]]+${to//./\\.}" "$file" 2>/dev/null
}

echo ""
echo "========================================================================"
echo "  Phase 8 Staging Evidence Cross-Document Consistency Validator"
echo "========================================================================"

require_file "Staging evidence template exists" "$STAGING"
require_file "Staging intake checklist exists" "$STAGING_INTAKE"
require_file "Production evidence template exists" "$PRODUCTION"
require_file "GO/NO-GO checklist exists" "$GONOGO"
require_file "Phase 8 runbook exists" "$RUNBOOK"
require_file "External evidence intake index exists" "$INTAKE"
require_file "Completion index exists" "$COMPLETION"
require_file "Aggregate split gate exists" "$AGGREGATE"

echo ""
echo "-- STG field definitions remain gated in the staging template --"
for group in 0 1 2 3 4 5 6; do
  case "$group" in
    0) max=5 ;;
    1) max=7 ;;
    2) max=8 ;;
    3) max=7 ;;
    4) max=5 ;;
    5) max=5 ;;
    6) max=6 ;;
  esac
  for idx in $(seq 1 "$max"); do
    field="STG-$group.$idx"
    if field_line_has_gated_value "$STAGING" "$field"; then
      pass "$field exists in staging template and remains EXTERNAL-GATED"
    else
      fail "$field missing from staging template or not EXTERNAL-GATED"
    fi
  done
done

echo ""
echo "-- STG ranges match across intake checklist and GO/NO-GO checklist --"
for range in \
  "STG-1.1:STG-1.7:DBA DDL and grants:GNG-1" \
  "STG-2.1:STG-2.8:Ops deploy, discovery, jobs, MQ, config:GNG-2" \
  "STG-3.1:STG-3.7:Engineering flow validation:GNG-3" \
  "STG-4.1:STG-4.5:Oncall metrics and observation:GNG-4" \
  "STG-5.1:STG-5.5:Product approval or exemption:GNG-5" \
  "STG-6.1:STG-6.6:Staging GO/NO-GO decision:GNG-6"; do
  from="${range%%:*}"
  rest="${range#*:}"
  to="${rest%%:*}"
  rest="${rest#*:}"
  gate="${rest%%:*}"
  gng="${rest#*:}"

  require_text "Intake checklist names gate: $gate" "$STAGING_INTAKE" "$gate"
  if field_range_present "$STAGING_INTAKE" "$from" "$to"; then
    pass "Intake checklist maps $from through $to"
  else
    fail "Intake checklist missing $from through $to"
  fi
  require_text "GO/NO-GO checklist maps $gng to $from through $to" "$GONOGO" "$gng.*$from through $to"
  require_text "External evidence intake maps $from through $to" "$INTAKE" "$from.*$to"
done

echo ""
echo "-- Per-service checklist references valid STG fields --"
for service in account-service fulfillment-service rebate-service strategy-service activity-service; do
  require_text "Per-service checklist covers $service" "$STAGING_INTAKE" "^\\| $service \\|"
done
for field in \
  STG-1.1 STG-1.2 STG-1.3 STG-1.5 STG-1.6 STG-1.7 \
  STG-2.1 STG-2.2 STG-2.3 STG-2.4 STG-2.5 STG-2.6 STG-2.7 STG-2.8 \
  STG-3.1 STG-3.2 STG-3.3 STG-3.4 STG-3.5 STG-3.6 STG-3.7 \
  STG-4.1 STG-4.2 STG-4.5 STG-5.1 STG-5.2 STG-5.3 STG-5.4 STG-5.5; do
  require_text "Per-service checklist references $field" "$STAGING_INTAKE" "$field"
done

echo ""
echo "-- Cross-document links include this consistency gate --"
for rel in \
  "docs/evidence/phase-8-evidence-pack.md" \
  "docs/evidence/phase-8-evidence-pack.md" \
  "docs/evidence/phase-8-evidence-pack.md" \
  "scripts/validate-microservices-phase-8-staging-evidence-intake.sh" \
  "scripts/validate-microservices-phase-8-staging-evidence-consistency.sh"; do
  require_text "Runbook links $rel" "$RUNBOOK" "$rel"
  require_text "Completion index links $rel" "$COMPLETION" "$rel"
done
require_text "External evidence intake links consistency gate" "$INTAKE" "scripts/validate-microservices-phase-8-staging-evidence-consistency\\.sh"
require_text "Aggregate gate includes consistency validator" "$AGGREGATE" "validate-microservices-phase-8-staging-evidence-consistency\\.sh"

echo ""
echo "-- Production and final decisions remain gated --"
for group in 0 1 2 3 4 5 6; do
  case "$group" in
    0) max=5 ;;
    1) max=7 ;;
    2) max=8 ;;
    3) max=7 ;;
    4) max=5 ;;
    5) max=5 ;;
    6) max=7 ;;
  esac
  for idx in $(seq 1 "$max"); do
    field="PROD-$group.$idx"
    if field_line_has_gated_value "$PRODUCTION" "$field"; then
      pass "$field remains EXTERNAL-GATED"
    else
      fail "$field missing from production template or not EXTERNAL-GATED"
    fi
  done
done
require_text "GNG-D9 final result remains EXTERNAL-GATED" "$GONOGO" "GNG-D9.*EXTERNAL-GATED"

echo ""
echo "-- No accidental evidence completion claims --"
for file in "$STAGING" "$STAGING_INTAKE" "$PRODUCTION" "$GONOGO" "$RUNBOOK" "$INTAKE" "$COMPLETION"; do
  assert_absent "$(basename "$file") does not claim staging evidence complete" "$file" "staging cutover (is )?complete|staging evidence (is )?(complete|recorded)|evidence status:[[:space:]]*(complete|done|approved)"
  assert_absent "$(basename "$file") does not claim production evidence complete" "$file" "production cutover (is )?complete|production evidence (is )?(complete|recorded)|external cutover (is )?complete"
  assert_absent "$(basename "$file") does not contain final GO value" "$file" "Final (staging |production |GO/NO-GO )?(decision|result)[^|\\n]*\\|[[:space:]]*GO([[:space:]]|$)"
done

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - Phase 8 staging evidence docs are consistent and remain external-gated"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
