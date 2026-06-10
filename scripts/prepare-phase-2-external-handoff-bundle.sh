#!/usr/bin/env bash
# prepare-phase-2-external-handoff-bundle.sh — Phase 2 External Handoff Bundle Generator
#
# Creates a timestamped, local-only handoff bundle under docs/evidence/generated/.
# The bundle packages the exact documents, templates, validator outputs, current
# readiness state, and role-specific instructions for DBA, Ops, Engineer, and Oncall.
#
# Safety constraints:
#   - No DB, Docker, staging, or production access at any time
#   - No network calls (no mysql, docker, curl, wget)
#   - All output written only to docs/evidence/generated/ (gitignored)
#   - No dangerous flags are enabled
#   - This bundle is NOT an approval and does NOT enable traffic
#
# Usage:
#   bash scripts/prepare-phase-2-external-handoff-bundle.sh
#
# Output:
#   docs/evidence/generated/phase2-handoff-bundle-<TIMESTAMP>/
#     README.md                           — execution order, gate state, blockers
#     MANIFEST.md                         — every source doc/script used
#     NOT-AN-APPROVAL.txt                 — explicit safety disclaimer
#     git-state/
#       git-head.txt
#       git-status.txt
#       git-tags.txt
#     validator-outputs/
#       validate-intake.txt
#       validate-completion.txt
#       validate-consistency.txt
#       validate-execution-pack.txt
#     DBA/
#       intake-dba-ddl-evidence.md        — DBA intake template (fill DA1–DA14)
#       instructions.md                   — role-specific execution instructions
#     Ops/
#       intake-ops-xxl-job-evidence.md    — Ops intake template (fill OA1–OA6)
#       instructions.md
#     Engineer/
#       intake-engineer-b17-b23c-e2e-evidence.md  — Engineer intake template (EA1–EA10)
#       instructions.md
#     Oncall/
#       intake-oncall-signoff-evidence.md — Oncall intake template (OC1–OC5)
#       instructions.md
#
# Exit code:
#   0 — bundle created successfully
#   1 — one or more steps failed

set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d%H%M%S)"
BUNDLE_DIR="$ROOT/docs/evidence/generated/phase2-handoff-bundle-$TIMESTAMP"

FAIL=0

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2 External Handoff Bundle Generator"
echo "  Timestamp  : $TIMESTAMP"
echo "  Output dir : $BUNDLE_DIR"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  THIS BUNDLE IS LOCAL-ONLY AND NOT AN APPROVAL TO ENABLE TRAFFIC."
echo "  All dangerous flags remain false by default."
echo "  Generated output is under docs/evidence/generated/ — gitignored, never committed."
echo ""

# ── Create directory structure ────────────────────────────────────────────────

mkdir -p "$BUNDLE_DIR/git-state"
mkdir -p "$BUNDLE_DIR/validator-outputs"
mkdir -p "$BUNDLE_DIR/DBA"
mkdir -p "$BUNDLE_DIR/Ops"
mkdir -p "$BUNDLE_DIR/Engineer"
mkdir -p "$BUNDLE_DIR/Oncall"

echo "[1/9] Directory structure created."

# ── NOT-AN-APPROVAL.txt ───────────────────────────────────────────────────────

cat > "$BUNDLE_DIR/NOT-AN-APPROVAL.txt" <<'EOF'
THIS BUNDLE IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC.

This handoff bundle is a local-only snapshot of Phase 2 external execution
preparation materials. It packages intake templates, validator output, and
role-specific instructions for DBA, Ops, Engineer, and Oncall.

Hard safety invariants:
  - B23-E cutover must NOT start until all four roles show COMPLETE
  - All three dangerous flags must remain false until external sign-offs are complete:
      account.award-credit-outbox.enabled        = false (default)
      account.fulfillment.remote-award.enabled   = false (default)
      account.service.remote-quota-decrement.enabled = false (default)
  - No repo-only action enables any flag or substitutes for real-world execution
  - DDL rollback (DROP TABLE) requires written incident lead approval

Remaining external blockers (require real staging/production access):
  X1  DBA: apply staging DDL + verify unique keys
  X2  Ops: register XXL-Job handlers in staging + manual trigger SUCCESS
  X3  Engineer: run B17 E2E + B23-C E2E in staging
  X4  Oncall: issue B17 Phase K GO decision
  X5  DBA: apply production DDL
  X6  Ops: register XXL-Job handlers in production
  X7  Oncall: issue B23-C SE11 GO + B23-D Phase E GO + P4 written approval
  X8  Engineer: execute B23-E cutover (S1–S8 staging, P1–P8 production)
  X9  Oncall: issue B23-E final GO decision (OC5)
EOF

echo "[2/9] NOT-AN-APPROVAL.txt written."

# ── Git state ─────────────────────────────────────────────────────────────────

