# Phase 2 Ops XXL-Job Checklist

**Date:** 2026-06-10
**Scope:** DispatchCreditAwardTaskJob_DB1 and _DB2 registration in staging and production XXL-Job
**Status:** TEMPLATE — fill in as each action is completed

> **This checklist is for Ops use only.**
> Do not enable any application flags. Flag enabling is done by the Engineer.
> Do not trigger production jobs until the Engineer gives explicit sign-off
> (after step P5 in the cutover runbook).

---

## Handler Reference

Both handlers run on the same executor and follow the same configuration in both environments.

| Field | Value |
|-------|-------|
| **Executor AppName** | `big-market-message-job-service` |
| **Handler 1** | `DispatchCreditAwardTaskJob_DB1` |
| **Handler 2** | `DispatchCreditAwardTaskJob_DB2` |
| **Cron Expression** | `0/30 * * * * ?` (every 30 seconds) |
| **Routing Strategy** | FIRST |
| **Timeout (ms)** | 30000 (recommended) |
| **Max Retry** | 0 (no auto-retry — the outbox poller handles its own retry) |

**What the job does:**
- `DispatchCreditAwardTaskJob_DB1` — polls `credit_award_task_{000,001,002,003}` on `big_market_01`
- `DispatchCreditAwardTaskJob_DB2` — polls `credit_award_task_{000,001,002,003}` on `big_market_02`
- Each run: selects rows with `state='pending'`, calls account-service via `IAccountCreditWriteAdapter.createOrder`, then sets `state='dispatched'`
- Both jobs are **idempotent** — if the job is triggered while no pending rows exist, it exits cleanly

**Where it runs:** `big-market-message-job-service` (NOT fulfillment-service). This must not change.

---

## Phase 1: Staging XXL-Job Registration

### Prerequisites Before Registration

| Check | Status |
|-------|--------|
| DBA: staging `credit_award_task_000–003` tables applied to `big_market_01` | PENDING |
| DBA: staging `credit_award_task_000–003` tables applied to `big_market_02` | PENDING |
| `big-market-message-job-service` executor is online in staging XXL-Job admin | PENDING |

Do NOT register handlers if the executor is offline — the jobs will fail immediately.

### Registration Steps (Staging)

1. Log in to the **staging** XXL-Job admin UI.
2. Navigate to: **Job Management → [Select Executor: big-market-message-job-service]**
3. Click **Add** and fill in the following for handler DB1:

```
JobHandler:          DispatchCreditAwardTaskJob_DB1
Cron:                0/30 * * * * ?
Routing Strategy:    FIRST
Timeout (ms):        30000
Max Retry:           0
Description:         Dispatch credit award outbox — shard DB1
AppName/Executor:    big-market-message-job-service
```

4. Click **Add** and fill in the following for handler DB2:

```
JobHandler:          DispatchCreditAwardTaskJob_DB2
Cron:                0/30 * * * * ?
Routing Strategy:    FIRST
Timeout (ms):        30000
Max Retry:           0
Description:         Dispatch credit award outbox — shard DB2
AppName/Executor:    big-market-message-job-service
```

5. Click **Save** for each handler. Record the auto-assigned Job IDs below.

### Staging Registration Record

| Handler | Job ID | Cron | Registered By | Screenshot Path | Timestamp |
|---------|--------|------|--------------|----------------|-----------|
| `DispatchCreditAwardTaskJob_DB1` | ___ | `0/30 * * * * ?` | ___ | ___ | ___ |
| `DispatchCreditAwardTaskJob_DB2` | ___ | `0/30 * * * * ?` | ___ | ___ | ___ |

### Staging Manual Trigger Validation

After registration, validate each handler with a manual trigger:

1. In XXL-Job admin, click **Execute Once** on `DispatchCreditAwardTaskJob_DB1`.
2. Check the execution log in XXL-Job admin → Scheduling Log:
   - `exitCode` must be `200`
   - `handleMsg` must contain `SUCCESS` (no exception in log)
3. Repeat for `DispatchCreditAwardTaskJob_DB2`.

Expected log output (from message-job-service application log):
```
DispatchCreditAwardTaskJob_DB1 triggered
creditAwardTaskList size: 0
```
(Empty list is correct at baseline — no pending rows yet. Job exits cleanly.)

