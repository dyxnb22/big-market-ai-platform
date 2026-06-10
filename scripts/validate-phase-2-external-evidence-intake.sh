#!/usr/bin/env bash
# validate-phase-2-external-evidence-intake.sh — Phase 2 External Evidence Intake Validator
#
# Deterministic, repo-only validator for Phase 2 external evidence intake templates.
# Checks that all four intake templates exist and contain required sections.
# Does NOT connect to any external service, DB, Docker, or network.
#
# Checks:
#   1. All four intake template files exist
#   2. DBA intake: required sections for staging + production DDL evidence
#   3. Ops intake: required sections for staging + production XXL-Job evidence
#   4. Engineer intake: required sections for B17 + B23-C E2E evidence
#   5. Oncall intake: required sections for all five sign-off gates
#   6. B23-E cutover approval prerequisites present in each template
#   7. Dangerous flag safety language present in each template
#   8. Generated evidence remains local-only / gitignored (gitignore policy)
#
# Usage:
#   bash scripts/validate-phase-2-external-evidence-intake.sh
#
# Exit code:
#   0 — all checks PASS
#   1 — one or more checks FAIL

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAIL=0
PASS=0

pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

check_file_exists() {
  local label="$1" path="$2"
  if [ -f "$ROOT/$path" ]; then
    pass "$label: $path"
  else
    fail "$label: NOT FOUND — $path"
  fi
}

check_file_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file not found ($path)"
    return
  fi
  if grep -q "$pattern" "$ROOT/$path" 2>/dev/null; then
    pass "$label"
  else
    fail "$label: pattern not found in $path — $pattern"
  fi
}

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2 External Evidence Intake Validator"
echo "  Repo: $ROOT"
echo "════════════════════════════════════════════════════════════════════════════"

DBA_INTAKE="docs/evidence/intake-dba-ddl-evidence.md"
OPS_INTAKE="docs/evidence/intake-ops-xxl-job-evidence.md"
ENG_INTAKE="docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md"
OC_INTAKE="docs/evidence/intake-oncall-signoff-evidence.md"

# ── 1. Template file existence ─────────────────────────────────────────────────

echo ""
echo "── [1] Intake template file existence ───────────────────────────────────────"

check_file_exists "INTAKE-FILE-1: DBA DDL evidence intake" "$DBA_INTAKE"
check_file_exists "INTAKE-FILE-2: Ops XXL-Job evidence intake" "$OPS_INTAKE"
check_file_exists "INTAKE-FILE-3: Engineer B17/B23-C E2E evidence intake" "$ENG_INTAKE"
check_file_exists "INTAKE-FILE-4: Oncall sign-off evidence intake" "$OC_INTAKE"

# ── 2. DBA intake template — required sections ────────────────────────────────

echo ""
echo "── [2] DBA DDL evidence intake — required sections ─────────────────────────"

check_file_contains "DBA-INTAKE-1: staging DDL evidence section" \
  "$DBA_INTAKE" "DBA Staging DDL Evidence"

check_file_contains "DBA-INTAKE-2: production DDL evidence section" \
  "$DBA_INTAKE" "DBA Production DDL Evidence"

check_file_contains "DBA-INTAKE-3: uq_award_order_id verification reference" \
  "$DBA_INTAKE" "uq_award_order_id"

check_file_contains "DBA-INTAKE-4: uq_out_business_no verification reference" \
  "$DBA_INTAKE" "uq_out_business_no"

check_file_contains "DBA-INTAKE-5: DBA staging sign-off row" \
  "$DBA_INTAKE" "DBA Staging Sign-Off"

check_file_contains "DBA-INTAKE-6: DBA production sign-off row" \
  "$DBA_INTAKE" "DBA Production Sign-Off"

check_file_contains "DBA-INTAKE-7: B23-E cutover approval prerequisites section" \
  "$DBA_INTAKE" "B23-E Cutover Approval Prerequisites"

check_file_contains "DBA-INTAKE-8: NO-GO rules section" \
  "$DBA_INTAKE" "NO-GO"

check_file_contains "DBA-INTAKE-9: dangerous flag safety section" \
  "$DBA_INTAKE" "Dangerous Flag Safety"

check_file_contains "DBA-INTAKE-10: not-approval disclaimer" \
  "$DBA_INTAKE" "NOT AN APPROVAL"

check_file_contains "DBA-INTAKE-11: gitignored generated evidence note" \
  "$DBA_INTAKE" "generated/"

check_file_contains "DBA-INTAKE-12: big_market_01 reference" \
  "$DBA_INTAKE" "big_market_01"

check_file_contains "DBA-INTAKE-13: big_market_02 reference" \
  "$DBA_INTAKE" "big_market_02"

# ── 3. Ops intake template — required sections ────────────────────────────────