{
  echo "# git HEAD — Phase 2 handoff bundle generated at $TIMESTAMP"
  echo ""
  echo "## git rev-parse HEAD"
  git -C "$ROOT" rev-parse HEAD 2>&1
  echo ""
  echo "## git log --oneline -10"
  git -C "$ROOT" log --oneline -10 2>&1
  echo ""
  echo "## Phase 2 tags"
  git -C "$ROOT" tag | grep -E "phase-2" | sort 2>&1 || true
} > "$BUNDLE_DIR/git-state/git-head.txt"

{
  echo "# git status — $TIMESTAMP"
  echo ""
  git -C "$ROOT" status --short 2>&1
} > "$BUNDLE_DIR/git-state/git-status.txt"

{
  echo "# All local tags — $TIMESTAMP"
  echo ""
  git -C "$ROOT" tag | sort 2>&1
} > "$BUNDLE_DIR/git-state/git-tags.txt"

echo "[3/9] Git state captured."

# ── Run validators ────────────────────────────────────────────────────────────

echo "[4/9] Running validators and capturing output..."

run_validator() {
  local label="$1" script="$2" outfile="$3" pass_pattern="$4"
  if [ ! -f "$ROOT/$script" ]; then
    echo "  [SKIP] $script not found" | tee -a "$outfile"
    return
  fi
  {
    echo "# $label"
    echo "# Script: $script"
    echo "# Run at: $(date)"
    echo ""
    bash "$ROOT/$script" 2>&1
  } > "$outfile"
  if grep -q "$pass_pattern" "$outfile" 2>/dev/null; then
    echo "  [PASS] $label"
  else
    echo "  [WARN] $label: pass pattern not found — check $outfile"
    FAIL=$((FAIL + 1))
  fi
}

run_validator \
  "validate-phase-2-external-evidence-intake.sh" \
  "scripts/validate-phase-2-external-evidence-intake.sh" \
  "$BUNDLE_DIR/validator-outputs/validate-intake.txt" \
  "RESULT: ALL CHECKS PASS"

run_validator \
  "validate-phase-2-external-evidence-completion.sh" \
  "scripts/validate-phase-2-external-evidence-completion.sh" \
  "$BUNDLE_DIR/validator-outputs/validate-completion.txt" \
  "RESULT: GATE PASS"

run_validator \
  "validate-phase-2-evidence-consistency.sh" \
  "scripts/validate-phase-2-evidence-consistency.sh" \
  "$BUNDLE_DIR/validator-outputs/validate-consistency.txt" \
  "RESULT: ALL CHECKS PASS"

run_validator \
  "validate-phase-2-external-execution-pack.sh" \
  "scripts/validate-phase-2-external-execution-pack.sh" \
  "$BUNDLE_DIR/validator-outputs/validate-execution-pack.txt" \
  "RESULT: ALL CHECKS PASS"

# ── Copy intake templates to role folders ─────────────────────────────────────

echo "[5/9] Copying intake templates to role folders..."

copy_intake() {
  local src="$1" dst="$2" label="$3"
  if [ -f "$ROOT/$src" ]; then
    cp "$ROOT/$src" "$dst"
    echo "  [COPY] $label"
  else
    echo "  [MISS] $label: $src not found"
    FAIL=$((FAIL + 1))
  fi
}

copy_intake \
  "docs/evidence/intake-dba-ddl-evidence.md" \
  "$BUNDLE_DIR/DBA/intake-dba-ddl-evidence.md" \
  "DBA/intake-dba-ddl-evidence.md"

copy_intake \
  "docs/evidence/intake-ops-xxl-job-evidence.md" \
  "$BUNDLE_DIR/Ops/intake-ops-xxl-job-evidence.md" \
  "Ops/intake-ops-xxl-job-evidence.md"

copy_intake \
  "docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md" \
  "$BUNDLE_DIR/Engineer/intake-engineer-b17-b23c-e2e-evidence.md" \
  "Engineer/intake-engineer-b17-b23c-e2e-evidence.md"

copy_intake \
  "docs/evidence/intake-oncall-signoff-evidence.md" \
  "$BUNDLE_DIR/Oncall/intake-oncall-signoff-evidence.md" \
  "Oncall/intake-oncall-signoff-evidence.md"

# ── Role-specific instruction files ───────────────────────────────────────────

echo "[6/9] Writing role-specific instruction files..."

# DBA instructions
cat > "$BUNDLE_DIR/DBA/instructions.md" <<'EOF'
# DBA — Phase 2 Handoff Instructions

**Bundle generated:** see ../git-state/git-head.txt for exact commit
**Your intake template:** intake-dba-ddl-evidence.md (in this folder)

> THIS IS NOT AN APPROVAL. Fill this template during real staging/production execution.
> All dangerous flags remain false. See ../NOT-AN-APPROVAL.txt.

## Your Gate: DA1–DA14

