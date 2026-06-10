#!/usr/bin/env bash
# validate-phase-2-external-evidence-completion.sh — Phase 2 Completion Gate Validator
#
# Deterministic, repo-only validator for Phase 2 external evidence completion semantics.
# Reads the "## Completion Status" table in each of the four intake templates and reports:
#
#   TEMPLATE_READY — all fields are TODO/PENDING (structure OK; external evidence not yet collected)
#   PARTIAL        — some fields filled (PASS/GO), some still TODO/PENDING
#   COMPLETE       — all required fields are PASS or GO (evidence and approvals filled)
#   NO_GO          — at least one field carries an explicit FAIL or NO-GO marker
#   MALFORMED      — Completion Status section missing or table has no parseable rows
#
# B23-E cutover gate:
#   Opens only when DBA + Ops + Engineer + Oncall are all COMPLETE.
#   Remains blocked (reported, not a failure) for TEMPLATE_READY or PARTIAL state.
#
# Exit code:
#   0 — no NO-GO or MALFORMED template detected (gate may be blocked but is not in error)
#   1 — at least one template is NO_GO or MALFORMED, or critical safety check failed
#
# Does NOT connect to any external service, DB, Docker, or network.
#
# Usage:
#   bash scripts/validate-phase-2-external-evidence-completion.sh

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FAIL=0
PASS=0
GATE_FAIL=0

pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $1"; }

# completion_state FILE
# Reads the "## Completion Status" table in FILE and prints one of:
#   TEMPLATE_READY | PARTIAL | COMPLETE | NO_GO | MALFORMED
completion_state() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "MALFORMED"
    return
  fi
  if ! grep -q "^## Completion Status" "$file" 2>/dev/null; then
    echo "MALFORMED"
    return
  fi

  # Extract text between "## Completion Status" and the next "## " heading (or EOF)
  local section
  section=$(awk '
    /^## Completion Status/ { in_s=1; next }
    in_s && /^## / { in_s=0 }
    in_s { print }
  ' "$file" 2>/dev/null || true)

  # Count each status value in the completion table rows
  local todo_count pending_count pass_count go_count fail_count nogo_count total_count
  todo_count=$(printf '%s\n' "$section" | grep -c '| TODO |' || true)
  pending_count=$(printf '%s\n' "$section" | grep -c '| PENDING |' || true)
  pass_count=$(printf '%s\n' "$section" | grep -c '| PASS |' || true)
  # Match "| GO |" but not "| NO-GO |" — grep for literal " | GO |" avoids the NO-GO substring
  go_count=$(printf '%s\n' "$section" | grep -c ' | GO |' || true)
  fail_count=$(printf '%s\n' "$section" | grep -c '| FAIL |' || true)
  nogo_count=$(printf '%s\n' "$section" | grep -c '| NO-GO |' || true)
  total_count=$((todo_count + pending_count + pass_count + go_count + fail_count + nogo_count))

  if [ "$total_count" -eq 0 ]; then
    echo "MALFORMED"
    return
  fi

  # Explicit NO-GO or FAIL → NO_GO (hard stop)
  if [ "$nogo_count" -gt 0 ] || [ "$fail_count" -gt 0 ]; then
    echo "NO_GO"
    return
  fi

  local incomplete=$((todo_count + pending_count))
  local complete=$((pass_count + go_count))

  if [ "$incomplete" -eq "$total_count" ]; then
    echo "TEMPLATE_READY"
  elif [ "$complete" -eq "$total_count" ]; then
    echo "COMPLETE"
  else
    echo "PARTIAL"
  fi
}

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2 External Evidence Completion Gate Validator"
echo "  Repo: $ROOT"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  Reads '## Completion Status' table in each intake template."
echo "  Reports: TEMPLATE_READY | PARTIAL | COMPLETE | NO_GO | MALFORMED"
echo "  Fails (exit 1) only on: NO_GO, MALFORMED, or safety check failure."
echo "  TEMPLATE_READY and PARTIAL are expected — they do not cause failure."
echo ""

DBA_INTAKE="docs/evidence/intake-dba-ddl-evidence.md"
OPS_INTAKE="docs/evidence/intake-ops-xxl-job-evidence.md"
ENG_INTAKE="docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md"
OC_INTAKE="docs/evidence/intake-oncall-signoff-evidence.md"

