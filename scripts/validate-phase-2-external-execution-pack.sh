#!/usr/bin/env bash
# validate-phase-2-external-execution-pack.sh — Phase 2 External Pack Validator
#
# Validates all artifacts in the Phase 2 external execution pack without
# requiring any network, Docker, DB, staging, or production access.
#
# Checks:
#   1. All new docs exist and include required role sections
#   2. DBA checklist includes required SQL files and verification SQL topics
#   3. Ops checklist includes both XXL-Job handlers
#   4. Evidence collector exists and avoids DB/Docker/network commands
#   5. Runs validate-fulfillment-service-phase-2-3.sh
#   6. Dangerous flag scan across all config files
#
# Usage:
#   bash scripts/validate-phase-2-external-execution-pack.sh
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
    pass "$label: exists at $path"
  else
    fail "$label: NOT FOUND at $path"
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
    fail "$label: pattern not found in $path: $pattern"
  fi
}

check_file_not_contains() {
  local label="$1" path="$2" pattern="$3"
  if [ ! -f "$ROOT/$path" ]; then
    fail "$label: file not found ($path)"
    return
  fi
  if grep -q "$pattern" "$ROOT/$path" 2>/dev/null; then
    fail "$label: forbidden pattern found in $path: $pattern"
  else
    pass "$label"
  fi
}

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2 External Execution Pack Validator"
echo "════════════════════════════════════════════════════════════════════════════"

# ── 1. All new docs exist ─────────────────────────────────────────────────────

echo ""
echo "── [1] New document existence ──────────────────────────────────────────────"

check_file_exists "EXP-DOC-1: external execution pack" \
  "docs/evidence/phase-2-external-execution-pack.md"

check_file_exists "EXP-DOC-2: DBA checklist" \
  "docs/evidence/phase-2-dba-checklist.md"

check_file_exists "EXP-DOC-3: Ops XXL-Job checklist" \
  "docs/evidence/phase-2-ops-xxl-job-checklist.md"

check_file_exists "EXP-DOC-4: evidence collector script" \
  "scripts/collect-phase-2-external-evidence.sh"

check_file_exists "EXP-DOC-5: this validator script" \
  "scripts/validate-phase-2-external-execution-pack.sh"

check_file_exists "EXP-DOC-6: intake validator script" \
  "scripts/validate-phase-2-external-evidence-intake.sh"

check_file_exists "EXP-DOC-7: DBA DDL evidence intake template" \
  "docs/evidence/intake-dba-ddl-evidence.md"

check_file_exists "EXP-DOC-8: Ops XXL-Job evidence intake template" \
  "docs/evidence/intake-ops-xxl-job-evidence.md"

check_file_exists "EXP-DOC-9: Engineer B17/B23-C E2E evidence intake template" \
  "docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md"

check_file_exists "EXP-DOC-10: Oncall sign-off evidence intake template" \
  "docs/evidence/intake-oncall-signoff-evidence.md"

check_file_exists "EXP-DOC-11: completion gate validator script" \
  "scripts/validate-phase-2-external-evidence-completion.sh"

check_file_exists "EXP-DOC-12: external readiness dashboard" \
  "docs/evidence/phase-2-external-readiness-dashboard.md"

# ── 2. External pack has required role sections ────────────────────────────────

echo ""
echo "── [2] External pack role sections ─────────────────────────────────────────"

PACK="docs/evidence/phase-2-external-execution-pack.md"

check_file_contains "EXP-ROLE-1: DBA section present" \
  "$PACK" "Section A — DBA Tasks"

check_file_contains "EXP-ROLE-2: Ops section present" \
  "$PACK" "Section B — Ops Tasks"

check_file_contains "EXP-ROLE-3: Engineer section present" \
  "$PACK" "Section C — Engineer Tasks"

check_file_contains "EXP-ROLE-4: Oncall section present" \
  "$PACK" "Section D — Oncall Lead Tasks"

check_file_contains "EXP-ROLE-5: B17 staging GO section present" \
  "$PACK" "Phase 2.2-B17 Staging GO"

check_file_contains "EXP-ROLE-6: B23-C staging evidence section present" \
  "$PACK" "Phase 2.3-C Staging Evidence"