You are responsible for applying and verifying all DDL to staging and production shard
databases. No traffic flags are enabled by the DBA — that is done by the Engineer after
DBA sign-off.

## Execution Order

### Phase 1 — Staging DDL (unblocks B17 E2E and B23-C E2E)

1. Apply ledger DDL to big_market_01 and big_market_02 (staging)
   - SQL file: docs/sql/proposed-quota-decrement-ledger.sql
2. Apply outbox DDL to big_market_01 and big_market_02 (staging)
   - SQL file: docs/sql/proposed-credit-award-task-outbox.sql
3. Verify table presence and unique keys:
   - uq_award_order_id on all 8 credit_award_task shards
   - uq_out_business_no on all user_credit_order shards
   - uq_user_activity_biz on all raffle_quota_decrement_ledger shards
4. Fill DA1–DA9 in intake-dba-ddl-evidence.md
5. Update "Staging DDL Gate" and "DBA Staging Sign-Off" rows to PASS

### Phase 3 — Production DDL (unblocks P5 outbox flag enable)

Only after Oncall issues B23-C SE11 GO decision.

1. Apply outbox DDL to big_market_01 and big_market_02 (production)
2. Verify unique keys on all 8 production shards
3. Fill DA10–DA14 in intake-dba-ddl-evidence.md
4. Update "Production DDL Gate" and "DBA Production Sign-Off" rows to PASS

## Hard NO-GO Triggers

Stop and escalate if:
- UNIQUE KEY uq_award_order_id missing from any deployed shard
- UNIQUE KEY uq_out_business_no missing from any user_credit_order shard
- Any user_credit_order count > 1 for the same out_business_no

## After Filling the Template

Return intake-dba-ddl-evidence.md to the repo (docs/evidence/intake-dba-ddl-evidence.md)
so the Engineer and Oncall can run the completion gate validator:

  bash scripts/validate-phase-2-external-evidence-completion.sh

## Full Execution Pack Reference

See: docs/evidence/phase-2-external-execution-pack.md — Section A
See: docs/evidence/phase-2-dba-checklist.md
See: docs/evidence/phase-2-external-readiness-dashboard.md
EOF

# Ops instructions
cat > "$BUNDLE_DIR/Ops/instructions.md" <<'EOF'
# Ops — Phase 2 Handoff Instructions

**Bundle generated:** see ../git-state/git-head.txt for exact commit
**Your intake template:** intake-ops-xxl-job-evidence.md (in this folder)

> THIS IS NOT AN APPROVAL. Fill this template during real staging/production execution.
> All dangerous flags remain false. See ../NOT-AN-APPROVAL.txt.

## Your Gate: OA1–OA6

You are responsible for registering XXL-Job handlers in staging and production XXL-Job
admin. The same two handlers are required in both environments.

## Execution Order

### Phase 1 — Staging XXL-Job Registration (unblocks B17 E2E)

Prerequisite: DBA staging DDL complete (DA1–DA9 PASS).

In the staging XXL-Job admin UI:
1. Navigate to: Job Management → Executor: big-market-message-job-service
2. Create DispatchCreditAwardTaskJob_DB1 (Cron: 0/30 * * * * ?, FIRST routing)
3. Create DispatchCreditAwardTaskJob_DB2 (same spec)
4. Manually trigger DB1 → confirm exitCode=200 in execution log
5. Manually trigger DB2 → confirm exitCode=200 in execution log
6. Fill OA1–OA4 in intake-ops-xxl-job-evidence.md
7. Update "Staging Handler Registration" and "Ops Staging Sign-Off" rows to PASS

### Phase 3 — Production XXL-Job Registration (unblocks P5 outbox flag enable)

Only after Oncall issues B23-C SE11 GO decision and DBA production DDL is complete.

1. Register DispatchCreditAwardTaskJob_DB1 in production XXL-Job (same spec as staging)
2. Register DispatchCreditAwardTaskJob_DB2 in production XXL-Job
3. Do NOT trigger production jobs yet — wait for Engineer/Oncall to enable the outbox flag
4. Fill OA5–OA6 in intake-ops-xxl-job-evidence.md
5. Update "Production Handler Registration" and "Ops Production Sign-Off" rows to PASS

## Hard NO-GO Triggers

Stop and escalate if:
- XXL-Job executor big-market-message-job-service is not online
- Manual trigger returns exitCode ≠ 200 or logs contain exception/error
- DispatchCreditAwardTaskJob is running in fulfillment-service (must stay in message-job-service)

## After Filling the Template

Return intake-ops-xxl-job-evidence.md to the repo (docs/evidence/intake-ops-xxl-job-evidence.md)
so the Engineer and Oncall can run the completion gate validator:

  bash scripts/validate-phase-2-external-evidence-completion.sh

## Full Execution Pack Reference

