# Phase 2 External Readiness Dashboard

**Date:** 2026-06-10
**Status:** TEMPLATE_READY — intake templates exist; external evidence not yet collected
**Last updated by:** phase-2-external-evidence-completion-gates batch

> **THIS DOCUMENT IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC.**
> All three dangerous flags remain `false` by default.
> Update the completion status tables in the intake templates as real-world evidence is collected.
> Re-run `bash scripts/validate-phase-2-external-evidence-completion.sh` to refresh this dashboard view.

---

## B23-E Cutover Gate

| Gate | Requirement | Current Status |
|------|-------------|---------------|
| DBA DDL evidence | DA1–DA14 filled + staging/production sign-offs | **TEMPLATE_READY** |
| Ops XXL-Job evidence | OA1–OA6 filled + staging/production sign-offs | **TEMPLATE_READY** |
| Engineer E2E evidence | EA1–EA10 filled + B17/B23-C sign-offs | **TEMPLATE_READY** |
| Oncall sign-off | OC1–OC5 all GO + P4 written approval | **TEMPLATE_READY** |
| **B23-E overall gate** | All four roles COMPLETE | **BLOCKED** |

> **Hard gate:** B23-E cutover execution must NOT start until all four roles show COMPLETE.
> COMPLETE means: all checks in the intake template's `## Completion Status` table are PASS or GO.

---

## Role Evidence Status

### DBA — `docs/evidence/intake-dba-ddl-evidence.md`

| Check | Current Status | Required To Change |
|-------|---------------|--------------------|
| Staging DDL Gate (DA1–DA9) | **TODO** | DBA applies staging DDL + verifies unique keys |
| Production DDL Gate (DA10–DA14) | **TODO** | DBA applies production DDL + verifies unique keys |
| DBA Staging Sign-Off | **TODO** | DBA enters name + timestamp, changes to PASS |
| DBA Production Sign-Off | **TODO** | DBA enters name + timestamp, changes to PASS |
| B23-E Gate Decision | **PENDING** | Oncall lead confirms DBA prerequisites met → GO |

**DBA overall:** TEMPLATE_READY

---

### Ops — `docs/evidence/intake-ops-xxl-job-evidence.md`

| Check | Current Status | Required To Change |
|-------|---------------|--------------------|
| Staging Handler Registration (OA1–OA4) | **TODO** | Ops registers DB1/DB2 handlers in staging XXL-Job + manual trigger SUCCESS |
| Production Handler Registration (OA5–OA6) | **TODO** | Ops registers DB1/DB2 handlers in production XXL-Job |
| Ops Staging Sign-Off | **TODO** | Ops enters name + timestamp, changes to PASS |
| Ops Production Sign-Off | **TODO** | Ops enters name + timestamp, changes to PASS |
| B23-E Gate Decision | **PENDING** | Oncall lead confirms Ops prerequisites met → GO |

**Ops overall:** TEMPLATE_READY

---

### Engineer — `docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md`

| Check | Current Status | Required To Change |
|-------|---------------|--------------------|
| Pre-flight Gate (EA1–EA2) | **TODO** | Engineer runs static validators all PASS |
| B17 E2E Gate (EA3–EA6) | **TODO** | Engineer runs B17 staging E2E + all rows CONFIRMED |
| B23-C E2E Gate (EA7–EA10) | **TODO** | Engineer runs B23-C outbox + Dubbo E2E + flag restore |
| Engineer B17 Sign-Off | **TODO** | Engineer enters name + timestamp, changes to PASS |
| Engineer B23-C Sign-Off | **TODO** | Engineer enters name + timestamp, changes to PASS |
| B23-E Gate Decision | **PENDING** | Oncall lead issues B23-C SE11 GO → then Engineer confirms → GO |

**Engineer overall:** TEMPLATE_READY

---

### Oncall — `docs/evidence/intake-oncall-signoff-evidence.md`

| Check | Current Status | Required To Change |
|-------|---------------|--------------------|
| OC1 B17 Phase K Decision | **PENDING** | Oncall reviews DA1–DA9 + OA1–OA4 + EA3–EA6; issues GO |
| OC2 B23-C SE11 Decision | **PENDING** | Oncall reviews B23-C evidence; issues GO |
| OC3 B23-D Phase E Decision | **PENDING** | Oncall reviews production gate evidence; issues GO |
| OC4 P4 Written Approval | **PENDING** | Oncall issues written approval before P5 flag enable |
| OC5 B23-E Final Decision | **PENDING** | Oncall reviews E1–E12; issues final GO |
| B23-E Gate Decision | **PENDING** | Oncall issues final GO after all post-cutover clean windows |

**Oncall overall:** TEMPLATE_READY

---

## Remaining External Blockers

These items require real staging/production access and cannot be resolved by repo-only changes.

