# Microservices Legacy Cleanup Inventory

Last revised: 2026-06-11.

Status: repo-only inventory. No item below is currently removable from the
repository. Every cleanup candidate remains EXTERNAL-GATED until real external
cutover evidence, 7-day stability evidence, and 30-day removal evidence exist.

## Inventory Rules

- Legacy providers, default-local adapters, local fallback ports, mapper XML
  compatibility copies, and shared task/outbox fallbacks must stay present until
  their evidence gates are satisfied.
- The 7-day stable gate can only disable a legacy provider or fallback in an
  environment; it does not authorize repository deletion.
- The 30-day removal gate is the earliest point at which obsolete code or mapper
  copies can be removed from the repo.
- Validators that should fail if an item is removed too early:
  `scripts/validate-microservices-legacy-cleanup-readiness.sh` and
  `scripts/validate-microservices-post-cutover-cleanup-gates.sh`.

## Legacy RPC Providers

| Item | Current owner | Reason it still exists | Flag gate | External evidence required before disabling | 7-day stable gate | 30-day removal gate | Exact validator that should fail if removed too early |
|------|---------------|------------------------|-----------|---------------------------------------------|-------------------|---------------------|-------------------------------------------------------|
| `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java` | market-service legacy trigger | Prevents duplicate provider/cutover risk while rebate-service is dark-launched and remote rebate flags are false | `REBATE_LEGACY_RPC_PROVIDER_ENABLED=true` default; `@ConditionalOnProperty(matchIfMissing=true)` | EXTERNAL-GATED: rebate-service DBA/Ops/Engineering/Oncall/Product evidence, provider listing, rebate outbox DDL evidence | EXTERNAL-GATED: 7 clean days after remote rebate write/read cutover | EXTERNAL-GATED: 30 clean days plus cleanup signoff | `scripts/validate-microservices-legacy-cleanup-readiness.sh`, `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java` | market-service legacy trigger | Preserves read-only strategy RPC compatibility while strategy-service read traffic is gated | `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED=true` default; `@ConditionalOnProperty(matchIfMissing=true)` | EXTERNAL-GATED: strategy-service Ops provider listing, Engineering read parity, Oncall monitoring, Product evidence or exemption | EXTERNAL-GATED: 7 clean days after strategy remote-read cutover | EXTERNAL-GATED: 30 clean days plus cleanup signoff | `scripts/validate-microservices-legacy-cleanup-readiness.sh`, `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java` Dubbo provider surface | market-service legacy trigger | Owns current draw/activity RPC and HTTP surface; activity-service has no runtime draw surface | no repo flag yet; activity draw cutover remains EXTERNAL-GATED | EXTERNAL-GATED: Product, DBA, Ops, Engineering, and Oncall activity draw cutover evidence | EXTERNAL-GATED: 7 clean days after approved activity draw cutover | EXTERNAL-GATED: 30 clean days plus cleanup signoff | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-trigger/src/main/java/com/dyx/market/trigger/http/ErpOperateController.java` Dubbo provider surface | market-service legacy trigger | ERP operation provider is outside current service extraction scope | no cleanup flag; not eligible in Phase 8 | EXTERNAL-GATED: explicit future owner decision and cutover evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |

## Default-Local Adapters

| Item | Current owner | Reason it still exists | Flag gate | External evidence required before disabling | 7-day stable gate | 30-day removal gate | Exact validator that should fail if removed too early |
|------|---------------|------------------------|-----------|---------------------------------------------|-------------------|---------------------|-------------------------------------------------------|
| `LocalAccountReadAdapter` | market-service legacy trigger | Default read path when `account.service.remote-read.enabled=false` or remote read fails | `ACCOUNT_SERVICE_REMOTE_READ_ENABLED=false` default | EXTERNAL-GATED: account staging/prod read evidence and rollback proof | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |
| `LocalAccountCreditWriteAdapter` | market-service/message-job compatibility | Default credit write path while account write cutover is gated | `ACCOUNT_SERVICE_REMOTE_CREDIT_WRITE_ENABLED=false` default | EXTERNAL-GATED: account credit write cutover evidence and no drift | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |
| `LocalAccountQuotaWriteAdapter` | market-service/message-job compatibility | Default quota write path while account quota cutover is gated | `ACCOUNT_SERVICE_REMOTE_QUOTA_WRITE_ENABLED=false` default | EXTERNAL-GATED: quota write cutover evidence and no quota drift | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |
| `LocalAwardDispatchAdapter` | message-job compatibility | Default award dispatch path while fulfillment remote award is gated | `ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false` default | EXTERNAL-GATED: fulfillment cutover evidence and no missing/duplicate awards | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalRebateOrderAdapter` | market-service legacy trigger | Default calendar-sign rebate write path while rebate remote create is gated | `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=false` default | EXTERNAL-GATED: rebate write cutover evidence and duplicate-order check | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |
| `LocalRebateReadAdapter` | market-service legacy trigger | Default calendar-sign rebate read path while rebate remote read is gated | `REBATE_SERVICE_REMOTE_READ_ENABLED=false` default | EXTERNAL-GATED: rebate read parity evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |
| `LocalStrategyReadAdapter` | market-service legacy trigger | Default strategy/rule read path while strategy remote read is gated | `STRATEGY_SERVICE_REMOTE_READ_ENABLED=false` default | EXTERNAL-GATED: strategy read parity and latency evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |

