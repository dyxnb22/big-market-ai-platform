#!/usr/bin/env bash
# validate-account-service-production-b18.sh — Phase 2.2-B18
#
# Production promotion gate for account-service quota-decrement extraction.
# Conservative-by-default: gate/runbook only.  Does NOT enable production flags,
# apply DDL, or mutate any data.  Production promotion requires completed B17
# staging evidence and explicit operator approval.
#
# Usage:
#   ./scripts/validate-account-service-production-b18.sh
#       Default dry-run/static mode: verify all code-side artefacts, baselines,
#       and flag defaults.  Print blocker status and go/no-go prerequisites.
#       No DB, no writes, no flag changes.
#
#   B18_PRINT_PLAN=true ./scripts/validate-account-service-production-b18.sh
#       Print the ordered production promotion plan (Phases A–J) and exit.
#
#   B18_STAGING_EVIDENCE=<path> ./scripts/validate-account-service-production-b18.sh
#       Validate that the B17 staging evidence file exists and has all required
#       sections filled (no obvious placeholder blanks).  Prints missing fields
#       and evidence-count drift on failure.  Required before any production
#       promotion action.
#
#   CONNECT_REMOTE=true \
#     MYSQL_HOST=<prod-or-staging-host> MYSQL_PORT=3306 \
#     MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
#     ./scripts/validate-account-service-production-b18.sh
#       Read-only DB verification: delegates to B16 CONNECT_REMOTE mode.
#       Verifies ledger/outbox tables and UNIQUE KEYs in production or staging.
#       NEVER writes, mutates, or modifies any data.
#
#   B18_EVIDENCE_FILE=<path> ./scripts/validate-account-service-production-b18.sh
#       Write the production promotion evidence template to the specified path.
#       Safe to run multiple times — each run appends a fresh timestamped section.
#
#   B18_POST_CHECK=true \
#     MYSQL_HOST=<prod-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
#     ./scripts/validate-account-service-production-b18.sh
#       Post-production-window checklist mode: re-runs CONNECT_REMOTE read-only
#       checks and prints the Phase 2.2 completion sign-off checklist.
#
# Combined examples (all flags compose freely):
#   B18_STAGING_EVIDENCE=docs/evidence/b17-staging-evidence-<YYYYMMDD>.md \
#     B18_PRINT_PLAN=true \
#     ./scripts/validate-account-service-production-b18.sh
#
# Safety constraints:
#   - NEVER applies staging/production DDL automatically
#   - NEVER enables or disables remote-quota-decrement.enabled
#   - CONNECT_REMOTE delegates to B16 read-only mode — strictly SELECT from information_schema
#   - B18_POST_CHECK is read-only; no writes to any table
#   - B18_EVIDENCE_FILE writes only the local file at the given path; no DB writes
#   - Production flag enable (Phase D) is a MANUAL operator action — never automated
#   - Phase 2.2 cannot be marked complete unless B18_STAGING_EVIDENCE passes and
#     the B18 production evidence file is fully filled out by the operator
set -euo pipefail

B18_PRINT_PLAN="${B18_PRINT_PLAN:-false}"
B18_STAGING_EVIDENCE="${B18_STAGING_EVIDENCE:-}"
CONNECT_REMOTE="${CONNECT_REMOTE:-false}"
B18_EVIDENCE_FILE="${B18_EVIDENCE_FILE:-}"
B18_POST_CHECK="${B18_POST_CHECK:-false}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"

B17_SCRIPT="scripts/execute-account-service-staging-b17.sh"
B16_SCRIPT="scripts/validate-account-service-cutover-b16.sh"
B15_SCRIPT="scripts/validate-quota-decrement-b15-e2e.sh"
B14_SCRIPT="scripts/validate-quota-decrement-b14.sh"
PROD_DDL_SCRIPT="scripts/validate-production-ddl.sh"
B17_EVIDENCE_CONSISTENCY_SCRIPT="scripts/validate-b17-evidence-consistency.sh"

PASS=0
FAIL=0

ok()   { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }
info() { echo "[INFO] $*"; }
warn() { echo "[WARN] $*"; }

# ---------------------------------------------------------------------------
# Production promotion plan
# ---------------------------------------------------------------------------
PROMOTION_PLAN='
=============================================================================
B18 PRODUCTION PROMOTION PLAN — ORDERED EXECUTION
All phases must be executed in order.  Do NOT skip or re-order.
This script is read-only.  Every phase step is executed MANUALLY by the operator.
Production flag enable must be approved by oncall lead before Phase D.
=============================================================================

Phase A: Verify B17 staging GO evidence
-----------------------------------------
  Prerequisite: B17 staging cutover window complete; evidence file fully filled out.
  Verify with this script:
    B18_STAGING_EVIDENCE=<path-to-b17-evidence> \
        ./scripts/validate-account-service-production-b18.sh
  All required sections must be present and non-empty.
  HARD GATE: do NOT proceed if B18_STAGING_EVIDENCE validation fails.
  Record evidence file path and validation result in B18 evidence file.

Phase B: Apply production DDL manually
---------------------------------------
  DDL files (proposed — verify with DBA before applying):
    docs/sql/proposed-quota-decrement-ledger.sql   → big_market_01, big_market_02
    docs/sql/proposed-credit-award-task-outbox.sql → big_market_01, big_market_02

  Commands (run manually — NOT by this script):
    mysql -h <prod-host> -u <admin-user> -p big_market_01 \
        < docs/sql/proposed-quota-decrement-ledger.sql
    mysql -h <prod-host> -u <admin-user> -p big_market_02 \
        < docs/sql/proposed-quota-decrement-ledger.sql
    mysql -h <prod-host> -u <admin-user> -p big_market_01 \
        < docs/sql/proposed-credit-award-task-outbox.sql
    mysql -h <prod-host> -u <admin-user> -p big_market_02 \
        < docs/sql/proposed-credit-award-task-outbox.sql

  Record DDL apply timestamps in B18 evidence file.
  Gate: Phase C read-only verification must show all tables present.

Phase C: Read-only production DB verification
----------------------------------------------
  Command (run this script in CONNECT_REMOTE mode against production):
    CONNECT_REMOTE=true \
      MYSQL_HOST=<prod-host> MYSQL_PORT=3306 \
      MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
      ./scripts/validate-account-service-production-b18.sh
  All ledger/outbox table checks and UNIQUE KEY checks must PASS.
  HARD GATE: do NOT proceed to Phase D if any check fails.
  Record result and check count in B18 evidence file.

