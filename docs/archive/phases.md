# Microservices Phase Archive

This consolidated archive replaces the old per-phase files and phase-specific
validator notes. The old one-off validator scripts were removed from the active
script surface; this document keeps their conclusions for traceability.

## Phase 1

Scaffold and inventory phase. No production traffic, schema grants, or remote
flag changes were made.

## Phase 2.2 Account Service

Phase 2.2-B5 completed the account-service ownership plan around
`user_credit_account`, `user_credit_order`, `raffle_activity_account`,
`raffle_activity_account_day`, `raffle_activity_account_month`,
`raffle_quota_decrement_ledger`, and `credit_award_task`.

The account path keeps local compatibility while ports own credit, quota, and
award-credit outbox behavior. Direct remote adapter wiring is explicitly
forbidden until external gates pass.

## Phase 2.3 Fulfillment Service

Phase 2.3-C, Phase 2.3-D, and Phase 2.3-E prepared fulfillment cutover without
turning on production traffic. `user_award_record`, `award`,
`credit_award_task`, and award-credit handoff remain guarded. Readiness,
production gate, and cutover execution validators still require DBA DDL,
external evidence, rollback notes, and remote flag approval.

## Phase 3 Next Extraction

Target: rebate-service. Remaining Monolith Coupling Points were documented for
`BehaviorRebateRepository`, `user_behavior_rebate_order`,
`daily_behavior_rebate`, and task/outbox ownership. Rebate read and create-order
adapters are repo-ready; remote create-order and read traffic stay off until
EXTERNAL-GATED evidence is supplied.

### Database and Table Ownership

Rebate owns rebate tables; shared task use is compatibility-only.

### RPC and API Contract Gaps

RPC exposure remains dark-launch and must not bypass external gates.
rebate adapter boundary: `IRebateOrderAdapter` and `IRebateReadAdapter` keep
legacy fallback while remote create/read adapters stay disabled.
Duplicate provider risk is controlled by `REBATE_LEGACY_RPC_PROVIDER_ENABLED`;
cutover order includes staging provider verification, remote adapter canary,
then `REBATE_LEGACY_RPC_PROVIDER_ENABLED=false`.

### Job Ownership

Rebate job/outbox ownership is documented but not production-enabled.
`RebateMessageConsumer` ownership and shared task outbox coupling remain
blockers. The former Phase 3 validator scripts were removed during script
surface cleanup; their status is preserved in this archive.

## Phase 3 Rebate Outbox Ownership

The rebate outbox decision covers rebate-owned tables `daily_behavior_rebate`
and `user_behavior_rebate_order`; it moves toward Phase 7-C
`rebate_task_outbox` and a per-domain outbox model. Local fallback through the
shared task table remains only for compatibility. Migration order, rollback
strategy, and validation gates remain external-gated.

## Phase 4 Strategy Service

`strategy-service` is read-first. `strategy.service.remote-read.enabled=false`
and `strategy.service.remote-decision.enabled=false` remain default. Non-Goal:
no strategy decision traffic or production reads are enabled by this phase.
Any blocker requires keeping market/app local reads.

## Phase 4 Strategy Table Ownership

Strategy owns `strategy`, `strategy_award`, `strategy_rule`, `rule_tree`,
`rule_tree_node`, and `rule_tree_node_line`. Mapper copies:
`IStrategyDao.xml`, `IStrategyAwardDao.xml`, `IStrategyRuleDao.xml`,
`IRuleTreeDao.xml`, `IRuleTreeNodeDao.xml`, and `IRuleTreeNodeLineDao.xml`.
Runtime XML names: `strategy_mapper.xml`, `strategy_award_mapper.xml`,
`strategy_rule_mapper.xml`, `rule_tree_mapper.xml`, `rule_tree_node_mapper.xml`,
and `rule_tree_node_line_mapper.xml`.

## Phase 5 Activity Draw Orchestration

`RaffleApplicationService` orchestrates strategy, activity/quota, award
fulfillment, and outbox/task boundaries through ports. It references
`IRaffleStrategy`, `IStrategyDecisionPort`, `performRaffle`, `IAwardService`,
`ITaskService`, `SendMessageTaskJob`, `createOrder`, `saveUserAwardRecord`,
`activity`, `quota`, `award`, `fulfillment`, and `outbox/task` as bounded
dependencies.
Non-goals: no remote activity draw, no remote award fulfillment, no production
flag enablement. No activity-service scaffold is created by Phase 5-A; later
activity-service scaffold work stays dark-launch. Phase5-E keeps the
orchestration repo-ready only.

