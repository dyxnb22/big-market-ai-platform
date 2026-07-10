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

Chat billing (BM-010 / NR-002): Redis idempotency key is `chat:request:{userId}:{requestId}`.
`ChatCreditSession` lives on shards `big_market_01` / `big_market_02` only (see
`z-reconcile-tables.sql`). Both `ChatCreditSessionRepository` (market/job) and
`ChatCreditSessionSupport` (chatbot) must call `IDBRouterStrategy.doRouter(userId)`
before DAO access. `recordDeduction` is insert-only; duplicate keys must not reset
`refund_state`. Paid chat requires valid JWT before idempotency cache lookup.
Refunds require a deduction session; public refund HTTP is removed — chatbot uses internal token route.
Credit out_business_no: `chat_{userId}_{requestId}` / `chat_refund_{userId}_{requestId}`.
Refund compensation: `refund_state` may transition `none|pending → refunding → refunded`.

Exchange (NR-007): `SkuProductShopCartRequestDTO.requestId` is required; `out_business_no`
is derived as `{userId}_{sku}_{requestId}` (no millisecond suffix).

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

## Stock flush (BM-008)

Strategy award stock uses `reservationId` per queue event. Activity SKU stock uses **`lockSurplus`** (Redis decr snapshot) per queue event — dedupe key `sku_mysql_decrement:{sku}:{lockSurplus}` so each Redis decrement maps to exactly one MySQL `-1`.

## Pending remote write (BM-007)

`PendingRemoteWriteSupport.enqueue(..., userId)` routes inserts through
`dbRouter.doRouter(userId)` so compensation tasks land on the correct shard.

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
