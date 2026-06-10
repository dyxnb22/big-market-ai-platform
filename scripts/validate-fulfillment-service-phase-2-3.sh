#!/usr/bin/env bash
# validate-fulfillment-service-phase-2-3.sh
# Phase 2.3 one-command validator suite.
# Runs B23-B, B23-C, B23-D, and B23-E validators in order, then performs a final
# dangerous-flag scan and verifies all Phase 2.3 git tags exist locally.
# No network, Docker, DB, staging, or production access required.

set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

SUITE_FAIL=0

# ── Helper ─────────────────────────────────────────────────────────────────────

run_validator() {
  local label="$1"
  local script="$2"
  echo ""
  echo "════════════════════════════════════════════════════════════════════════════"
  echo "  Running: $label"
  echo "  Script:  $script"
  echo "════════════════════════════════════════════════════════════════════════════"
  if bash "$ROOT/$script"; then
    echo "[SUITE PASS] $label"
  else
    echo "[SUITE FAIL] $label"
    SUITE_FAIL=$((SUITE_FAIL + 1))
  fi
}

# ── 1. Per-batch validators ────────────────────────────────────────────────────

run_validator "B23-B: Award dispatch adapter scaffold" \
  "scripts/validate-fulfillment-service-b23-b.sh"

run_validator "B23-C: Staging readiness" \
  "scripts/validate-fulfillment-service-b23-c-readiness.sh"

run_validator "B23-D: Production promotion gate" \
  "scripts/validate-fulfillment-service-b23-d-production-gate.sh"

run_validator "B23-E: Cutover execution pack" \
  "scripts/validate-fulfillment-service-b23-e-cutover-execution.sh"

# ── 2. Final dangerous-flag scan ──────────────────────────────────────────────

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Final dangerous-flag scan (all config files)"
echo "════════════════════════════════════════════════════════════════════════════"

FLAG_FAIL=0

# Scan for ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED hardcoded to :true
OUTBOX_TRUE=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true"
    OUTBOX_TRUE=$((OUTBOX_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$OUTBOX_TRUE" -eq 0 ]; then
  echo "[PASS] FLAG-SCAN-1: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED not hardcoded true in any config"
else
  echo "[FAIL] FLAG-SCAN-1: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED hardcoded true in $OUTBOX_TRUE file(s)"
  FLAG_FAIL=$((FLAG_FAIL + 1))
fi

# Scan for ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED hardcoded to :true
REMOTE_AWARD_TRUE=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true"
    REMOTE_AWARD_TRUE=$((REMOTE_AWARD_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$REMOTE_AWARD_TRUE" -eq 0 ]; then
  echo "[PASS] FLAG-SCAN-2: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED not hardcoded true in any config"
else
  echo "[FAIL] FLAG-SCAN-2: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED hardcoded true in $REMOTE_AWARD_TRUE file(s)"
  FLAG_FAIL=$((FLAG_FAIL + 1))
fi

# Scan for remote-quota-decrement.enabled: true in YAML
QUOTA_TRUE=0
while IFS= read -r f; do
  if grep -qE "remote-quota-decrement.*enabled.*: true|REMOTE_QUOTA_DECREMENT.*:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: remote-quota-decrement.enabled: true"
    QUOTA_TRUE=$((QUOTA_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
if [ "$QUOTA_TRUE" -eq 0 ]; then
  echo "[PASS] FLAG-SCAN-3: account.service.remote-quota-decrement.enabled not true in any config"
else
  echo "[FAIL] FLAG-SCAN-3: account.service.remote-quota-decrement.enabled true in $QUOTA_TRUE file(s)"
  FLAG_FAIL=$((FLAG_FAIL + 1))
fi

if [ "$FLAG_FAIL" -gt 0 ]; then
  SUITE_FAIL=$((SUITE_FAIL + 1))
fi

# ── 3. Phase 2.3 git tag verification ─────────────────────────────────────────

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2.3 git tag verification (local)"
echo "════════════════════════════════════════════════════════════════════════════"

TAG_FAIL=0

check_tag() {
  local tag="$1"
  if git -C "$ROOT" tag | grep -qxF "$tag"; then
    echo "[PASS] TAG: $tag"
  else
    echo "[FAIL] TAG: $tag (not found locally)"
    TAG_FAIL=$((TAG_FAIL + 1))
  fi
}

check_tag "phase-2.3-a-fulfillment-service-dark-launch"
check_tag "phase-2.3-b-award-dispatch-adapter-scaffold"
check_tag "phase-2.3-c-fulfillment-staging-readiness"
check_tag "phase-2.3-d-fulfillment-production-gate"
check_tag "phase-2.3-e-fulfillment-cutover-execution-pack"

if [ "$TAG_FAIL" -gt 0 ]; then
  SUITE_FAIL=$((SUITE_FAIL + 1))
fi

# ── 4. Final summary ──────────────────────────────────────────────────────────

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 2.3 FINAL READINESS SUITE — SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""

if [ "$SUITE_FAIL" -eq 0 ]; then
  echo "  RESULT: ALL SUITES PASS"
  echo ""
  echo "  The repo is in a verified safe state for Phase 2.3 handoff."
  echo "  All dangerous flags are false by default."
  echo "  All Phase 2.3 git tags are present locally."
  echo ""
  echo "  Remaining external blockers before any staging/production traffic:"
  echo "    - B23-C staging evidence (SE1–SE11) signed by oncall lead"
  echo "    - DBA applies credit_award_task DDL to staging and production DB shards"
  echo "    - Ops registers DispatchCreditAwardTaskJob_DB1/_DB2 in staging + production XXL-Job"
  echo "    - B23-D evidence file completed and signed"
  echo "    - Oncall lead issues written approval for production cutover window"
  echo ""
  echo "  See: docs/evidence/phase-2-3-fulfillment-final-readiness-index.md"
  echo "  See: docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md"
  exit 0
else
  echo "  RESULT: $SUITE_FAIL SUITE(S) FAILED"
  echo ""
  echo "  Fix all failures above before proceeding with any staging or production action."
  exit 1
fi