## Local Fallback Ports

| Item | Current owner | Reason it still exists | Flag gate | External evidence required before disabling | 7-day stable gate | 30-day removal gate | Exact validator that should fail if removed too early |
|------|---------------|------------------------|-----------|---------------------------------------------|-------------------|---------------------|-------------------------------------------------------|
| `LocalActivityAccountPort` | infrastructure compatibility | Default quota decrement/read fallback for draw path | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false` default | EXTERNAL-GATED: account quota decrement ledger DDL and staging/prod idempotency evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalStrategyActivityAccountPort` | infrastructure compatibility | Strategy service still needs local participation counts until account read ownership is proven | strategy remote read remains false until parity | EXTERNAL-GATED: account participation read parity evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-legacy-cleanup-readiness.sh` |
| `LocalStrategyActivityMappingPort` | infrastructure compatibility | Resolves AL-1 without physical activity table isolation | activity/strategy table isolation remains EXTERNAL-GATED | EXTERNAL-GATED: activity table isolation evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalStrategyDecisionPort` | infrastructure compatibility | Draw decision remains in-process until activity draw cutover is approved | no remote decision flag enabled | EXTERNAL-GATED: activity draw cutover and Product approval | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalAwardFulfillmentPort` | infrastructure compatibility | Award fulfillment remains local in draw hot path until fulfillment/activity cutover evidence exists | fulfillment remote flags default false | EXTERNAL-GATED: fulfillment cutover and activity draw evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalAwardCreditWritePort` | infrastructure compatibility | Credit write/outbox path remains local/default-off while account outbox cutover is gated | `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` default | EXTERNAL-GATED: credit-award outbox DDL, dispatch evidence, no credit drift | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalCreditAwardTaskDispatchPort` | infrastructure/message-job compatibility | Dispatch job reads credit-award tasks through port; physical ownership not externally cut over | `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` default | EXTERNAL-GATED: account outbox cutover evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalAwardActivityOrderPort` | infrastructure compatibility | User raffle order compatibility remains local until activity/award table isolation | activity/fulfillment cutovers gated | EXTERNAL-GATED: activity and fulfillment cutover evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalDrawOutboxPort` | infrastructure compatibility | Saga/outbox scaffold only; draw outbox is not hot-path wired | activity draw/outbox flags default false | EXTERNAL-GATED: Product/DBA/Ops/Engineering/Oncall activity draw evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |

## Shared Mapper Compatibility Copies

| Item | Current owner | Reason it still exists | Flag gate | External evidence required before disabling | 7-day stable gate | 30-day removal gate | Exact validator that should fail if removed too early |
|------|---------------|------------------------|-----------|---------------------------------------------|-------------------|---------------------|-------------------------------------------------------|
| `big-market-app/src/main/resources/mybatis/mapper/mysql/*.xml` compatibility set | monolith fallback | Keeps `big-market-app` buildable/runnable as single-process fallback | all remote/outbox flags default false | EXTERNAL-GATED: all service cutovers and fallback retirement signoff | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-market-service/src/main/resources/mybatis/mapper/mysql/*.xml` compatibility set | market-service | Local adapters and legacy controllers still use local DAOs | service remote flags default false | EXTERNAL-GATED: per-service cutover evidence by mapper domain | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-message-job-service/src/main/resources/mybatis/mapper/mysql/*.xml` compatibility set | message-job-service | MQ consumers/jobs still need local fallback adapters and shared task dispatch | account/fulfillment/outbox flags default false | EXTERNAL-GATED: job/outbox cutover evidence by domain | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-account-service/src/main/resources/mybatis/mapper/mysql/*.xml` compatibility set | account-service | Dark-launch account service still carries mapper copies for build/runtime compatibility | account remote/outbox flags default false | EXTERNAL-GATED: account DB ownership and grant evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-rebate-service/src/main/resources/mybatis/mapper/mysql/daily_behavior_rebate_mapper.xml`, `task_mapper.xml`, `user_behavior_rebate_order_mapper.xml` | rebate-service | Rebate service still uses legacy task fallback until rebate outbox DDL and cutover evidence exist | rebate remote flags default false | EXTERNAL-GATED: rebate outbox DDL and message publish evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/*.xml` strategy set | strategy-service | Strategy read service owns strategy mappers; not a cleanup candidate before read cutover | strategy remote read default false | EXTERNAL-GATED: strategy read parity and grant evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |

Explicit exemptions:
- `big-market-activity-service` intentionally has no mapper XML in Phase 8; it is dark-launch only.
- `big-market-fulfillment-service` award mapper files (`award_mapper.xml`, `user_award_record_mapper.xml`) are service-owned and protected by service ownership validators.
- `big-market-fulfillment-service/src/main/resources/mybatis/mapper/mysql/user_credit_account_mapper.xml`,
  `big-market-fulfillment-service/src/main/resources/mybatis/mapper/mysql/credit_award_task_mapper.xml`,
  `big-market-fulfillment-service/src/main/resources/mybatis/mapper/mysql/task_mapper.xml`,
  `big-market-fulfillment-service/src/main/resources/mybatis/mapper/mysql/user_raffle_order_mapper.xml`,
  and `big-market-fulfillment-service/src/main/resources/mybatis/mapper/elasticsearch/user_raffle_order_mapper.xml`
  are local learning-mode compatibility copies required for the dark-launch
  stack to start with shared infrastructure scanning
  (`com.dyx.market.infrastructure` transitively requires `ITaskDao`,
  `IUserCreditAccountDao`, `ICreditAwardTaskDao`, and `IUserRaffleOrderDao`
  mapper resolution). They are not evidence of account/activity/task table
  ownership transfer and remain cleanup-gated under the per-domain external
  evidence and 30-day removal gate.

## Generic Task and Outbox Fallbacks

| Item | Current owner | Reason it still exists | Flag gate | External evidence required before disabling | 7-day stable gate | 30-day removal gate | Exact validator that should fail if removed too early |
|------|---------------|------------------------|-----------|---------------------------------------------|-------------------|---------------------|-------------------------------------------------------|
| `LocalRebateTaskOutboxPort` | infrastructure compatibility | Preserves shared `task` fallback for rebate until `rebate_task_outbox` is externally cut over | rebate remote/outbox cutover gated | EXTERNAL-GATED: rebate outbox DDL, publish parity, drain evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalCreditTradeTaskOutboxPort` | infrastructure compatibility | Preserves shared `task` fallback for credit trade until `credit_trade_task_outbox` is externally cut over | credit trade outbox cutover gated | EXTERNAL-GATED: credit trade outbox DDL, publish parity, drain evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `LocalAwardDispatchTaskOutboxPort` | infrastructure compatibility | Preserves shared `task` fallback for award dispatch until `award_dispatch_task_outbox` is externally cut over | award dispatch outbox cutover gated | EXTERNAL-GATED: award dispatch outbox DDL, publish parity, drain evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `DispatchCreditAwardTaskJob` port boundary | message-job-service | Dispatch job is flag-gated and remains default-off until account outbox evidence exists | `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` default | EXTERNAL-GATED: credit award outbox DDL and dispatch evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |
| `SendMessageTaskJob` shared task dispatcher | message-job-service legacy trigger | Shared `task` table still carries fallback MQ dispatch behavior | per-domain outbox cutovers gated | EXTERNAL-GATED: all per-domain outbox DDL, drain, and no-pending evidence | EXTERNAL-GATED | EXTERNAL-GATED | `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |

## Current Cleanup Eligibility

No provider, fallback, mapper copy, or shared task/outbox path is removable at
this time. The repository has readiness docs and validators, but it does not
contain real DBA/Ops/Engineering/Oncall/Product evidence proving production
cutover, 7-day stability, or 30-day removal eligibility.

## Learning-Mode Documentation Cleanup

The local learning project is LEARNING-MODE-COMPLETE using
LOCAL-LEARNING-EVIDENCE and SIMULATED-CUTOVER-EVIDENCE, but that does not make
runtime cleanup candidates removable. Documentation cleanup was handled by
creating `docs/archive/microservices-historical-docs-index.md` and leaving
validator-referenced historical docs in place.
