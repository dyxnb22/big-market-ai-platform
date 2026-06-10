# Phase 2 Oncall Lead Sign-Off Evidence — Intake Template

**Status:** TEMPLATE — fill in as each gate decision is issued
**Owner:** Oncall Lead
**Last updated:** ___

> **THIS TEMPLATE IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC.**
> Each sign-off in this template must be backed by reviewed evidence from DBA, Ops, and Engineer.
> A sign-off without reviewing the referenced evidence is not valid.
>
> **Who fills this:** Oncall Lead only.
> **Who is blocked by this:** Engineer cannot proceed past each gate without the corresponding sign-off.
> **Generated evidence** must be stored locally or in a secure artifact store.
> Files in `docs/evidence/generated/` are gitignored and must never be committed to this repo.

---

## Dangerous Flag Safety

All three dangerous flags must remain `false` in all config files until the oncall lead explicitly
approves each flag enable step. No sign-off in this template enables flags automatically.

| Flag | Hard Rule |
|------|-----------|
| `account.award-credit-outbox.enabled` | Oncall must verify DBA DDL + unique-key evidence before approving |
| `account.fulfillment.remote-award.enabled` | Oncall must verify outbox flag is stable and B23-C evidence signed before approving |
| `account.service.remote-quota-decrement.enabled` | Phase 2.2 separate gate — not in scope here |

> **Production flag enable is a hard gate:** No Engineer may enable any production flag without
> the oncall lead's written approval recorded in the P4 Written Approval section below.

---

## B17 Phase K GO Decision

**Gate:** Blocks B23-C staging evidence (Phase 2).
**Review requirements before signing:**
- DA1–DA9 DBA staging evidence all SIGNED (`intake-dba-ddl-evidence.md`)
- OA1–OA4 Ops staging evidence all SUCCESS (`intake-ops-xxl-job-evidence.md`)
- EA3–EA6 B17 E2E evidence all CONFIRMED (`intake-engineer-b17-b23c-e2e-evidence.md`)
- Phase A–J of `docs/evidence/b17-staging-evidence-20260610.md` completed by Engineer

| # | Review Item | Reviewed | Notes |
|---|-------------|----------|-------|
| OC1-R1 | DBA staging sign-off (DA1–DA9 all SIGNED) | YES / NO | ___ |
| OC1-R2 | Ops staging sign-off (OA1–OA4 all SUCCESS) | YES / NO | ___ |
| OC1-R3 | B17 E2E sign-off (EA3–EA6 all CONFIRMED) | YES / NO | ___ |
| OC1-R4 | No double-credit observed | YES / NO | ___ |
| OC1-R5 | All dangerous flags restored to `false` after staging | YES / NO | ___ |

**B17 Phase K Decision:**

| # | Document | Section | Signed By | Timestamp | Decision |
|---|----------|---------|-----------|-----------|---------|
| OC1 | `b17-staging-evidence-20260610.md` | Phase K | ___ | ___ | **GO / NO-GO** |

> **If NO-GO:** Record reason: ___
> Do NOT proceed to B23-C staging. Open incident if GO criteria cannot be met within SLA.

---

## B23-C SE11 Staging GO Decision

**Gate:** Blocks B23-D production gate (Phase 3).
**Review requirements before signing:**
- B17 Phase K = GO (OC1 above)
- EA7–EA10 B23-C E2E evidence all CONFIRMED (`intake-engineer-b17-b23c-e2e-evidence.md`)
- SE1–SE10 of `docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md` completed

| # | Review Item | Reviewed | Notes |
|---|-------------|----------|-------|
| OC2-R1 | B17 Phase K GO issued (OC1 = GO) | YES / NO | ___ |
| OC2-R2 | B23-C outbox E2E confirmed (EA7 = CONFIRMED) | YES / NO | ___ |
| OC2-R3 | B23-C idempotency confirmed (EA8 = CONFIRMED, no double-credit) | YES / NO | ___ |
| OC2-R4 | B23-C remote-award Dubbo E2E confirmed (EA9 = CONFIRMED) | YES / NO | ___ |
| OC2-R5 | All flags restored to `false` after staging (EA10 = CONFIRMED) | YES / NO | ___ |
| OC2-R6 | SE1–SE10 items all checked in staging readiness doc | YES / NO | ___ |