| Handler | Manual Trigger Timestamp | Exit Code | Log Screenshot Path | Result |
|---------|------------------------|-----------|--------------------|----|
| `DispatchCreditAwardTaskJob_DB1` | ___ | ___ | ___ | SUCCESS / FAIL |
| `DispatchCreditAwardTaskJob_DB2` | ___ | ___ | ___ | SUCCESS / FAIL |

### Staging Gate

**Both handlers registered and manually triggered with SUCCESS:** YES / NO

> Hard gate: Do NOT proceed to Phase 2 (B23-C E2E) until this gate is YES.

**Ops sign-off (staging):**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## Phase 3: Production XXL-Job Registration

### Prerequisites Before Registration

| Check | Status |
|-------|--------|
| B23-C staging evidence SE1–SE11 signed off by oncall lead | PENDING |
| B23-D production gate Phase B signed off | PENDING |
| DBA: production `credit_award_task_000–003` tables applied to `big_market_01` | PENDING |
| DBA: production `credit_award_task_000–003` tables applied to `big_market_02` | PENDING |
| `big-market-message-job-service` executor is online in production XXL-Job admin | PENDING |

### Registration Steps (Production)

1. Log in to the **production** XXL-Job admin UI.
2. Navigate to: **Job Management → [Select Executor: big-market-message-job-service]**
3. Register `DispatchCreditAwardTaskJob_DB1` (exact same spec as staging above).
4. Register `DispatchCreditAwardTaskJob_DB2` (exact same spec as staging above).
5. Record Job IDs below.

> **Do NOT manually trigger the production jobs yet.**
> Production trigger is controlled by the Engineer during the cutover window (step P5 onward).
> Triggering before the outbox flag is enabled will result in a no-op (empty pending list),
> but do not trigger outside the approved maintenance window.

### Production Registration Record

| Handler | Job ID | Cron | Registered By | Screenshot Path | Timestamp |
|---------|--------|------|--------------|----------------|-----------|
| `DispatchCreditAwardTaskJob_DB1` | ___ | `0/30 * * * * ?` | ___ | ___ | ___ |
| `DispatchCreditAwardTaskJob_DB2` | ___ | `0/30 * * * * ?` | ___ | ___ | ___ |

### Production Gate

**Both handlers registered in production:** YES / NO

> Hard gate: Do NOT allow Engineer to enable outbox flag (P5) until this gate is YES.

**Ops sign-off (production):**

| Name | Timestamp | Decision |
|------|-----------|---------|
| ___ | ___ | SIGNED / REFUSED |

---

## NO-GO Triggers

Do not proceed (or escalate to oncall lead) if ANY of the following:

1. XXL-Job executor `big-market-message-job-service` is offline or not registered in the target environment.
2. Manual trigger for either handler returns exit code ≠ 200.
3. Handler execution log shows Java exception or `NullPointerException`.
4. DBA evidence DA3/DA4 (staging) or DA10/DA11 (production) not yet signed.
5. Outbox tables `credit_award_task_000–003` not confirmed present in the target DB before registering jobs.
6. Any attempt to register `DispatchCreditAwardTaskJob` on the `big-market-fulfillment-service` executor — this job must remain in `big-market-message-job-service`.

---

## Evidence Attachment Summary

| # | Evidence | Screenshot / Log Path | Signed By | Timestamp |
|---|----------|----------------------|-----------|-----------|
| OA1 | Staging Job ID for DispatchCreditAwardTaskJob_DB1 | ___ | ___ | ___ |
| OA2 | Staging Job ID for DispatchCreditAwardTaskJob_DB2 | ___ | ___ | ___ |
| OA3 | Staging manual trigger result for DB1 (exitCode=200) | ___ | ___ | ___ |
| OA4 | Staging manual trigger result for DB2 (exitCode=200) | ___ | ___ | ___ |
| OA5 | Production Job ID for DispatchCreditAwardTaskJob_DB1 | ___ | ___ | ___ |
| OA6 | Production Job ID for DispatchCreditAwardTaskJob_DB2 | ___ | ___ | ___ |
