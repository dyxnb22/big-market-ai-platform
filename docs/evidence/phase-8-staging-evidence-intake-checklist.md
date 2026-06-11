# Phase 8 Staging Evidence Intake Checklist

Status: repo-only staging intake preparation. Real Phase 8 staging cutover
evidence is not present in this repository. Every staging field remains
EXTERNAL-GATED until an approved staging window produces concrete, auditable
references.

## Intake Rules

- Do not replace EXTERNAL-GATED with notes, assumptions, screenshots, or log
  snippets unless the source reference is concrete and auditable.
- Do not record a staging GO decision until STG-1 through STG-6 are fully
  backed by DBA, Ops, Engineering, Oncall, and Product evidence or explicit
  Product exemption where applicable.
- Do not use Phase 2 account or fulfillment staging artifacts as Phase 8
  evidence unless the external owner explicitly re-issues them for Phase 8 and
  links the current Phase 8 staging window.
- Keep all production fields in
  `docs/evidence/phase-8-production-cutover-evidence-template.md`
  EXTERNAL-GATED until a later production evidence batch.
- Do not flip source-controlled production, remote, outbox, legacy, or cutover
  defaults from this checklist.

## Required Auditable Reference Format

Each real evidence value must include:

| Requirement | Expected content |
|-------------|------------------|
| Owner | DBA, Ops, Engineering, Oncall, or Product owner name/team |
| Source | Ticket, change record, dashboard permalink, signed evidence file, or approved runbook record |
| Time window | Staging start/end timestamp and timezone |
| Scope | Service, shard/table, job, flag, metric, or flow covered |
| Result | PASS, signed exemption, rollback result, or NO-GO finding |
| Rollback note | Command/runbook reference or owner acknowledgment where relevant |

Plain text such as "done", "verified", "looks good", or "approved" is not
auditable without the source fields above.

## Missing Evidence Register

| Gate | Required staging fields | Current status | Missing evidence |
|------|-------------------------|----------------|------------------|
| DBA DDL and grants | STG-1.1 through STG-1.7 | EXTERNAL-GATED | DDL verification per proposed table, DB grants, secret rollout, DBA rollback note |
| Ops deploy, discovery, jobs, MQ, config | STG-2.1 through STG-2.8 | EXTERNAL-GATED | Deployment versions, Nacos/Dubbo provider listings, XXL-Job rows, MQ bindings, config diff |
| Engineering flow validation | STG-3.1 through STG-3.7 | EXTERNAL-GATED | account, fulfillment, rebate, strategy, optional activity flow results, flag canary, rollback rehearsal |
| Oncall metrics and observation | STG-4.1 through STG-4.5 | EXTERNAL-GATED | Dashboard links, observation window, error/latency/retry metrics, MQ lag, drift checks, rollback readiness |
| Product approval or exemption | STG-5.1 through STG-5.5 | EXTERNAL-GATED | User-visible acceptance or explicit exemption per service |
| Staging GO/NO-GO decision | STG-6.1 through STG-6.6 | EXTERNAL-GATED | DBA/Ops/Engineering/Oncall/Product sign-offs and final staging decision |

## Per-Service Collection Checklist

| Service | DBA evidence | Ops evidence | Engineering evidence | Oncall evidence | Product evidence |
|---------|--------------|--------------|----------------------|-----------------|------------------|
| account-service | STG-1.1, STG-1.2, STG-1.6, STG-1.7 | STG-2.1, STG-2.8 | STG-3.1, STG-3.6, STG-3.7 | STG-4.1 through STG-4.5 | STG-5.1 |
| fulfillment-service | STG-1.1, STG-1.5, STG-1.6, STG-1.7 | STG-2.2, STG-2.6, STG-2.7, STG-2.8 | STG-3.2, STG-3.6, STG-3.7 | STG-4.1 through STG-4.5 | STG-5.2 |
| rebate-service | STG-1.3, STG-1.6, STG-1.7 | STG-2.3, STG-2.6, STG-2.7, STG-2.8 | STG-3.3, STG-3.6, STG-3.7 | STG-4.1 through STG-4.5 | STG-5.3 |
| strategy-service | DBA exemption in STG-1.7 if no DDL | STG-2.4, STG-2.8 | STG-3.4, STG-3.6, STG-3.7 | STG-4.1, STG-4.2, STG-4.5 | STG-5.4 |
| activity-service | STG-1.6, STG-1.7 only after Phase 5-G/7-D approval | STG-2.5, STG-2.6, STG-2.7, STG-2.8 | STG-3.5, STG-3.6, STG-3.7 | STG-4.1 through STG-4.5 | STG-5.5 |

## Staging Decision Guardrail

The staging GO/NO-GO review remains blocked while any row is EXTERNAL-GATED in
the missing evidence register. A green repo validator for this checklist means
only that the intake structure and guardrails exist; it does not accept Phase 8
staging evidence.
