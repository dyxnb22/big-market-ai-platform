# Data, idempotency, and outbox

## Keys

| Path | Key | Notes |
| --- | --- | --- |
| SKU exchange | `out_business_no = {userId}_{sku}_{requestId}` | Duplicate requestId → no double debit |
| Chat deduct | chat session + idempotent cache by `{userId, requestId}` | Same requestId returns prior balance |
| Chat refund | `out_business_no` derived from user + requestId | Safe replay |
| Award credit | `award_order_id` as credit `out_business_no` | Worker dispatch idempotent |
| Calendar sign | day key + `sign_{user}_{day}` | One grant per day |
| Quota consume | draw consume business no | Per draw |

## Outbox path (awards)

```text
draw → send_award message (local queue / Rabbit)
    → credit_award_task (pending)
    → apply_trade (forward)
    → task dispatched | failed
```

When Rabbit is active, worker publishes/consumes queues and skips local outbox consume in the same tick.

Query: `POST /api/v1/raffle/activity/query_credit_award_task_by_token` `{ "awardOrderId": "..." }`.

## Stock

- Award surplus: MySQL `strategy_award.award_count_surplus` (immediate)  
- Activity soft stock: memory dirty set → `flush_dirty` → `activity_soft_stock` (MySQL)

## Honesty check

Do not treat `user_award_record.award_state=completed` as “user was paid.” Always verify credit task / balance movement.
