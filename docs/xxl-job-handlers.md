# XXL-Job Handlers

Seed source: `docs/dev-ops/mysql/sql/xxl_job.sql` (executor group `big-market-message-job`).
Older volumes may need `docs/dev-ops/mysql/sql/z-xxl-job-extra-handlers.sql` for jobs 7–13.

`trigger_status`: `0` = stopped, `1` = running.

| Handler | Seed id | Default `trigger_status` | Money-replay note |
| --- | ---: | ---: | --- |
| `updateAwardStockJob` | 1 | 1 | Flushes strategy award Redis stock queue → MySQL ledger; safe to leave on (idempotent by `reservationId`). |
| `SendMessageTaskJob_DB1` | 2 | 1 | Outbox MQ republish for shard db01; not a money debit itself. |
| `SendMessageTaskJob_DB2` | 3 | 1 | Same for shard db02. |
| `UpdateActivitySkuStockJob` | 4 | 1 | Flushes activity SKU stock queue; idempotent by `(sku, lockSurplus)` ledger. |
| `DispatchCreditAwardTaskJob_DB1` | 5 | 1 | Award-credit outbox → account credit. **Money path**; Docker enables `account.award-credit-outbox`, account-side `out_business_no=award_order_id` provides idempotency. A bare non-Docker launcher still defaults the bean off. |
| `DispatchCreditAwardTaskJob_DB2` | 6 | 1 | Same for db02. |
| `StrategyAwardStockConfirmJob_DB1` | 7 | 1 | Confirms pending stock reservations after award save; compensation, not a new debit. |
| `StrategyAwardStockConfirmJob_DB2` | 8 | 1 | Same for db02. |
| `CreditPayDeliveryReconcileJob_DB1` | 9 | 1 | Retries stuck credit-exchange deliveries / refunds; **money-adjacent** — review before forcing. |
| `CreditPayDeliveryReconcileJob_DB2` | 10 | 1 | Same for db02. |
| `RemoteWriteReconcileJob` | 11 | 1 | Retries `pending_remote_write_task` UNKNOWN outcomes; **money-adjacent**. |
| `DlqReplayJob` | 12 | 0 | Replays `mq_dead_letter` rows in `reviewed` only. **Money-replay**; keep stopped until operator review + `JOB_DLQ_REPLAY_ENABLED=true`. |
| `ChatRefundReconcileJob` | 13 | 1 | Retries chat `refund_state=pending`; **money path** but idempotent by `chat_refund_{userId}_{requestId}`. |

Alignment gate: `XxlJobHandlerAlignmentTest` / `scripts/validate-microservices-runtime-safety.sh` section 8.
