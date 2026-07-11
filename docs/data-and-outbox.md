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

Chat billing: Redis idempotency key is `chat:request:{userId}:{requestId}`.
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

In the default Docker topology, `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true` and
shared-task credit dispatch is disabled. `SendAwardConsumer` therefore writes a
`credit_award_task`; XXL jobs 5/6 must move it to `dispatched` and call account
RPC. Their seeds are enabled by both fresh init SQL and
`z-learning-freeze-demo.sql` for reused volumes.

`user_award_record.award_state=completed` means that the award action has been
durably accepted by the local award flow. For a credit award it does **not** by
itself prove final account delivery. Operational verification must correlate
`award_order_id` across the award record, `credit_award_task`, and
`user_credit_order`/balance. `smoke-raffle-award-e2e.sh` enforces that closure.

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

## Stock flush

Strategy award stock uses `reservationId` per queue event. Activity SKU stock uses
**`lockSurplus`** (Redis decr snapshot) per queue event. Durable idempotency is a
MySQL ledger (`strategy_award_stock_decrement_ledger` /
`activity_sku_stock_decrement_ledger`) in the same transaction as the surplus
`-1`. Redis `SETNX` is an optional fast-path only — a crash between SETNX and DB
must still apply the ledger on retry. Pending queue keys are tracked in Redis
sets so flush jobs can drain work after an activity goes offline.

## Chat billing intent

`ChatCreditApplicationService.deduct` inserts `chat_credit_session` with
`deduct_state=deducting` **before** remote debit (`chat_{userId}_{requestId}`).
On SUCCESS / INDEX_DUP the session CAS/marks `deducted`. Explicit REJECTED may
mark `failed`; UNKNOWN keeps `deducting` for reconcile. Refund keys remain
`chat_refund_{userId}_{requestId}`.

## Pending remote write

`PendingRemoteWriteSupport.enqueue(..., userId)` routes inserts through
`dbRouter.doRouter(userId)` so compensation tasks land on the correct shard.
Task states: `pending` → `continuation_pending` → `done`. Remote adapters
classify `SUCCESS` / `REJECTED` / `UNKNOWN`; only UNKNOWN enqueues pending.
Continuation failures must not mark `done`.

## Idempotency key catalog

| Domain | Key / shape | Where enforced |
| --- | --- | --- |
| Credit trade | `out_business_no` | `user_credit_order` unique; account createOrder |
| Chat debit | Redis `chat:request:{userId}:{requestId}`; credit `chat_{userId}_{requestId}` | Chatbot cache + `user_credit_order` |
| Chat refund | `chat_refund_{userId}_{requestId}` | Credit refund order / reconcile |
| SKU exchange | `out_business_no` = `{userId}_{sku}_{requestId}` | Activity order unique (`SkuProductShopCartRequestDTO.requestId`) |
| Award dispatch | `award_order_id` (also credit `out_business_no` when awarding points) | `user_award_record` / credit award task |
| Award MQ / task | `message_id` on outbox `task` rows | Task unique + consumer INDEX_DUP |
| Rebate order | `biz_id` = `{userId}_{rebateType}_{outBusinessNo}` (sign-in `outBusinessNo` = `yyyyMMdd`) | `user_behavior_rebate_order` unique |
| Rebate consume | same `bizId` as credit/quota `out_business_no` | `RebateMessageApplicationService` treats INDEX_DUP as benign |
| Strategy award stock | `reservationId` (raffle `orderId`) | Redis reserve + `strategy_award_stock_decrement_ledger` |
| Activity SKU stock | `(sku, lockSurplus)` | `activity_sku_stock_decrement_ledger` |
| Quota decrement | ledger key per draw/order | `raffle_quota_decrement_ledger` |
| DLQ | `business_message_id` (stable); `message_id` per DLQ event | `mq_dead_letter` |

## MQ DLQ topology

`RabbitMQDlqConfig` declares DLX `dlx` and four durable DLQ queues (routing key = original queue name):

| Original queue | DLQ queue |
| --- | --- |
| `activity_sku_stock_zero` | `activity_sku_stock_zero.dlq` |
| `credit_adjust_success` | `credit_adjust_success.dlq` |
| `send_rebate` | `send_rebate.dlq` |
| `send_award` | `send_award.dlq` |

Persistence table: `mq_dead_letter` (`docs/sql/mq-dead-letter.sql`). Auto-replay selects `state='reviewed'` only.

## Duplicate Handling

The project prevents duplicate effects through:

- MySQL unique keys on business ids and message ids.
- Duplicate-key handling in repositories and consumers.
- Redisson locks around account and task operations.
- State fields such as `create`, `completed`, `fail`, `pending`, `dispatched`,
  and `failed`.
- Explicit refund/rollback operations for chatbot credit and raffle quota.

Chat debit `CREDIT_CREATE` tasks use a continuation after remote reconciliation:
only after the account order is confirmed does the job mark the matching
`chat_credit_session` from `deducting` to `deducted`, preserving refundability
after a timeout with an unknown remote outcome.

DLQ replay is manual-by-default for money-effect messages. The job bean and XXL
seed are disabled unless explicitly enabled, and its query only selects rows in
`reviewed` state. After checking the queue, business idempotency key, and remote
terminal state, an operator may authorize one row with:

```sql
UPDATE mq_dead_letter
SET state = 'reviewed', retry_count = 0, update_time = NOW()
WHERE id = ? AND state IN ('pending', 'manual_pending');
```

## Verification

For local learning, explain each write path from controller/listener to
repository and then run `./scripts/acceptance.sh --reuse`. A health endpoint or
static validator alone is not outbox proof.
