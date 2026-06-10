#!/usr/bin/env bash
# validate-phase-2-evidence-consistency.sh — Phase 2 Evidence Consistency Validator
#
# Deterministic, repo-only checker for Phase 2.2 and Phase 2.3 documentation and
# script coverage. No network, Docker, DB, staging, or production access required.
#
# Checks:
#   1. Key Phase 2.2 evidence docs exist
#   2. Key Phase 2.3 evidence docs exist
#   3. docs/evidence/generated/ is listed in .gitignore
#   4. Current key Phase 2 git tags exist locally
#   5. No dangerous flags hardcoded true in any config file
#   6. Final readiness and external execution docs cross-link correctly
#   7. Key validator scripts exist and are executable
#
# Usage:
#   bash scripts/validate-phase-2-evidence-consistency.sh
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

check_file_not_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file not found ($path)"
    return
  fi
  if grep -q "$pattern" "$ROOT/$path" 2>/dev/null; then
    fail "$label: forbidden pattern found in $path — $pattern"
  else
    pass "$label"
  fi
}

check_tag_exists() {
  local label="$1" tag="$2"
  if git -C "$ROOT" tag | grep -qxF "$tag"; then
    pass "$label: $tag"
  else
    fail "$label: tag not found locally — $tag"
  fi
}

check_executable() {
  local label="$1" path="$2"
  if [ -x "$ROOT/$path" ]; then
    pass "$label: $path"
  elif [ -f "$ROOT/$path" ]; then
    pass "$label: $path (not executable but exists — run via bash)"
  else
    fail "$label: NOT FOUND — $path"
  fi
}

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2 Evidence Consistency Validator"
echo "  Repo: $ROOT"
echo "════════════════════════════════════════════════════════════════════════════"

# ── 1. Phase 2.2 key evidence docs ────────────────────────────────────────────

echo ""
echo "── [1] Phase 2.2 key evidence docs ─────────────────────────────────────────"

check_file_exists "P22-DOC-1: B17 staging cutover template" \
  "docs/evidence/phase-2-2-b17-staging-cutover-template.md"

check_file_exists "P22-DOC-2: B17 dated staging evidence (2026-06-10)" \
  "docs/evidence/b17-staging-evidence-20260610.md"

check_file_exists "P22-DOC-3: B18 production promotion template" \
  "docs/evidence/phase-2-2-b18-production-promotion-template.md"

check_file_exists "P22-DOC-4: B21 evidence consistency hardening" \
  "docs/evidence/phase-2-2-b21-evidence-consistency-hardening.md"

# ── 2. Phase 2.3 key evidence docs ────────────────────────────────────────────

echo ""
echo "── [2] Phase 2.3 key evidence docs ─────────────────────────────────────────"

check_file_exists "P23-DOC-1: B23-C fulfillment staging readiness" \
  "docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md"

check_file_exists "P23-DOC-2: B23-D fulfillment production promotion gate" \
  "docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md"

check_file_exists "P23-DOC-3: B23-E fulfillment cutover execution" \
  "docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md"

check_file_exists "P23-DOC-4: Phase 2.3 fulfillment final readiness index" \
  "docs/evidence/phase-2-3-fulfillment-final-readiness-index.md"

check_file_exists "P23-DOC-5: Phase 2 external execution pack" \
  "docs/evidence/phase-2-external-execution-pack.md"

check_file_exists "P23-DOC-6: Phase 2 DBA checklist" \
  "docs/evidence/phase-2-dba-checklist.md"

check_file_exists "P23-DOC-7: Phase 2 Ops XXL-Job checklist" \
  "docs/evidence/phase-2-ops-xxl-job-checklist.md"

check_file_exists "P23-DOC-8: Phase 2.3 fulfillment-service design doc" \
  "docs/microservices-split-phase-2-3-fulfillment-service.md"

check_file_exists "P23-DOC-9: Phase 2.2 account-service design doc" \
  "docs/microservices-split-phase-2-2-account-service.md"

check_file_exists "P23-DOC-10: DBA DDL evidence intake template" \
  "docs/evidence/intake-dba-ddl-evidence.md"

