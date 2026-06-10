# Phase 2.3 Fulfillment-Service Migration: Final Readiness Index

**Date:** 2026-06-10
**Status:** REPO COMPLETE — Awaiting external sign-offs before staging/production execution
**HEAD:** 143f1ce (chore: ignore generated Phase 2 evidence snapshots)
**Baseline at final readiness:** 0a13a06 (tag: phase-2.3-e-fulfillment-cutover-execution-pack)
**Latest Phase 2 tag:** phase-2-external-execution-pack (d8f375d — docs: add Phase 2 external execution pack)

> **This document is an index, not an execution approval.**
> It summarises the completed repo work for all Phase 2.3 batches (B23-A through B23-E)
> and records which external actions remain blocked.
> Actual staging and production execution remains blocked until the external evidence
> and sign-offs listed in the Blocked Items section are complete.

---

## Batch Summary

| Batch | Description | Commit/Tag | Validator | Checks | Status |
|-------|-------------|------------|-----------|--------|--------|
| **B23-A** | fulfillment-service dark launch (port 8087, Dubbo port 20882) | tag: `phase-2.3-a-fulfillment-service-dark-launch` | `validate-fulfillment-service-b23-a.sh` | 15/15 | PASS (local) |
| **B23-B** | Award dispatch adapter scaffold (`IAwardDispatchAdapter`, `LocalAwardDispatchAdapter`, `RemoteAwardDispatchAdapter`) | tag: `phase-2.3-b-award-dispatch-adapter-scaffold` | `validate-fulfillment-service-b23-b.sh` | 16/16 | PASS (local) |
| **B23-C** | Staging readiness evidence template + job ownership decision | tag: `phase-2.3-c-fulfillment-staging-readiness` | `validate-fulfillment-service-b23-c-readiness.sh` | all | PASS (local) |
| **B23-D** | Production promotion gate: GO/NO-GO checklist, deployment order, rollback plan | tag: `phase-2.3-d-fulfillment-production-gate` | `validate-fulfillment-service-b23-d-production-gate.sh` | all | PASS (local) |
| **B23-E** | Cutover execution runbook: staging steps S1–S8, production steps P1–P8, canary plan, rollback commands, evidence table E1–E12 | tag: `phase-2.3-e-fulfillment-cutover-execution-pack` | `validate-fulfillment-service-b23-e-cutover-execution.sh` | 37/37 | PASS (local) |

---

## Artifact Links

### Evidence Documents

| Document | Purpose |
|----------|---------|
| [`docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md`](phase-2-3-c-fulfillment-staging-readiness.md) | B23-C staging evidence template (SE1–SE11 must be filled and signed) |
| [`docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md`](phase-2-3-d-fulfillment-production-promotion-gate.md) | B23-D production gate: deployment order, NO-GO triggers, DBA sign-off table |
| [`docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md`](phase-2-3-e-fulfillment-cutover-execution.md) | B23-E cutover execution runbook: staging S1–S8 + production P1–P8 |

### Design Document

| Document | Purpose |
|----------|---------|
| [`docs/microservices-split-phase-2-3-fulfillment-service.md`](../microservices-split-phase-2-3-fulfillment-service.md) | Phase 2.3 architecture, outbox dependency rationale, batch history |

### Validator Scripts

| Script | Scope | Checks |
|--------|-------|--------|
| `scripts/validate-fulfillment-service-b23-b.sh` | B23-B adapter scaffold | 16 |
| `scripts/validate-fulfillment-service-b23-c-readiness.sh` | B23-C config safety + docs | all |
| `scripts/validate-fulfillment-service-b23-d-production-gate.sh` | B23-D config safety + docs + wiring | all |
| `scripts/validate-fulfillment-service-b23-e-cutover-execution.sh` | B23-E full pack (37 checks) | 37 |
| `scripts/validate-fulfillment-service-phase-2-3.sh` | **One-command suite: B23-B/C/D/E + flag scan + tag verification** | all |

### External Execution Pack (new — 2026-06-10)

| Document | Purpose |
|----------|---------|
| [`docs/evidence/phase-2-external-execution-pack.md`](phase-2-external-execution-pack.md) | Consolidated pack: B17 staging GO + B23-C/D/E gates; role sections for DBA, Ops, Engineer, Oncall |
| [`docs/evidence/phase-2-dba-checklist.md`](phase-2-dba-checklist.md) | DBA checklist: staging + production DDL apply, verification SQL, sign-off table |
| [`docs/evidence/phase-2-ops-xxl-job-checklist.md`](phase-2-ops-xxl-job-checklist.md) | Ops checklist: XXL-Job registration for DB1/DB2 in staging and production |

### External Pack Scripts (new — 2026-06-10)