Phase D: Enable ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true (canary window)
-------------------------------------------------------------------------------------
  Requires explicit oncall lead approval recorded in B18 evidence file.
  Enable on ONE production market-service instance only:
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true
  Redeploy that instance.
  Confirm via:
    docker exec big-market-market-service env | grep REMOTE_QUOTA_DECREMENT
    Expected: ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true
  Record canary instance, start timestamp, and approver in B18 evidence file.
  CANARY WINDOW: ~15 minutes maximum.  Monitor logs and metrics continuously.

Phase E: Run canary partake flow
----------------------------------
  POST /api/v1/raffle/activity/draw  {"activityId": <id>, "userId": "<canary-user>"}
  Expected: HTTP 200, awardId present.

  Verify ledger row (must be status=applied after draw):
    SELECT * FROM raffle_quota_decrement_ledger_000
    WHERE user_id='"'"'<canary-user>'"'"' AND activity_id=<id>;

  Verify quota decrement:
    SELECT total_count_surplus FROM raffle_activity_account
    WHERE user_id='"'"'<canary-user>'"'"' AND activity_id=<id>;
    Expected: (quota before draw) - 1

  Idempotency (duplicate draw, same outBusinessNo):
    Re-submit same request.
    Expected: quota unchanged, ledger row count = 1.

  Record all values in B18 evidence file.
  HARD GATE: any failure → immediately execute rollback (flag=false).

Phase F: Verify rollback path
-------------------------------
  Trigger rollback (savePartakeOrderOnly intentional failure or controlled test).

  Verify ledger row status = rolled_back:
    SELECT status FROM raffle_quota_decrement_ledger_000
    WHERE user_id='"'"'<canary-user>'"'"' AND out_business_no='"'"'<biz-no>'"'"';

  Verify quota restored (must equal pre-draw value):
    SELECT total_count_surplus FROM raffle_activity_account
    WHERE user_id='"'"'<canary-user>'"'"' AND activity_id=<id>;

  Idempotency (duplicate rollback):
    Trigger rollback again — rows_affected must be 0; quota must be unchanged.

  Record all values in B18 evidence file.

Phase G: Verify credit-award outbox dispatch/idempotency
----------------------------------------------------------
  Verify outbox row state = pending:
    SELECT state FROM credit_award_task_000
    WHERE award_order_id='"'"'<award_order_id>'"'"';

  Trigger DispatchCreditAwardTaskJob_DB1 via XXL-Job admin UI manual trigger.

  Verify outbox row state = dispatched after trigger.
  Verify exactly 1 user_credit_order row:
    SELECT COUNT(*) FROM user_credit_order_000
    WHERE out_business_no='"'"'<award_order_id>'"'"';
    Expected: 1

  Idempotency (second dispatch):
    Trigger DispatchCreditAwardTaskJob_DB1 again.
    Expected: user_credit_order count still = 1 (no double credit).

  Record all values in B18 evidence file.

Phase H: Monitor logs/metrics (during canary window)
------------------------------------------------------
  Monitor continuously during Phases E–G:
    - Error rate on draw endpoint (must be 0%)
    - Quota leak query (raffle_activity_account total_count_surplus drift)
    - user_credit_order double-count query (per out_business_no)
    - Latency P99 on /api/v1/raffle/activity/draw
    - account-service and market-service GC / heap metrics

  Record monitoring output and anomaly count in B18 evidence file.
  HARD GATE: any anomaly → flag=false rollback immediately.

Phase I: Decide full rollout or restore flag=false
----------------------------------------------------
  After Phases E–H, make the production go/no-go decision:
    GO    → expand ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true to all
            production market-service instances.  Record rollout timestamp.
    NO-GO → restore flag=false on canary instance immediately.
            Record reason and rollback timestamp in B18 evidence file.
            Do NOT expand until root cause is resolved and staging re-validated.

  Rollback command (instant — no data loss):
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
    docker compose up -d --no-deps --build big-market-market-service

Phase J: Final Phase 2.2 completion sign-off
----------------------------------------------
  All of the following must be confirmed before signing off:
    [ ] B18 evidence file fully filled out
    [ ] Phases A–I all recorded with PASS/GO outcomes
    [ ] No quota leak observed at any step
    [ ] No double-credit observed at any step
    [ ] No rollback failure observed
    [ ] Production flag either fully rolled out (GO) or restored to false (NO-GO)
    [ ] Sign-off recorded: approver, role, timestamp

  If GO: Phase 2.2 account-service quota-decrement extraction is COMPLETE.
  If NO-GO: Phase 2.2 remains open; schedule root-cause fix batch before retry.

=============================================================================
'

# ---------------------------------------------------------------------------
# Mode: B18_PRINT_PLAN
# ---------------------------------------------------------------------------
if [[ "$B18_PRINT_PLAN" == "true" ]]; then
    echo "$PROMOTION_PLAN"
    exit 0
fi

info "=== Phase 2.2-B18 Production Promotion Gate ==="
echo ""

# ---------------------------------------------------------------------------
# Section 1: Static pre-flight checks
# ---------------------------------------------------------------------------
info "=== Section 1: Static pre-flight checks ==="
echo ""

# P1: B17 script exists and is executable
if [[ -x "$B17_SCRIPT" ]]; then
    ok "P1: $B17_SCRIPT exists and is executable (B17 staging cutover executor in place)"
elif [[ -f "$B17_SCRIPT" ]]; then
    ok "P1: $B17_SCRIPT exists (run chmod +x to make executable)"
else
    fail "P1: $B17_SCRIPT missing — B17 staging cutover package not found; B18 cannot proceed"
fi

# P2: B17 static gate must be green
B17_OUT=$(./scripts/execute-account-service-staging-b17.sh 2>&1) || true
B17_PASS=$(echo "$B17_OUT" | grep -c "^\[PASS\]" || true)
B17_FAIL=$(echo "$B17_OUT" | grep -c "^\[FAIL\]" || true)
if [[ "${B17_FAIL:-0}" -eq 0 && "${B17_PASS:-0}" -gt 0 ]]; then
    ok "P2: B17 static gate: ${B17_PASS} PASS, 0 FAIL (all B17 pre-flight invariants intact)"