check_file_contains "EXP-ROLE-7: B23-D production gate section present" \
  "$PACK" "Phase 2.3-D Production Gate"

check_file_contains "EXP-ROLE-8: B23-E cutover window section present" \
  "$PACK" "Phase 2.3-E Cutover Window"

check_file_contains "EXP-ROLE-9: strict ordering present" \
  "$PACK" "Strict Execution Ordering"

check_file_contains "EXP-ROLE-10: hard NO-GO rules present" \
  "$PACK" "Hard NO-GO Rules"

check_file_contains "EXP-ROLE-11: not-approval disclaimer present" \
  "$PACK" "NOT AN APPROVAL"

check_file_contains "EXP-ROLE-12: DBA evidence table present" \
  "$PACK" "DBA Evidence Artifacts"

check_file_contains "EXP-ROLE-13: Ops evidence table present" \
  "$PACK" "Ops Evidence Artifacts"

check_file_contains "EXP-ROLE-14: Engineer evidence table present" \
  "$PACK" "Engineer Evidence Artifacts"

check_file_contains "EXP-ROLE-15: Oncall evidence table present" \
  "$PACK" "Oncall Evidence Artifacts"

# ── 3. DBA checklist ─────────────────────────────────────────────────────────

echo ""
echo "── [3] DBA checklist content ───────────────────────────────────────────────"

DBA="docs/evidence/phase-2-dba-checklist.md"

check_file_contains "DBA-1: references ledger SQL file" \
  "$DBA" "proposed-quota-decrement-ledger.sql"

check_file_contains "DBA-2: references outbox SQL file" \
  "$DBA" "proposed-credit-award-task-outbox.sql"

check_file_contains "DBA-3: credit_award_task staging verification SQL" \
  "$DBA" "credit_award_task"

check_file_contains "DBA-4: UNIQUE KEY uq_award_order_id verification" \
  "$DBA" "uq_award_order_id"

check_file_contains "DBA-5: UNIQUE KEY uq_out_business_no verification" \
  "$DBA" "uq_out_business_no"

check_file_contains "DBA-6: ledger table verification SQL" \
  "$DBA" "raffle_quota_decrement_ledger"

check_file_contains "DBA-7: UNIQUE KEY uq_user_activity_biz verification" \
  "$DBA" "uq_user_activity_biz"

check_file_contains "DBA-8: big_market_01 mentioned" \
  "$DBA" "big_market_01"

check_file_contains "DBA-9: big_market_02 mentioned" \
  "$DBA" "big_market_02"

check_file_contains "DBA-10: rollback guidance present" \
  "$DBA" "Rollback"

check_file_contains "DBA-11: do not rollback DDL without incident lead approval" \
  "$DBA" "incident lead"

check_file_contains "DBA-12: staging DDL section present" \
  "$DBA" "Phase 1: Staging DDL"

check_file_contains "DBA-13: production DDL section present" \
  "$DBA" "Phase 2: Production DDL"

# ── 4. Ops checklist ─────────────────────────────────────────────────────────

echo ""
echo "── [4] Ops checklist content ───────────────────────────────────────────────"

OPS="docs/evidence/phase-2-ops-xxl-job-checklist.md"

check_file_contains "OPS-1: DispatchCreditAwardTaskJob_DB1 present" \
  "$OPS" "DispatchCreditAwardTaskJob_DB1"

check_file_contains "OPS-2: DispatchCreditAwardTaskJob_DB2 present" \
  "$OPS" "DispatchCreditAwardTaskJob_DB2"

check_file_contains "OPS-3: executor AppName present" \
  "$OPS" "big-market-message-job-service"

check_file_contains "OPS-4: cron expression present" \
  "$OPS" '0/30'

check_file_contains "OPS-5: routing strategy present" \
  "$OPS" "FIRST"

check_file_contains "OPS-6: manual trigger validation present" \
  "$OPS" "manual trigger"

check_file_contains "OPS-7: expected logs present" \
  "$OPS" "exitCode"

check_file_contains "OPS-8: staging registration section present" \
  "$OPS" "Phase 1: Staging"

check_file_contains "OPS-9: production registration section present" \
  "$OPS" "Phase 3: Production"

