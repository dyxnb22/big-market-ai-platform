> **Archived (2026-06-12):** Phase 1-7 historical implementation record. See `docs/MICROSERVICES.md` for current status.

# Phase 7-E DB Users And Grants Plan

Status: repo artifact complete; execution is EXTERNAL-GATED. This file is a
planning artifact only. It contains no executable `GRANT` statements and must
not be treated as DBA evidence.

## Scope

Bounded contexts covered: account, activity, fulfillment, rebate, strategy,
message-job, market, app legacy compatibility.

## Service Users

| Context | Runtime user | Intended owned tables | Compatibility reads/writes | Phase 8 gate |
|---------|--------------|-----------------------|----------------------------|--------------|
| account | `big_market_account_rw` | `user_credit_account`, `user_credit_order`, `raffle_activity_account`, `raffle_activity_account_day`, `raffle_activity_account_month`, `raffle_quota_decrement_ledger`, `credit_award_task`, future `credit_trade_task_outbox` | legacy app/market access remains until flags cut over | DBA user creation, grants, staging verification, canary approval |
| activity | `big_market_activity_rw` | `raffle_activity`, `raffle_activity_count`, `raffle_activity_sku`, `raffle_activity_stage`, `raffle_activity_order`, `user_raffle_order`, future draw outbox | draw execution remains in market/app until Phase 5-G/7-D approval | EXTERNAL-GATED |
| fulfillment | `big_market_fulfillment_rw` | `award`, `user_award_record`, future `award_dispatch_task_outbox` | credit write is via `credit_award_task`/account-service cutover; legacy app still compatible | EXTERNAL-GATED |
| rebate | `big_market_rebate_rw` | `daily_behavior_rebate`, `user_behavior_rebate_order`, future `rebate_task_outbox` | legacy app/market write fallback remains until remote create-order cutover | EXTERNAL-GATED |
| strategy | `big_market_strategy_ro` | `strategy`, `strategy_award`, `strategy_rule`, `rule_tree`, `rule_tree_node`, `rule_tree_node_line` | market/app reads remain until strategy read cutover | EXTERNAL-GATED |
| message-job | `big_market_message_job_rw` | dispatch-only access to task/outbox tables it owns operationally | shared `task` read/update remains for compatibility until all domain outboxes cut over | EXTERNAL-GATED |
| market | `big_market_market_compat_rw` | none long-term | compatibility user for legacy market-service during migration | remove after 30-day stability gates |
| app legacy compatibility | `big_market_app_compat_rw` | none long-term | monolith compatibility user for `big-market-app` | remove after 30-day stability gates |

## Grant Shape

DBA should create least-privilege users per environment and shard database.
The planned grant shape is described, not executed:

- Owned write services receive read/write privileges only on their owned table families.
- Strategy receives read-only privileges unless a future write API is explicitly approved.
- Message-job receives read/update privileges for dispatch queues it owns and temporary access to legacy `task`.
- Compatibility users retain broad legacy access only during cutover windows.
- No production or remote traffic flag is enabled by this plan.

## Shared Task Replacement

The shared `task` table is replaced by proposed per-domain outbox tables:

- `rebate_task_outbox_{000..003}` from `docs/sql/proposed-rebate-task-outbox.sql`
- `credit_trade_task_outbox_{000..003}` from `docs/sql/proposed-credit-trade-task-outbox.sql`
- `award_dispatch_task_outbox_{000..003}` from `docs/sql/proposed-award-dispatch-task-outbox.sql`

Direct repository DAO coupling for AL-8/AL-9/AL-10 is resolved in code by
domain ports. Physical runtime table isolation remains Phase 8 external-gated
until DBA-applied DDL, grant evidence, staging validation, and flag cutover.

## Evidence Required

All items below are EXTERNAL-GATED:

- DBA-created users in staging and production.
- DBA-applied grants per shard database.
- Read-only verification queries showing expected access and denied foreign access.
- Ops confirmation that service secrets point at the correct user.
- Engineering and oncall approval for canary windows.
