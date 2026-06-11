#!/usr/bin/env bash
# Repo-only validator for Phase 8 staging evidence intake preparation.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHECKLIST="$REPO_ROOT/docs/evidence/phase-8-staging-evidence-intake-checklist.md"
STAGING="$REPO_ROOT/docs/evidence/phase-8-staging-cutover-evidence-template.md"
PRODUCTION="$REPO_ROOT/docs/evidence/phase-8-production-cutover-evidence-template.md"
GONOGO="$REPO_ROOT/docs/evidence/phase-8-go-no-go-checklist.md"
RUNBOOK="$REPO_ROOT/docs/microservices-phase-8-cutover-runbook.md"
INTAKE="$REPO_ROOT/docs/microservices-phase-8-external-evidence-intake.md"
COMPLETION="$REPO_ROOT/docs/microservices-split-completion-index.md"
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

assert_repo_absent() {
  local label="$1" pattern="$2"
  shift 2
  local matches
  matches=$(grep -RInE "$pattern" "$@" 2>/dev/null | grep -v '/target/' || true)
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

echo ""
echo "========================================================================"
echo "  Phase 8 Staging Evidence Intake Preparation Validator"
echo "========================================================================"

require_file "Staging evidence intake checklist exists" "$CHECKLIST"
require_file "Staging cutover evidence template exists" "$STAGING"
require_file "Production cutover evidence template exists" "$PRODUCTION"
require_file "GO/NO-GO checklist exists" "$GONOGO"
require_file "Phase 8 runbook exists" "$RUNBOOK"
require_file "Phase 8 external evidence intake exists" "$INTAKE"
require_file "Completion index exists" "$COMPLETION"
require_file "Aggregate split gate exists" "$AGGREGATE"

echo ""
echo "-- Checklist structure --"
for section in \
  "Intake Rules" \
  "Required Auditable Reference Format" \
  "Missing Evidence Register" \
  "Per-Service Collection Checklist" \
  "Staging Decision Guardrail"; do
  require_text "Checklist section present: $section" "$CHECKLIST" "^## $section"
done

for term in \
  "EXTERNAL-GATED" \
  "Do not replace EXTERNAL-GATED" \
  "Do not record a staging GO decision" \
  "Phase 2 account or fulfillment staging artifacts" \
  "Keep all production fields" \
  "Owner" \
  "Source" \
  "Time window" \
  "Rollback note"; do
  require_text "Checklist guard present: $term" "$CHECKLIST" "$term"
done

for gate in \
  "DBA DDL and grants" \
  "Ops deploy, discovery, jobs, MQ, config" \
  "Engineering flow validation" \
  "Oncall metrics and observation" \
  "Product approval or exemption" \
  "Staging GO/NO-GO decision"; do
  require_text "Missing evidence register covers: $gate" "$CHECKLIST" "$gate"
done

for service in account-service fulfillment-service rebate-service strategy-service activity-service; do
  require_text "Per-service checklist covers $service" "$CHECKLIST" "$service"
done

echo ""
echo "-- Staging evidence remains blocked until real evidence exists --"
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
      pass "$field remains EXTERNAL-GATED"
    else
      fail "$field is missing or not EXTERNAL-GATED"
    fi
  done
done

require_text "GO/NO-GO decision result remains EXTERNAL-GATED" "$GONOGO" "GNG-D9.*EXTERNAL-GATED"
require_text "Staging checklist says review is blocked while evidence is gated" "$CHECKLIST" "review remains blocked while any row is EXTERNAL-GATED"

echo ""
echo "-- Production evidence stays gated --"
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
      fail "$field is missing or not EXTERNAL-GATED"
    fi
  done
done

echo ""
echo "-- Cross-links --"
for rel in \
  "docs/evidence/phase-8-staging-evidence-intake-checklist.md" \
  "scripts/validate-microservices-phase-8-staging-evidence-intake.sh"; do
  require_text "Runbook links $rel" "$RUNBOOK" "$rel"
  require_text "External evidence intake links $rel" "$INTAKE" "$rel"
  require_text "Completion index links $rel" "$COMPLETION" "$rel"
done
require_text "Aggregate gate includes staging intake validator" "$AGGREGATE" "validate-microservices-phase-8-staging-evidence-intake\\.sh"

echo ""
echo "-- No accidental completion claims or flag flips --"
for file in "$CHECKLIST" "$STAGING" "$PRODUCTION" "$GONOGO" "$INTAKE" "$COMPLETION"; do
  assert_absent "$(basename "$file") does not claim staging cutover complete" "$file" "staging cutover (is )?complete|staging evidence (is )?(complete|recorded)|evidence status:[[:space:]]*(complete|done|approved)"
  assert_absent "$(basename "$file") does not claim production cutover complete" "$file" "production cutover (is )?complete|production evidence (is )?(complete|recorded)|external cutover (is )?complete"
  assert_absent "$(basename "$file") does not contain final GO value" "$file" "Final (staging |production |GO/NO-GO )?(decision|result)[^|\\n]*\\|[[:space:]]*GO([[:space:]]|$)"
  assert_absent "$(basename "$file") does not claim DDL applied" "$file" "DDL (applied|complete)|applied DDL|schema applied"
  assert_absent "$(basename "$file") does not claim flags enabled" "$file" "([A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER|PRODUCTION)[A-Z0-9_]*[[:space:]]*[:=][[:space:]]*true|[a-z0-9.-]*(remote|outbox|cutover|production)[a-z0-9.-]*\\.enabled[[:space:]]*[:=][[:space:]]*true)"
done

RESOURCE_DIRS=("$REPO_ROOT"/big-market-*/src/main/resources)
COMPOSE_FILES=("$REPO_ROOT"/docker-compose*.yml "$REPO_ROOT"/docs/dev-ops/docker-compose*.yml)
assert_repo_absent \
  "No service remote/outbox/cutover flag defaults true" \
  '(remote-[a-z-]+|[a-z-]+-outbox|cutover).*enabled:[[:space:]]*(true|\$\{[A-Z0-9_]+:true\})' \
  "${RESOURCE_DIRS[@]}"
assert_repo_absent \
  "No env-backed REMOTE/OUTBOX/CUTOVER default true" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:true\}' \
  "${RESOURCE_DIRS[@]}"
assert_repo_absent \
  "No docker compose REMOTE/OUTBOX/CUTOVER default true" \
  '\$\{[A-Z0-9_]*(REMOTE|OUTBOX|CUTOVER)[A-Z0-9_]*:-true\}' \
  "${COMPOSE_FILES[@]}"

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - Phase 8 staging evidence intake is prepared; staging remains EXTERNAL-GATED"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
