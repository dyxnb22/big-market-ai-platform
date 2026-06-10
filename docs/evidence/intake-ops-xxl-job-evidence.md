# Phase 2 Ops XXL-Job Registration Evidence — Intake Template

**Status:** TEMPLATE — fill in as each registration action is completed
**Owner:** Ops
**Last updated:** ___

> **THIS TEMPLATE IS NOT AN APPROVAL AND DOES NOT ENABLE TRAFFIC.**
> The Ops team registers XXL-Job handlers only. Flag enabling is done by the Engineer after Ops sign-off.
> Do not trigger production jobs outside the approved maintenance window.
>
> **Who fills this:** Ops only.
> **Who reviews it:** Oncall lead + Engineer (required before E2E / before P5 flag enable).
> **Generated evidence** (screenshots, XXL-Job log exports) must be stored locally or in a secure artifact store.
> Files in `docs/evidence/generated/` are gitignored and must never be committed to this repo.

---

## Dangerous Flag Safety

The Ops team does NOT enable application flags. The following flags must remain `false` in all configs
during XXL-Job registration.

| Flag | Hard Rule |
|------|-----------|
| `account.award-credit-outbox.enabled` | Ops must not touch this flag — Engineer only |
| `account.fulfillment.remote-award.enabled` | Ops must not touch this flag — Engineer only |
| `account.service.remote-quota-decrement.enabled` | Ops must not touch this flag — Engineer only |

---

## Ops Staging XXL-Job Evidence

**Gate:** Both handlers must be registered and manually triggered with SUCCESS before B23-C E2E.
**Prerequisite:** DBA staging sign-off complete (DA1–DA9 all SIGNED in `intake-dba-ddl-evidence.md`).
**Prerequisite docs:** [`phase-2-ops-xxl-job-checklist.md`](phase-2-ops-xxl-job-checklist.md)

### Handler Spec (both handlers, both environments)

| Field | Expected Value |
|-------|---------------|
| Executor AppName | `big-market-message-job-service` |
| Cron | `0/30 * * * * ?` |
| Routing Strategy | `FIRST` |
| Timeout (ms) | `30000` |
| Max Retry | `0` |

> **IMPORTANT:** Both handlers must be registered on executor `big-market-message-job-service`.
> NEVER register these handlers on the `big-market-fulfillment-service` executor.
> `DispatchCreditAwardTaskJob` must remain in message-job-service permanently through Phase 2.3.

### Staging Handler Registration

| # | Evidence | Screenshot / Log Ref | Registered By | Timestamp | Result |
|---|----------|---------------------|--------------|-----------|--------|
| OA1 | `DispatchCreditAwardTaskJob_DB1` registered in staging XXL-Job (Job ID: ___) | ___ | ___ | ___ | REGISTERED / FAIL |
| OA2 | `DispatchCreditAwardTaskJob_DB2` registered in staging XXL-Job (Job ID: ___) | ___ | ___ | ___ | REGISTERED / FAIL |
| OA3 | Staging manual trigger `DispatchCreditAwardTaskJob_DB1` — exitCode=200, SUCCESS | ___ | ___ | ___ | SUCCESS / FAIL |
| OA4 | Staging manual trigger `DispatchCreditAwardTaskJob_DB2` — exitCode=200, SUCCESS | ___ | ___ | ___ | SUCCESS / FAIL |

**Staging Gate — Both handlers registered + manually triggered with SUCCESS:** YES / NO

> **NO-GO:** If either manual trigger returns exitCode ≠ 200, escalate immediately.
> Do NOT proceed to B23-C E2E until this gate is YES.

**Ops Staging Sign-Off:**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## Ops Production XXL-Job Evidence

**Gate:** Both handlers must be registered before the B23-E cutover window (P5).
**Prerequisites:** B23-C staging evidence SE1–SE11 signed + B23-D Phase C gate + DBA production DDL signed.
**Prerequisite docs:** [`phase-2-ops-xxl-job-checklist.md`](phase-2-ops-xxl-job-checklist.md)

> **Do NOT manually trigger production jobs during registration.**
> Production job triggering is controlled by the Engineer during the approved maintenance window (P5 onward).

### Production Handler Registration

| # | Evidence | Screenshot / Log Ref | Registered By | Timestamp | Result |
|---|----------|---------------------|--------------|-----------|--------|
| OA5 | `DispatchCreditAwardTaskJob_DB1` registered in production XXL-Job (Job ID: ___) | ___ | ___ | ___ | REGISTERED / FAIL |
| OA6 | `DispatchCreditAwardTaskJob_DB2` registered in production XXL-Job (Job ID: ___) | ___ | ___ | ___ | REGISTERED / FAIL |

**Production Gate — Both handlers registered in production:** YES / NO

> **NO-GO:** If either handler fails to register, escalate immediately.
> Do NOT allow Engineer to proceed to P5 (outbox flag enable) until this gate is YES.

**Ops Production Sign-Off:**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## B23-E Cutover Approval Prerequisites (Ops view)

Before the B23-E cutover window opens, Ops must confirm all of the following:

| Check | Status |
|-------|--------|
| Staging Gate complete (OA1–OA4, all SUCCESS, signed) | PENDING / DONE |
| Production Gate complete (OA5–OA6, all REGISTERED, signed) | PENDING / DONE |
| `big-market-message-job-service` executor is online in production XXL-Job | PENDING / CONFIRMED |
| Oncall lead has reviewed Ops sign-offs | PENDING / CONFIRMED |

> **Hard gate:** The B23-E cutover execution must NOT start until all rows above are DONE or CONFIRMED.

---

## NO-GO Rules for Ops

Stop and escalate immediately to the oncall lead if ANY of the following:

1. XXL-Job executor `big-market-message-job-service` is offline or not registered in the target environment.
2. Manual trigger for either staging handler returns exitCode ≠ 200.
3. Handler execution log shows Java exception or `NullPointerException`.
4. DBA evidence (DA1–DA9 for staging, DA10–DA14 for production) not yet signed.
5. Outbox tables `credit_award_task_000–003` not confirmed present in the target DB before registering jobs.
6. Any attempt to register `DispatchCreditAwardTaskJob` on the `big-market-fulfillment-service` executor.

---

## Evidence Storage Note

XXL-Job screenshots, execution logs, and job configuration exports must be stored in your team's secure
artifact store or in the local `docs/evidence/generated/` directory. The `generated/` directory is listed
in `.gitignore` and is local-only — evidence in that directory is never committed to this repo.

See: `scripts/collect-phase-2-external-evidence.sh` for the local evidence snapshot script.
See: `docs/evidence/phase-2-external-execution-pack.md` for the full execution pack.