See: docs/evidence/phase-2-external-execution-pack.md — Section B
See: docs/evidence/phase-2-ops-xxl-job-checklist.md
See: docs/evidence/phase-2-external-readiness-dashboard.md
EOF

# Engineer instructions
cat > "$BUNDLE_DIR/Engineer/instructions.md" <<'EOF'
# Engineer — Phase 2 Handoff Instructions

**Bundle generated:** see ../git-state/git-head.txt for exact commit
**Your intake template:** intake-engineer-b17-b23c-e2e-evidence.md (in this folder)
**Validator outputs:** see ../validator-outputs/ for current repo state

> THIS IS NOT AN APPROVAL. Fill this template during real staging/production execution.
> All dangerous flags remain false. See ../NOT-AN-APPROVAL.txt.

## Your Gate: EA1–EA10

You are responsible for static pre-flight validation, executing E2E staging tests,
and enabling/restoring flags during validated maintenance windows. Do NOT enable
production flags without oncall written approval.

## Execution Order

### Pre-flight (any time, no staging/prod access required)

Run all static validators and confirm ALL PASS before any staging action:

  bash scripts/validate-phase-2-external-evidence-intake.sh
  bash scripts/validate-phase-2-external-evidence-completion.sh
  bash scripts/validate-phase-2-evidence-consistency.sh
  bash scripts/validate-phase-2-external-execution-pack.sh
  bash scripts/validate-fulfillment-service-phase-2-3.sh

  # Generate local evidence snapshot (gitignored — never committed)
  bash scripts/collect-phase-2-external-evidence.sh

Fill EA1–EA2 in intake-engineer-b17-b23c-e2e-evidence.md with pre-flight evidence.

### Phase 1 — B17 Staging E2E (after DBA Phase 1 and Ops Phase 1 complete)

1. Run CONNECT_REMOTE verification (0 FAIL required)
2. Enable remote-quota-decrement flag in staging market-service
3. Run armory step
4. Run E2E draw test (fill Phases F–H in b17-staging-evidence-20260610.md)
5. Restore remote-quota-decrement flag to false
6. Run post-window verification (0 FAIL required)
7. Fill EA3–EA6 in intake-engineer-b17-b23c-e2e-evidence.md

### Phase 2 — B23-C Staging Evidence (after Oncall issues B17 Phase K GO)

1. Enable ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true in staging message-job-service
2. Insert test outbox row, trigger DispatchCreditAwardTaskJob_DB1
3. Verify: state=dispatched, user_credit_order count=1 (idempotency)
4. Enable ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true, validate Dubbo path
5. Restore all flags to false
6. Fill EA7–EA10 in intake-engineer-b17-b23c-e2e-evidence.md
7. Fill SE1–SE11 in docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md

### Phase 4 — B23-E Cutover (after Oncall issues P4 written approval)

Follow docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md:
- Staging cutover steps S1–S8
- Production cutover steps P1–P8
- Evidence table E1–E12

## Hard NO-GO Triggers

Stop and escalate if:
- Any static validator fails (non-zero exit)
- Any dangerous flag found hardcoded true in any config file
- user_credit_order count > 1 for same out_business_no (double-credit)
- Any quota change on duplicate draw (idempotency violation)
- Draw endpoint error rate > 0% during any canary window
- fulfillment-service OOM during any canary window
- DispatchCreditAwardTaskJob running in fulfillment-service (must stay in message-job-service)

## After Filling the Template

Return intake-engineer-b17-b23c-e2e-evidence.md to the repo so Oncall can review:

  bash scripts/validate-phase-2-external-evidence-completion.sh

## Full Execution Pack Reference

See: docs/evidence/phase-2-external-execution-pack.md — Section C
See: docs/evidence/phase-2-external-readiness-dashboard.md
EOF

# Oncall instructions
cat > "$BUNDLE_DIR/Oncall/instructions.md" <<'EOF'
# Oncall — Phase 2 Handoff Instructions

**Bundle generated:** see ../git-state/git-head.txt for exact commit
**Your intake template:** intake-oncall-signoff-evidence.md (in this folder)
**Validator outputs:** see ../validator-outputs/ for current repo state

> THIS IS NOT AN APPROVAL. Fill this template during real staging/production execution.
> All dangerous flags remain false. See ../NOT-AN-APPROVAL.txt.

## Your Gate: OC1–OC5

You are responsible for reviewing evidence at each gate, issuing written GO decisions,
and approving production flag enable windows. No code or DDL changes are made by Oncall.

## Execution Order

### Gate OC1 — B17 Phase K GO (after DBA Phase 1 + Ops Phase 1 + Engineer B17 E2E)

Review: DA1–DA9 (DBA staging), OA1–OA4 (Ops staging), EA3–EA6 (Engineer B17 E2E)
Sign: Phase K in b17-staging-evidence-20260610.md
Action: Fill OC1 row in intake-oncall-signoff-evidence.md with GO/NO-GO + name + timestamp

