# Phase 8 Evidence Pack

Status: EXTERNAL-GATED. This consolidated evidence pack replaces the old Phase
8 evidence templates, staging checklist, GO/NO-GO checklist, and local learning
evidence file.

LOCAL-LEARNING-EVIDENCE / SIMULATED-CUTOVER-EVIDENCE / LEARNING-MODE-COMPLETE.
It records local learning only and does not prove staging or production readiness.

Commands recorded: `mvn clean package -DskipTests`,
`./scripts/validate-microservices-split-all-gates.sh`,
`./scripts/validate-microservices-master-plan.sh`,
`./scripts/validate-microservices-production-flag-matrix.sh`,
`./scripts/validate-microservices-legacy-cleanup-readiness.sh`,
`./scripts/validate-microservices-post-cutover-cleanup-gates.sh`,
`./scripts/validate-production-ddl.sh`, `docker compose ps`,
`./scripts/validate-microservices-stack.sh --skip-build`,
`./scripts/smoke-test-phase-1.sh`.

## Intake Rules

Do not replace EXTERNAL-GATED without real evidence. Do not record a staging GO decision while any row is EXTERNAL-GATED. Keep all production fields gated.
Phase 2 account or fulfillment staging artifacts are historical only.

## Required Auditable Reference Format

Owner, Source, Time window, Rollback note.

## Missing Evidence Register

| Gate | Range | Status |
| --- | --- | --- |
| DBA DDL and grants | STG-1.1 through STG-1.7 | EXTERNAL-GATED |
| Ops deploy, discovery, jobs, MQ, config | STG-2.1 through STG-2.8 | EXTERNAL-GATED |
| Engineering flow validation | STG-3.1 through STG-3.7 | EXTERNAL-GATED |
| Oncall metrics and observation | STG-4.1 through STG-4.5 | EXTERNAL-GATED |
| Product approval or exemption | STG-5.1 through STG-5.5 | EXTERNAL-GATED |
| Staging GO/NO-GO decision | STG-6.1 through STG-6.6 | EXTERNAL-GATED |

## Per-Service Collection Checklist

| Service | Fields | Status |
| --- | --- | --- |
| account-service | STG-1.1 STG-1.2 STG-1.3 STG-1.5 STG-1.6 STG-1.7 STG-2.1 STG-2.6 STG-3.1 STG-3.6 STG-4.1 STG-5.1 | EXTERNAL-GATED |
| fulfillment-service | STG-1.1 STG-1.6 STG-2.2 STG-2.5 STG-3.2 STG-3.6 STG-4.2 STG-5.2 | EXTERNAL-GATED |
| rebate-service | STG-1.3 STG-1.6 STG-2.3 STG-2.4 STG-2.7 STG-3.3 STG-3.7 STG-4.5 STG-5.3 | EXTERNAL-GATED |
| strategy-service | STG-1.5 STG-2.4 STG-3.4 STG-4.5 STG-5.4 | EXTERNAL-GATED |
| activity-service | STG-1.7 STG-2.8 STG-3.5 STG-4.5 STG-5.5 | EXTERNAL-GATED |

## Staging Decision Guardrail

Staging review remains blocked while any row is EXTERNAL-GATED.

## Staging Cutover Evidence

## STG-0 Metadata

| Field | Evidence | Status |
| --- | --- | --- |
| STG-0.1 | Metadata | EXTERNAL-GATED |
| STG-0.2 | Environment | EXTERNAL-GATED |
| STG-0.3 | Commit | EXTERNAL-GATED |
| STG-0.4 | Owner | EXTERNAL-GATED |
| STG-0.5 | Window | EXTERNAL-GATED |

## STG-1 DBA DDL And Grants Evidence

| Field | Evidence | Status |
| --- | --- | --- |
| STG-1.1 | credit_award_task | EXTERNAL-GATED |
| STG-1.2 | raffle_quota_decrement_ledger | EXTERNAL-GATED |
| STG-1.3 | rebate_task_outbox_000..003 | EXTERNAL-GATED |
| STG-1.4 | credit_trade_task_outbox_000..003 | EXTERNAL-GATED |
| STG-1.5 | award_dispatch_task_outbox_000..003 | EXTERNAL-GATED |
| STG-1.6 | DB grants | EXTERNAL-GATED |
| STG-1.7 | DBA rollback note | EXTERNAL-GATED |

