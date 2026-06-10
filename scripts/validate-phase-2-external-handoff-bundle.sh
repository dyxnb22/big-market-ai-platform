#!/usr/bin/env bash
# validate-phase-2-external-handoff-bundle.sh — Phase 2 Handoff Bundle Validator
#
# Validates the handoff bundle generator script (repo-only checks) and, if a
# bundle path is supplied, validates the contents of a generated bundle.
#
# Repo-only checks (always run):
#   1. Generator script exists at scripts/prepare-phase-2-external-handoff-bundle.sh
#   2. Generator does not invoke mysql / docker / curl / wget
#   3. Generator writes only under docs/evidence/generated/
#   4. docs/evidence/generated/ is listed in .gitignore
#   5. Role folder names DBA, Ops, Engineer, Oncall are referenced in generator
#   6. Required validator outputs are captured (intake, completion, consistency, execution-pack)
#   7. README.md and MANIFEST.md are produced by the generator
#   8. Current readiness summary (completion state) is included
#   9. No generated handoff files are tracked by git
#
# Optional bundle path checks (run when $1 is supplied):
#   10. Bundle directory exists
#   11. NOT-AN-APPROVAL.txt present
#   12. README.md present and contains gate state
#   13. MANIFEST.md present
#   14. git-state/ directory with expected files
#   15. validator-outputs/ directory with expected files
#   16. DBA/, Ops/, Engineer/, Oncall/ role folders with intake template + instructions
#   17. No bundle files tracked by git
#
# Usage:
#   bash scripts/validate-phase-2-external-handoff-bundle.sh
#   bash scripts/validate-phase-2-external-handoff-bundle.sh <bundle-path>
#
# Exit code:
#   0 — all checks PASS
#   1 — one or more checks FAIL

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUNDLE_PATH="${1:-}"
FAIL=0
PASS=0

pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $1"; }

check_file_exists() {
  local label="$1" path="$2"
  if [ -f "$path" ]; then
    pass "$label"
  else
    fail "$label — NOT FOUND: $path"
  fi
}

check_file_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$path" ]; then
    fail "$label — file not found: $path"
    return
  fi
  if grep -q "$pattern" "$path" 2>/dev/null; then
    pass "$label"
  else
    fail "$label — pattern not found in $(basename $path): $pattern"
  fi
}

check_file_not_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$path" ]; then
    fail "$label — file not found: $path"
    return
  fi
  if grep -Eq "$pattern" "$path" 2>/dev/null; then
    fail "$label — forbidden pattern found in $(basename $path): $pattern"
  else
    pass "$label"
  fi
}

check_dir_exists() {
  local label="$1" path="$2"
  if [ -d "$path" ]; then
    pass "$label"
  else
    fail "$label — directory NOT FOUND: $path"
  fi
}

GENERATOR="$ROOT/scripts/prepare-phase-2-external-handoff-bundle.sh"

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2 External Handoff Bundle Validator"
echo "  Repo: $ROOT"
if [ -n "$BUNDLE_PATH" ]; then
  echo "  Bundle: $BUNDLE_PATH"
else
  echo "  Bundle: (none supplied — repo-only checks only)"
fi
echo "════════════════════════════════════════════════════════════════════════════"

# ── 1. Generator script exists ────────────────────────────────────────────────

echo ""
echo "── [1] Generator script existence ──────────────────────────────────────────"

check_file_exists "GEN-EXIST-1: generator script exists" "$GENERATOR"

# ── 2. Generator does not invoke mysql / docker / curl / wget ─────────────────

echo ""
echo "── [2] Generator safety — no forbidden external commands ────────────────────"

if [ -f "$GENERATOR" ]; then
  check_file_not_contains "GEN-SAFE-1: generator does not invoke mysql" \
    "$GENERATOR" "^[^#]*[[:space:]]mysql[[:space:]]"

  check_file_not_contains "GEN-SAFE-2: generator does not invoke docker" \
    "$GENERATOR" "^[^#]*[[:space:]]docker[[:space:]]"

  check_file_not_contains "GEN-SAFE-3: generator does not invoke curl" \
    "$GENERATOR" "^[^#]*[[:space:]]curl[[:space:]]"

  check_file_not_contains "GEN-SAFE-4: generator does not invoke wget" \
    "$GENERATOR" "^[^#]*[[:space:]]wget[[:space:]]"
