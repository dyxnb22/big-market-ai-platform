# Phase 8 GO/NO-GO Checklist

Status: EXTERNAL-GATED. This checklist is a repo-only execution aid. It does
not record approval and does not open staging, production, 7-day, or 30-day
cleanup gates by itself.

## Required Inputs

| Check ID | Required input | Evidence field |
|----------|----------------|----------------|
| GNG-1 | Staging DBA DDL, grants, and secret rollout evidence | STG-1.1 through STG-1.7 |
| GNG-2 | Staging deploy, provider discovery, XXL-Job, and MQ evidence | STG-2.1 through STG-2.8 |
| GNG-3 | Staging business-flow, canary, and rollback evidence | STG-3.1 through STG-3.7 |
| GNG-4 | Staging metrics/logs/oncall observation evidence | STG-4.1 through STG-4.5 |
| GNG-5 | Staging Product evidence or explicit exemption | STG-5.1 through STG-5.5 |
| GNG-6 | Staging final decision before any production cutover | STG-6.1 through STG-6.6 |
| GNG-7 | Production DBA DDL, grants, and secret rollout evidence | PROD-1.1 through PROD-1.7 |
| GNG-8 | Production deploy, provider discovery, XXL-Job, and MQ evidence | PROD-2.1 through PROD-2.8 |
| GNG-9 | Production single-instance canary and rollback evidence | PROD-3.1 through PROD-3.7 |
| GNG-10 | Production metrics/logs/oncall observation evidence | PROD-4.1 through PROD-4.5 |
| GNG-11 | Production Product/support evidence or explicit exemption | PROD-5.1 through PROD-5.5 |
| GNG-12 | Production final decision and stability-clock evidence | PROD-6.1 through PROD-6.7 |

## Hard NO-GO Conditions

- Any required field remains EXTERNAL-GATED for the environment under review.
- Any proposed DDL was not applied and verified by DBA-owned evidence.
- Any service DB grants or secrets are missing for an enabled path.
- Nacos/Dubbo provider discovery is missing or shows an unsafe duplicate-provider state.
- Required XXL-Job or MQ evidence is missing for an enabled outbox path.
- Any remote, outbox, production, or cutover flag diff is missing or cannot be rolled back.
- Staging evidence is missing before a production review.
- Oncall dashboards, alert thresholds, or rollback owner are missing.
- Product approval or exemption is missing for user-visible behavior.
- Activity draw is in scope without Phase 5-G/7-D approval evidence.
- Legacy provider disablement or fallback removal is requested before the 7-day and 30-day cleanup gates.

## Decision Record

| Field ID | Decision field | Current value |
|----------|----------------|---------------|
| GNG-D1 | Environment under review | EXTERNAL-GATED |
| GNG-D2 | Decision time | EXTERNAL-GATED |
| GNG-D3 | Required evidence package link | EXTERNAL-GATED |
| GNG-D4 | DBA decision | EXTERNAL-GATED |
| GNG-D5 | Ops decision | EXTERNAL-GATED |
| GNG-D6 | Engineering decision | EXTERNAL-GATED |
| GNG-D7 | Oncall decision | EXTERNAL-GATED |
| GNG-D8 | Product decision or exemption | EXTERNAL-GATED |
| GNG-D9 | Final GO/NO-GO result | EXTERNAL-GATED |
| GNG-D10 | Follow-up owner and deadline | EXTERNAL-GATED |

Green repo validators only mean this checklist and the evidence templates are
present and still gated. They do not prove that any external cutover occurred.

## Local Learning-Mode Note

The local learning project has a separate LEARNING-MODE-COMPLETE path documented
in `docs/microservices-learning-mode-closure.md` and
`docs/evidence/phase-8-local-learning-cutover-evidence.md`.

That path uses LOCAL-LEARNING-EVIDENCE and SIMULATED-CUTOVER-EVIDENCE only. It
does not change GNG-D9, does not replace external evidence, and does not prove
real production readiness. Production readiness is not proven by this local
learning-mode closure.
