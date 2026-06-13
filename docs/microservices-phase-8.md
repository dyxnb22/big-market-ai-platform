# Microservices Phase 8

Status: repo-only, external cutover remains EXTERNAL-GATED.

LOCAL-LEARNING-EVIDENCE / SIMULATED-CUTOVER-EVIDENCE / LEARNING-MODE-COMPLETE.
This document does not prove staging or production readiness.

## Purpose

This consolidated Phase 8 document replaces the separate cutover runbook,
external evidence intake, readiness pack, conflict matrix, and
idempotency/rollback matrix. Evidence lives in
`docs/evidence/phase-8-evidence-pack.md`.

Validators:

- `scripts/validate-microservices-phase-8-cutover-readiness.sh`
- `scripts/validate-microservices-phase-8-external-evidence-intake.sh`
- `scripts/validate-microservices-phase-8-external-evidence-readiness-pack.sh`
- `scripts/validate-microservices-phase-8-cutover-conflict-matrix.sh`
- `scripts/validate-microservices-phase-8-idempotency-rollback-matrix.sh`
- `scripts/validate-microservices-phase-8-staging-evidence-intake.sh`
- `scripts/validate-microservices-phase-8-staging-evidence-consistency.sh`
- `scripts/validate-microservices-phase-8-cutover-evidence-pack.sh`

## Cutover Runbook

Services: account-service, fulfillment-service, rebate-service,
strategy-service, activity-service.

Prerequisites: DBA DDL/grants, Ops deploy/discovery/jobs/MQ, Engineering
staging validation, Oncall dashboards/alerts, Product GO/NO-GO.

Proposed DDL: `proposed-credit-award-task-outbox.sql`,
`proposed-quota-decrement-ledger.sql`, `proposed-rebate-task-outbox.sql`,
`proposed-credit-trade-task-outbox.sql`, `proposed-award-dispatch-task-outbox.sql`.

Staging validation: use `docs/evidence/phase-8-evidence-pack.md` and keep all
STG/PROD/GNG rows EXTERNAL-GATED until real references exist.

Production canary: only after external staging approval is recorded. Rollback:
disable remote/outbox flags and restore legacy provider defaults. acceptance criteria: 7-day stable legacy-provider disable, then 30-day removal.

## External Evidence Intake

## DBA Gates

Owner: DBA. Status: EXTERNAL-GATED. Covers DDL verification, DB grants, schema
rollback note, and shard checks. STG-1.1 through STG-1.7; PROD-1.1 through
PROD-1.7.

## Ops Gates

Owner: Ops. Status: EXTERNAL-GATED. Covers Dubbo provider, Nacos registration,
XXL-Job, MQ, config rollout, and rollback. STG-2.1 through STG-2.8; PROD-2.1
through PROD-2.8.

## Engineering Gates

Owner: Engineering. Status: EXTERNAL-GATED. Covers Staging Canary, idempotency,
rollback rehearsal, high-risk flows, and validation commands. STG-3.1 through
STG-3.7; PROD-3.1 through PROD-3.7.

## Oncall Gates

Owner: Oncall. Status: EXTERNAL-GATED. Covers Dashboards and Alerts,
observation windows, error budget, and rollback monitoring. STG-4.1 through
STG-4.5; PROD-4.1 through PROD-4.5.

## Product Gates

Owner: Product. Status: EXTERNAL-GATED. Covers GO/NO-GO, exemptions, user-facing
impact, and final decision. STG-5.1 through STG-5.5; PROD-5.1 through PROD-5.5;
GNG-D9.

Evidence field map: STG-1.1, STG-2.6, STG-3.6, STG-6.6, PROD-0.2, PROD-3.6,
PROD-6.6, GNG-D9.
Range map: STG-1.1 through STG-1.7; STG-2.1 through STG-2.8; STG-3.1 through STG-3.7; STG-4.1 through STG-4.5; STG-5.1 through STG-5.5; STG-6.1 through STG-6.6.

### account-service cutover evidence

Owning team DBA/Ops/Engineering/Oncall/Product. Status: EXTERNAL-GATED.
Flags: ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED,
ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED.

### fulfillment-service cutover evidence

Owning team DBA/Ops/Engineering/Oncall/Product. Status: EXTERNAL-GATED.

### rebate-service cutover evidence

Owning team DBA/Ops/Engineering/Oncall/Product. Status: EXTERNAL-GATED.
Flags: REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED,
REBATE_SERVICE_REMOTE_READ_ENABLED, REBATE_LEGACY_RPC_PROVIDER_ENABLED.

### strategy-service cutover evidence