| # | Blocker | Owner | Prerequisite | Unblocks |
|---|---------|-------|--------------|---------|
| X1 | Apply staging ledger DDL + outbox DDL to big_market_01 and big_market_02 | DBA | None | B17 E2E |
| X2 | Verify staging unique keys: uq_award_order_id, uq_out_business_no, uq_user_activity_biz | DBA | X1 | DA5–DA9 |
| X3 | Apply production outbox DDL to big_market_01 and big_market_02 | DBA | B23-C SE11 GO | P5 flag enable |
| X4 | Register DispatchCreditAwardTaskJob_DB1/_DB2 in staging XXL-Job + manual trigger SUCCESS | Ops | X1 (DBA staging signed) | B17 E2E |
| X5 | Register DispatchCreditAwardTaskJob_DB1/_DB2 in production XXL-Job | Ops | X3 (DBA production signed) | P5 flag enable |
| X6 | Run B17 staging E2E (EA3–EA6: draw, ledger, quota decrement, flag restore) | Engineer | X1 + X4 | OC1 B17 Phase K |
| X7 | Run B23-C E2E (EA7–EA10: outbox, idempotency, Dubbo, flag restore) | Engineer | OC1 GO + X4 | OC2 B23-C SE11 |
| X8 | Issue B17 Phase K GO decision | Oncall | DA1–DA9 + OA1–OA4 + EA3–EA6 reviewed | B23-C staging |
| X9 | Issue B23-C SE11 staging GO decision | Oncall | EA7–EA10 reviewed | B23-D production gate |
| X10 | Issue B23-D Phase E production gate GO | Oncall | DA10–DA14 + OA5–OA6 + SE1–SE10 reviewed | B23-E cutover |
| X11 | Issue P4 written approval for production flag enable | Oncall | B23-D signed | P5 production flag enable |
| X12 | Execute B23-E cutover (S1–S8 staging, P1–P8 production) | Engineer | OC4 approval + all above | OC5 final GO |

---

## Execution Ordering (strict)

```
Phase 1 — B17 Staging GO
  DBA: X1 (DDL) → X2 (key verify) → staging sign-off
  Ops: X4 (XXL-Job register + trigger)
  Engineer: X6 (B17 E2E + flag restore)
  Oncall: X8 (Phase K GO)
          ↓ gate: B17 Phase K = GO

Phase 2 — B23-C Staging Evidence
  Engineer: X7 (outbox E2E + Dubbo E2E + idempotency + flag restore)
  Oncall: X9 (SE11 GO)
          ↓ gate: B23-C SE11 = GO

Phase 3 — B23-D Production Gate
  DBA: X3 (production DDL) + sign-off
  Ops: X5 (production XXL-Job register)
  Oncall: X10 (B23-D Phase E GO) + X11 (P4 written approval)
          ↓ gate: B23-D Phase E = GO + OC4 = APPROVED

Phase 4 — B23-E Cutover
  Engineer: X12 (S1–S8 staging + P1–P8 production)
  Oncall: OC5 (final GO)
          ↓ gate: B23-E Phase E = GO
```

---

## Dangerous Flag Safety

All three dangerous flags remain `false` by default. No repo-only action enables any flag.

| Flag | Current Value | Hard Rule |
|------|-------------|-----------|
| `account.award-credit-outbox.enabled` | `false` | Never enable without DBA DDL confirmation + unique-key verification |
| `account.fulfillment.remote-award.enabled` | `false` | Never enable before outbox flag is stable and B23-C staging evidence signed |
| `account.service.remote-quota-decrement.enabled` | `false` | Phase 2.2 separate gate — not in scope here |

---

## Validators to Run

Run these in order. All must pass before any staging or production action.

```bash
# 1. Check intake template structure (64 checks)
bash scripts/validate-phase-2-external-evidence-intake.sh

# 2. Check completion state per role and B23-E gate
bash scripts/validate-phase-2-external-evidence-completion.sh

# 3. Check Phase 2.2/2.3 doc coverage, gitignore, tags, flags, cross-links
bash scripts/validate-phase-2-evidence-consistency.sh

# 4. Validate full external execution pack artifacts
bash scripts/validate-phase-2-external-execution-pack.sh

# 5. Full Phase 2.3 suite (B23-B/C/D/E validators + flag scan + tag check)
bash scripts/validate-fulfillment-service-phase-2-3.sh

# 6. Collect local evidence snapshot (output: docs/evidence/generated/ — gitignored)
bash scripts/collect-phase-2-external-evidence.sh
```

---

## Generated Evidence

`docs/evidence/generated/` is in `.gitignore` — all local snapshots are local-only and never committed.
Run `bash scripts/collect-phase-2-external-evidence.sh` to capture a timestamped snapshot.

---

## Related Documents

| Document | Purpose |
|----------|---------|
| [`docs/evidence/intake-dba-ddl-evidence.md`](intake-dba-ddl-evidence.md) | DBA intake template (DA1–DA14) |
| [`docs/evidence/intake-ops-xxl-job-evidence.md`](intake-ops-xxl-job-evidence.md) | Ops intake template (OA1–OA6) |
| [`docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md`](intake-engineer-b17-b23c-e2e-evidence.md) | Engineer intake template (EA1–EA10) |
| [`docs/evidence/intake-oncall-signoff-evidence.md`](intake-oncall-signoff-evidence.md) | Oncall intake template (OC1–OC5) |
| [`docs/evidence/phase-2-external-execution-pack.md`](phase-2-external-execution-pack.md) | Full external execution pack (role tasks, evidence tables, gate summary) |
| [`docs/evidence/phase-2-3-fulfillment-final-readiness-index.md`](phase-2-3-fulfillment-final-readiness-index.md) | Final readiness index (batch history, artifact links) |
| [`scripts/validate-phase-2-external-evidence-completion.sh`](../../scripts/validate-phase-2-external-evidence-completion.sh) | Completion gate validator (this dashboard's automated check) |