else
    fail "P2: B17 static gate: ${B17_PASS} PASS, ${B17_FAIL} FAIL — resolve B17 failures before B18"
fi

# P3: B16 gate script exists
if [[ -f "$B16_SCRIPT" ]]; then
    ok "P3: $B16_SCRIPT exists (B16 cutover gate in place)"
else
    fail "P3: $B16_SCRIPT missing — B16 cutover gate not found"
fi

# P4: B15 runbook script exists
if [[ -f "$B15_SCRIPT" ]]; then
    ok "P4: $B15_SCRIPT exists (B15 E2E staging runbook in place)"
else
    fail "P4: $B15_SCRIPT missing — B15 E2E staging runbook not found"
fi

# P5: B14 script exists
if [[ -f "$B14_SCRIPT" ]]; then
    ok "P5: $B14_SCRIPT exists (B14 rollback+wiring baseline in place)"
else
    fail "P5: $B14_SCRIPT missing — B14 baseline not found"
fi

# P6: Production DDL script exists
if [[ -f "$PROD_DDL_SCRIPT" ]]; then
    ok "P6: $PROD_DDL_SCRIPT exists (production DDL verification script in place)"
else
    fail "P6: $PROD_DDL_SCRIPT missing — production DDL verification not in place"
fi

# P7: Ledger DDL artefact present
if [[ -f "docs/sql/proposed-quota-decrement-ledger.sql" ]]; then
    ok "P7: docs/sql/proposed-quota-decrement-ledger.sql exists (Phase B DDL artefact ready)"
else
    fail "P7: docs/sql/proposed-quota-decrement-ledger.sql missing — Phase B blocker not ready"
fi

# P8: Outbox DDL artefact present
if [[ -f "docs/sql/proposed-credit-award-task-outbox.sql" ]]; then
    ok "P8: docs/sql/proposed-credit-award-task-outbox.sql exists (Phase B DDL artefact ready)"
else
    fail "P8: docs/sql/proposed-credit-award-task-outbox.sql missing — Phase B blocker not ready"
fi

# P9: B17 evidence template exists
EVIDENCE_TEMPLATE="docs/evidence/phase-2-2-b17-staging-cutover-template.md"
if [[ -f "$EVIDENCE_TEMPLATE" ]]; then
    ok "P9: $EVIDENCE_TEMPLATE exists (B17 evidence template artefact ready)"
else
    fail "P9: $EVIDENCE_TEMPLATE missing — B17 evidence template not generated"
fi

# P10: Docs contain B17 Phases A–K
PHASE_MISSING=()
for phase in A B C D E F G H I J K; do
    if ! grep -q "Phase ${phase}" "$EVIDENCE_TEMPLATE" 2>/dev/null; then
        PHASE_MISSING+=("$phase")
    fi
done
if [[ "${#PHASE_MISSING[@]}" -eq 0 ]]; then
    ok "P10: B17 evidence template contains all Phases A–K"
else
    fail "P10: B17 evidence template missing phases: ${PHASE_MISSING[*]}"
fi

# P11: remote-quota-decrement defaults false (critical production gate)
ENABLED_MATCH=$(grep -r \
    "ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:true\|remote-quota-decrement\.enabled.*:.*true" \
    --include="*.yml" --include="*.yaml" --include="*.properties" . 2>/dev/null \
    | grep -v "target/" || true)
if [[ -z "$ENABLED_MATCH" ]]; then
    ok "P11: remote-quota-decrement=false in all configs (production gate preserved)"
else
    fail "P11: remote-quota-decrement enabled in config — PRODUCTION GATE VIOLATION: $ENABLED_MATCH"
fi

# P12: Production promotion blocked without staging evidence
if [[ -z "$B18_STAGING_EVIDENCE" ]]; then
    ok "P12: Production promotion gate intact — B18_STAGING_EVIDENCE not supplied (correct for dry-run; required before any production action)"
else
    ok "P12: B18_STAGING_EVIDENCE supplied — will validate in Section 2"
fi

echo ""

