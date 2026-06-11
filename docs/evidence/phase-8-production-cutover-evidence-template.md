# Phase 8 Production Cutover Evidence Template

Status: EXTERNAL-GATED. This template is repo-only and contains no live
production evidence. Production cutover evidence must be recorded in a later
batch after staging GO evidence exists.

## PROD-0 Metadata

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| PROD-0.1 | Production change ticket | EXTERNAL-GATED |
| PROD-0.2 | Linked staging GO evidence | EXTERNAL-GATED |
| PROD-0.3 | Git commit/tag deployed | EXTERNAL-GATED |
| PROD-0.4 | Approved production window | EXTERNAL-GATED |
| PROD-0.5 | Rollback owner and escalation channel | EXTERNAL-GATED |

## PROD-1 DBA DDL And Grants Evidence

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| PROD-1.1 | `credit_award_task` production shard/table/index verification | EXTERNAL-GATED |
| PROD-1.2 | `raffle_quota_decrement_ledger` production shard/table/index verification | EXTERNAL-GATED |
| PROD-1.3 | `rebate_task_outbox_000..003` production verification | EXTERNAL-GATED |
| PROD-1.4 | `credit_trade_task_outbox_000..003` production verification | EXTERNAL-GATED |
| PROD-1.5 | `award_dispatch_task_outbox_000..003` production verification | EXTERNAL-GATED |
| PROD-1.6 | Production DB grants and secret rollout evidence | EXTERNAL-GATED |
| PROD-1.7 | DBA rollback note and post-DDL verification query references | EXTERNAL-GATED |

## PROD-2 Ops Deploy, Discovery, And Job Evidence

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| PROD-2.1 | account-service production deploy and Nacos/Dubbo provider discovery | EXTERNAL-GATED |
| PROD-2.2 | fulfillment-service production deploy and Nacos/Dubbo provider discovery | EXTERNAL-GATED |
| PROD-2.3 | rebate-service production deploy and duplicate-provider check | EXTERNAL-GATED |
| PROD-2.4 | strategy-service production deploy and read provider discovery | EXTERNAL-GATED |
| PROD-2.5 | activity-service production deploy evidence, if approved | EXTERNAL-GATED |
| PROD-2.6 | XXL-Job registration for production-enabled outbox handlers | EXTERNAL-GATED |
| PROD-2.7 | MQ binding/topic evidence for production-enabled flows | EXTERNAL-GATED |
| PROD-2.8 | Production config diff for canary overrides and rollback commands | EXTERNAL-GATED |

## PROD-3 Single-Instance Canary Evidence

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| PROD-3.1 | account write/quota single-instance canary result | EXTERNAL-GATED |
| PROD-3.2 | fulfillment award/credit outbox single-instance canary result | EXTERNAL-GATED |
| PROD-3.3 | rebate write/read single-instance canary result | EXTERNAL-GATED |
| PROD-3.4 | strategy read-only single-instance canary result | EXTERNAL-GATED |
| PROD-3.5 | activity draw canary result, only if Phase 5-G/7-D approval exists | EXTERNAL-GATED |
| PROD-3.6 | Flag diff for every production canary override | EXTERNAL-GATED |
| PROD-3.7 | Rollback rehearsal or live rollback readiness confirmation | EXTERNAL-GATED |

## PROD-4 Metrics, Logs, And Oncall Observation

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| PROD-4.1 | Dashboard links and observation window | EXTERNAL-GATED |
| PROD-4.2 | Error-rate, latency, retry, and saturation metrics | EXTERNAL-GATED |
| PROD-4.3 | MQ lag, outbox backlog, and job execution metrics | EXTERNAL-GATED |
| PROD-4.4 | Duplicate order, credit drift, quota drift, and award-miss checks | EXTERNAL-GATED |
| PROD-4.5 | Oncall anomaly review and incident checklist | EXTERNAL-GATED |

## PROD-5 Product And Support Evidence

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| PROD-5.1 | account credit behavior acceptance or exemption | EXTERNAL-GATED |
| PROD-5.2 | fulfillment award delivery acceptance and support note | EXTERNAL-GATED |
| PROD-5.3 | rebate calendar sign acceptance and support note | EXTERNAL-GATED |
| PROD-5.4 | strategy read-only acceptance or exemption | EXTERNAL-GATED |
| PROD-5.5 | activity draw production approval and support note, if in scope | EXTERNAL-GATED |

## PROD-6 GO/NO-GO Decision

| Field ID | Evidence field | Current value |
|----------|----------------|---------------|
| PROD-6.1 | DBA sign-off | EXTERNAL-GATED |
| PROD-6.2 | Ops sign-off | EXTERNAL-GATED |
| PROD-6.3 | Engineering sign-off | EXTERNAL-GATED |
| PROD-6.4 | Oncall sign-off | EXTERNAL-GATED |
| PROD-6.5 | Product sign-off or exemption | EXTERNAL-GATED |
| PROD-6.6 | Final production decision | EXTERNAL-GATED |
| PROD-6.7 | 7-day stability clock start, if final decision is GO later | EXTERNAL-GATED |

This file does not authorize production traffic, flag defaults, legacy provider
disablement, or 30-day cleanup while the fields above remain EXTERNAL-GATED.