else
  fail "GEN-SAFE-1: generator not found — skipping safety checks"
  FAIL=$((FAIL + 2))
fi

# ── 3. Generator writes only under docs/evidence/generated/ ───────────────────

echo ""
echo "── [3] Generator output path ────────────────────────────────────────────────"

if [ -f "$GENERATOR" ]; then
  check_file_contains "GEN-PATH-1: generator output rooted at docs/evidence/generated/" \
    "$GENERATOR" "docs/evidence/generated/"

  check_file_contains "GEN-PATH-2: generator uses BUNDLE_DIR variable" \
    "$GENERATOR" "BUNDLE_DIR"
else
  fail "GEN-PATH-1: generator not found — skipping path checks"
fi

# ── 4. docs/evidence/generated/ is gitignored ────────────────────────────────

echo ""
echo "── [4] Gitignore policy ─────────────────────────────────────────────────────"

GITIGNORE="$ROOT/.gitignore"
if [ -f "$GITIGNORE" ] && grep -qF "docs/evidence/generated/" "$GITIGNORE" 2>/dev/null; then
  pass "GEN-GITIGNORE-1: docs/evidence/generated/ is listed in .gitignore"
else
  fail "GEN-GITIGNORE-1: docs/evidence/generated/ is NOT in .gitignore"
fi

if git -C "$ROOT" ls-files --error-unmatch "docs/evidence/generated/" >/dev/null 2>&1; then
  fail "GEN-GITIGNORE-2: docs/evidence/generated/ is tracked by git — must be gitignored"
else
  pass "GEN-GITIGNORE-2: docs/evidence/generated/ is not tracked by git"
fi

# ── 5. Role folder names are referenced in generator ─────────────────────────

echo ""
echo "── [5] Role folder names in generator ───────────────────────────────────────"

if [ -f "$GENERATOR" ]; then
  for role in DBA Ops Engineer Oncall; do
    if grep -q "\"$role/" "$GENERATOR" 2>/dev/null || grep -q "\$BUNDLE_DIR/$role" "$GENERATOR" 2>/dev/null || grep -q "mkdir.*$role" "$GENERATOR" 2>/dev/null; then
      pass "GEN-ROLE-$role: role folder $role/ referenced in generator"
    else
      fail "GEN-ROLE-$role: role folder $role/ NOT found in generator"
    fi
  done
else
  fail "GEN-ROLE-1: generator not found — skipping role folder checks"
fi

# ── 6. Required validator outputs are captured ────────────────────────────────

echo ""
echo "── [6] Required validator outputs captured by generator ─────────────────────"

if [ -f "$GENERATOR" ]; then
  check_file_contains "GEN-VAL-1: generator captures validate-intake output" \
    "$GENERATOR" "validate-intake"

  check_file_contains "GEN-VAL-2: generator captures validate-completion output" \
    "$GENERATOR" "validate-completion"

  check_file_contains "GEN-VAL-3: generator captures validate-consistency output" \
    "$GENERATOR" "validate-consistency"

  check_file_contains "GEN-VAL-4: generator captures validate-execution-pack output" \
    "$GENERATOR" "validate-execution-pack"
else
  fail "GEN-VAL-1: generator not found — skipping validator output checks"
fi

# ── 7. README.md and MANIFEST.md are produced by the generator ───────────────

echo ""
echo "── [7] README.md and MANIFEST.md produced ───────────────────────────────────"

if [ -f "$GENERATOR" ]; then
  check_file_contains "GEN-DOC-1: generator produces README.md" \
    "$GENERATOR" "README.md"

  check_file_contains "GEN-DOC-2: generator produces MANIFEST.md" \
    "$GENERATOR" "MANIFEST.md"

  check_file_contains "GEN-DOC-3: generator produces NOT-AN-APPROVAL.txt" \
    "$GENERATOR" "NOT-AN-APPROVAL"