check_file_contains "OPS-10: NO-GO triggers present" \
  "$OPS" "NO-GO Triggers"

check_file_contains "OPS-11: evidence table present" \
  "$OPS" "Evidence Attachment"

check_file_contains "OPS-12: job must stay in message-job-service (not fulfillment-service)" \
  "$OPS" "big-market-message-job-service"

# ── 5. Evidence collector safety checks ──────────────────────────────────────

echo ""
echo "── [5] Evidence collector safety ───────────────────────────────────────────"

COLLECTOR="scripts/collect-phase-2-external-evidence.sh"

check_file_exists "COLL-1: collector script exists" "$COLLECTOR"

check_file_contains "COLL-2: collector generates timestamped output directory" \
  "$COLLECTOR" "phase2-evidence-"

check_file_contains "COLL-3: collector writes git HEAD" \
  "$COLLECTOR" "git-head.txt"

check_file_contains "COLL-4: collector writes git-status" \
  "$COLLECTOR" "git-status.txt"

check_file_contains "COLL-5: collector runs Phase 2.3 suite validator" \
  "$COLLECTOR" "validate-fulfillment-service-phase-2-3.sh"

check_file_contains "COLL-6: collector does dangerous flag scan" \
  "$COLLECTOR" "dangerous-flag-scan"

check_file_contains "COLL-7: collector writes doc manifest" \
  "$COLLECTOR" "doc-manifest.txt"

check_file_contains "COLL-8: collector writes SQL manifest" \
  "$COLLECTOR" "sql-manifest.txt"

# Safety: collector must not invoke mysql/docker/curl/wget
check_file_not_contains "COLL-9: collector does not invoke mysql" \
  "$COLLECTOR" "^[^#]*mysql "

check_file_not_contains "COLL-10: collector does not invoke docker" \
  "$COLLECTOR" "^[^#]*docker "

check_file_not_contains "COLL-11: collector does not invoke curl" \
  "$COLLECTOR" "^[^#]*curl "

check_file_not_contains "COLL-12: collector does not invoke wget" \
  "$COLLECTOR" "^[^#]*wget "

# ── 5b. Run validate-phase-2-external-evidence-intake.sh ─────────────────────

echo ""
echo "── [5b] External evidence intake validator ──────────────────────────────────"

INTAKE_SCRIPT="$ROOT/scripts/validate-phase-2-external-evidence-intake.sh"
if [ ! -f "$INTAKE_SCRIPT" ]; then
  fail "INTAKE-RUN-1: validate-phase-2-external-evidence-intake.sh not found"
else
  if bash "$INTAKE_SCRIPT" > /tmp/phase2-intake-pack-out.txt 2>&1; then
    pass "INTAKE-RUN-1: validate-phase-2-external-evidence-intake.sh ALL CHECKS PASS"
  else
    fail "INTAKE-RUN-1: validate-phase-2-external-evidence-intake.sh FAILED"
    echo ""
    echo "  --- Intake validator output (last 20 lines) ---"
    tail -20 /tmp/phase2-intake-pack-out.txt | sed 's/^/  /'
    echo "  ---"
  fi
fi

# ── 5c. Run validate-phase-2-external-evidence-completion.sh ─────────────────

echo ""
echo "── [5c] Evidence completion gate validator ──────────────────────────────────"

COMPLETION_SCRIPT="$ROOT/scripts/validate-phase-2-external-evidence-completion.sh"
if [ ! -f "$COMPLETION_SCRIPT" ]; then
  fail "COMPLETION-RUN-1: validate-phase-2-external-evidence-completion.sh not found"
else
  if bash "$COMPLETION_SCRIPT" > /tmp/phase2-completion-pack-out.txt 2>&1; then
    pass "COMPLETION-RUN-1: validate-phase-2-external-evidence-completion.sh GATE PASS"
  else
    fail "COMPLETION-RUN-1: validate-phase-2-external-evidence-completion.sh GATE FAIL"
    echo ""
    echo "  --- Completion gate output (last 20 lines) ---"
    tail -20 /tmp/phase2-completion-pack-out.txt | sed 's/^/  /'
    echo "  ---"
  fi
fi

# ── 6. Run validate-fulfillment-service-phase-2-3.sh ─────────────────────────