Unblocks: B23-C staging evidence (EA7–EA10)

### Gate OC2 — B23-C SE11 GO (after Engineer B23-C evidence complete)

Review: SE1–SE10 in phase-2-3-c-fulfillment-staging-readiness.md
Sign: SE11 staging GO decision
Action: Fill OC2 row → GO

Unblocks: B23-D production gate (Phase 3)

### Gate OC3 — B23-D Phase E Production Gate GO (after DBA Phase 3 + Ops Phase 3)

Review: DA10–DA14 (DBA production), OA5–OA6 (Ops production), B23-D Phase A–D evidence
Issue: written approval for production cutover window
Sign: Phase E in phase-2-3-d-fulfillment-production-promotion-gate.md
Action: Fill OC3 row → GO

Unblocks: Phase 4 B23-E cutover window

### Gate OC4 — P4 Written Approval (immediately before production flag enable)

Record the following in phase-2-3-e-fulfillment-cutover-execution.md P4 checkpoint:
  Oncall lead name:              ___
  Approval timestamp:            ___
  Approved cutover window:       ___
  Any conditions or restrictions: ___
Action: Fill OC4 row → APPROVED

Unblocks: P5 production flag enable (ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true)

### Gate OC5 — B23-E Final GO Decision (after evidence table E1–E12 complete)

Review: E1–E12 in phase-2-3-e-fulfillment-cutover-execution.md
Confirm: ≥30 min clean post-cutover window, zero double-credit
Sign: Final Phase E GO decision
Action: Fill OC5 row → GO

This is the final gate. B23-E Phase E = GO closes the migration.

## Hard NO-GO Triggers

Escalate immediately if:
- Any validator script fails
- Any dangerous flag found hardcoded true in any config file
- Double-credit detected (user_credit_order count > 1 for same out_business_no)
- Any idempotency violation during E2E
- Draw endpoint error rate > 0% during any canary window
- Oncall written approval (OC4) not recorded before P5 production flag enable
- DispatchCreditAwardTaskJob found running in fulfillment-service

## After Signing

Update the relevant rows in intake-oncall-signoff-evidence.md (OC1–OC5) and return
the file to the repo so the completion gate validator reflects your decision:

  bash scripts/validate-phase-2-external-evidence-completion.sh

## Full Execution Pack Reference

See: docs/evidence/phase-2-external-execution-pack.md — Section D
See: docs/evidence/phase-2-external-readiness-dashboard.md
EOF

echo "[6/9] Role-specific instruction files written."

# ── Derive current readiness state from completion state ─────────────────────

echo "[7/9] Deriving current readiness state..."

# Re-use the completion state logic inline (no external call to avoid recursion)
_completion_state() {
  local file="$1"
  [ -f "$file" ] || { echo "MALFORMED"; return; }
  grep -q "^## Completion Status" "$file" 2>/dev/null || { echo "MALFORMED"; return; }
  local section
  section=$(awk '/^## Completion Status/{in_s=1;next} in_s && /^## /{in_s=0} in_s{print}' "$file" 2>/dev/null || true)
  local todo pend pass go fail nogo total
  todo=$(printf '%s\n' "$section" | grep -c '| TODO |' || true)
  pend=$(printf '%s\n' "$section" | grep -c '| PENDING |' || true)
  pass=$(printf '%s\n' "$section" | grep -c '| PASS |' || true)
  go=$(printf '%s\n' "$section" | grep -c ' | GO |' || true)
  fail=$(printf '%s\n' "$section" | grep -c '| FAIL |' || true)
  nogo=$(printf '%s\n' "$section" | grep -c '| NO-GO |' || true)
  total=$((todo + pend + pass + go + fail + nogo))
  [ "$total" -eq 0 ] && { echo "MALFORMED"; return; }
  [ "$nogo" -gt 0 ] || [ "$fail" -gt 0 ] && { echo "NO_GO"; return; }
  local incomplete=$((todo + pend))
  local complete=$((pass + go))
  if [ "$incomplete" -eq "$total" ]; then echo "TEMPLATE_READY"
  elif [ "$complete" -eq "$total" ]; then echo "COMPLETE"
  else echo "PARTIAL"
  fi
}

DBA_STATE=$(_completion_state "$ROOT/docs/evidence/intake-dba-ddl-evidence.md")
OPS_STATE=$(_completion_state "$ROOT/docs/evidence/intake-ops-xxl-job-evidence.md")
ENG_STATE=$(_completion_state "$ROOT/docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md")
OC_STATE=$(_completion_state "$ROOT/docs/evidence/intake-oncall-signoff-evidence.md")

if [ "$DBA_STATE" = "COMPLETE" ] && [ "$OPS_STATE" = "COMPLETE" ] \
   && [ "$ENG_STATE" = "COMPLETE" ] && [ "$OC_STATE" = "COMPLETE" ]; then
  B23E_GATE="READY — all four roles COMPLETE"