## STG-2 Ops Deploy, Discovery, And Job Evidence

| Field | Evidence | Status |
| --- | --- | --- |
| STG-2.1 | account deploy | EXTERNAL-GATED |
| STG-2.2 | fulfillment deploy | EXTERNAL-GATED |
| STG-2.3 | rebate deploy | EXTERNAL-GATED |
| STG-2.4 | strategy deploy | EXTERNAL-GATED |
| STG-2.5 | Nacos/Dubbo provider | EXTERNAL-GATED |
| STG-2.6 | XXL-Job | EXTERNAL-GATED |
| STG-2.7 | MQ | EXTERNAL-GATED |
| STG-2.8 | config | EXTERNAL-GATED |

## STG-3 Metrics, Logs, And Oncall Observation

| Field | Evidence | Status |
| --- | --- | --- |
| STG-3.1 | quota validation | EXTERNAL-GATED |
| STG-3.2 | award validation | EXTERNAL-GATED |
| STG-3.3 | rebate validation | EXTERNAL-GATED |
| STG-3.4 | strategy validation | EXTERNAL-GATED |
| STG-3.5 | activity validation | EXTERNAL-GATED |
| STG-3.6 | Rollback | EXTERNAL-GATED |
| STG-3.7 | logs | EXTERNAL-GATED |

## STG-4 Metrics, Logs, And Oncall Observation

| Field | Evidence | Status |
| --- | --- | --- |
| STG-4.1 | dashboard | EXTERNAL-GATED |
| STG-4.2 | alert | EXTERNAL-GATED |
| STG-4.3 | observation | EXTERNAL-GATED |
| STG-4.4 | error budget | EXTERNAL-GATED |
| STG-4.5 | rollback monitoring | EXTERNAL-GATED |

## STG-5 Product And Approval

| Field | Evidence | Status |
| --- | --- | --- |
| STG-5.1 | account approval | EXTERNAL-GATED |
| STG-5.2 | fulfillment approval | EXTERNAL-GATED |
| STG-5.3 | rebate approval | EXTERNAL-GATED |
| STG-5.4 | strategy approval | EXTERNAL-GATED |
| STG-5.5 | activity approval | EXTERNAL-GATED |

## STG-6 GO/NO-GO

| Field | Evidence | Status |
| --- | --- | --- |
| STG-6.1 | staging decision input | EXTERNAL-GATED |
| STG-6.2 | risk acceptance | EXTERNAL-GATED |
| STG-6.3 | rollback owner | EXTERNAL-GATED |
| STG-6.4 | communication | EXTERNAL-GATED |
| STG-6.5 | final reviewer | EXTERNAL-GATED |
| STG-6.6 | Final staging decision | EXTERNAL-GATED |

## Production Cutover Evidence

## PROD-0 Metadata

| Field | Evidence | Status |
| --- | --- | --- |
| PROD-0.1 | Metadata | EXTERNAL-GATED |
| PROD-0.2 | Environment | EXTERNAL-GATED |
| PROD-0.3 | Commit | EXTERNAL-GATED |
| PROD-0.4 | Owner | EXTERNAL-GATED |
| PROD-0.5 | Window | EXTERNAL-GATED |

## PROD-1 DBA DDL And Grants Evidence

| Field | Evidence | Status |
| --- | --- | --- |
| PROD-1.1 | credit_award_task | EXTERNAL-GATED |
| PROD-1.2 | raffle_quota_decrement_ledger | EXTERNAL-GATED |
| PROD-1.3 | rebate_task_outbox_000..003 | EXTERNAL-GATED |
| PROD-1.4 | credit_trade_task_outbox_000..003 | EXTERNAL-GATED |
| PROD-1.5 | award_dispatch_task_outbox_000..003 | EXTERNAL-GATED |
| PROD-1.6 | DB grants | EXTERNAL-GATED |
| PROD-1.7 | DBA rollback note | EXTERNAL-GATED |