echo ""
echo "── [6] Phase 2.3 suite validator ────────────────────────────────────────────"

SUITE_SCRIPT="$ROOT/scripts/validate-fulfillment-service-phase-2-3.sh"
if [ ! -f "$SUITE_SCRIPT" ]; then
  fail "SUITE-1: validate-fulfillment-service-phase-2-3.sh not found"
else
  if bash "$SUITE_SCRIPT" > /tmp/phase23-suite-out.txt 2>&1; then
    pass "SUITE-1: validate-fulfillment-service-phase-2-3.sh ALL SUITES PASS"
  else
    fail "SUITE-1: validate-fulfillment-service-phase-2-3.sh FAILED"
    echo ""
    echo "  --- Suite output (last 30 lines) ---"
    tail -30 /tmp/phase23-suite-out.txt | sed 's/^/  /'
    echo "  ---"
  fi
fi

# ── 7. Dangerous flag scan ────────────────────────────────────────────────────

echo ""
echo "── [7] Dangerous flag scan ──────────────────────────────────────────────────"

FLAG_FAIL=0

OUTBOX_TRUE=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED:true"
    OUTBOX_TRUE=$((OUTBOX_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$OUTBOX_TRUE" -eq 0 ] && pass "FLAG-1: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED not hardcoded true" \
  || { fail "FLAG-1: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED hardcoded true in $OUTBOX_TRUE file(s)"; FLAG_FAIL=1; }

REMOTE_AWARD_TRUE=0
while IFS= read -r f; do
  if grep -qE "ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED:true"
    REMOTE_AWARD_TRUE=$((REMOTE_AWARD_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$REMOTE_AWARD_TRUE" -eq 0 ] && pass "FLAG-2: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED not hardcoded true" \
  || { fail "FLAG-2: ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED hardcoded true in $REMOTE_AWARD_TRUE file(s)"; FLAG_FAIL=1; }

QUOTA_TRUE=0
while IFS= read -r f; do
  if grep -qE "remote-quota-decrement.*enabled.*: true|REMOTE_QUOTA_DECREMENT.*:true" "$f" 2>/dev/null; then
    echo "  [DANGER] $f: remote-quota-decrement.enabled: true"
    QUOTA_TRUE=$((QUOTA_TRUE + 1))
  fi
done < <(find "$ROOT" -not -path "*/target/*" \( -name "*.yml" -o -name "*.properties" \) 2>/dev/null)
[ "$QUOTA_TRUE" -eq 0 ] && pass "FLAG-3: account.service.remote-quota-decrement.enabled not true" \
  || { fail "FLAG-3: account.service.remote-quota-decrement.enabled true in $QUOTA_TRUE file(s)"; FLAG_FAIL=1; }

[ "$FLAG_FAIL" -gt 0 ] && FAIL=$((FAIL + 1))

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 2 EXTERNAL EXECUTION PACK VALIDATOR — SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  Checks passed: $PASS / $TOTAL"
echo "  Checks failed: $FAIL"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo "  RESULT: ALL CHECKS PASS"
  echo ""
  echo "  The Phase 2 external execution pack is complete and safe."
  echo "  All dangerous flags remain false by default."
  echo ""
  echo "  Remaining external blockers (require real staging/prod access):"
  echo "    - DBA applies DDL to staging big_market_01 and big_market_02"
  echo "    - Ops registers DispatchCreditAwardTaskJob_DB1/_DB2 in staging XXL-Job"
  echo "    - Engineer runs B17 E2E + B23-C E2E in staging"
  echo "    - Oncall signs B17 Phase K + B23-C SE11 + B23-D Phase E + P4 approval"
  echo "    - DBA applies DDL to production big_market_01 and big_market_02"
  echo "    - Ops registers DispatchCreditAwardTaskJob_DB1/_DB2 in production XXL-Job"
  echo "    - Engineer executes B23-E cutover (S1-S8 staging + P1-P8 production)"
  echo ""
  echo "  See: docs/evidence/phase-2-external-execution-pack.md"
  exit 0
else
  echo "  RESULT: $FAIL CHECK(S) FAILED"
  echo ""
  echo "  Fix all failures before proceeding with any staging or production action."
  exit 1
fi