check_file_exists "P23-DOC-11: Ops XXL-Job evidence intake template" \
  "docs/evidence/intake-ops-xxl-job-evidence.md"

check_file_exists "P23-DOC-12: Engineer B17/B23-C E2E evidence intake template" \
  "docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md"

check_file_exists "P23-DOC-13: Oncall sign-off evidence intake template" \
  "docs/evidence/intake-oncall-signoff-evidence.md"

# ── 3. docs/evidence/generated/ is gitignored ─────────────────────────────────

echo ""
echo "── [3] Generated evidence directory gitignore policy ───────────────────────"

GITIGNORE="$ROOT/.gitignore"
if [ ! -f "$GITIGNORE" ]; then
  fail "GITIGNORE-1: .gitignore not found at $GITIGNORE"
else
  if grep -qF "docs/evidence/generated/" "$GITIGNORE" 2>/dev/null; then
    pass "GITIGNORE-1: docs/evidence/generated/ is listed in .gitignore"
  else
    fail "GITIGNORE-1: docs/evidence/generated/ is NOT in .gitignore — local snapshots would be committed"
  fi
fi

# Confirm the generated/ dir is not tracked
if git -C "$ROOT" ls-files --error-unmatch "docs/evidence/generated/" >/dev/null 2>&1; then
  fail "GITIGNORE-2: docs/evidence/generated/ is tracked by git — should be gitignored"
else
  pass "GITIGNORE-2: docs/evidence/generated/ is not tracked by git"
fi

# ── 4. Key Phase 2 git tags exist locally ─────────────────────────────────────

echo ""
echo "── [4] Key Phase 2 git tags (local) ────────────────────────────────────────"

check_tag_exists "TAG-1: external execution pack tag" \
  "phase-2-external-execution-pack"

check_tag_exists "TAG-2: Phase 2.3 final readiness index tag" \
  "phase-2.3-final-readiness-index"

check_tag_exists "TAG-3: Phase 2.3-E cutover execution pack tag" \
  "phase-2.3-e-fulfillment-cutover-execution-pack"

check_tag_exists "TAG-4: Phase 2.3-D production gate tag" \
  "phase-2.3-d-fulfillment-production-gate"

check_tag_exists "TAG-5: Phase 2.3-C staging readiness tag" \
  "phase-2.3-c-fulfillment-staging-readiness"

check_tag_exists "TAG-6: Phase 2.2-B21 evidence consistency hardening tag" \
  "phase-2.2-b21-evidence-consistency-hardening"

check_tag_exists "TAG-7: Phase 2.2-B18 production promotion gate tag" \
  "phase-2.2-b18-production-promotion-gate"

check_tag_exists "TAG-8: Phase 2.2-B17 staging cutover execution package tag" \
  "phase-2.2-b17-staging-cutover-execution-package"

# ── 5. Dangerous flag scan ────────────────────────────────────────────────────

echo ""
echo "── [5] Dangerous flag scan (all config files) ───────────────────────────────"

FLAG_FAIL=0