| Script | Purpose |
|--------|---------|
| `scripts/collect-phase-2-external-evidence.sh` | Local-only evidence collector; outputs timestamped snapshot to `docs/evidence/generated/` (gitignored — local only) |
| `scripts/validate-phase-2-external-execution-pack.sh` | Validates all new pack artifacts + runs Phase 2.3 suite + flag scan; no staging/prod required |
| `scripts/validate-phase-2-evidence-consistency.sh` | **Evidence consistency validator** (new — 2026-06-10 hardening batch): checks Phase 2.2/2.3 docs exist, gitignore policy, key tags, dangerous flags, and cross-links; no network/Docker/DB required |
| `scripts/validate-phase-2-external-evidence-intake.sh` | **Intake validator** (2026-06-10 automation batch): checks all four role-specific evidence intake templates exist and contain required sections, B23-E prerequisites, and dangerous flag safety language; no network/Docker/DB required |
| `scripts/validate-phase-2-external-evidence-completion.sh` | **Completion gate validator** (2026-06-10 completion-gates batch): reads the `## Completion Status` table in each intake template; reports TEMPLATE_READY / PARTIAL / COMPLETE / NO_GO; reports B23-E gate status; fails only on NO-GO or malformed templates; no network/Docker/DB required |
| `scripts/prepare-phase-2-external-handoff-bundle.sh` | **Handoff bundle generator** (2026-06-10 handoff-bundle batch): creates timestamped local-only bundle under docs/evidence/generated/ with role folders (DBA/Ops/Engineer/Oncall), intake templates, role-specific instructions, validator outputs, README, MANIFEST, and NOT-AN-APPROVAL.txt; no network/DB/Docker; output gitignored |
| `scripts/validate-phase-2-external-handoff-bundle.sh` | **Handoff bundle validator** (2026-06-10 handoff-bundle batch): repo-only checks that the generator is safe (no forbidden commands), writes only to generated/, produces all required role folders and docs; optionally validates a specific generated bundle path |

### Evidence Intake Templates (2026-06-10 automation batch)

Operators fill these templates during real staging/production execution. The intake validator verifies
their structure deterministically from the repo. The completion gate validator reports per-role
completion state and B23-E gate readiness.

| Template | Owner | Gate It Unlocks |
|----------|-------|-----------------|
| `docs/evidence/intake-dba-ddl-evidence.md` | DBA | DA1–DA14 DDL evidence; blocks B17 E2E and P5 flag enable |
| `docs/evidence/intake-ops-xxl-job-evidence.md` | Ops | OA1–OA6 XXL-Job evidence; blocks B23-C E2E and P5 flag enable |
| `docs/evidence/intake-engineer-b17-b23c-e2e-evidence.md` | Engineer | EA1–EA10 E2E evidence; blocks SE11 and B23-D gate |
| `docs/evidence/intake-oncall-signoff-evidence.md` | Oncall Lead | OC1–OC5 sign-offs; all five phase gates including P4 written approval |

### DDL

| File | Purpose |
|------|---------|
| `docs/sql/proposed-quota-decrement-ledger.sql` | `raffle_quota_decrement_ledger` DDL (apply to staging only for Phase 2.2-B17) |
| `docs/sql/proposed-credit-award-task-outbox.sql` | `credit_award_task` outbox DDL (apply to staging then production) |

---

## Safe Defaults (must remain false until external sign-offs complete)

| Flag | Services | Default | Rule |
|------|----------|---------|------|
| `account.award-credit-outbox.enabled` | message-job-service, fulfillment-service, big-market-app | **`false`** | Never enable without DBA DDL confirmation + unique-key verification |
| `account.fulfillment.remote-award.enabled` | message-job-service, big-market-app | **`false`** | Never enable before outbox flag is stable and B23-C staging evidence signed |
| `account.service.remote-quota-decrement.enabled` | market-service | **`false`** | Phase 2.2 separate gate — not part of Phase 2.3 |

---

## Job Ownership Decision (resolved B23-C, 2026-06-10)

**`DispatchCreditAwardTaskJob` remains in `big-market-message-job-service` permanently through Phase 2.3.**

Rationale: `credit_award_task_mapper.xml` is only in message-job-service; `FulfillmentServiceApplication.scanBasePackages` deliberately excludes `trigger.job`; both services connect to the same physical DB shards so job placement is irrelevant to data ownership. Any future move requires a dedicated batch.

---

## Blocked Items (external — repo cannot resolve)

| # | Blocker | Owner | Blocks |
|---|---------|-------|--------|
| X1 | B23-C staging evidence SE1–SE11 completed and signed by oncall lead | DBA + Ops + Engineer + Oncall | Staging cutover (S1–S8) |
| X2 | `credit_award_task` DDL applied to staging `big_market_01` and `big_market_02` | DBA | SE3/SE4; `outbox.enabled=true` in staging |
| X3 | `DispatchCreditAwardTaskJob_DB1/_DB2` registered in staging XXL-Job admin | Ops | SE5; E2E dispatch in staging |
| X4 | B23-D evidence file completed and signed | Oncall lead | Production cutover (P1–P8) |
| X5 | DBA applies `credit_award_task` DDL to production `big_market_01` and `big_market_02` | DBA | P5 outbox flag enable in production |
| X6 | Ops registers `DispatchCreditAwardTaskJob_DB1/_DB2` in production XXL-Job admin | Ops | P5 outbox flag enable in production |
| X7 | Oncall lead issues written approval for production cutover window | Oncall lead | Hard gate before P5 |
| X8 | Phase 2.2-B17 staging GO decision (ledger DDL, outbox DDL, XXL-Job registration) | DBA + Ops | Required before any staging cutover |

