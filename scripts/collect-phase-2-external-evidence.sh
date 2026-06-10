#!/usr/bin/env bash
# collect-phase-2-external-evidence.sh — Phase 2 External Evidence Collector
#
# Collects local repo evidence into a timestamped directory under
# docs/evidence/generated/. Safe and deterministic.
#
# Safety constraints:
#   - No DB, Docker, staging, or production access at any time
#   - No network calls
#   - No secrets written to output files
#   - All reads are from the local git repository only
#
# Usage:
#   bash scripts/collect-phase-2-external-evidence.sh
#
# Output:
#   docs/evidence/generated/phase2-evidence-<TIMESTAMP>/
#     git-head.txt
#     git-tags.txt
#     git-status.txt
#     validate-phase-2-3-suite.txt
#     dangerous-flag-scan.txt
#     doc-manifest.txt
#     sql-manifest.txt
#     summary.txt

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d%H%M%S)"
OUT_DIR="$ROOT/docs/evidence/generated/phase2-evidence-$TIMESTAMP"

echo "============================================================"
echo "  Phase 2 External Evidence Collector"
echo "  Timestamp: $TIMESTAMP"
echo "  Output: $OUT_DIR"
echo "============================================================"
echo ""

mkdir -p "$OUT_DIR"

FAIL=0

# ── 1. Git HEAD and tags ──────────────────────────────────────────────────────

echo "[1/8] Collecting git HEAD and tags..."
{
  echo "# git HEAD"
  git -C "$ROOT" rev-parse HEAD 2>&1
  echo ""
  echo "# git log --oneline -10"
  git -C "$ROOT" log --oneline -10 2>&1
} > "$OUT_DIR/git-head.txt"

{
  echo "# Phase 2 git tags"
  git -C "$ROOT" tag | grep -E "phase-2" | sort 2>&1 || true
} > "$OUT_DIR/git-tags.txt"

echo "[PASS] git-head.txt and git-tags.txt written"

# ── 2. Git status ─────────────────────────────────────────────────────────────

echo "[2/8] Collecting git status..."
{
  echo "# git status --short"
  git -C "$ROOT" status --short 2>&1
  echo ""
  echo "# git diff --check"
  git -C "$ROOT" diff --check 2>&1 || true
} > "$OUT_DIR/git-status.txt"

echo "[PASS] git-status.txt written"

# ── 2b. Run validate-phase-2-external-evidence-intake.sh ────────────────────

echo "[2b/8] Running external evidence intake validator..."
INTAKE_SCRIPT="$ROOT/scripts/validate-phase-2-external-evidence-intake.sh"
if [ ! -f "$INTAKE_SCRIPT" ]; then
  echo "[FAIL] validate-phase-2-external-evidence-intake.sh not found"
  echo "INTAKE VALIDATOR NOT FOUND" > "$OUT_DIR/validate-intake.txt"
  FAIL=$((FAIL + 1))
else
  {
    echo "# validate-phase-2-external-evidence-intake.sh output"
    echo "# Run at: $(date)"
    echo ""
    bash "$INTAKE_SCRIPT" 2>&1
  } > "$OUT_DIR/validate-intake.txt"
  if grep -q "RESULT: ALL CHECKS PASS" "$OUT_DIR/validate-intake.txt"; then
    echo "[PASS] External evidence intake validator: ALL CHECKS PASS"
  else
    echo "[FAIL] External evidence intake validator: one or more checks failed — see validate-intake.txt"
    FAIL=$((FAIL + 1))
  fi
fi

# ── 3. Run validate-fulfillment-service-phase-2-3.sh ─────────────────────────

echo "[3/8] Running Phase 2.3 suite validator..."
VALIDATOR_SCRIPT="$ROOT/scripts/validate-fulfillment-service-phase-2-3.sh"
if [ ! -f "$VALIDATOR_SCRIPT" ]; then
  echo "[FAIL] validate-fulfillment-service-phase-2-3.sh not found at $VALIDATOR_SCRIPT"
  echo "VALIDATOR NOT FOUND" > "$OUT_DIR/validate-phase-2-3-suite.txt"
  FAIL=$((FAIL + 1))