# ── 1. Template file existence ─────────────────────────────────────────────────

echo "── [1] Intake template existence ────────────────────────────────────────────"
echo ""

for f in "$DBA_INTAKE" "$OPS_INTAKE" "$ENG_INTAKE" "$OC_INTAKE"; do
  if [ -f "$ROOT/$f" ]; then
    pass "EXIST: $(basename $f)"
  else
    fail "EXIST: $(basename $f) — NOT FOUND at $ROOT/$f"
    GATE_FAIL=$((GATE_FAIL + 1))
  fi
done

# ── 2. Completion Status section present ──────────────────────────────────────

echo ""
echo "── [2] Completion Status section present ────────────────────────────────────"
echo ""

for f in "$DBA_INTAKE" "$OPS_INTAKE" "$ENG_INTAKE" "$OC_INTAKE"; do
  fname=$(basename "$f")
  if grep -q "^## Completion Status" "$ROOT/$f" 2>/dev/null; then
    pass "COMPLETION-SECTION: $fname"
  else
    fail "COMPLETION-SECTION: $fname — '## Completion Status' section not found (template not updated)"
    GATE_FAIL=$((GATE_FAIL + 1))
  fi
done

# ── 3. Per-template completion state ──────────────────────────────────────────

echo ""
echo "── [3] Per-template completion state ────────────────────────────────────────"
echo ""

DBA_STATE=$(completion_state "$ROOT/$DBA_INTAKE")
OPS_STATE=$(completion_state "$ROOT/$OPS_INTAKE")
ENG_STATE=$(completion_state "$ROOT/$ENG_INTAKE")
OC_STATE=$(completion_state "$ROOT/$OC_INTAKE")

report_state() {
  local role="$1" state="$2"
  case "$state" in
    COMPLETE)
      pass "COMPLETION[$role]: COMPLETE — all evidence fields filled and signed"
      ;;
    TEMPLATE_READY)
      pass "COMPLETION[$role]: TEMPLATE_READY — structure OK; external evidence not yet collected (expected state)"
      ;;
    PARTIAL)
      info "COMPLETION[$role]: PARTIAL — some evidence fields filled, some still pending"
      ;;
    NO_GO)
      fail "COMPLETION[$role]: NO_GO — explicit FAIL or NO-GO marker detected; escalate to oncall lead"
      GATE_FAIL=$((GATE_FAIL + 1))
      ;;
    MALFORMED)
      fail "COMPLETION[$role]: MALFORMED — Completion Status section missing or has no parseable rows"
      GATE_FAIL=$((GATE_FAIL + 1))
      ;;
    *)
      fail "COMPLETION[$role]: UNKNOWN state '$state'"
      GATE_FAIL=$((GATE_FAIL + 1))
      ;;
  esac
}

report_state "DBA"      "$DBA_STATE"
report_state "Ops"      "$OPS_STATE"
report_state "Engineer" "$ENG_STATE"
report_state "Oncall"   "$OC_STATE"

# ── 4. Placeholder field count (informational) ────────────────────────────────

echo ""
echo "── [4] Placeholder field count (informational) ──────────────────────────────"
echo ""

for f in "$DBA_INTAKE" "$OPS_INTAKE" "$ENG_INTAKE" "$OC_INTAKE"; do
  fname=$(basename "$f")
  if [ -f "$ROOT/$f" ]; then
    ph_count=$(grep -c ' ___ ' "$ROOT/$f" 2>/dev/null || true)
    if [ "$ph_count" -gt 0 ]; then
      info "PLACEHOLDER: $fname — $ph_count field(s) still contain '___' (awaiting external evidence fill)"
    else
      pass "PLACEHOLDER: $fname — no '___' placeholder fields found (evidence appears filled)"
    fi
  fi
done

# ── 5. Dangerous flag safety language present ─────────────────────────────────

echo ""
echo "── [5] Dangerous flag safety language ───────────────────────────────────────"
echo ""

for f in "$DBA_INTAKE" "$OPS_INTAKE" "$ENG_INTAKE" "$OC_INTAKE"; do
  fname=$(basename "$f")
  if grep -q "Dangerous Flag Safety" "$ROOT/$f" 2>/dev/null; then
    pass "FLAG-SAFETY: $fname"
  else
    fail "FLAG-SAFETY: $fname — 'Dangerous Flag Safety' section missing"
    GATE_FAIL=$((GATE_FAIL + 1))
  fi
