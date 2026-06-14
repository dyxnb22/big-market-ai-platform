# Data And Outbox

## Credit

Credit data is represented by `user_credit_account` and `user_credit_order`.
Credit writes are owned by the credit/account domain and repository adapters.

Code paths:

- `big-market-domain/src/main/java/com/dyx/market/domain/credit`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountCreditServiceRPC.java`

Idempotency keys include `out_business_no`, chatbot `requestId`, and
award-credit `award_order_id`.

## Award

Award data is represented by `award`, `user_award_record`, and award dispatch
task rows. Draw writes the award record and message task; consumers complete
distribution.

Code paths:

- `big-market-domain/src/main/java/com/dyx/market/domain/award`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java`

## Rebate

Rebate data is represented by `daily_behavior_rebate` and
`user_behavior_rebate_order`. Sign-in creates one rebate order per user/day and
publishes a rebate message.

Code paths:

- `big-market-domain/src/main/java/com/dyx/market/domain/rebate`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/BehaviorRebateRepository.java`
- `big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`

## Task Outbox

The shared `task` table supports message retry for award, rebate, credit trade,
and related events. Learning DDL references describe per-domain outbox tables:

- `credit_award_task`
- `rebate_task_outbox`
- `credit_trade_task_outbox`
- `award_dispatch_task_outbox`
- `raffle_quota_decrement_ledger`

These SQL files show table shape, shard key, unique key, state machine, and
retry indexes for local study.

## Duplicate Handling

The project prevents duplicate effects through:

- MySQL unique keys on business ids and message ids.
- Duplicate-key handling in repositories and consumers.
- Redisson locks around account and task operations.
- State fields such as `create`, `completed`, `fail`, `pending`, `dispatched`,
  and `failed`.
- Explicit refund/rollback operations for chatbot credit and raffle quota.

## Verification

For local learning, verify data/outbox behavior by explaining each write path
from controller/listener to repository, then running build and smoke scripts.