Owning team DBA/Ops/Engineering/Oncall/Product. Status: EXTERNAL-GATED.
Flags: STRATEGY_SERVICE_REMOTE_READ_ENABLED, STRATEGY_LEGACY_RPC_PROVIDER_ENABLED.

### activity-service cutover evidence

Owning team DBA/Ops/Engineering/Oncall/Product. Status: EXTERNAL-GATED.

## External Evidence Readiness Pack

Status: EXTERNAL-GATED.
No real production readiness, staging cutover, remote cutover, or DDL readiness
is proven; real staging and production readiness remain external-gated.

DBA DDL Verification. Ops Dubbo Provider / XXL-Job / MQ. Engineering Staging
Canary. Oncall Dashboards and Alerts. Product GO/NO-GO.

### 2.1 Quota Decrement / Rollback

Quota Decrement / Rollback.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-3 | EXTERNAL-GATED |
| Ops | OPS-1 | EXTERNAL-GATED |
| Engineering | ENG-3 | EXTERNAL-GATED |
| Oncall | ONC-1 | EXTERNAL-GATED |
| Product | PRD-6 | EXTERNAL-GATED |

### 2.2 Credit Award Outbox Dispatch

Credit Award Outbox Dispatch.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-1 | EXTERNAL-GATED |
| Ops | OPS-5 | EXTERNAL-GATED |
| Engineering | ENG-5 | EXTERNAL-GATED |
| Oncall | ONC-7 | EXTERNAL-GATED |
| Product | PRD-6 | EXTERNAL-GATED |

### 2.3 Award Fulfillment

Award Fulfillment.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-9 | EXTERNAL-GATED |
| Ops | OPS-2 | EXTERNAL-GATED |
| Engineering | ENG-4 | EXTERNAL-GATED |
| Oncall | ONC-3 | EXTERNAL-GATED |
| Product | PRD-2 | EXTERNAL-GATED |

### 2.4 Rebate Create / Read

Rebate Create / Read.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-5 | EXTERNAL-GATED |
| Ops | OPS-3 | EXTERNAL-GATED |
| Engineering | ENG-6 | EXTERNAL-GATED |
| Oncall | ONC-4 | EXTERNAL-GATED |
| Product | PRD-3 | EXTERNAL-GATED |

### 2.5 Credit Trade

Credit Trade.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-7 | EXTERNAL-GATED |
| Ops | OPS-1 | EXTERNAL-GATED |
| Engineering | ENG-1 | EXTERNAL-GATED |
| Oncall | ONC-2 | EXTERNAL-GATED |
| Product | PRD-1 | EXTERNAL-GATED |

### 2.6 SKU Exchange

SKU Exchange.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-11 | EXTERNAL-GATED |
| Ops | OPS-1 | EXTERNAL-GATED |
| Engineering | ENG-2 | EXTERNAL-GATED |
| Oncall | ONC-6 | EXTERNAL-GATED |
| Product | PRD-6 | EXTERNAL-GATED |

### 2.7 Shared Task Fallback vs Per-Domain Outbox

Shared Task Fallback vs Per-Domain Outbox.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-1 | EXTERNAL-GATED |
| Ops | OPS-5 | EXTERNAL-GATED |
| Engineering | ENG-5 | EXTERNAL-GATED |
| Oncall | ONC-7 | EXTERNAL-GATED |
| Product | PRD-5 | EXTERNAL-GATED |

### 2.8 Strategy Read

Strategy Read.

| Stakeholder | Checklist ID | Status |
| --- | --- | --- |
| DBA | DBA-11 | EXTERNAL-GATED |
| Ops | OPS-4 | EXTERNAL-GATED |
| Engineering | ENG-8 | EXTERNAL-GATED |
| Oncall | ONC-5 | EXTERNAL-GATED |
| Product | PRD-4 | EXTERNAL-GATED |

DBA-Staging EXTERNAL-GATED. Ops-Staging EXTERNAL-GATED.
Engineering-Staging EXTERNAL-GATED. Oncall-Staging EXTERNAL-GATED.
Product-Staging EXTERNAL-GATED.

### Proposed DDL Coverage Map

`proposed-credit-award-task-outbox.sql`, `proposed-quota-decrement-ledger.sql`,
`proposed-rebate-task-outbox.sql`, `proposed-credit-trade-task-outbox.sql`,
`proposed-award-dispatch-task-outbox.sql`.

### GO/NO-GO Decision Matrix

Staging GO criteria: all STG rows require real external evidence.
Production GO criteria: all PROD rows require real external evidence.

