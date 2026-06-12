# Evidence Directory

Last revised: 2026-06-12.

## Structure

### Active Phase 8 Evidence

| File | Type | Status |
|------|------|--------|
| `phase-8-staging-cutover-evidence-template.md` | Template | All fields EXTERNAL-GATED |
| `phase-8-production-cutover-evidence-template.md` | Template | All fields EXTERNAL-GATED |
| `phase-8-go-no-go-checklist.md` | Gate index | All items EXTERNAL-GATED |
| `phase-8-staging-evidence-intake-checklist.md` | Intake checklist | All rows EXTERNAL-GATED |
| `phase-8-cutover-readiness-template.md` | Template | Blank, for live evidence |
| `phase-8-local-learning-cutover-evidence.md` | Local evidence | LEARNING-MODE-COMPLETE (simulated only) |

### Historical Phase 2 Evidence (Frozen)

These files record Phase 2.2 and 2.3 cutover design, staging templates, and
production promotion gates for account-service and fulfillment-service. They
are no longer the active cutover target but are preserved for traceability.

| File | Type | Reference |
|------|------|-----------|
| `phase-2-2-b17-staging-cutover-template.md` | Historical template | Account-service staging cutover |
| `phase-2-2-b18-production-promotion-template.md` | Historical template | Account-service production promotion |
| `phase-2-2-b21-evidence-consistency-hardening.md` | Historical record | B17 evidence consistency fix |
| `phase-2-3-c-fulfillment-staging-readiness.md` | Historical readiness | Fulfillment staging gate |
| `phase-2-3-d-fulfillment-production-promotion-gate.md` | Historical gate | Fulfillment production gate |
| `phase-2-3-e-fulfillment-cutover-execution.md` | Historical template | Fulfillment cutover execution |
| `phase-2-3-fulfillment-final-readiness-index.md` | Historical index | Fulfillment final index |
| `phase-2-dba-checklist.md` | Historical template | DBA checklist |
| `phase-2-external-execution-pack.md` | Historical template | Overall execution pack |
| `phase-2-external-readiness-dashboard.md` | Historical template | External readiness dashboard |
| `phase-2-ops-xxl-job-checklist.md` | Historical template | Ops XXL-Job checklist |
| `b17-staging-evidence-20260610.md` | Historical actual | Dated B17 evidence record |

### Intake Templates (Historical, tied to Phase 2)

| File | Owner |
|------|-------|
| `intake-dba-ddl-evidence.md` | DBA |
| `intake-engineer-b17-b23c-e2e-evidence.md` | Engineer |
| `intake-oncall-signoff-evidence.md` | Oncall Lead |
| `intake-ops-xxl-job-evidence.md` | Ops |

### Generated Evidence (Machine Output)

`generated/` contains automated evidence snapshots from Phase 2 validator
runs. These are machine-generated and excluded from version control by
`.gitignore` (`/docs/evidence/generated/`). Do not add new generated
evidence snapshots.

## Authoritative Source

For the authoritative microservices decomposition status, see `docs/MICROSERVICES.md`.