## Phase 5 Draw Command Boundary

Recommended boundary: `DrawCommand` in, `DrawResult` out, with `orderId`,
idempotency, rollback, compensation, and precondition checks preserved. Option A
keeps monolith-local draw; Option B enables remote draw later. Phase 5-G and
`IAwardFulfillmentPort` remain blocked until remote award fulfillment is
approved. Remote award fulfillment blocked until Phase 5-G.

## Phase 5-E Award Fulfillment Port

Phase 5-E introduced `IAwardFulfillmentPort` with local award fulfillment only.
Remote award fulfillment remains disabled until later gates.

## Phase 5 Account Quota Port Reverification

`IActivityAccountPort` was reverified for B11. Quota decrement stays port-first
and defaults to local compatibility until account-service cutover evidence is
approved.

## Phase 5 Activity Service Scaffold

Phase 5-F and Phase 5-G scaffold activity-service boundaries only. The module
does not expose an HTTP controller for draw traffic. `RaffleApplicationService`
stays local until Phase 7 table ownership and DubboService registration are
externally approved.

## Phase 5 Activity Draw Saga Outbox

The Orchestration Saga uses the saga pattern with `orderId`, compensation,
rollback, and outbox handoff. `IDrawOutboxPort` is documented but not wired into
`RaffleApplicationService` for production traffic. Phase 7-D remains the table
ownership gate. Non-Goals: no applied DDL, no remote traffic, no dispatcher
enablement.

## Phase 7 Strategy Activity Mapping Boundary

`IStrategyActivityMappingPort` isolates strategy-to-activity mapping. AL-1
keeps `IRaffleActivityDao` and `raffle_activity` ownership under
activity-service while strategy reads are guarded.

## Phase 7 Task Outbox Ownership

AL-8 BehaviorRebateRepository -> ITaskDao, AL-9 CreditRepository -> ITaskDao,
and AL-10 AwardRepository -> ITaskDao are decision-complete but not runtime-resolved.
The per-domain outbox decision is required because a shared task table blocks
isolation.

Future tables: `rebate_task_outbox`, `credit_trade_task_outbox`,
`award_dispatch_task_outbox`; existing precedent: `credit_award_task`.

Migration Order: keep shared task fallback, add proposed domain outbox DDL,
wire ports, canary dispatchers, then remove fallback after stability.
Compatibility Strategy: legacy shared task stays until all remote flags and
evidence gates pass. Rollback Strategy: disable domain dispatcher and route
through local fallback. Validation Gates: repo validators plus staging/prod
evidence packs.

## Phase 7 DB Users And Grants

### Service Users

| Context | User | Scope | Gate |
| --- | --- | --- | --- |
| account | `big_market_account_rw` | account, quota, `credit_award_task`, future `credit_trade_task_outbox` | DBA user creation, grants, staging verification, canary approval |
| activity | `big_market_activity_rw` | `raffle_activity`, count/sku/stage/order, `user_raffle_order`, future draw outbox | EXTERNAL-GATED |
| fulfillment | `big_market_fulfillment_rw` | `award`, `user_award_record`, future `award_dispatch_task_outbox` | EXTERNAL-GATED |
| rebate | `big_market_rebate_rw` | rebate tables and future `rebate_task_outbox` | EXTERNAL-GATED |
| strategy | `big_market_strategy_ro` | strategy and rule-tree tables | EXTERNAL-GATED |
| message-job | `big_market_message_job_rw` | dispatcher-owned task/outbox access | EXTERNAL-GATED |
| market | `big_market_market_compat_rw` | compatibility only | remove after 30-day stability |
| app legacy compatibility | `big_market_app_compat_rw` | compatibility only | remove after 30-day stability |

### Grant Shape

Plans stay proposed-only; no executable GRANT statements are included here.
Service users get least privilege per bounded context and per shard database.

### Shared Task Replacement

`rebate_task_outbox`, `credit_trade_task_outbox`, and
`award_dispatch_task_outbox` replace shared-task ownership over time.

## Phase 7 Sharded Schema Isolation

Shards are `big_market_01` and `big_market_02`, with table suffixes `_000`
through `_003`. Each service keeps mapper/table ownership aligned with its
bounded context and avoids cross-service writes.

### Shared Task Table Replacement

Per-domain outbox tables replace shared `task` usage after external approval.
After 30-day stability, remove compatibility grants and legacy mapper copies.
