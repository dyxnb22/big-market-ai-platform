# Phase 2 Engineer B17 / B23-C E2E Evidence — Intake Template

**Status:** TEMPLATE — fill in as each E2E test step is completed
**Owner:** Engineer
**Last updated:** ___

> **THIS TEMPLATE IS NOT AN APPROVAL AND DOES NOT ENABLE PRODUCTION TRAFFIC.**
> Staging flags may be temporarily enabled during the approved maintenance window only.
> All flags must be restored to `false` after staging validation.
>
> **Who fills this:** Engineer only.
> **Who reviews it:** Oncall lead (reviews EA evidence before issuing Phase K and SE11 sign-offs).
> **Generated evidence** must be stored locally or in a secure artifact store.
> Files in `docs/evidence/generated/` are gitignored and must never be committed to this repo.

---

## Dangerous Flag Safety

All three dangerous flags must remain `false` in all config files throughout this template.
Temporary staging-only exceptions are noted in each phase where they apply.

| Flag | Default | Staging-Only Exception | Hard Rule |
|------|---------|------------------------|-----------|
| `account.award-credit-outbox.enabled` | `false` | Temporarily `true` for B23-C outbox E2E; must restore to `false` after | Never enable in production without oncall written approval |
| `account.fulfillment.remote-award.enabled` | `false` | Temporarily `true` for B23-C remote-award Dubbo E2E; must restore to `false` after | Never enable before outbox flag is stable and B23-C staging evidence signed |
| `account.service.remote-quota-decrement.enabled` | `false` | Temporarily `true` for B17 E2E; must restore to `false` after | Never enable in production without oncall written approval |

> **NEVER enable any of these flags in production without oncall written approval.**

---

## Static Pre-flight Evidence

Run locally before any staging action.

| # | Evidence | Command | Result |
|---|----------|---------|--------|
| EA1 | `validate-fulfillment-service-phase-2-3.sh` all PASS | `bash scripts/validate-fulfillment-service-phase-2-3.sh` | PASS / FAIL |
| EA2 | `validate-phase-2-external-evidence-intake.sh` all PASS | `bash scripts/validate-phase-2-external-evidence-intake.sh` | PASS / FAIL |
| EA3-pre | `collect-phase-2-external-evidence.sh` output directory path | `bash scripts/collect-phase-2-external-evidence.sh` | ___ |

> **Hard gate:** Do NOT proceed to any staging action if EA1 or EA2 is FAIL.

**Pre-flight Gate — EA1 + EA2 both PASS:** YES / NO

**Engineer Pre-flight Sign-Off:**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## B17 Staging E2E Evidence

**Prerequisites:** DBA staging sign-off (DA1–DA9) + Ops staging sign-off (OA1–OA4) both COMPLETE.
**Reference:** `docs/evidence/b17-staging-evidence-20260610.md` (Phases A–K)

| # | Evidence | Screenshot / Log Ref | Timestamp | Result |
|---|----------|---------------------|-----------|--------|
| EA3 | B17 `CONNECT_REMOTE` verification: 0 FAIL | ___ | ___ | 0 FAIL / FAIL |
| EA4 | B17 E2E draw result (HTTP 200, ledger row applied, quota decremented) | ___ | ___ | CONFIRMED / FAIL |
| EA5 | B17 outbox dispatch: `credit_award_task` `pending` → `dispatched`, `user_credit_order` count=1 | ___ | ___ | CONFIRMED / FAIL |
| EA6 | B17 post-window flag restore: `account.service.remote-quota-decrement.enabled=false` confirmed | ___ | ___ | CONFIRMED / FAIL |

> **NO-GO:** If any row is FAIL, stop and escalate to oncall lead.
> Oncall lead signs **Phase K** (`b17-staging-evidence-20260610.md`) only after EA3–EA6 are all CONFIRMED.

**B17 E2E Gate — EA3–EA6 all CONFIRMED:** YES / NO

**Engineer B17 Sign-Off:**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## B23-C Staging Evidence

**Prerequisites:** B17 Phase K GO decision issued by oncall lead.
**Reference:** `docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md` (SE1–SE11)

| # | Evidence | Screenshot / Log Ref | Timestamp | Result |
|---|----------|---------------------|-----------|--------|
| EA7 | B23-C outbox E2E: `credit_award_task` `pending` → `dispatched`, `user_credit_order` count=1 | ___ | ___ | CONFIRMED / FAIL |
| EA8 | B23-C idempotency confirmed: re-trigger produces 0 new `user_credit_order` rows (no double-credit) | ___ | ___ | CONFIRMED / FAIL |
| EA9 | B23-C remote-award Dubbo E2E: full path raffle → MQ → Dubbo → fulfillment-service → outbox → job → credit | ___ | ___ | CONFIRMED / FAIL |
| EA10 | B23-C all flags restored to `false` (`outbox.enabled=false`, `remote-award.enabled=false`) | ___ | ___ | CONFIRMED / FAIL |

> **NO-GO:** If `user_credit_order` count > 1 for same `out_business_no`, STOP immediately (double-credit).
> Oncall lead signs **SE11** (`phase-2-3-c-fulfillment-staging-readiness.md`) only after EA7–EA10 are all CONFIRMED.

**B23-C E2E Gate — EA7–EA10 all CONFIRMED:** YES / NO

**Engineer B23-C Sign-Off:**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## B23-E Cutover Approval Prerequisites (Engineer view)

Before the B23-E cutover window, the Engineer must confirm all of the following:

| Check | Status |
|-------|--------|
| Pre-flight gate complete (EA1 + EA2 both PASS) | PENDING / DONE |
| B17 E2E gate complete (EA3–EA6 all CONFIRMED, signed) | PENDING / DONE |
| B23-C E2E gate complete (EA7–EA10 all CONFIRMED, signed) | PENDING / DONE |
| DBA production sign-off complete (DA10–DA14 all SIGNED) | PENDING / DONE |
| Ops production sign-off complete (OA5–OA6 all REGISTERED, signed) | PENDING / DONE |
| Oncall written approval for production cutover window recorded (OC4) | PENDING / DONE |

> **Hard gate:** The B23-E cutover execution must NOT start until all rows above are DONE.
> Re-run static pre-flight immediately before the cutover window opens (gate S1/P1 in `phase-2-3-e-fulfillment-cutover-execution.md`).

---

## NO-GO Rules for Engineer

Stop and escalate immediately to the oncall lead if ANY of the following:

1. `validate-fulfillment-service-phase-2-3.sh` has any FAIL.
2. Any dangerous flag found hardcoded `true` in any config file.
3. B17: `uq_award_order_id` or `uq_out_business_no` missing from any deployed shard table.
4. B23-C: `user_credit_order` count > 1 for same `out_business_no` (double-credit — escalate immediately).
5. B23-C: Any quota change on duplicate draw (idempotency violation).
6. Draw endpoint error rate > 0% during any canary window.
7. fulfillment-service OOM during any canary window.
8. B23-C staging GO decision not signed before production cutover.
9. Oncall written approval not recorded before P5 production flag enable.
10. `DispatchCreditAwardTaskJob` found running in fulfillment-service (must stay in message-job-service).

---

## Evidence Storage Note

All E2E test outputs, screenshots, and log excerpts must be stored in your team's secure artifact store
or in the local `docs/evidence/generated/` directory. The `generated/` directory is listed in `.gitignore`
and is local-only — evidence in that directory is never committed to this repo.

See: `scripts/collect-phase-2-external-evidence.sh` for the local evidence snapshot script.
See: `docs/evidence/phase-2-external-execution-pack.md` for the full execution pack.