OUTBOX_TRUE=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true"
    OUTBOX_TRUE=$((OUTBOX_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$OUTBOX_TRUE" -eq 0 ]; then
  pass "FLAG-1: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED not hardcoded true in any config"
else
  fail "FLAG-1: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED hardcoded true in $OUTBOX_TRUE file(s)"
  FLAG_FAIL=$((FLAG_FAIL + 1))
fi

REMOTE_AWARD_TRUE=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true"
    REMOTE_AWARD_TRUE=$((REMOTE_AWARD_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$REMOTE_AWARD_TRUE" -eq 0 ]; then
  pass "FLAG-2: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED not hardcoded true in any config"
else
  fail "FLAG-2: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED hardcoded true in $REMOTE_AWARD_TRUE file(s)"
  FLAG_FAIL=$((FLAG_FAIL + 1))
fi

QUOTA_TRUE=0
while IFS= read -r f; do
  if grep -qE "remote-quota-decrement.*enabled.*: true|REMOTE_QUOTA_DECREMENT.*:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: remote-quota-decrement.enabled: true"
    QUOTA_TRUE=$((QUOTA_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$QUOTA_TRUE" -eq 0 ]; then
  pass "FLAG-3: account.service.remote-quota-decrement.enabled not true in any config"
else
  fail "FLAG-3: account.service.remote-quota-decrement.enabled true in $QUOTA_TRUE file(s)"
  FLAG_FAIL=$((FLAG_FAIL + 1))
fi

# ── 6. Cross-link consistency ─────────────────────────────────────────────────

echo ""
echo "── [6] Cross-link consistency ───────────────────────────────────────────────"

READINESS="docs/evidence/phase-2-3-fulfillment-final-readiness-index.md"
EXTPACK="docs/evidence/phase-2-external-execution-pack.md"

# Final readiness index must mention the external execution pack
check_file_contains "XLINK-1: final readiness index links to external execution pack" \
  "$READINESS" "phase-2-external-execution-pack"

# Final readiness index must mention the consistency validator
check_file_contains "XLINK-2: final readiness index mentions consistency validator" \
  "$READINESS" "validate-phase-2-evidence-consistency"

# External execution pack must reference the Phase 2.3 suite validator
check_file_contains "XLINK-3: external execution pack references validate-fulfillment-service-phase-2-3.sh" \
  "$EXTPACK" "validate-fulfillment-service-phase-2-3.sh"

# External execution pack must mention docs/evidence/generated/ gitignore policy
check_file_contains "XLINK-4: external execution pack mentions generated/ gitignore policy" \
  "$EXTPACK" "gitignore\|generated/"

# Final readiness index must mention generated/ gitignore policy
check_file_contains "XLINK-5: final readiness index mentions generated/ gitignore policy" \
  "$READINESS" "gitignore\|generated/"

# B17 staging evidence must have the historical-baseline clarification note
check_file_contains "XLINK-6: B17 staging evidence notes historical baseline" \
  "docs/evidence/b17-staging-evidence-20260610.md" "historical baseline\|point-in-time\|Evidence Preservation"

# External execution pack must reference the intake templates
check_file_contains "XLINK-7: external execution pack links intake templates" \
  "$EXTPACK" "intake-dba-ddl-evidence\|intake-oncall-signoff-evidence"

# Final readiness index must mention the intake templates
check_file_contains "XLINK-8: final readiness index mentions intake templates" \
  "$READINESS" "intake-dba-ddl-evidence\|intake-oncall-signoff-evidence"

# External execution pack must reference the intake validator
check_file_contains "XLINK-9: external execution pack references intake validator" \
  "$EXTPACK" "validate-phase-2-external-evidence-intake"

# Final readiness index must mention the completion gate validator
check_file_contains "XLINK-10: final readiness index mentions completion gate validator" \
  "$READINESS" "validate-phase-2-external-evidence-completion"

# External execution pack must reference the completion gate validator
check_file_contains "XLINK-11: external execution pack references completion gate validator" \
  "$EXTPACK" "validate-phase-2-external-evidence-completion"

# External execution pack must reference the readiness dashboard
check_file_contains "XLINK-12: external execution pack references readiness dashboard" \
  "$EXTPACK" "phase-2-external-readiness-dashboard"

# ── 7. Key validator scripts exist ────────────────────────────────────────────

echo ""
echo "── [7] Key validator scripts ────────────────────────────────────────────────"

check_executable "SCRIPT-1: validate-fulfillment-service-phase-2-3.sh" \
  "scripts/validate-fulfillment-service-phase-2-3.sh"

check_executable "SCRIPT-2: validate-phase-2-external-execution-pack.sh" \
  "scripts/validate-phase-2-external-execution-pack.sh"

check_executable "SCRIPT-3: validate-fulfillment-service-b23-e-cutover-execution.sh" \
  "scripts/validate-fulfillment-service-b23-e-cutover-execution.sh"

check_executable "SCRIPT-4: collect-phase-2-external-evidence.sh" \
  "scripts/collect-phase-2-external-evidence.sh"

check_executable "SCRIPT-5: validate-b17-evidence-consistency.sh" \
  "scripts/validate-b17-evidence-consistency.sh"

check_executable "SCRIPT-6: validate-phase-2-evidence-consistency.sh (this script)" \
  "scripts/validate-phase-2-evidence-consistency.sh"

check_executable "SCRIPT-7: validate-phase-2-external-evidence-intake.sh" \
  "scripts/validate-phase-2-external-evidence-intake.sh"

check_executable "SCRIPT-8: validate-phase-2-external-evidence-completion.sh" \
  "scripts/validate-phase-2-external-evidence-completion.sh"

# ── 8. Run external evidence intake validator ─────────────────────────────────

echo ""
echo "── [8] External evidence intake validator ───────────────────────────────────"

INTAKE_SCRIPT="$ROOT/scripts/validate-phase-2-external-evidence-intake.sh"
if [ ! -f "$INTAKE_SCRIPT" ]; then
  fail "INTAKE-RUN-1: validate-phase-2-external-evidence-intake.sh not found"
else
  if bash "$INTAKE_SCRIPT" > /tmp/phase2-intake-out.txt 2>&1; then
    pass "INTAKE-RUN-1: validate-phase-2-external-evidence-intake.sh ALL CHECKS PASS"
  else
    fail "INTAKE-RUN-1: validate-phase-2-external-evidence-intake.sh FAILED"
    echo ""
    echo "  --- Intake validator output (last 20 lines) ---"
    tail -20 /tmp/phase2-intake-out.txt | sed 's/^/  /'
    echo "  ---"
  fi
fi

# ── 9. Run completion gate validator ─────────────────────────────────────────

echo ""
echo "── [9] Evidence completion gate validator ───────────────────────────────────"

COMPLETION_SCRIPT="$ROOT/scripts/validate-phase-2-external-evidence-completion.sh"
if [ ! -f "$COMPLETION_SCRIPT" ]; then
  fail "COMPLETION-RUN-1: validate-phase-2-external-evidence-completion.sh not found"
else
  if bash "$COMPLETION_SCRIPT" > /tmp/phase2-completion-out.txt 2>&1; then
    pass "COMPLETION-RUN-1: validate-phase-2-external-evidence-completion.sh GATE PASS"
  else
    fail "COMPLETION-RUN-1: validate-phase-2-external-evidence-completion.sh GATE FAIL"
    echo ""
    echo "  --- Completion gate output (last 20 lines) ---"
    tail -20 /tmp/phase2-completion-out.txt | sed 's/^/  /'
    echo "  ---"
  fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 2 EVIDENCE CONSISTENCY VALIDATOR — SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  Checks passed: $PASS / $TOTAL"
echo "  Checks failed: $FAIL"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo "  RESULT: ALL CHECKS PASS"
  echo ""
  echo "  Phase 2 documentation and script coverage are consistent."
  echo "  Generated evidence snapshots are gitignored (local-only)."
  echo "  All dangerous flags remain false by default."
  echo "  No staging or production traffic has been enabled by this repo-only batch."
  echo ""
  echo "  Remaining external blockers (require real staging/prod access):"
  echo "    - DBA applies DDL to staging and production big_market_01 / big_market_02"
  echo "    - Ops registers DispatchCreditAwardTaskJob_DB1/_DB2 in XXL-Job (staging + prod)"
  echo "    - Engineer runs B17 E2E + B23-C E2E in staging"
  echo "    - Oncall signs B17 Phase K + B23-C SE11 + B23-D Phase E + P4 approval"
  echo "    - Engineer executes B23-E cutover (S1-S8 staging + P1-P8 production)"
  echo ""
  echo "  See: docs/evidence/phase-2-3-fulfillment-final-readiness-index.md"
  echo "  See: docs/evidence/phase-2-external-execution-pack.md"
  exit 0
else
  echo "  RESULT: $FAIL CHECK(S) FAILED"
  echo ""
  echo "  Fix all failures before proceeding with any staging or production action."
  exit 1
fi