| Stakeholder | Status |
| --- | --- |
| DBA | EXTERNAL-GATED |
| Ops | EXTERNAL-GATED |
| Engineering | EXTERNAL-GATED |
| Oncall | EXTERNAL-GATED |
| Product | EXTERNAL-GATED |

### Execution Order

1. External evidence intake.
2. Staging evidence review.
3. Production canary.
4. 7-day stable observation.
5. 30-day cleanup clock.

## Cutover Conflict Matrix

| Domain | Legacy path | Future path | Owning service | Flag that enables new path | Flag that disables old path | Why both must not run simultaneously | Current safe default | Gate |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Account / Credit Write | local adapter | account remote write | account-service | account.service.remote-credit-write.enabled | legacy provider | double credit write | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Account / Quota Write | local adapter | account remote quota write | account-service | account.service.remote-quota-write.enabled | legacy provider | double quota write | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Account / Quota Decrement | LocalActivityAccountPort | remote decrement | account-service | account.service.remote-quota-decrement.enabled | local fallback | double decrement | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Fulfillment / Award Dispatch | LocalAwardDispatchAdapter | remote award dispatch | fulfillment-service | account.fulfillment.remote-award.enabled | local fallback | double award dispatch | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Fulfillment / Award Draw Hot Path | LocalAwardFulfillmentPort | future remote fulfillment | fulfillment-service | award.service.remote-fulfillment.enabled | local fallback | double fulfillment | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Rebate / Create Order | RebateServiceRPC | rebate remote create | rebate-service | rebate.service.remote-create-order.enabled | rebate.legacy-rpc-provider.enabled | duplicate rebate order | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Rebate / Read | LocalRebateReadAdapter | rebate remote read | rebate-service | rebate.service.remote-read.enabled | local fallback | inconsistent read owner | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Strategy / Read | RaffleStrategyServiceRPC | strategy remote read | strategy-service | strategy.service.remote-read.enabled | strategy.legacy-rpc-provider.enabled | duplicate provider reads | false | EXTERNAL-GATED; 7-day stable; 30-day removal |
| Shared Task / Outbox Dispatcher | SendMessageTaskJob | per-domain outbox dispatchers | message-job-service | account.award-credit-outbox.enabled=false | mutual exclusion | duplicate dispatch | false | EXTERNAL-GATED; 7-day stable; 30-day removal |

## Idempotency And Rollback Matrix

| Business operation | Idempotency key | DB unique key | Retry behavior | Rollback behavior | Status |
| --- | --- | --- | --- | --- | --- |
| Quota Decrement | outBusinessNo | raffle_quota_decrement_ledger.uq_user_activity_biz on user_id/activity_id/out_business_no | retry same key | rollbackQuota | EXTERNAL-GATED |
| Credit Award Outbox Dispatch | awardOrderId | credit_award_task.uq_award_order_id on user_id/award_order_id | retry pending task | disable dispatcher | EXTERNAL-GATED |
| Award Fulfillment | order_id | user_award_record.uq_order_id on order_id | DuplicateKeyException idempotent | local fallback | EXTERNAL-GATED |
| Rebate Create Order | biz_id | user_behavior_rebate_order.uq_biz_id on biz_id | DuplicateKeyException idempotent | remote flag false | EXTERNAL-GATED |
| Rebate Read | userId/outBusinessNo | user_behavior_rebate_order.uq_biz_id on biz_id | retry read | remote flag false | EXTERNAL-GATED |
| Credit Trade | out_business_no | user_credit_order.uq_out_business_no on out_business_no | DuplicateKeyException idempotent | remote flag false | EXTERNAL-GATED |
| SKU Exchange | out_business_no | raffle_activity_order.uq_out_business_no on out_business_no | retry same order | rollback quota | EXTERNAL-GATED |
| Shared Task Fallback | message_id | task.uq_message_id on message_id | retry task | fail task | EXTERNAL-GATED |
| Per-domain outboxes | user/message id | uq_user_message_id | retry pending | disable domain dispatcher | EXTERNAL-GATED |

`task.uq_message_id` uses `message_id`; `user_award_record.uq_order_id` uses
`order_id`; `user_behavior_rebate_order.uq_biz_id` uses `biz_id`;
`user_credit_order.uq_out_business_no` and `raffle_activity_order.uq_out_business_no`
use `out_business_no`.

Dual-path idempotency gap: both path variants must not run simultaneously
without the same idempotency key and rollback owner.
dual-dispatch risk: shared task + outbox must not both dispatch the same
message. `JobMutualExclusionValidator` guards startup and
`shared-task-fallback.credit-award-disabled` documents the fallback switch.