**B23-C SE11 Decision:**

| # | Document | Section | Signed By | Timestamp | Decision |
|---|----------|---------|-----------|-----------|---------|
| OC2 | `phase-2-3-c-fulfillment-staging-readiness.md` | SE11 | ___ | ___ | **GO / NO-GO** |

> **If NO-GO:** Record reason: ___
> Do NOT proceed to B23-D production gate.

---

## B23-D Phase E Production Gate Sign-Off

**Gate:** Blocks B23-E production cutover (Phase 4).
**Review requirements before signing:**
- B23-C SE11 = GO (OC2 above)
- DA10–DA14 DBA production evidence all SIGNED (`intake-dba-ddl-evidence.md`)
- OA5–OA6 Ops production evidence all REGISTERED (`intake-ops-xxl-job-evidence.md`)
- Phase A–D of `docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md` completed

| # | Review Item | Reviewed | Notes |
|---|-------------|----------|-------|
| OC3-R1 | B23-C SE11 GO issued (OC2 = GO) | YES / NO | ___ |
| OC3-R2 | DBA production sign-off (DA10–DA14 all SIGNED) | YES / NO | ___ |
| OC3-R3 | Ops production sign-off (OA5–OA6 all REGISTERED) | YES / NO | ___ |
| OC3-R4 | B23-D Phase A static PASS | YES / NO | ___ |
| OC3-R5 | B23-D Phase B staging evidence validated | YES / NO | ___ |
| OC3-R6 | B23-D Phase C production DDL verified | YES / NO | ___ |
| OC3-R7 | B23-D Phase D canary window complete and clean | YES / NO | ___ |
| OC3-R8 | No double-credit at any step | YES / NO | ___ |

**B23-D Phase E Decision:**

| # | Document | Section | Signed By | Timestamp | Decision |
|---|----------|---------|-----------|-----------|---------|
| OC3 | `phase-2-3-d-fulfillment-production-promotion-gate.md` | Phase E | ___ | ___ | **GO / NO-GO** |

> **If NO-GO:** Record reason: ___
> Do NOT proceed to B23-E cutover window.

---

## P4 Written Approval for Production Flag Enable

**Gate:** Blocks P5 (production outbox flag enable) in B23-E cutover.
**This is the final oncall authorization before production flags are enabled.**

> This approval is required immediately before the Engineer executes step P5 in the B23-E cutover runbook.
> Without this written record, the Engineer must NOT enable any production flag.
> Hard gate: P4 written approval must be present before P5.

| Field | Value |
|-------|-------|
| Oncall lead name | ___ |
| Approval timestamp | ___ |
| Approved cutover window (start–end) | ___ |
| Environment scope | Production only — `big-market-message-job-service` and `big-market-fulfillment-service` |
| Flags approved for enable (in order) | 1. `account.award-credit-outbox.enabled=true` (P5), then 2. `account.fulfillment.remote-award.enabled=true` (P7 only after P6 canary clean) |
| Any conditions or restrictions | ___ |
| Rollback authority | Oncall lead and Engineer may both trigger rollback without further approval |

| # | Document | Section | Signed By | Timestamp | Decision |
|---|----------|---------|-----------|-----------|---------|
| OC4 | `phase-2-3-e-fulfillment-cutover-execution.md` | P4 | ___ | ___ | **APPROVED / DENIED** |

> **If DENIED:** Record reason: ___
> Do NOT proceed to P5.

---

## B23-E Final GO Decision

**Gate:** Final decision; marks Phase 2.3 production cutover complete.
**Review requirements before signing:**
- Evidence table E1–E12 in `docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md` complete
- Post-cutover window ≥30 min clean
- Zero double-credit observed at any step

