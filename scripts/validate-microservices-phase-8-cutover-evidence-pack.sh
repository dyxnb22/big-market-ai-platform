#!/usr/bin/env bash
# Repo-only validator for the Phase 8 cutover evidence execution pack.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAGING="$REPO_ROOT/docs/evidence/phase-8-staging-cutover-evidence-template.md"
PRODUCTION="$REPO_ROOT/docs/evidence/phase-8-production-cutover-evidence-template.md"
CHECKLIST="$REPO_ROOT/docs/evidence/phase-8-go-no-go-checklist.md"
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

echo ""
echo "========================================================================"
echo "  Phase 8 Cutover Evidence Execution Pack Validator"
echo "========================================================================"

require_file "Staging cutover evidence template exists" "$STAGING"
require_file "Production cutover evidence template exists" "$PRODUCTION"
require_file "GO/NO-GO checklist exists" "$CHECKLIST"
require_file "Phase 8 runbook exists" "$RUNBOOK"
require_file "Phase 8 external evidence intake exists" "$INTAKE"
require_file "Completion index exists" "$COMPLETION"
require_file "Aggregate split gate exists" "$AGGREGATE"

echo ""
echo "-- Evidence template structure --"
for pair in \
  "$STAGING:STG:Staging" \
  "$PRODUCTION:PROD:Production"; do
  file="${pair%%:*}"
  rest="${pair#*:}"
  prefix="${rest%%:*}"
  label="${rest#*:}"

  for section in \
    "Metadata" \
    "DBA DDL And Grants Evidence" \
    "Ops Deploy, Discovery, And Job Evidence" \
    "Metrics, Logs, And Oncall Observation" \
    "Product And"; do
    require_text "$label section present: $section" "$file" "^## ${prefix}-[0-9] .*$section"
  done

  for term in \
    "credit_award_task" \
    "raffle_quota_decrement_ledger" \
    "rebate_task_outbox_000..003" \
    "credit_trade_task_outbox_000..003" \
    "award_dispatch_task_outbox_000..003" \
    "DB grants" \
    "Nacos/Dubbo provider" \
    "XXL-Job" \
    "MQ" \
    "Rollback" \
    "GO/NO-GO"; do
    require_text "$label template contains: $term" "$file" "$term"
  done

  for service in account fulfillment rebate strategy activity; do
    require_text "$label template covers $service evidence" "$file" "$service"
  done

  require_text "$label final decision remains EXTERNAL-GATED" "$file" "${prefix}-6\\.[0-9].*Final .*decision.*EXTERNAL-GATED"
done

echo ""
echo "-- Checklist structure --"
for field in GNG-1 GNG-6 GNG-7 GNG-12 GNG-D9; do
  require_text "Checklist contains $field" "$CHECKLIST" "$field"
done
for term in \
  "Hard NO-GO Conditions" \
  "Any required field remains EXTERNAL-GATED" \
  "Staging evidence is missing before a production review" \
  "7-day and 30-day cleanup gates"; do
  require_text "Checklist contains guard: $term" "$CHECKLIST" "$term"
done

echo ""
echo "-- Cross-links --"
for rel in \
  "docs/evidence/phase-8-staging-cutover-evidence-template.md" \
  "docs/evidence/phase-8-production-cutover-evidence-template.md" \
  "docs/evidence/phase-8-go-no-go-checklist.md" \
  "scripts/validate-microservices-phase-8-cutover-evidence-pack.sh"; do
  require_text "Runbook links $rel" "$RUNBOOK" "$rel"
  require_text "Completion index links $rel" "$COMPLETION" "$rel"
done
for field in STG-1.1 STG-2.6 STG-3.6 STG-6.6 PROD-0.2 PROD-3.6 PROD-6.6 GNG-D9; do
  require_text "Intake maps evidence field $field" "$INTAKE" "$field"
done
require_text "Aggregate gate includes evidence pack validator" "$AGGREGATE" "validate-microservices-phase-8-cutover-evidence-pack\\.sh"

echo ""
echo "-- External gates remain closed --"
for file in "$STAGING" "$PRODUCTION" "$CHECKLIST"; do
  require_text "$(basename "$file") keeps EXTERNAL-GATED placeholders" "$file" "EXTERNAL-GATED"
  assert_absent "$(basename "$file") does not mark evidence complete" "$file" "evidence status:[[:space:]]*(complete|done|approved)|cutover (is )?complete|production cutover (is )?complete|external cutover (is )?complete"
  assert_absent "$(basename "$file") does not contain final GO claim" "$file" "Final (staging |production |GO/NO-GO )?(decision|result)[^|\\n]*\\|[[:space:]]*GO([[:space:]]|$)"
  assert_absent "$(basename "$file") does not claim DDL applied" "$file" "DDL (applied|complete)|applied DDL|schema applied"
  assert_absent "$(basename "$file") does not claim flags enabled" "$file" "(remote|outbox|production|cutover).*enabled[[:space:]]*[:=][[:space:]]*true"
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
  echo "RESULT: ALL CHECKS PASSED - Phase 8 cutover evidence execution pack is present and external-gated"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