# ---------------------------------------------------------------------------
# Section 2: Staging evidence validation (B18_STAGING_EVIDENCE mode)
# ---------------------------------------------------------------------------
if [[ -n "$B18_STAGING_EVIDENCE" ]]; then
    echo ""
    info "=== Section 2: B17 staging evidence validation ==="
    info "    File: $B18_STAGING_EVIDENCE"
    echo ""

    if [[ ! -f "$B18_STAGING_EVIDENCE" ]]; then
        fail "E1: $B18_STAGING_EVIDENCE does not exist — supply the completed B17 evidence file"
        echo ""
        info "Production promotion is BLOCKED until a valid B17 evidence file is supplied."
        info "Generate the template with:"
        info "    B17_EVIDENCE_FILE=<path> ./scripts/execute-account-service-staging-b17.sh"
        echo ""
    else
        ok "E1: $B18_STAGING_EVIDENCE exists"

        if [[ -x "$B17_EVIDENCE_CONSISTENCY_SCRIPT" ]]; then
            CONSISTENCY_OUT=$("./$B17_EVIDENCE_CONSISTENCY_SCRIPT" "$B18_STAGING_EVIDENCE" 2>&1) || true
            CONSISTENCY_FAIL=$(echo "$CONSISTENCY_OUT" | awk -F': *' '/^FAIL:/ {print $2}' | tail -1)
            CONSISTENCY_FAIL="${CONSISTENCY_FAIL:-1}"
            if [[ "$CONSISTENCY_FAIL" == "0" ]]; then
                ok "E1a: B17 evidence PASS count matches B17 script dry-run output"
            else
                fail "E1a: B17 evidence consistency guard failed — run $B17_EVIDENCE_CONSISTENCY_SCRIPT $B18_STAGING_EVIDENCE"
                echo "$CONSISTENCY_OUT" | sed 's/^/      /'
            fi
        else
            fail "E1a: $B17_EVIDENCE_CONSISTENCY_SCRIPT missing or not executable"
        fi

        # Check required sections are present
        MISSING_SECTIONS=()
        for section in \
            "Phase A" "Phase B" "Phase C" "Phase D" "Phase E" \
            "Phase F" "Phase G" "Phase H" "Phase I" "Phase J" "Phase K" \
            "Production go decision" "Production Promotion Criteria" "Rollback Plan"; do
            if ! grep -q "$section" "$B18_STAGING_EVIDENCE" 2>/dev/null; then
                MISSING_SECTIONS+=("$section")
            fi
        done

        if [[ "${#MISSING_SECTIONS[@]}" -eq 0 ]]; then
            ok "E2: All required sections present in B17 evidence file"
        else
            fail "E2: Missing sections in B17 evidence file: ${MISSING_SECTIONS[*]}"
        fi

        # Check placeholders are not obviously empty (contains ___ in critical fields)
        MISSING_FIELDS=()

        PHASE_C_BLOCK=$(awk '/Phase C/,/Phase D/' "$B18_STAGING_EVIDENCE" 2>/dev/null || true)
        PHASE_D_BLOCK=$(awk '/Phase D/,/Phase E/' "$B18_STAGING_EVIDENCE" 2>/dev/null || true)
        PHASE_E_BLOCK=$(awk '/Phase E/,/Phase F/' "$B18_STAGING_EVIDENCE" 2>/dev/null || true)
        PHASE_F_BLOCK=$(awk '/Phase F/,/Phase G/' "$B18_STAGING_EVIDENCE" 2>/dev/null || true)
        PHASE_G_BLOCK=$(awk '/Phase G/,/Phase H/' "$B18_STAGING_EVIDENCE" 2>/dev/null || true)
        PHASE_H_BLOCK=$(awk '/Phase H/,/Phase I/' "$B18_STAGING_EVIDENCE" 2>/dev/null || true)
        PHASE_K_BLOCK=$(awk '/Phase K/,/Production Promotion/' "$B18_STAGING_EVIDENCE" 2>/dev/null || true)

        # Phase C: DB verification result
        if echo "$PHASE_C_BLOCK" | grep -qiE "Result .*PENDING|Phase C gate: PENDING|Result .*_{3,}"; then
            MISSING_FIELDS+=("Phase-C: DB verification result")
        fi

        # Phase D: XXL-Job handler IDs
        if echo "$PHASE_D_BLOCK" | grep -qE "DispatchCreditAwardTaskJob_DB[12] \| (PENDING|_{3,})"; then
            MISSING_FIELDS+=("Phase-D: XXL-Job handler IDs")
        fi

        # Phase E: flag=true window timestamp
        if echo "$PHASE_E_BLOCK" | grep -qE "flag=true start.*_{3,}"; then
            MISSING_FIELDS+=("Phase-E: flag=true start timestamp")
        fi

        # Phase F: partake E2E result (response code or ledger status)
        if echo "$PHASE_F_BLOCK" | grep -qE "Response code.*_{3,}|HTTP status.*_{3,}|Armory gate.*PASS / FAIL"; then
            MISSING_FIELDS+=("Phase-F: partake HTTP response code")
        fi

        # Phase G: rollback result (ledger status)
        if echo "$PHASE_G_BLOCK" | grep -qE "Status: *_{3,}|Ledger status.*_{3,}|Second rollback rows affected.*_{3,}"; then
            MISSING_FIELDS+=("Phase-G: rollback ledger status")
        fi

        # Phase H: outbox dispatch result
        if echo "$PHASE_H_BLOCK" | grep -qE "state after dispatch.*_{3,}|Outbox row state.*_{3,}"; then
            MISSING_FIELDS+=("Phase-H: outbox row state after dispatch")
        fi

        # Phase H: idempotency (user_credit_order count)
        if echo "$PHASE_H_BLOCK" | grep -qE "user_credit_order count.*_{3,}|Count: *_{3,}|count.*expected.*1.*_{3,}"; then
            MISSING_FIELDS+=("Phase-H: user_credit_order count after dispatch")
        fi

        # Phase K: final staging GO decision
        if echo "$PHASE_K_BLOCK" | grep -qE "Production go decision.*GO / NO-GO|Decision by.*_{3,}|Decision timestamp.*_{3,}"; then
            MISSING_FIELDS+=("Phase-K: production go/no-go decision")
        fi

        if [[ "${#MISSING_FIELDS[@]}" -eq 0 ]]; then
            ok "E3: All required evidence fields appear non-empty in B17 evidence file"
            echo ""
            info "B17 staging evidence validation: PASS"
            info "Phase A gate: PASS — staging evidence confirmed; proceed to Phase B (production DDL)."
        else
            fail "E3: The following required fields appear empty or unfilled in B17 evidence:"
            for field in "${MISSING_FIELDS[@]}"; do
                echo "      - $field"
            done
            echo ""
            info "Fill out all listed fields in the B17 evidence file before production promotion."
            info "Production promotion is BLOCKED until all fields are complete."
        fi
    fi
else
    echo ""
    info "=== Section 2: B17 staging evidence validation — SKIPPED ==="
    info "    Set B18_STAGING_EVIDENCE=<path-to-completed-b17-evidence> to validate."
    info "    Suggested: B18_STAGING_EVIDENCE=docs/evidence/b17-staging-evidence-<YYYYMMDD>.md"
    info "    IMPORTANT: production promotion is BLOCKED until this validation passes."
fi

# ---------------------------------------------------------------------------
# Section 3: Manual blocker status
# ---------------------------------------------------------------------------
echo ""
info "=== Section 3: Manual blocker and production action status ==="
echo ""
info "The following blockers cannot be verified automatically."
info "They must be confirmed manually before opening the production promotion window."
echo ""
cat <<'BLOCKERS'
  [BLOCKER 1] Staging ledger DDL (prerequisite for staging E2E)
    Apply docs/sql/proposed-quota-decrement-ledger.sql to staging big_market_01 and big_market_02.
    Status: PENDING (verify with B17 CONNECT_REMOTE mode)

  [BLOCKER 2] Staging credit-award outbox DDL (prerequisite for staging E2E)
    Apply docs/sql/proposed-credit-award-task-outbox.sql to staging big_market_01 and big_market_02.
    Status: PENDING (verify with B17 CONNECT_REMOTE mode)

  [BLOCKER 3] XXL-Job handler registration on staging (prerequisite for staging E2E)
    Register DispatchCreditAwardTaskJob_DB1 (cron: 0/30 * * * * ?) in staging XXL-Job admin.
    Register DispatchCreditAwardTaskJob_DB2 (cron: 0/30 * * * * ?) in staging XXL-Job admin.
    Status: PENDING (manual — not automatable)

  [PRODUCTION ACTION 4] Production DDL apply (Phase B — after staging GO)
    Apply ledger + outbox DDL to production big_market_01 and big_market_02.
    Status: MANUAL-ONLY — after B18_STAGING_EVIDENCE validation passes.

  [PRODUCTION ACTION 5] Production flag enable (Phase D — canary only)
    ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true on ONE instance.
    Status: MANUAL-ONLY — requires oncall lead approval and B18 evidence Phase A–C complete.