else
  B23E_GATE="BLOCKED — external evidence pending"
fi

# ── Write README.md ────────────────────────────────────────────────────────────

cat > "$BUNDLE_DIR/README.md" <<EOF
# Phase 2 External Handoff Bundle

**Generated:** $TIMESTAMP
**Git HEAD:** $(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo "unknown")
**Latest commit:** $(git -C "$ROOT" log --oneline -1 2>/dev/null || echo "unknown")

> **THIS BUNDLE IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC.**
> All three dangerous flags remain \`false\` by default.
> See \`NOT-AN-APPROVAL.txt\` for full safety disclaimer.

---

## Current Completion Gate State

| Role | Status | Template |
|------|--------|---------|
| DBA | **$DBA_STATE** | DBA/intake-dba-ddl-evidence.md |
| Ops | **$OPS_STATE** | Ops/intake-ops-xxl-job-evidence.md |
| Engineer | **$ENG_STATE** | Engineer/intake-engineer-b17-b23c-e2e-evidence.md |
| Oncall | **$OC_STATE** | Oncall/intake-oncall-signoff-evidence.md |
| **B23-E overall gate** | **$B23E_GATE** | all four roles must be COMPLETE |

---

## Bundle Contents

\`\`\`
phase2-handoff-bundle-$TIMESTAMP/
  README.md                         — this file
  MANIFEST.md                       — all source docs and scripts used
  NOT-AN-APPROVAL.txt               — safety disclaimer
  git-state/
    git-head.txt                    — commit hash + recent log
    git-status.txt                  — working tree status
    git-tags.txt                    — all local tags
  validator-outputs/
    validate-intake.txt             — intake template structure check
    validate-completion.txt         — completion gate check (per-role state)
    validate-consistency.txt        — doc coverage + flag scan + cross-links
    validate-execution-pack.txt     — full external execution pack check
  DBA/
    intake-dba-ddl-evidence.md     — fill DA1–DA14 during execution
    instructions.md                 — role-specific instructions
  Ops/
    intake-ops-xxl-job-evidence.md — fill OA1–OA6 during execution
    instructions.md
  Engineer/
    intake-engineer-b17-b23c-e2e-evidence.md  — fill EA1–EA10 during execution
    instructions.md
  Oncall/
    intake-oncall-signoff-evidence.md  — fill OC1–OC5 during execution
    instructions.md
\`\`\`

---

## Strict Execution Order

\`\`\`
Phase 1 — B17 Staging GO
  DBA   → apply staging DDL (big_market_01 + big_market_02) → verify unique keys → sign DA1–DA9
  Ops   → register XXL-Job handlers in staging → manual trigger SUCCESS → sign OA1–OA4
  Engineer → run B17 E2E (EA3–EA6) + fill b17-staging-evidence-20260610.md → sign pre-flight
  Oncall → review DA1–DA9 + OA1–OA4 + EA3–EA6 → issue B17 Phase K GO (OC1)
           ↓  gate: B17 Phase K = GO

Phase 2 — B23-C Staging Evidence
  Engineer → run B23-C outbox E2E + Dubbo E2E + idempotency + flag restore → sign EA7–EA10
             fill SE1–SE11 in phase-2-3-c-fulfillment-staging-readiness.md
  Oncall   → review SE1–SE10 → issue B23-C SE11 GO (OC2)
           ↓  gate: B23-C SE11 = GO

Phase 3 — B23-D Production Gate
  DBA   → apply production DDL → verify unique keys → sign DA10–DA14
  Ops   → register XXL-Job handlers in production → sign OA5–OA6
  Oncall → review + issue written approval for production window (OC3)
         → issue P4 written approval before flag enable (OC4)
           ↓  gate: B23-D Phase E = GO + OC4 = APPROVED

Phase 4 — B23-E Cutover Execution
  Engineer → execute S1–S8 (staging) + P1–P8 (production) per cutover runbook
  Oncall   → review E1–E12 → issue final B23-E GO (OC5)
           ↓  gate: B23-E Phase E = GO  ← DONE
\`\`\`

---

## Remaining External Blockers

These require real staging/production access and cannot be resolved by repo-only changes.

| # | Blocker | Owner | Unblocks |
|---|---------|-------|---------|
| X1 | Apply staging ledger + outbox DDL to big_market_01 and big_market_02 | DBA | B17 E2E |
| X2 | Verify staging unique keys (uq_award_order_id, uq_out_business_no, uq_user_activity_biz) | DBA | DA5–DA9 |
| X3 | Register DispatchCreditAwardTaskJob_DB1/_DB2 in staging XXL-Job + manual trigger SUCCESS | Ops | B17 E2E |
| X4 | Run B17 staging E2E (EA3–EA6: draw, ledger, quota decrement, flag restore) | Engineer | OC1 B17 Phase K |
| X5 | Issue B17 Phase K GO decision | Oncall | B23-C staging |
| X6 | Run B23-C E2E (EA7–EA10: outbox, idempotency, Dubbo, flag restore) | Engineer | OC2 B23-C SE11 |
| X7 | Issue B23-C SE11 staging GO decision | Oncall | B23-D production gate |
| X8 | Apply production outbox DDL to big_market_01 and big_market_02 | DBA | P5 flag enable |
| X9 | Register DispatchCreditAwardTaskJob_DB1/_DB2 in production XXL-Job | Ops | P5 flag enable |
| X10 | Issue B23-D Phase E GO + P4 written approval | Oncall | B23-E cutover |
| X11 | Execute B23-E cutover (S1–S8 staging, P1–P8 production) | Engineer | OC5 final GO |
| X12 | Issue B23-E final GO decision (OC5) | Oncall | DONE |

---

## How to Use This Bundle

1. **Distribute role folders**: give DBA/ to DBA, Ops/ to Ops, etc.
   Each folder contains the intake template to fill and role-specific instructions.

2. **Fill templates during execution**: each role fills their intake template as they
   perform the real-world steps listed in their instructions.md.

3. **Return completed templates to the repo**: place the filled templates back in
   docs/evidence/ and commit. Then run:
     bash scripts/validate-phase-2-external-evidence-completion.sh

4. **Gate opens when all four roles are COMPLETE**: re-run the completion validator
   to confirm. B23-E cutover must NOT start until the gate shows all four roles COMPLETE.

---

## Repo Validators to Run (Before Any Staging/Production Action)

\`\`\`bash
# 1. Check intake template structure (64 checks)
bash scripts/validate-phase-2-external-evidence-intake.sh

# 2. Check per-role completion state and B23-E gate
bash scripts/validate-phase-2-external-evidence-completion.sh

# 3. Check Phase 2.2/2.3 doc coverage, gitignore, tags, flags, cross-links
bash scripts/validate-phase-2-evidence-consistency.sh

# 4. Validate full external execution pack artifacts
bash scripts/validate-phase-2-external-execution-pack.sh

# 5. Full Phase 2.3 suite (B23-B/C/D/E validators + flag scan + tag check)
bash scripts/validate-fulfillment-service-phase-2-3.sh

# 6. Validate this handoff bundle generator
bash scripts/validate-phase-2-external-handoff-bundle.sh

# 7. Collect local evidence snapshot (gitignored)
bash scripts/collect-phase-2-external-evidence.sh

# 8. Build (no tests)
mvn clean package -DskipTests
\`\`\`

---

## Validator Output Summary

Check validator-outputs/ for current state:
- validate-intake.txt       — intake template structure (64 checks)
- validate-completion.txt   — per-role completion state
- validate-consistency.txt  — doc coverage, flags, cross-links
- validate-execution-pack.txt — full pack validation

---

## Related Repo Documents

- docs/evidence/phase-2-external-execution-pack.md
- docs/evidence/phase-2-external-readiness-dashboard.md
- docs/evidence/phase-2-3-fulfillment-final-readiness-index.md
- docs/evidence/phase-2-dba-checklist.md
- docs/evidence/phase-2-ops-xxl-job-checklist.md
EOF

echo "[7/9] README.md written."

# ── Write MANIFEST.md ──────────────────────────────────────────────────────────

{
  echo "# Phase 2 Handoff Bundle — Source Manifest"
  echo ""
  echo "**Generated:** $TIMESTAMP"
  echo ""
  echo "All source files used to produce this bundle are listed below."
  echo "SHA-256 checksums are provided for intake templates to enable integrity verification."
  echo ""
  echo "## Intake Templates (copied to role folders)"
  echo ""
  for item in \
    "docs/evidence/intake-dba-ddl-evidence.md:DBA/intake-dba-ddl-evidence.md" \
    "docs/evidence/intake-ops-xxl-job-evidence.md:Ops/intake-ops-xxl-job-evidence.md" \
    "docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md:Engineer/intake-engineer-b17-b23c-e2e-evidence.md" \
    "docs/evidence/intake-oncall-signoff-evidence.md:Oncall/intake-oncall-signoff-evidence.md"; do
    src="${item%%:*}"
    dst="${item##*:}"
    if [ -f "$ROOT/$src" ]; then
      SHA=$(shasum -a 256 "$ROOT/$src" 2>/dev/null | awk '{print $1}' || echo "n/a")
      LINES=$(wc -l < "$ROOT/$src" 2>/dev/null || echo "?")
      echo "- [$src]($ROOT/$src)"
      echo "  - Copied to: $dst"
      echo "  - Lines: $LINES"
      echo "  - SHA-256: $SHA"
    else
      echo "- [$src] — MISSING"
    fi
    echo ""
  done
  echo ""
  echo "## Evidence Documents (referenced, not copied)"
  echo ""
  for doc in \
    "docs/evidence/phase-2-external-execution-pack.md" \
    "docs/evidence/phase-2-external-readiness-dashboard.md" \
    "docs/evidence/phase-2-3-fulfillment-final-readiness-index.md" \
    "docs/evidence/phase-2-dba-checklist.md" \
    "docs/evidence/phase-2-ops-xxl-job-checklist.md" \
    "docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md" \
    "docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md" \
    "docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md" \
    "docs/evidence/b17-staging-evidence-20260610.md" \
    "docs/evidence/phase-2-2-b17-staging-cutover-template.md"; do
    if [ -f "$ROOT/$doc" ]; then
      LINES=$(wc -l < "$ROOT/$doc" 2>/dev/null || echo "?")
      echo "- $doc ($LINES lines)"
    else
      echo "- $doc — MISSING"
    fi
  done
  echo ""
  echo "## SQL Files"
  echo ""
  for sql in \
    "docs/sql/proposed-quota-decrement-ledger.sql" \
    "docs/sql/proposed-credit-award-task-outbox.sql"; do
    if [ -f "$ROOT/$sql" ]; then
      SHA=$(shasum -a 256 "$ROOT/$sql" 2>/dev/null | awk '{print $1}' || echo "n/a")
      LINES=$(wc -l < "$ROOT/$sql" 2>/dev/null || echo "?")
      echo "- $sql ($LINES lines, sha256: $SHA)"
    else
      echo "- $sql — MISSING"
    fi
  done
  echo ""
  echo "## Validator Scripts"
  echo ""
  for script in \
    "scripts/prepare-phase-2-external-handoff-bundle.sh" \
    "scripts/validate-phase-2-external-handoff-bundle.sh" \
    "scripts/validate-phase-2-external-evidence-intake.sh" \
    "scripts/validate-phase-2-external-evidence-completion.sh" \
    "scripts/validate-phase-2-evidence-consistency.sh" \
    "scripts/validate-phase-2-external-execution-pack.sh" \
    "scripts/validate-fulfillment-service-phase-2-3.sh" \
    "scripts/collect-phase-2-external-evidence.sh"; do
    if [ -f "$ROOT/$script" ]; then
      SHA=$(shasum -a 256 "$ROOT/$script" 2>/dev/null | awk '{print $1}' || echo "n/a")
      LINES=$(wc -l < "$ROOT/$script" 2>/dev/null || echo "?")
      echo "- $script ($LINES lines, sha256: $SHA)"
    else
      echo "- $script — MISSING"
    fi
  done
  echo ""
  echo "## Git State"
  echo ""
  echo "- HEAD: $(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo "unknown")"
  echo "- Commit: $(git -C "$ROOT" log --oneline -1 2>/dev/null || echo "unknown")"
  echo ""
  echo "## Completion States at Bundle Generation Time"
  echo ""
  echo "| Role | State |"
  echo "|------|-------|"
  echo "| DBA | $DBA_STATE |"
  echo "| Ops | $OPS_STATE |"
  echo "| Engineer | $ENG_STATE |"
  echo "| Oncall | $OC_STATE |"
  echo "| B23-E gate | $B23E_GATE |"
} > "$BUNDLE_DIR/MANIFEST.md"

echo "[8/9] MANIFEST.md written."

# ── Final summary ─────────────────────────────────────────────────────────────

echo "[9/9] Writing bundle summary..."

BUNDLE_SIZE=$(find "$BUNDLE_DIR" -type f | wc -l | tr -d ' ')

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  Phase 2 External Handoff Bundle — COMPLETE"
echo "════════════════════════════════════════════════════════════════════════════"
echo ""
echo "  Bundle directory : $BUNDLE_DIR"
echo "  Files created    : $BUNDLE_SIZE"
echo ""
echo "  Completion gate state at bundle time:"
echo "    DBA      : $DBA_STATE"
echo "    Ops      : $OPS_STATE"
echo "    Engineer : $ENG_STATE"
echo "    Oncall   : $OC_STATE"
echo "    B23-E    : $B23E_GATE"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo "  RESULT: BUNDLE CREATED SUCCESSFULLY"
  echo ""
  echo "  THIS BUNDLE IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC."
  echo "  All three dangerous flags remain false by default."
  echo "  The generated output is gitignored and will not be committed."
  echo ""
  echo "  To validate this bundle generator:"
  echo "    bash scripts/validate-phase-2-external-handoff-bundle.sh"
  echo ""
  echo "  To validate a specific bundle:"
  echo "    bash scripts/validate-phase-2-external-handoff-bundle.sh $BUNDLE_DIR"
  exit 0
else
  echo "  RESULT: $FAIL STEP(S) FAILED — review output above"
  echo ""
  echo "  Fix all failures before distributing this bundle."
  exit 1
fi