else
  fail "GEN-DOC-1: generator not found — skipping doc checks"
fi

# ── 8. Current readiness summary is included ─────────────────────────────────

echo ""
echo "── [8] Current readiness summary included ───────────────────────────────────"

if [ -f "$GENERATOR" ]; then
  check_file_contains "GEN-GATE-1: generator derives DBA completion state" \
    "$GENERATOR" "DBA_STATE"

  check_file_contains "GEN-GATE-2: generator derives Ops completion state" \
    "$GENERATOR" "OPS_STATE"

  check_file_contains "GEN-GATE-3: generator derives Engineer completion state" \
    "$GENERATOR" "ENG_STATE"

  check_file_contains "GEN-GATE-4: generator derives Oncall completion state" \
    "$GENERATOR" "OC_STATE"

  check_file_contains "GEN-GATE-5: generator derives B23-E gate state" \
    "$GENERATOR" "B23E_GATE"
else
  fail "GEN-GATE-1: generator not found — skipping gate state checks"
fi

# ── 9. No generated handoff files tracked by git ─────────────────────────────

echo ""
echo "── [9] No generated handoff files tracked by git ───────────────────────────"

TRACKED_BUNDLES=$(git -C "$ROOT" ls-files "docs/evidence/generated/" 2>/dev/null | grep -c "phase2-handoff-bundle" || true)
if [ "$TRACKED_BUNDLES" -eq 0 ]; then
  pass "GEN-TRACK-1: no phase2-handoff-bundle files are tracked by git"
else
  fail "GEN-TRACK-1: $TRACKED_BUNDLES phase2-handoff-bundle file(s) are tracked by git — must be gitignored"
fi

# ── 10–17. Optional bundle path checks ───────────────────────────────────────