echo ""
echo "── [3] Ops XXL-Job evidence intake — required sections ─────────────────────"

check_file_contains "OPS-INTAKE-1: staging XXL-Job evidence section" \
  "$OPS_INTAKE" "Ops Staging XXL-Job Evidence"

check_file_contains "OPS-INTAKE-2: production XXL-Job evidence section" \
  "$OPS_INTAKE" "Ops Production XXL-Job Evidence"

check_file_contains "OPS-INTAKE-3: DispatchCreditAwardTaskJob_DB1 reference" \
  "$OPS_INTAKE" "DispatchCreditAwardTaskJob_DB1"

check_file_contains "OPS-INTAKE-4: DispatchCreditAwardTaskJob_DB2 reference" \
  "$OPS_INTAKE" "DispatchCreditAwardTaskJob_DB2"

check_file_contains "OPS-INTAKE-5: Ops staging sign-off row" \
  "$OPS_INTAKE" "Ops Staging Sign-Off"

check_file_contains "OPS-INTAKE-6: Ops production sign-off row" \
  "$OPS_INTAKE" "Ops Production Sign-Off"

check_file_contains "OPS-INTAKE-7: B23-E cutover approval prerequisites section" \
  "$OPS_INTAKE" "B23-E Cutover Approval Prerequisites"

check_file_contains "OPS-INTAKE-8: NO-GO rules section" \
  "$OPS_INTAKE" "NO-GO"

check_file_contains "OPS-INTAKE-9: dangerous flag safety section" \
  "$OPS_INTAKE" "Dangerous Flag Safety"

check_file_contains "OPS-INTAKE-10: job must stay in message-job-service" \
  "$OPS_INTAKE" "message-job-service"

check_file_contains "OPS-INTAKE-11: not-approval disclaimer" \
  "$OPS_INTAKE" "NOT AN APPROVAL"

check_file_contains "OPS-INTAKE-12: gitignored generated evidence note" \
  "$OPS_INTAKE" "generated/"

# ── 4. Engineer intake template — required sections ───────────────────────────

echo ""
echo "── [4] Engineer B17/B23-C E2E evidence intake — required sections ───────────"

check_file_contains "ENG-INTAKE-1: static pre-flight evidence section" \
  "$ENG_INTAKE" "Static Pre-flight Evidence"

check_file_contains "ENG-INTAKE-2: B17 staging E2E evidence section" \
  "$ENG_INTAKE" "B17 Staging E2E Evidence"

check_file_contains "ENG-INTAKE-3: B23-C staging evidence section" \
  "$ENG_INTAKE" "B23-C Staging Evidence"

check_file_contains "ENG-INTAKE-4: B23-E cutover approval prerequisites section" \
  "$ENG_INTAKE" "B23-E Cutover Approval Prerequisites"

check_file_contains "ENG-INTAKE-5: NO-GO rules section" \
  "$ENG_INTAKE" "NO-GO"

check_file_contains "ENG-INTAKE-6: dangerous flag safety section" \
  "$ENG_INTAKE" "Dangerous Flag Safety"

check_file_contains "ENG-INTAKE-7: double-credit NO-GO trigger" \
  "$ENG_INTAKE" "double-credit"

check_file_contains "ENG-INTAKE-8: idempotency evidence reference" \
  "$ENG_INTAKE" "idempotency"

check_file_contains "ENG-INTAKE-9: validate-fulfillment-service-phase-2-3 reference" \
  "$ENG_INTAKE" "validate-fulfillment-service-phase-2-3"

check_file_contains "ENG-INTAKE-10: not-approval disclaimer" \
  "$ENG_INTAKE" "NOT AN APPROVAL"

check_file_contains "ENG-INTAKE-11: gitignored generated evidence note" \
  "$ENG_INTAKE" "generated/"

check_file_contains "ENG-INTAKE-12: pre-flight sign-off row" \
  "$ENG_INTAKE" "Pre-flight Sign-Off"

# ── 5. Oncall intake template — required sections ─────────────────────────────

echo ""
echo "── [5] Oncall sign-off evidence intake — required sections ──────────────────"

check_file_contains "OC-INTAKE-1: B17 Phase K GO section" \
  "$OC_INTAKE" "B17 Phase K"

check_file_contains "OC-INTAKE-2: B23-C SE11 staging GO section" \
  "$OC_INTAKE" "SE11"

check_file_contains "OC-INTAKE-3: B23-D Phase E production gate section" \
  "$OC_INTAKE" "B23-D Phase E"

check_file_contains "OC-INTAKE-4: P4 written approval section" \
  "$OC_INTAKE" "P4 Written Approval"

check_file_contains "OC-INTAKE-5: B23-E final GO decision section" \
  "$OC_INTAKE" "B23-E Final GO"