## PROD-2 Ops Deploy, Discovery, And Job Evidence

| Field | Evidence | Status |
| --- | --- | --- |
| PROD-2.1 | account deploy | EXTERNAL-GATED |
| PROD-2.2 | fulfillment deploy | EXTERNAL-GATED |
| PROD-2.3 | rebate deploy | EXTERNAL-GATED |
| PROD-2.4 | strategy deploy | EXTERNAL-GATED |
| PROD-2.5 | Nacos/Dubbo provider | EXTERNAL-GATED |
| PROD-2.6 | XXL-Job | EXTERNAL-GATED |
| PROD-2.7 | MQ | EXTERNAL-GATED |
| PROD-2.8 | config | EXTERNAL-GATED |

## PROD-3 Metrics, Logs, And Oncall Observation

| Field | Evidence | Status |
| --- | --- | --- |
| PROD-3.1 | quota validation | EXTERNAL-GATED |
| PROD-3.2 | award validation | EXTERNAL-GATED |
| PROD-3.3 | rebate validation | EXTERNAL-GATED |
| PROD-3.4 | strategy validation | EXTERNAL-GATED |
| PROD-3.5 | activity validation | EXTERNAL-GATED |
| PROD-3.6 | Rollback | EXTERNAL-GATED |
| PROD-3.7 | logs | EXTERNAL-GATED |

## PROD-4 Metrics, Logs, And Oncall Observation

| Field | Evidence | Status |
| --- | --- | --- |
| PROD-4.1 | dashboard | EXTERNAL-GATED |
| PROD-4.2 | alert | EXTERNAL-GATED |
| PROD-4.3 | observation | EXTERNAL-GATED |
| PROD-4.4 | error budget | EXTERNAL-GATED |
| PROD-4.5 | rollback monitoring | EXTERNAL-GATED |

## PROD-5 Product And Approval

| Field | Evidence | Status |
| --- | --- | --- |
| PROD-5.1 | account approval | EXTERNAL-GATED |
| PROD-5.2 | fulfillment approval | EXTERNAL-GATED |
| PROD-5.3 | rebate approval | EXTERNAL-GATED |
| PROD-5.4 | strategy approval | EXTERNAL-GATED |
| PROD-5.5 | activity approval | EXTERNAL-GATED |

## PROD-6 GO/NO-GO

| Field | Evidence | Status |
| --- | --- | --- |
| PROD-6.1 | production decision input | EXTERNAL-GATED |
| PROD-6.2 | risk acceptance | EXTERNAL-GATED |
| PROD-6.3 | rollback owner | EXTERNAL-GATED |
| PROD-6.4 | communication | EXTERNAL-GATED |
| PROD-6.5 | final reviewer | EXTERNAL-GATED |
| PROD-6.6 | Final production decision | EXTERNAL-GATED |
| PROD-6.7 | post-cutover watch | EXTERNAL-GATED |

## GO/NO-GO Checklist

| Gate | Source | Status |
| --- | --- | --- |
| GNG-1 | STG-1.1 through STG-1.7 | EXTERNAL-GATED |
| GNG-2 | STG-2.1 through STG-2.8 | EXTERNAL-GATED |
| GNG-3 | STG-3.1 through STG-3.7 | EXTERNAL-GATED |
| GNG-4 | STG-4.1 through STG-4.5 | EXTERNAL-GATED |
| GNG-5 | STG-5.1 through STG-5.5 | EXTERNAL-GATED |
| GNG-6 | STG-6.1 through STG-6.6 | EXTERNAL-GATED |
| GNG-7 | PROD-1.1 through PROD-1.7 | EXTERNAL-GATED |
| GNG-12 | 7-day and 30-day cleanup gates | EXTERNAL-GATED |
| GNG-D9 | Final GO/NO-GO decision result | EXTERNAL-GATED |

Hard NO-GO Conditions: Any required field remains EXTERNAL-GATED; Staging evidence is missing before a production review; 7-day and 30-day cleanup gates are not satisfied.