if [ -n "$BUNDLE_PATH" ]; then
  echo ""
  echo "── [10] Bundle path validation: $BUNDLE_PATH ────────────────────────────────"
  echo ""

  check_dir_exists "BUNDLE-1: bundle directory exists" "$BUNDLE_PATH"

  if [ -d "$BUNDLE_PATH" ]; then
    check_file_exists "BUNDLE-2: NOT-AN-APPROVAL.txt present" \
      "$BUNDLE_PATH/NOT-AN-APPROVAL.txt"

    check_file_exists "BUNDLE-3: README.md present" \
      "$BUNDLE_PATH/README.md"

    if [ -f "$BUNDLE_PATH/README.md" ]; then
      check_file_contains "BUNDLE-4: README.md contains gate state table" \
        "$BUNDLE_PATH/README.md" "B23-E overall gate"

      check_file_contains "BUNDLE-5: README.md contains execution order" \
        "$BUNDLE_PATH/README.md" "Strict Execution Order"

      check_file_contains "BUNDLE-6: README.md contains not-approval disclaimer" \
        "$BUNDLE_PATH/README.md" "NOT AN APPROVAL"

      check_file_contains "BUNDLE-7: README.md contains remaining blockers table" \
        "$BUNDLE_PATH/README.md" "Remaining External Blockers"
    fi

    check_file_exists "BUNDLE-8: MANIFEST.md present" \
      "$BUNDLE_PATH/MANIFEST.md"

    check_dir_exists "BUNDLE-9: git-state/ directory present" \
      "$BUNDLE_PATH/git-state"

    check_file_exists "BUNDLE-10: git-state/git-head.txt present" \
      "$BUNDLE_PATH/git-state/git-head.txt"

    check_file_exists "BUNDLE-11: git-state/git-status.txt present" \
      "$BUNDLE_PATH/git-state/git-status.txt"

    check_file_exists "BUNDLE-12: git-state/git-tags.txt present" \
      "$BUNDLE_PATH/git-state/git-tags.txt"

    check_dir_exists "BUNDLE-13: validator-outputs/ directory present" \
      "$BUNDLE_PATH/validator-outputs"

    check_file_exists "BUNDLE-14: validator-outputs/validate-intake.txt present" \
      "$BUNDLE_PATH/validator-outputs/validate-intake.txt"

    check_file_exists "BUNDLE-15: validator-outputs/validate-completion.txt present" \
      "$BUNDLE_PATH/validator-outputs/validate-completion.txt"

    check_file_exists "BUNDLE-16: validator-outputs/validate-consistency.txt present" \
      "$BUNDLE_PATH/validator-outputs/validate-consistency.txt"

    check_file_exists "BUNDLE-17: validator-outputs/validate-execution-pack.txt present" \
      "$BUNDLE_PATH/validator-outputs/validate-execution-pack.txt"

    for role in DBA Ops Engineer Oncall; do
      check_dir_exists "BUNDLE-ROLE-$role: $role/ folder present" \
        "$BUNDLE_PATH/$role"
      check_file_exists "BUNDLE-ROLE-$role-instructions: $role/instructions.md present" \
        "$BUNDLE_PATH/$role/instructions.md"
    done

    check_file_exists "BUNDLE-DBA-INTAKE: DBA/intake-dba-ddl-evidence.md present" \
      "$BUNDLE_PATH/DBA/intake-dba-ddl-evidence.md"

    check_file_exists "BUNDLE-OPS-INTAKE: Ops/intake-ops-xxl-job-evidence.md present" \
      "$BUNDLE_PATH/Ops/intake-ops-xxl-job-evidence.md"

    check_file_exists "BUNDLE-ENG-INTAKE: Engineer/intake-engineer-b17-b23c-e2e-evidence.md present" \
      "$BUNDLE_PATH/Engineer/intake-engineer-b17-b23c-e2e-evidence.md"

    check_file_exists "BUNDLE-OC-INTAKE: Oncall/intake-oncall-signoff-evidence.md present" \
      "$BUNDLE_PATH/Oncall/intake-oncall-signoff-evidence.md"

    # Check bundle is not tracked
    BUNDLE_REL="${BUNDLE_PATH#$ROOT/}"
    TRACKED_BUNDLE_FILES=$(git -C "$ROOT" ls-files "$BUNDLE_REL" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$TRACKED_BUNDLE_FILES" -eq 0 ]; then
      pass "BUNDLE-TRACK-1: no bundle files are tracked by git (gitignored correctly)"
    else
      fail "BUNDLE-TRACK-1: $TRACKED_BUNDLE_FILES bundle file(s) are tracked by git — bundle must be gitignored"
    fi
  else
    info "BUNDLE-*: skipping bundle content checks — directory not found"
  fi
else
  echo ""
  info "Optional bundle validation: no bundle path supplied."
  info "To validate a specific bundle, run:"
  info "  bash scripts/validate-phase-2-external-handoff-bundle.sh <bundle-path>"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 2 HANDOFF BUNDLE VALIDATOR — SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  Checks passed: $PASS / $TOTAL"
echo "  Checks failed: $FAIL"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo "  RESULT: ALL CHECKS PASS"
  echo ""
  echo "  The handoff bundle generator is repo-only safe:"
  echo "    - No forbidden commands (mysql/docker/curl/wget)"
  echo "    - Writes only to docs/evidence/generated/ (gitignored)"
  echo "    - Role folders (DBA/Ops/Engineer/Oncall) are produced"
  echo "    - All four validator outputs are captured"
  echo "    - README.md + MANIFEST.md + NOT-AN-APPROVAL.txt are produced"
  echo "    - Current readiness state is derived at bundle time"
  echo "    - Generated files are never committed to git"
  echo ""
  echo "  To generate the handoff bundle:"
  echo "    bash scripts/prepare-phase-2-external-handoff-bundle.sh"
  echo ""
  echo "  See: docs/evidence/phase-2-external-readiness-dashboard.md"
  exit 0
else
  echo "  RESULT: $FAIL CHECK(S) FAILED"
  echo ""
  echo "  Fix all failures before distributing the handoff bundle."
  exit 1
fi