check_file_contains "OC-INTAKE-6: oncall sign-off summary table" \
  "$OC_INTAKE" "Oncall Sign-Off Summary"

check_file_contains "OC-INTAKE-7: B23-E cutover approval prerequisites section" \
  "$OC_INTAKE" "B23-E Cutover Approval Prerequisites"

check_file_contains "OC-INTAKE-8: NO-GO rules section" \
  "$OC_INTAKE" "NO-GO"

check_file_contains "OC-INTAKE-9: dangerous flag safety section" \
  "$OC_INTAKE" "Dangerous Flag Safety"

check_file_contains "OC-INTAKE-10: production flag enable hard gate language" \
  "$OC_INTAKE" "written approval"

check_file_contains "OC-INTAKE-11: not-approval disclaimer" \
  "$OC_INTAKE" "NOT AN APPROVAL"

check_file_contains "OC-INTAKE-12: gitignored generated evidence note" \
  "$OC_INTAKE" "generated/"

check_file_contains "OC-INTAKE-13: OC1 reference (B17 Phase K)" \
  "$OC_INTAKE" "OC1"

check_file_contains "OC-INTAKE-14: OC2 reference (B23-C SE11)" \
  "$OC_INTAKE" "OC2"

check_file_contains "OC-INTAKE-15: OC3 reference (B23-D Phase E)" \
  "$OC_INTAKE" "OC3"

check_file_contains "OC-INTAKE-16: OC4 reference (P4 approval)" \
  "$OC_INTAKE" "OC4"

check_file_contains "OC-INTAKE-17: OC5 reference (B23-E final GO)" \
  "$OC_INTAKE" "OC5"

# ── 6. Gitignore policy ───────────────────────────────────────────────────────

echo ""
echo "── [6] Generated evidence gitignore policy ──────────────────────────────────"

GITIGNORE="$ROOT/.gitignore"
if [ ! -f "$GITIGNORE" ]; then
  fail "GITIGNORE-1: .gitignore not found"
else
  if grep -qF "docs/evidence/generated/" "$GITIGNORE" 2>/dev/null; then
    pass "GITIGNORE-1: docs/evidence/generated/ is listed in .gitignore"
  else
    fail "GITIGNORE-1: docs/evidence/generated/ is NOT in .gitignore — local snapshots would be committed"
  fi
fi

if git -C "$ROOT" ls-files --error-unmatch "docs/evidence/generated/" >/dev/null 2>&1; then
  fail "GITIGNORE-2: docs/evidence/generated/ is tracked by git — should be gitignored"
else
  pass "GITIGNORE-2: docs/evidence/generated/ is not tracked by git"
fi

# ── 7. Intake templates must not be shell scripts (safety check) ──────────────

echo ""
echo "── [7] Intake template format safety ────────────────────────────────────────"

for intake_file in "$DBA_INTAKE" "$OPS_INTAKE" "$ENG_INTAKE" "$OC_INTAKE"; do
  fname=$(basename "$intake_file")
  if [ -f "$ROOT/$intake_file" ]; then
    FIRST_LINE=$(head -1 "$ROOT/$intake_file" 2>/dev/null || echo "")
    if echo "$FIRST_LINE" | grep -q "^#!/"; then
      fail "INTAKE-FORMAT-$fname: file starts with shebang — should be markdown, not a script"
    else
      pass "INTAKE-FORMAT-$fname: file is a markdown template (not a shell script)"
    fi
  else
    fail "INTAKE-FORMAT-$fname: file not found"
  fi
done

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 2 EXTERNAL EVIDENCE INTAKE VALIDATOR — SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  Checks passed: $PASS / $TOTAL"
echo "  Checks failed: $FAIL"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo "  RESULT: ALL CHECKS PASS"
  echo ""
  echo "  All four evidence intake templates exist and contain required sections."
  echo "  B23-E cutover prerequisites are present in each template."
  echo "  Dangerous flag safety language is present in each template."
  echo "  Generated evidence is gitignored (local-only, never committed)."
  echo ""
  echo "  Remaining external blockers (require real staging/prod access):"
  echo "    - DBA fills intake-dba-ddl-evidence.md (DA1–DA14)"
  echo "    - Ops fills intake-ops-xxl-job-evidence.md (OA1–OA6)"
  echo "    - Engineer fills intake-engineer-b17-b23c-e2e-evidence.md (EA1–EA10)"
  echo "    - Oncall fills intake-oncall-signoff-evidence.md (OC1–OC5)"
  echo ""
  echo "  See: docs/evidence/phase-2-external-execution-pack.md"
  echo "  See: docs/evidence/phase-2-3-fulfillment-final-readiness-index.md"
  exit 0
else
  echo "  RESULT: $FAIL CHECK(S) FAILED"
  echo ""
  echo "  Fix all failures before proceeding with any staging or production action."
  exit 1
fi
