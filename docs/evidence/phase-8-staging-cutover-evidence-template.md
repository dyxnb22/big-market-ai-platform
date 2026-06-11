# Phase 8 Staging Cutover Evidence Template

Status: EXTERNAL-GATED. This template is repo-only and contains no live staging
evidence. Fill it only after an approved staging window has produced external
DBA, Ops, Engineering, Oncall, and Product evidence.

## STG-0 Metadata

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| STG-0.1 | Staging window ticket | EXTERNAL-GATED |
| STG-0.2 | Git commit/tag deployed | EXTERNAL-GATED |
| STG-0.3 | Services included | EXTERNAL-GATED |
| STG-0.4 | Evidence owner | EXTERNAL-GATED |
| STG-0.5 | Rollback owner | EXTERNAL-GATED |

## STG-1 DBA DDL And Grants Evidence

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| STG-1.1 | `credit_award_task` shard/table/index verification | EXTERNAL-GATED |
| STG-1.2 | `raffle_quota_decrement_ledger` shard/table/index verification | EXTERNAL-GATED |
| STG-1.3 | `rebate_task_outbox_000..003` table/index verification | EXTERNAL-GATED |
| STG-1.4 | `credit_trade_task_outbox_000..003` table/index verification | EXTERNAL-GATED |
| STG-1.5 | `award_dispatch_task_outbox_000..003` table/index verification | EXTERNAL-GATED |
| STG-1.6 | DB grants and service secret rollout evidence | EXTERNAL-GATED |
| STG-1.7 | DBA rollback note for staging DDL | EXTERNAL-GATED |

## STG-2 Ops Deploy, Discovery, And Job Evidence

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| STG-2.1 | account-service deploy and Nacos/Dubbo provider discovery | EXTERNAL-GATED |
| STG-2.2 | fulfillment-service deploy and Nacos/Dubbo provider discovery | EXTERNAL-GATED |
| STG-2.3 | rebate-service deploy and duplicate-provider check | EXTERNAL-GATED |
| STG-2.4 | strategy-service deploy and read provider discovery | EXTERNAL-GATED |
| STG-2.5 | activity-service deploy evidence, if draw rehearsal is approved | EXTERNAL-GATED |
| STG-2.6 | XXL-Job registration for enabled outbox handlers | EXTERNAL-GATED |
| STG-2.7 | MQ binding/topic evidence for enabled staging flows | EXTERNAL-GATED |
| STG-2.8 | Config diff proving repo defaults remain false | EXTERNAL-GATED |

## STG-3 Engineering Canary And Flow Evidence

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| STG-3.1 | account write/quota/credit idempotency validation | EXTERNAL-GATED |
| STG-3.2 | fulfillment award write and credit-award outbox validation | EXTERNAL-GATED |
| STG-3.3 | rebate calendar sign write/read and outbox validation | EXTERNAL-GATED |
| STG-3.4 | strategy read parity and latency comparison | EXTERNAL-GATED |
| STG-3.5 | activity draw saga validation, only if Phase 5-G/7-D approval exists | EXTERNAL-GATED |
| STG-3.6 | Staging flag canary evidence and exact flag diff | EXTERNAL-GATED |
| STG-3.7 | Rollback rehearsal result and replay/drain notes | EXTERNAL-GATED |

## STG-4 Metrics, Logs, And Oncall Observation

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| STG-4.1 | Dashboard links and observation window | EXTERNAL-GATED |
| STG-4.2 | Error-rate, latency, and retry metrics | EXTERNAL-GATED |
| STG-4.3 | MQ lag and outbox backlog metrics | EXTERNAL-GATED |
| STG-4.4 | Duplicate order, credit drift, and quota drift checks | EXTERNAL-GATED |
| STG-4.5 | Oncall incident/rollback readiness note | EXTERNAL-GATED |

## STG-5 Product And User-Visible Acceptance

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| STG-5.1 | account credit behavior acceptance or exemption | EXTERNAL-GATED |
| STG-5.2 | fulfillment award delivery acceptance | EXTERNAL-GATED |
| STG-5.3 | rebate calendar sign acceptance | EXTERNAL-GATED |
| STG-5.4 | strategy read-only acceptance or exemption | EXTERNAL-GATED |
| STG-5.5 | activity draw cohort and support approval, if in scope | EXTERNAL-GATED |

## STG-6 GO/NO-GO Decision

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| STG-6.1 | DBA sign-off | EXTERNAL-GATED |
| STG-6.2 | Ops sign-off | EXTERNAL-GATED |
| STG-6.3 | Engineering sign-off | EXTERNAL-GATED |
| STG-6.4 | Oncall sign-off | EXTERNAL-GATED |
| STG-6.5 | Product sign-off or exemption | EXTERNAL-GATED |
| STG-6.6 | Final staging decision | EXTERNAL-GATED |

No production cutover may use this file as approval until STG-6.6 contains real
external evidence in a later evidence batch.