done

# ── 6. B23-E cutover gate assessment ──────────────────────────────────────────

echo ""
echo "── [6] B23-E cutover gate ───────────────────────────────────────────────────"
echo ""

B23E_READY=true
for role_state in "DBA:$DBA_STATE" "Ops:$OPS_STATE" "Engineer:$ENG_STATE" "Oncall:$OC_STATE"; do
  role="${role_state%%:*}"
  state="${role_state##*:}"
  if [ "$state" != "COMPLETE" ]; then
    B23E_READY=false
    info "B23-E[$role]: NOT READY — state: $state"
  fi
done

if [ "$B23E_READY" = true ]; then
  pass "B23-E-GATE: ALL FOUR ROLES COMPLETE — B23-E cutover prerequisites met"
else
  pass "B23-E-GATE: BLOCKED — external evidence pending (expected before real-world execution)"
fi

# ── 7. Gitignore policy ───────────────────────────────────────────────────────

echo ""
echo "── [7] Generated evidence gitignore policy ──────────────────────────────────"
echo ""

if grep -qF "docs/evidence/generated/" "$ROOT/.gitignore" 2>/dev/null; then
  pass "GITIGNORE: docs/evidence/generated/ is listed in .gitignore"
else
  fail "GITIGNORE: docs/evidence/generated/ NOT in .gitignore"
  GATE_FAIL=$((GATE_FAIL + 1))
fi

if git -C "$ROOT" ls-files --error-unmatch "docs/evidence/generated/" >/dev/null 2>&1; then
  fail "GITIGNORE: docs/evidence/generated/ is tracked by git — must be gitignored"
  GATE_FAIL=$((GATE_FAIL + 1))
else
  pass "GITIGNORE: docs/evidence/generated/ is not tracked by git"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 2 COMPLETION GATE — SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  Template completion states:"
echo "    DBA      : $DBA_STATE"
echo "    Ops      : $OPS_STATE"
echo "    Engineer : $ENG_STATE"
echo "    Oncall   : $OC_STATE"
echo ""
if [ "$B23E_READY" = true ]; then
  echo "  B23-E cutover gate : READY — all four roles COMPLETE"
else
  echo "  B23-E cutover gate : BLOCKED — external evidence pending"
fi
echo ""
echo "  Checks passed : $PASS / $TOTAL"
echo "  Checks failed : $FAIL"
echo ""

if [ "$GATE_FAIL" -gt 0 ]; then
  echo "  RESULT: GATE FAIL — $GATE_FAIL critical issue(s) detected"
  echo ""
  echo "  Investigate all [FAIL] items above before any staging or production action."
  echo "  NO-GO markers and malformed templates are hard blockers."
  exit 1
fi

echo "  RESULT: GATE PASS — no NO-GO, malformed, or critical failures"
echo ""
echo "  Remaining external blockers (require real staging/prod access):"
echo "    DBA:      fill DA1–DA9 (staging), DA10–DA14 (production) in intake-dba-ddl-evidence.md"
echo "              then update 'Staging DDL Gate', 'Production DDL Gate', sign-off rows to PASS"
echo "    Ops:      fill OA1–OA4 (staging), OA5–OA6 (production) in intake-ops-xxl-job-evidence.md"
echo "              then update 'Staging Handler Registration', 'Production Handler Registration' to PASS"
echo "    Engineer: fill EA1–EA10 in intake-engineer-b17-b23c-e2e-evidence.md"
echo "              then update 'Pre-flight Gate', 'B17 E2E Gate', 'B23-C E2E Gate' to PASS"
echo "    Oncall:   fill OC1–OC5 in intake-oncall-signoff-evidence.md"
echo "              then update each gate decision row to GO"
echo "    All four roles COMPLETE → B23-E cutover gate opens"
echo ""
echo "  Validators to run:"
echo "    bash scripts/validate-phase-2-external-evidence-intake.sh"
echo "    bash scripts/validate-phase-2-external-evidence-completion.sh"
echo "    bash scripts/validate-phase-2-evidence-consistency.sh"
echo "    bash scripts/validate-phase-2-external-execution-pack.sh"
echo ""
echo "  See: docs/evidence/phase-2-external-readiness-dashboard.md"
echo "  See: docs/evidence/phase-2-external-execution-pack.md"
exit 0