BLOCKERS

# ---------------------------------------------------------------------------
# Section 4: CONNECT_REMOTE — delegate to B16 read-only verification
# ---------------------------------------------------------------------------
if [[ "$CONNECT_REMOTE" == "true" ]]; then
    echo ""
    info "=== Section 4: Remote DB verification (delegating to B16 CONNECT_REMOTE) ==="
    info "    Host: $MYSQL_HOST:$MYSQL_PORT  User: $MYSQL_USER"
    info "    This is read-only — delegating to CONNECT_REMOTE=true $B16_SCRIPT"
    echo ""

    REMOTE_OUT=$(CONNECT_REMOTE=true \
        MYSQL_HOST="$MYSQL_HOST" MYSQL_PORT="$MYSQL_PORT" \
        MYSQL_USER="$MYSQL_USER" MYSQL_PASS="$MYSQL_PASS" \
        ./"$B16_SCRIPT" 2>&1) || true

    echo "$REMOTE_OUT"

    REMOTE_PASS=$(echo "$REMOTE_OUT" | grep -c "^\[PASS\]" || true)
    REMOTE_FAIL=$(echo "$REMOTE_OUT" | grep -c "^\[FAIL\]" || true)

    echo ""
    if [[ "${REMOTE_FAIL:-0}" -eq 0 && "${REMOTE_PASS:-0}" -gt 0 ]]; then
        ok "R1: B16 CONNECT_REMOTE: ${REMOTE_PASS} PASS, 0 FAIL — all tables and UNIQUE KEYs verified"
        info "Phase C gate: PASS — DDL applied correctly; proceed to Phase D (canary flag enable)."
    else
        fail "R1: B16 CONNECT_REMOTE: ${REMOTE_PASS} PASS, ${REMOTE_FAIL} FAIL — resolve DDL issues before Phase D"
        info "Phase C gate: FAIL — apply missing DDL and re-run CONNECT_REMOTE verification."
    fi
else
    echo ""
    info "=== Section 4: Remote DB verification — SKIPPED ==="
    info "    Set CONNECT_REMOTE=true with MYSQL_HOST/MYSQL_USER/MYSQL_PASS."
    info "    Run against production after applying Phase B DDL."
    info "    This is the Phase C gate (read-only — will not write to any DB)."
fi

# ---------------------------------------------------------------------------
# Section 5: B18_EVIDENCE_FILE — write production promotion evidence template
# ---------------------------------------------------------------------------
if [[ -n "$B18_EVIDENCE_FILE" ]]; then
    echo ""
    info "=== Section 5: Production promotion evidence file write ==="
    info "    Target: $B18_EVIDENCE_FILE"

    EVIDENCE_DIR=$(dirname "$B18_EVIDENCE_FILE")
    if [[ ! -d "$EVIDENCE_DIR" ]]; then
        mkdir -p "$EVIDENCE_DIR"
        info "Created directory: $EVIDENCE_DIR"
    fi

    RUN_TS=$(date '+%Y-%m-%d %H:%M:%S %Z')
    RUN_SEPARATOR="---
<!-- B18 evidence section appended at: ${RUN_TS} -->
---
"

    if [[ -f "$B18_EVIDENCE_FILE" ]]; then
        {
            echo ""
            echo "$RUN_SEPARATOR"
        } >> "$B18_EVIDENCE_FILE"
        info "Appending new evidence section to existing file."
    fi

    cat >> "$B18_EVIDENCE_FILE" <<EVIDENCE_BODY
# B18 Production Promotion Evidence — $(date '+%Y-%m-%d')