else
  {
    echo "# validate-fulfillment-service-phase-2-3.sh output"
    echo "# Run at: $(date)"
    echo ""
    bash "$VALIDATOR_SCRIPT" 2>&1
  } > "$OUT_DIR/validate-phase-2-3-suite.txt"
  if grep -q "RESULT: ALL SUITES PASS" "$OUT_DIR/validate-phase-2-3-suite.txt"; then
    echo "[PASS] Phase 2.3 suite: ALL SUITES PASS"
  else
    echo "[FAIL] Phase 2.3 suite: one or more suites failed — see validate-phase-2-3-suite.txt"
    FAIL=$((FAIL + 1))
  fi
fi

# ── 4. Dangerous flag scan ────────────────────────────────────────────────────

echo "[4/8] Scanning for dangerous flags..."
{
  echo "# Dangerous flag scan — $(date)"
  echo "# Scans all *.yml and *.properties (excluding target/) for flags hardcoded true"
  echo ""
  FOUND=0

  while IFS= read -r f; do
    if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" "$f" 2>/dev/null; then
      echo "[DANGER] $f: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true"
      FOUND=$((FOUND + 1))
    fi
  done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)

  while IFS= read -r f; do
    if grep -qE "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true" "$f" 2>/dev/null; then
      echo "[DANGER] $f: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true"
      FOUND=$((FOUND + 1))
    fi
  done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)

  while IFS= read -r f; do
    if grep -qE "remote-quota-decrement.*enabled.*: true|REMOTE_QUOTA_DECREMENT.*:true" "$f" 2>/dev/null; then
      echo "[DANGER] $f: remote-quota-decrement.enabled: true"
      FOUND=$((FOUND + 1))
    fi
  done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)

  if [ "$FOUND" -eq 0 ]; then
    echo "[PASS] No dangerous flags hardcoded true in any config file"
  else
    echo "[FAIL] $FOUND dangerous flag(s) found hardcoded true"
  fi
  echo ""
  echo "DANGEROUS_FLAGS_FOUND=$FOUND"
} > "$OUT_DIR/dangerous-flag-scan.txt"

if grep -q "DANGEROUS_FLAGS_FOUND=0" "$OUT_DIR/dangerous-flag-scan.txt"; then
  echo "[PASS] Dangerous flag scan: no flags hardcoded true"
else
  echo "[FAIL] Dangerous flag scan: dangerous flags found — see dangerous-flag-scan.txt"
  FAIL=$((FAIL + 1))
fi

# ── 5. Doc manifest ───────────────────────────────────────────────────────────

echo "[5/8] Collecting relevant doc manifest..."
{
  echo "# Relevant evidence and design docs — $(date)"
  echo ""
  for doc in \
    "docs/evidence/phase-2-external-execution-pack.md" \
    "docs/evidence/phase-2-dba-checklist.md" \
    "docs/evidence/phase-2-ops-xxl-job-checklist.md" \
    "docs/evidence/intake-dba-ddl-evidence.md" \
    "docs/evidence/intake-ops-xxl-job-evidence.md" \
    "docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md" \
    "docs/evidence/intake-oncall-signoff-evidence.md" \
    "docs/evidence/phase-2-3-fulfillment-final-readiness-index.md" \
    "docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md" \
    "docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md" \
    "docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md" \
    "docs/evidence/phase-2-2-b17-staging-cutover-template.md" \
    "docs/evidence/b17-staging-evidence-20260610.md" \
    "docs/microservices-split-phase-2-3-fulfillment-service.md"; do
    if [ -f "$ROOT/$doc" ]; then
      SIZE=$(wc -l < "$ROOT/$doc" 2>/dev/null || echo "?")
      echo "[FOUND] $doc ($SIZE lines)"
    else
      echo "[MISSING] $doc"
    fi
  done
} > "$OUT_DIR/doc-manifest.txt"