| # | Review Item | Reviewed | Notes |
|---|-------------|----------|-------|
| OC5-R1 | Staging cutover S1–S8 complete and clean | YES / NO | ___ |
| OC5-R2 | Production canary P6 ≥15 min clean | YES / NO | ___ |
| OC5-R3 | Post-cutover P8 ≥30 min clean | YES / NO | ___ |
| OC5-R4 | Zero `user_credit_order` double-count at any step | YES / NO | ___ |
| OC5-R5 | Evidence table E1–E12 complete | YES / NO | ___ |
| OC5-R6 | All three dangerous flags in correct final state | YES / NO | ___ |

**B23-E Final Decision:**

| # | Document | Section | Signed By | Timestamp | Decision |
|---|----------|---------|-----------|-----------|---------|
| OC5 | `phase-2-3-e-fulfillment-cutover-execution.md` | Phase E | ___ | ___ | **GO / NO-GO** |

> **If NO-GO:** Record reason: ___
> Execute rollback per `phase-2-3-e-fulfillment-cutover-execution.md` rollback procedures.

---

## Oncall Sign-Off Summary

| Gate | Document | Section | Signed By | Timestamp | Decision |
|------|----------|---------|-----------|-----------|---------|
| B17 staging GO | `b17-staging-evidence-20260610.md` | Phase K | ___ | ___ | GO / NO-GO |
| B23-C staging GO | `phase-2-3-c-fulfillment-staging-readiness.md` | SE11 | ___ | ___ | GO / NO-GO |
| B23-D production gate | `phase-2-3-d-fulfillment-production-promotion-gate.md` | Phase E | ___ | ___ | GO / NO-GO |
| Production flag enable approval | `phase-2-3-e-fulfillment-cutover-execution.md` | P4 | ___ | ___ | APPROVED / DENIED |
| B23-E final GO | `phase-2-3-e-fulfillment-cutover-execution.md` | Phase E | ___ | ___ | GO / NO-GO |

---

## B23-E Cutover Approval Prerequisites (Oncall view)

Before the B23-E cutover window opens, the oncall lead must confirm all of the following:

| Check | Status |
|-------|--------|
| OC1 B17 Phase K = GO | PENDING / DONE |
| OC2 B23-C SE11 = GO | PENDING / DONE |
| OC3 B23-D Phase E = GO | PENDING / DONE |
| DBA staging + production sign-offs reviewed and accepted | PENDING / DONE |
| Ops staging + production sign-offs reviewed and accepted | PENDING / DONE |
| Engineer B17 + B23-C E2E sign-offs reviewed and accepted | PENDING / DONE |
| Cutover window approved (OC4) | PENDING / DONE |

> **Hard gate:** OC4 must be issued at the start of the cutover window, immediately before P5.

---

## NO-GO Rules for Oncall

Stop and refuse sign-off if ANY of the following:

1. Any referenced evidence item is MISSING, INCOMPLETE, or shows FAIL/ERROR.
2. Any dangerous flag found hardcoded `true` in any config file at review time.
3. DBA evidence shows missing `uq_award_order_id` or `uq_out_business_no` unique keys.
4. Engineer evidence shows any double-credit (`user_credit_order` count > 1 for same `out_business_no`).
5. B23-C staging GO would be issued before DA1–DA9 + OA1–OA4 are complete.
6. Production flag enable written approval (OC4) is requested outside the agreed maintenance window.
7. Evidence was produced more than 7 days ago without a refresh run.

---

## Evidence Storage Note

All sign-off records, reviewed evidence artifacts, and decision logs must be stored in your team's
incident/change management system. References to `docs/evidence/generated/` paths are local-only snapshots
and are gitignored — they are never committed to this repo.

See: `scripts/collect-phase-2-external-evidence.sh` for the local evidence snapshot script.
See: `docs/evidence/phase-2-external-execution-pack.md` for the full execution pack.
