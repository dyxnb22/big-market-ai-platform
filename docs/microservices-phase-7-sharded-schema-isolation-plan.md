# Phase 7-F Sharded Schema Isolation Plan

Status: repo artifact complete; runtime isolation is EXTERNAL-GATED. This file
is a planning artifact only and contains no executable DDL.

## Shard Model

The current platform uses the existing 2-DB / 4-table routing model with
`big_market_01` and `big_market_02`, and table suffixes `_000` through `_003`.
Phase 7 keeps shard routing stable and changes ownership boundaries before any
physical split.

## Context Coverage

| Context | Current shard-owned table families | Isolation strategy |
|---------|------------------------------------|--------------------|
| account | `user_credit_account`, `user_credit_order`, `raffle_activity_account*`, `raffle_quota_decrement_ledger`, `credit_award_task` | account-service becomes the only writer after Phase 8 account cutover; compatibility users removed after stability |
| activity | `raffle_activity*`, `raffle_activity_order`, `user_raffle_order` | draw cutover waits for Phase 5-G/7-D approval; no activity-service draw traffic by default |
| fulfillment | `award`, `user_award_record` | fulfillment-service owns award persistence; credit side-effects remain mediated by account outbox |
| rebate | `daily_behavior_rebate`, `user_behavior_rebate_order` | rebate-service owns rebate writes; remote create-order/read flags remain default false until cutover |
| strategy | `strategy*`, `rule_tree*` | read-only strategy cutover first; write ownership deferred |
| message-job | legacy `task`, `credit_award_task`, future per-domain dispatch reads | job access narrows as outbox ownership moves to per-domain queues |
| market | legacy compatibility only | retains local fallback until service cutovers complete |
| app legacy compatibility | legacy compatibility only | kept buildable and runnable until final cleanup gates |

## Shared Task Table Replacement

Phase 7-B chose per-domain task outboxes. Phase 7-C supplies proposed-only DDL:

- `rebate_task_outbox_{000..003}` for rebate events.
- `credit_trade_task_outbox_{000..003}` for credit trade events.
- `award_dispatch_task_outbox_{000..003}` for award dispatch events.

AL-8, AL-9, and AL-10 direct repository DAO couplings are resolved through
`IRebateTaskOutboxPort`, `ICreditTradeTaskOutboxPort`, and
`IAwardDispatchTaskOutboxPort`. The local adapters still delegate to `ITaskDao`
by default, so physical table isolation remains Phase 8 external-gated.

## Runtime Cutover Sequence

1. DBA applies proposed DDL to staging shard DBs.
2. Engineering deploys default-false table-specific producer/dispatcher flags in a later batch.
3. Staging validates dual-read or shadow-dispatch behavior, idempotency, and retry handling.
4. Production canary enables one service at a time.
5. Rollback disables the service flag and resumes legacy shared `task` writes.
6. After 7-day stability, freeze compatibility writes.
7. After 30-day stability, remove compatibility grants and legacy mapper copies.

All steps that touch staging/production databases, users, secrets, traffic, or
oncall approval are EXTERNAL-GATED.