echo "[PASS] doc-manifest.txt written"

# ── 6. SQL manifest ───────────────────────────────────────────────────────────

echo "[6/8] Collecting SQL file manifest..."
{
  echo "# SQL files — $(date)"
  echo ""
  for sql in \
    "docs/sql/proposed-quota-decrement-ledger.sql" \
    "docs/sql/proposed-credit-award-task-outbox.sql"; do
    if [ -f "$ROOT/$sql" ]; then
      SIZE=$(wc -l < "$ROOT/$sql" 2>/dev/null || echo "?")
      SHA=$(shasum -a 256 "$ROOT/$sql" 2>/dev/null | awk '{print $1}' || echo "n/a")
      echo "[FOUND] $sql ($SIZE lines, sha256: $SHA)"
    else
      echo "[MISSING] $sql"
    fi
  done
  echo ""
  echo "# All SQL files in docs/sql/"
  find "$ROOT/docs/sql" -name "*.sql" 2>/dev/null | sort | while read -r f; do
    echo "  $f"
  done
} > "$OUT_DIR/sql-manifest.txt"

echo "[PASS] sql-manifest.txt written"

# ── 7. Summary ────────────────────────────────────────────────────────────────

echo "[7/8] Collecting script manifest..."
{
  echo "# Validator scripts — $(date)"
  echo ""
  for script in \
    "scripts/validate-phase-2-external-evidence-intake.sh" \
    "scripts/validate-phase-2-evidence-consistency.sh" \
    "scripts/validate-phase-2-external-execution-pack.sh" \
    "scripts/validate-fulfillment-service-phase-2-3.sh" \
    "scripts/collect-phase-2-external-evidence.sh"; do
    if [ -f "$ROOT/$script" ]; then
      SIZE=$(wc -l < "$ROOT/$script" 2>/dev/null || echo "?")
      echo "[FOUND] $script ($SIZE lines)"
    else
      echo "[MISSING] $script"
    fi
  done
} > "$OUT_DIR/script-manifest.txt"

echo "[PASS] script-manifest.txt written"

echo "[8/8] Writing summary..."
{
  echo "# Phase 2 External Evidence Collection Summary"
  echo "# Timestamp: $TIMESTAMP"
  echo "# Collected by: collect-phase-2-external-evidence.sh"
  echo "# Root: $ROOT"
  echo ""
  echo "Output directory: $OUT_DIR"
  echo ""
  echo "Files collected:"
  ls "$OUT_DIR/" 2>/dev/null | grep -v summary.txt | while read -r f; do
    echo "  - $f"
  done
  echo ""
  if [ "$FAIL" -eq 0 ]; then
    echo "RESULT: ALL COLLECTION STEPS PASS"
    echo ""
    echo "This snapshot represents local repo state only."
    echo "No staging or production evidence was accessed."
    echo "All dangerous flags remain false by default."
    echo ""
    echo "For role-specific evidence intake templates, see:"
    echo "  docs/evidence/intake-dba-ddl-evidence.md"
    echo "  docs/evidence/intake-ops-xxl-job-evidence.md"
    echo "  docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md"
    echo "  docs/evidence/intake-oncall-signoff-evidence.md"
    echo "For the full execution pack, see:"
    echo "  docs/evidence/phase-2-external-execution-pack.md"
  else
    echo "RESULT: $FAIL STEP(S) FAILED — review output above"
  fi
} > "$OUT_DIR/summary.txt"

echo ""
echo "============================================================"
echo "  Collection complete"
echo "  Output directory: $OUT_DIR"
echo "============================================================"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo "  RESULT: ALL STEPS PASS"
  exit 0
else
  echo "  RESULT: $FAIL STEP(S) FAILED"
  exit 1
fi