---

## Exact Next Real-World Action Sequence

Follow this sequence in order. Do not skip or reorder.

### Step 1 — Phase 2.2-B17 staging GO (prerequisite)
- DBA applies ledger DDL and outbox DDL to staging
- Ops registers XXL-Job handlers in staging
- Issue Phase 2.2-B17 staging GO decision

### Step 2 — B23-C staging evidence (SE1–SE11)
Run `bash scripts/validate-fulfillment-service-phase-2-3.sh` locally (all PASS required first).

Then, with staging access:
1. Apply `credit_award_task` DDL to staging `big_market_01` and `big_market_02`
2. Register `DispatchCreditAwardTaskJob_DB1/_DB2` in staging XXL-Job admin
3. Enable `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true` in staging message-job-service
4. Run E2E outbox flow validation (SE6–SE9 per B23-C evidence doc)
5. Confirm idempotency (SE7)
6. Enable `ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=true` in staging, validate Dubbo path (SE8/SE9)
7. Restore all flags to `false` after validation (SE10)
8. Oncall lead signs B23-C staging GO decision (SE11)

Fill in `docs/evidence/phase-2-3-c-fulfillment-staging-readiness.md` SE1–SE11.

### Step 3 — B23-D production gate review
- Attach B23-C evidence to `docs/evidence/phase-2-3-d-fulfillment-production-promotion-gate.md` (SE1–SE11)
- DBA applies production DDL (PP3–PP6)
- Ops registers production XXL-Job handlers (PP7)
- Oncall lead reviews and approves production window (PP9)
- Complete B23-D evidence file and sign off

### Step 4 — B23-E production cutover execution
Execute staging cutover runbook S1–S8, then production runbook P1–P8 as documented in
`docs/evidence/phase-2-3-e-fulfillment-cutover-execution.md`.

Run `bash scripts/validate-fulfillment-service-phase-2-3.sh` as pre-flight (gate S1 / gate P1).

---

## Generated Evidence Snapshots

`docs/evidence/generated/` is listed in `.gitignore` and contains only local-only timestamped
snapshots produced by `scripts/collect-phase-2-external-evidence.sh`. These files are never
committed. No repo-only batch enables staging or production traffic, and all dangerous flags
remain `false` by default unless an external sign-off gate has been passed.

## Validators

### One-Command Phase 2.3 Suite

```bash
bash scripts/validate-fulfillment-service-phase-2-3.sh
```

Runs B23-B/C/D/E validators in order, performs a final dangerous-flag scan across all config files,
and verifies all five Phase 2.3 git tags exist locally. Prints a concise PASS/FAIL summary.
No network, Docker, DB, staging, or production access required.

### Evidence Consistency Validator (hardening batch — 2026-06-10)

```bash
bash scripts/validate-phase-2-evidence-consistency.sh
```

Checks Phase 2.2 and Phase 2.3 doc coverage, gitignore policy for `docs/evidence/generated/`,
current key Phase 2 tag presence, dangerous-flag scan, and cross-link correctness between
final readiness and external execution documents. No network, Docker, DB, staging, or
production access required.

### Completion Gate Validator (completion-gates batch — 2026-06-10)

```bash
bash scripts/validate-phase-2-external-evidence-completion.sh
```

Reads the `## Completion Status` table in each intake template. Reports TEMPLATE_READY /
PARTIAL / COMPLETE / NO_GO per role, and reports whether B23-E cutover prerequisites are met.
Fails only on NO-GO or malformed templates. No network, Docker, DB, staging, or production
access required.

See: `docs/evidence/phase-2-external-readiness-dashboard.md` for the dashboard view.

### Handoff Bundle Generator (handoff-bundle batch — 2026-06-10)

```bash
# Run when preparing materials for DBA / Ops / Engineer / Oncall
bash scripts/prepare-phase-2-external-handoff-bundle.sh
# Output: docs/evidence/generated/phase2-handoff-bundle-<TIMESTAMP>/
# Contains: DBA/ Ops/ Engineer/ Oncall/ role folders, README.md, MANIFEST.md,
#           NOT-AN-APPROVAL.txt, validator outputs, git state
# This output is gitignored — never committed.
```

Packages intake templates, role-specific instructions, validator outputs, current
readiness state, and execution ordering into a single local bundle for external role
distribution. Run before handing off to DBA, Ops, Engineer, or Oncall.

### Handoff Bundle Validator (handoff-bundle batch — 2026-06-10)

```bash
# Validate the generator script (repo-only)
bash scripts/validate-phase-2-external-handoff-bundle.sh

# Validate a specific generated bundle
bash scripts/validate-phase-2-external-handoff-bundle.sh <bundle-path>
```

Repo-only checks: generator has no forbidden commands (mysql/docker/curl/wget),
writes only to docs/evidence/generated/, produces all required role folders and
output docs. Run after any change to the generator script.