**Script:** \`./scripts/validate-account-service-production-b18.sh\`
**Run at:** ${RUN_TS}
**Environment:** production (canary → full rollout)
**B17 staging evidence file:** ___________________________________

---

## Phase A — B17 Staging Evidence Validation

| Check | Result |
|-------|--------|
| B17 evidence file path | ___________________ |
| B18_STAGING_EVIDENCE validation result | PASS / FAIL |
| All B17 Phases A–K present | YES / NO |
| All required fields non-empty | YES / NO |
| Phase K production GO decision recorded | YES / NO |
| Decision by | ___________________ |
| Decision timestamp | ___________________ |

**Phase A gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase B if Phase A gate is FAIL.

---

## Phase B — Production DDL Apply

| | big_market_01 | big_market_02 |
|---|---|---|
| Ledger DDL applied by | ___________________ | ___________________ |
| Ledger DDL timestamp | ___________________ | ___________________ |
| Ledger DDL result | SUCCESS / ERROR | SUCCESS / ERROR |
| Outbox DDL applied by | ___________________ | ___________________ |
| Outbox DDL timestamp | ___________________ | ___________________ |
| Outbox DDL result | SUCCESS / ERROR | SUCCESS / ERROR |

Commands applied:
\`\`\`bash
mysql -h <prod-host> -u <admin> -p big_market_01 < docs/sql/proposed-quota-decrement-ledger.sql
mysql -h <prod-host> -u <admin> -p big_market_02 < docs/sql/proposed-quota-decrement-ledger.sql
mysql -h <prod-host> -u <admin> -p big_market_01 < docs/sql/proposed-credit-award-task-outbox.sql
mysql -h <prod-host> -u <admin> -p big_market_02 < docs/sql/proposed-credit-award-task-outbox.sql
\`\`\`

---

## Phase C — Production DB Verification (CONNECT_REMOTE, read-only)

Command run:
\`\`\`bash
CONNECT_REMOTE=true MYSQL_HOST=<prod-host> MYSQL_PORT=3306 MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \\
    ./scripts/validate-account-service-production-b18.sh
\`\`\`

| Check | Result |
|-------|--------|
| B16 CONNECT_REMOTE PASS count | ___________________ |
| B16 CONNECT_REMOTE FAIL count | ___________________ (must be 0) |
| Log/screenshot path | ___________________ |

**Phase C gate:** PASS / FAIL

> Hard gate: do NOT proceed to Phase D if Phase C gate is FAIL.

Checks verified:
- [ ] \`raffle_quota_decrement_ledger_{000..003}\` in \`big_market_01\` — all 4 tables present
- [ ] \`raffle_quota_decrement_ledger_{000..003}\` in \`big_market_02\` — all 4 tables present
- [ ] \`UNIQUE KEY uq_user_activity_biz\` on all 8 ledger shards
- [ ] \`credit_award_task_{000..003}\` in \`big_market_01\` — all 4 tables present
- [ ] \`credit_award_task_{000..003}\` in \`big_market_02\` — all 4 tables present
- [ ] \`UNIQUE KEY uq_award_order_id\` on all 8 outbox shards
- [ ] \`UNIQUE KEY uq_out_business_no\` on all \`user_credit_order_*\` shards
- [ ] \`UNIQUE KEY uq_biz_id\` on all \`user_behavior_rebate_order_*\` shards

---

## Phase D — Canary Flag Enable Window

| | Value |
|---|---|
| Oncall lead approver | ___________________ |
| Approval timestamp | ___________________ |
| Canary instance | ___________________ |
| flag=true start timestamp | ___________________ |
| Env key | \`ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true\` |
| Deployed to | big-market-market-service (canary instance only) |
| Confirmed via | \`docker exec big-market-market-service env \| grep REMOTE_QUOTA_DECREMENT\` |
| Confirmation output | ___________________ |

> IMPORTANT: production flag=true on canary instance only.  All other instances remain flag=false.

---

## Phase E — Canary Partake Flow

### Test Values

| | Value |
|---|---|
| canary userId | ___________________ |
| activityId | ___________________ |
| outBusinessNo | ___________________ |

### HTTP Request

\`\`\`
POST /api/v1/raffle/activity/draw
{"activityId": <id>, "userId": "<canary-user>"}
\`\`\`

| | Value |
|---|---|
| Response code | ___________________ (expected: 200) |
| Response body (awardId) | ___________________ |

### Ledger State

| | Value |
|---|---|
| Ledger row BEFORE draw | ___________________ (expected: no row) |
| Ledger row AFTER draw (status) | ___________________ (expected: applied) |

### Quota State

| | Value |
|---|---|
| total_count_surplus BEFORE draw | ___________________ |
| total_count_surplus AFTER draw | ___________________ (expected: before - 1) |

### Idempotency — Duplicate Draw (same outBusinessNo)

| | Value |
|---|---|
| Re-submitted | YES / NO |
| Quota after duplicate draw | ___________________ (must equal post-draw value) |
| Ledger row count after duplicate | ___________________ (must be 1) |

**Phase E gate:** PASS / FAIL
> Hard gate: any FAIL → immediately execute Phase I rollback (flag=false).

---

## Phase F — Rollback Path Verification

### Rollback Method

- [ ] \`savePartakeOrderOnly\` intentional failure
- [ ] Controlled test trigger

### Ledger State After Rollback

| | Value |
|---|---|
| Ledger status after rollback | ___________________ (expected: rolled_back) |
| Quota after rollback | ___________________ (expected: restored to pre-draw value) |

### Idempotency — Duplicate Rollback

| | Value |
|---|---|
| Second rollback rows affected | ___________________ (expected: 0) |
| Quota after duplicate rollback | ___________________ (expected: unchanged) |

**Phase F gate:** PASS / FAIL

---

## Phase G — Credit-Award Outbox Dispatch/Idempotency

### Test Outbox Row

| | Value |
|---|---|
| DB/Table | ___________________ |
| award_order_id | ___________________ |
| State at insert | pending |

### First Dispatch — DispatchCreditAwardTaskJob_DB1

| | Value |
|---|---|
| Trigger timestamp | ___________________ |
| Via | XXL-Job admin UI manual trigger |
| Outbox row state after dispatch | ___________________ (expected: dispatched) |
| user_credit_order count | ___________________ (expected: 1) |

### Idempotency — Second Dispatch

| | Value |
|---|---|
| Second trigger timestamp | ___________________ |
| user_credit_order count after second dispatch | ___________________ (expected: still 1, no double credit) |

**Phase G gate:** PASS / FAIL

---

## Phase H — Monitoring/Log Checks (Canary Window)

| Metric | Value | Threshold | Status |
|--------|-------|-----------|--------|
| Draw endpoint error rate | ___________________ | 0% | PASS / FAIL |
| Quota leak observations | ___________________ | 0 | PASS / FAIL |
| user_credit_order double-count | ___________________ | 0 | PASS / FAIL |
| Latency P99 /draw | ___________________ | baseline ±20% | PASS / FAIL |
| account-service heap/GC anomaly | ___________________ | none | PASS / FAIL |
| market-service heap/GC anomaly | ___________________ | none | PASS / FAIL |
| Log/screenshot path | ___________________ | — | — |

**Phase H gate:** PASS / FAIL

---

## Phase I — Production Rollout or Flag=false Restore

| | Value |
|---|---|
| Decision | GO (full rollout) / NO-GO (flag=false) |
| Decision timestamp | ___________________ |
| Decision by | ___________________ |

**If GO:**
| | Value |
|---|---|
| Full rollout timestamp | ___________________ |
| All instances confirmed flag=true | YES / NO |
| Post-full-rollout error rate | ___________________ |

**If NO-GO:**
| | Value |
|---|---|
| Rollback command run | \`ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false docker compose up -d --no-deps --build big-market-market-service\` |
| flag=false restore timestamp | ___________________ |
| Health check after restore | ___________________ (expected: "UP") |
| NO-GO reason | ___________________ |

---

## Phase J — Final Phase 2.2 Sign-Off

| Check | Result |
|-------|--------|
| Phase A: B17 staging evidence validated | YES / NO |
| Phase B: Production DDL applied (both DBs, both tables) | YES / NO |
| Phase C: CONNECT_REMOTE all checks PASS (0 FAIL) | YES / NO |
| Phase D: Canary instance flag=true confirmed | YES / NO |
| Phase E: Partake E2E: HTTP 200, ledger applied, quota decremented | YES / NO |
| Phase E idempotency: quota unchanged, ledger count = 1 | YES / NO |
| Phase F: Rollback: ledger rolled_back, quota restored | YES / NO |
| Phase F idempotency: duplicate rollback = 0 rows, quota unchanged | YES / NO |
| Phase G: Outbox dispatched, user_credit_order count = 1 | YES / NO |
| Phase G idempotency: second dispatch count still = 1 | YES / NO |
| Phase H: No quota leak, no double-credit, latency within threshold | YES / NO |
| Phase I: Full GO rollout OR clean NO-GO flag restore | YES / NO |
| No quota leak observed at any step | YES / NO **(YES required for GO)** |
| No double-credit observed at any step | YES / NO **(YES required for GO)** |

**Final Phase 2.2 go decision:** **GO / NO-GO**
**Sign-off by:** ___________________________________
**Role:** ___________________________________
**Timestamp:** ___________________________________
**If NO-GO, reason and next batch:** ___________________________________

---

## Rollback Plan

**Instant rollback (any phase):**
\`\`\`bash
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
docker compose up -d --no-deps --build big-market-market-service
\`\`\`
The \`saveCreatePartakeOrderAggregate\` path takes effect immediately — no data loss.

**Quota leak repair (if automatic rollback did not fire):**
\`\`\`sql
UPDATE raffle_quota_decrement_ledger_000
  SET status='rolled_back'
  WHERE user_id='<user>' AND out_business_no='<biz-no>';

UPDATE raffle_activity_account
  SET total_count_surplus = total_count_surplus + 1
  WHERE user_id='<user>' AND activity_id=<id>;
\`\`\`

**flag=false rollback command:**
\`\`\`bash
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false
docker compose up -d --no-deps --build big-market-market-service
\`\`\`

---

## Production No-Go Criteria

Do NOT proceed (or immediately rollback) if ANY of the following:
- B17 staging evidence validation FAIL or evidence file incomplete
- Any FAIL in B18 static checks or CONNECT_REMOTE checks
- Any FAIL in Phase E (partake E2E or idempotency)
- Any FAIL in Phase F (rollback or duplicate-rollback idempotency)
- Any FAIL in Phase G (outbox dispatch or double-credit)
- Quota changed on duplicate draw
- \`user_credit_order\` count > 1 for same \`out_business_no\` (double credit)
- Quota not restored after rollback
- Any quota leak detected in monitoring (Phase H)
- B18 evidence file incomplete or unsigned

---

## Remaining Blockers (at time of template generation)

The following blockers were unresolved when this template was generated.
Update this section when each blocker is completed.

1. **Staging ledger DDL** — apply \`docs/sql/proposed-quota-decrement-ledger.sql\` to staging \`big_market_01\` and \`big_market_02\`. Status: **PENDING**
2. **Staging credit-award outbox DDL** — apply \`docs/sql/proposed-credit-award-task-outbox.sql\` to staging \`big_market_01\` and \`big_market_02\`. Status: **PENDING**
3. **XXL-Job handlers on staging** — register \`DispatchCreditAwardTaskJob_DB1\` and \`DispatchCreditAwardTaskJob_DB2\` in staging XXL-Job admin UI. Status: **PENDING**
4. **B17 staging cutover** — complete the staging E2E window and fill out the B17 evidence file. Status: **PENDING**
5. **Phase 2.2 sign-off** — complete Phases A–J above and record final decision. Status: **PENDING**

EVIDENCE_BODY

    ok "EV1: Production promotion evidence template written/appended to $B18_EVIDENCE_FILE"
    info "Fill this file out during the production promotion window."
else
    echo ""
    info "=== Section 5: Evidence file write — SKIPPED ==="
    info "    Set B18_EVIDENCE_FILE=<path> to write the production evidence template."
    info "    Suggested: B18_EVIDENCE_FILE=docs/evidence/phase-2-2-b18-production-promotion-template.md"
fi

# ---------------------------------------------------------------------------
# Section 6: B18_POST_CHECK — post-production-window checklist
# ---------------------------------------------------------------------------
if [[ "$B18_POST_CHECK" == "true" ]]; then
    echo ""
    info "=== Section 6: Post-production-window verification ==="
    info "    Host: $MYSQL_HOST  User: $MYSQL_USER"
    info "    Re-running CONNECT_REMOTE verification (read-only)."
    echo ""

    POST_OUT=$(CONNECT_REMOTE=true \
        MYSQL_HOST="$MYSQL_HOST" MYSQL_PORT="$MYSQL_PORT" \
        MYSQL_USER="$MYSQL_USER" MYSQL_PASS="$MYSQL_PASS" \
        ./"$B16_SCRIPT" 2>&1) || true

    echo "$POST_OUT"

    POST_PASS=$(echo "$POST_OUT" | grep -c "^\[PASS\]" || true)
    POST_FAIL=$(echo "$POST_OUT" | grep -c "^\[FAIL\]" || true)

    echo ""
    if [[ "${POST_FAIL:-0}" -eq 0 && "${POST_PASS:-0}" -gt 0 ]]; then
        ok "PC1: Post-window CONNECT_REMOTE: ${POST_PASS} PASS, 0 FAIL"
    else
        fail "PC1: Post-window CONNECT_REMOTE: ${POST_PASS} PASS, ${POST_FAIL} FAIL — investigate"
    fi

    echo ""
    info "=== Phase 2.2 Completion Sign-Off Checklist ==="
    cat <<'SIGN_OFF'

  Confirm ALL of the following before declaring Phase 2.2 complete:

  --- Staging gate ---
  [ ] B17 evidence file fully filled out (Phases A–K)
  [ ] Phase C gate PASS: all CONNECT_REMOTE staging checks PASS (0 FAIL)
  [ ] Phase D: XXL-Job handler IDs recorded (DB1 + DB2)
  [ ] Phase E: Partake flow E2E: HTTP 200, ledger applied, quota decremented by 1
  [ ] Phase E idempotency: duplicate draw — quota unchanged, ledger count = 1
  [ ] Phase F (staging): rollback — ledger rolled_back, quota restored
  [ ] Phase F idempotency: duplicate rollback — 0 rows, quota unchanged
  [ ] Phase H (staging): outbox dispatch — pending→dispatched, count = 1
  [ ] Phase H idempotency: second dispatch — count still = 1 (no double credit)
  [ ] Phase I (staging): flag restored to false, market-service health = "UP"
  [ ] Phase K (staging): GO decision recorded with approver and timestamp

  --- Production gate ---
  [ ] B18 Phase A: B17 staging evidence validated (B18_STAGING_EVIDENCE PASS)
  [ ] B18 Phase B: Production DDL applied to all physical shard DBs (timestamps recorded)
  [ ] B18 Phase C: CONNECT_REMOTE production verification PASS (0 FAIL)
  [ ] B18 Phase D: Canary flag=true confirmed, approver recorded
  [ ] B18 Phase E: Production partake E2E PASS (HTTP 200, ledger, quota, idempotency)
  [ ] B18 Phase F: Production rollback path PASS (quota restored, idempotency confirmed)
  [ ] B18 Phase G: Production outbox dispatch PASS (no double credit)
  [ ] B18 Phase H: No quota leak, no anomaly in monitoring window
  [ ] B18 Phase I: GO — full rollout complete, OR NO-GO — flag restored to false
  [ ] B18 Phase J: Final sign-off recorded with approver, role, and timestamp

  --- Hard no-go blockers (any one blocks completion) ---
  - Any unresolved FAIL in any phase above
  - Quota changed on duplicate draw (idempotency violation)
  - user_credit_order count > 1 for same out_business_no (double credit)
  - Quota not restored after rollback (data integrity failure)
  - B17 or B18 evidence file incomplete or unsigned

SIGN_OFF
else
    echo ""
    info "=== Section 6: Post-production-window verification — SKIPPED ==="
    info "    Set B18_POST_CHECK=true with MYSQL_HOST/MYSQL_USER/MYSQL_PASS after Phase I."
    info "    This re-runs read-only CONNECT_REMOTE checks and prints the Phase 2.2 sign-off checklist."
fi

# ---------------------------------------------------------------------------
# Dry-run summary (default mode only)
# ---------------------------------------------------------------------------
if [[ "$CONNECT_REMOTE" != "true" && -z "$B18_STAGING_EVIDENCE" && \
      -z "$B18_EVIDENCE_FILE" && "$B18_POST_CHECK" != "true" ]]; then
    echo ""
    info "=== Dry-run: B18 Production Promotion Checklist ==="
    cat <<'DRY_RUN'

  To promote account-service quota-decrement to production, run in order:

  Step 1 — Print the ordered production promotion plan:
    B18_PRINT_PLAN=true ./scripts/validate-account-service-production-b18.sh

  Step 2 — Complete the B17 staging cutover (must finish before B18):
    B17_PRINT_PLAN=true ./scripts/execute-account-service-staging-b17.sh
    (Follow all phases A–K, fill evidence file, record Phase K GO decision)

  Step 3 — Validate completed B17 staging evidence:
    B18_STAGING_EVIDENCE=docs/evidence/b17-staging-evidence-<YYYYMMDD>.md \
        ./scripts/validate-account-service-production-b18.sh

  Step 4 — Generate production promotion evidence template:
    B18_EVIDENCE_FILE=docs/evidence/phase-2-2-b18-production-promotion-template.md \
        ./scripts/validate-account-service-production-b18.sh

  Step 5 — Apply production DDL (manual, Phase B):
    mysql -h <prod-host> -u <admin> -p big_market_01 \
        < docs/sql/proposed-quota-decrement-ledger.sql
    mysql -h <prod-host> -u <admin> -p big_market_02 \
        < docs/sql/proposed-quota-decrement-ledger.sql
    mysql -h <prod-host> -u <admin> -p big_market_01 \
        < docs/sql/proposed-credit-award-task-outbox.sql
    mysql -h <prod-host> -u <admin> -p big_market_02 \
        < docs/sql/proposed-credit-award-task-outbox.sql

  Step 6 — Phase C gate (production DB read-only verification):
    CONNECT_REMOTE=true MYSQL_HOST=<prod-host> MYSQL_PORT=3306 \
      MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
      ./scripts/validate-account-service-production-b18.sh

  Step 7 — Canary flag enable (manual, Phase D — oncall lead approval required):
    Deploy ONE production market-service instance with:
      ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=true

  Step 8 — Run Phases E, F, G (manual canary E2E + rollback + outbox, per promotion plan):
    B18_PRINT_PLAN=true ./scripts/validate-account-service-production-b18.sh | grep -A 200 "Phase E"

  Step 9 — Monitor Phase H (continuous during canary window):
    Watch error rate, quota leak queries, user_credit_order counts, P99 latency.

  Step 10 — Phase I decision (manual):
    GO  → expand flag=true to all production market-service instances.
    NO-GO → ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false and redeploy.

  Step 11 — Post-window sign-off (Phase J):
    B18_POST_CHECK=true MYSQL_HOST=<prod-host> MYSQL_USER=<ro-user> MYSQL_PASS=<pass> \
        ./scripts/validate-account-service-production-b18.sh
    Fill out Phase J in B18 evidence file. Record final decision.

  Baseline validations (must remain green throughout):
    ./scripts/execute-account-service-staging-b17.sh       # B17: 6/6
    ./scripts/validate-account-service-cutover-b16.sh      # B16: 18/18
    ./scripts/validate-quota-decrement-b15-e2e.sh          # B15: 20/20
    ./scripts/validate-quota-decrement-b14.sh              # B14: 21/21
    ./scripts/validate-production-ddl.sh                   # DDL: 14/14
    ./scripts/validate-mq-idempotency.sh                   # MQ:  12/12
    mvn compile                                            # BUILD SUCCESS

DRY_RUN
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== B18 Production Promotion Gate Summary ==="
echo "PASS: $PASS"
echo "FAIL: $FAIL"
echo ""

if [[ "$FAIL" -eq 0 ]]; then
    echo "[OK] All B18 pre-flight checks pass."
    if [[ -n "$B18_STAGING_EVIDENCE" ]]; then
        echo "     If staging evidence validation above passed, proceed to Phase B (production DDL)."
    else
        echo "     Resolve the 3 manual staging blockers, complete B17 staging cutover,"
        echo "     then run B18_STAGING_EVIDENCE=<path> mode to validate before any production action."
    fi
    exit 0
else
    echo "[FAIL] $FAIL check(s) failed. Resolve before proceeding with production promotion."
    exit 1
fi
