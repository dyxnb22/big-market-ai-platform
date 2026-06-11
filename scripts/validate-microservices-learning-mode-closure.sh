#!/usr/bin/env bash
# Local learning-mode closure validator for the microservices split.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLOSURE="$REPO_ROOT/docs/microservices-learning-mode-closure.md"
EVIDENCE="$REPO_ROOT/docs/evidence/phase-8-local-learning-cutover-evidence.md"
COMPLETION="$REPO_ROOT/docs/microservices-split-completion-index.md"
ROADMAP="$REPO_ROOT/docs/microservices-next-execution-roadmap.md"
RUNBOOK="$REPO_ROOT/docs/microservices-phase-8-cutover-runbook.md"
INTAKE="$REPO_ROOT/docs/microservices-phase-8-external-evidence-intake.md"
GONOGO="$REPO_ROOT/docs/evidence/phase-8-go-no-go-checklist.md"
ARCHIVE_INDEX="$REPO_ROOT/docs/archive/microservices-historical-docs-index.md"
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

echo ""
echo "========================================================================"
echo "  Microservices Learning-Mode Closure Validator"
echo "========================================================================"

for item in \
  "Learning closure:$CLOSURE" \
  "Local evidence:$EVIDENCE" \
  "Completion index:$COMPLETION" \
  "Roadmap:$ROADMAP" \
  "Phase 8 runbook:$RUNBOOK" \
  "External evidence intake:$INTAKE" \
  "GO/NO-GO checklist:$GONOGO" \
  "Archive index:$ARCHIVE_INDEX" \
  "Aggregate gate:$AGGREGATE"; do
  label="${item%%:*}"
  file="${item#*:}"
  require_file "$label exists" "$file"
done

echo ""
echo "-- Required learning-mode markers --"
for file in "$CLOSURE" "$EVIDENCE" "$COMPLETION" "$ROADMAP" "$RUNBOOK" "$INTAKE" "$GONOGO"; do
  require_text "$(basename "$file") contains LOCAL-LEARNING-EVIDENCE" "$file" "LOCAL-LEARNING-EVIDENCE"
  require_text "$(basename "$file") contains SIMULATED-CUTOVER-EVIDENCE" "$file" "SIMULATED-CUTOVER-EVIDENCE"
  require_text "$(basename "$file") contains LEARNING-MODE-COMPLETE" "$file" "LEARNING-MODE-COMPLETE"
done

echo ""
echo "-- Evidence command coverage --"
for command in \
  "mvn clean package -DskipTests" \
  "./scripts/validate-microservices-split-all-gates.sh" \
  "./scripts/validate-microservices-master-plan.sh" \
  "./scripts/validate-microservices-production-flag-matrix.sh" \
  "./scripts/validate-microservices-legacy-cleanup-readiness.sh" \
  "./scripts/validate-microservices-post-cutover-cleanup-gates.sh" \
  "./scripts/validate-production-ddl.sh" \
  "docker compose ps" \
  "./scripts/validate-microservices-stack.sh --skip-build" \
  "./scripts/smoke-test-phase-1.sh"; do
  require_text "Local evidence records command: $command" "$EVIDENCE" "$(printf '%s' "$command" | sed 's/[.[\*^$()+?{}|]/\\&/g')"
done

echo ""
echo "-- Production-safety language --"
for file in "$CLOSURE" "$EVIDENCE" "$COMPLETION" "$ROADMAP" "$RUNBOOK" "$INTAKE" "$GONOGO"; do
  require_text "$(basename "$file") says real production is not proven/applicable" "$file" "production readiness.*(not applicable|not proven)|not prove staging or production readiness|does not claim real.*production"
  require_text "$(basename "$file") preserves external-gated language" "$file" "EXTERNAL-GATED|external-gated|external gates"
done

echo ""
echo "-- Aggregate wiring and cleanup index --"
require_text "Aggregate includes learning-mode closure validator" "$AGGREGATE" "validate-microservices-learning-mode-closure\\.sh"
require_text "Archive index explains no physical moves" "$ARCHIVE_INDEX" "archived by reference"
require_text "Archive index lists active source of truth" "$ARCHIVE_INDEX" "active source of truth"

echo ""
echo "-- No accidental real-production claims in learning docs --"
for file in "$CLOSURE" "$EVIDENCE"; do
  assert_absent "$(basename "$file") does not claim real production readiness" "$file" "real production readiness[^\\n]*(complete|ready|proven)|production readiness[^\\n]*(complete|ready|proven)"
  assert_absent "$(basename "$file") does not claim real approvals" "$file" "real (DBA|Ops|Engineering|Oncall|Product) (approval|evidence)[^\\n]*(provided|complete|approved)"
done

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - learning-mode closure is locally evidenced and production-safe"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
