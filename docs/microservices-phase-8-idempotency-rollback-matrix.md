# Phase 8 Idempotency & Rollback Matrix

Last revised: 2026-06-12.

Status: repo-only design artifact. Every row below remains EXTERNAL-GATED.
No remote, outbox, or cutover flag defaults to `true` in this repository.
No DDL has been applied from this repository.

## Purpose

This matrix maps every business operation in the Phase 8 cutover surface to its
idempotency key, retry behavior, rollback/compensation behavior, and current
evidence status. It complements:

- `docs/microservices-phase-8-cutover-conflict-matrix.md` (flag pair mapping)
- `docs/sql/proposed-*.sql` (proposed DDL with UNIQUE KEY definitions)
- `scripts/validate-microservices-phase-8-runtime-safety.sh` (credential/class guards)

This document is the authoritative reference for
`scripts/validate-microservices-phase-8-idempotency-rollback-matrix.sh`.

---

## 1. Quota Decrement (Draw Path)

| Field | Value |
|-------|-------|
| **Business operation** | `decrementQuota(userId, activityId, outBusinessNo)` — atomically decrements total/month/day quota with idempotency guard |
| **Owning service** | `big-market-account-service` |
| **Legacy path** | `LocalActivityAccountPort` → `ActivityRepository.decrementQuotaWithLedger()` — in-process call, uses `IRaffleQuotaDecrementLedgerDao` |
| **Future path** | `AccountRemoteActivityAccountPort` → `AccountQuotaServiceRPC.decrementQuota()` — Dubbo RPC to account-service |
| **Idempotency key** | `outBusinessNo` (= raffle order's `orderId`) |
| **DB unique key** | `raffle_quota_decrement_ledger.uq_user_activity_biz (user_id, activity_id, out_business_no)` |
| **Retry behavior** | INSERT ledger row first; `DuplicateKeyException` → return `true` (already applied). Update quota counts atomically in same transaction. |
| **Rollback behavior** | `rollbackQuota(userId, activityId, outBusinessNo)` — if `status == rolled_back` → idempotent return `true`. If `status == applied` → UPDATE status to `rolled_back`, increment surplus counts. No ledger row → no-op. |
| **Saga compensation** | `RaffleApplicationService.doDraw()` calls `rollbackQuota` when draw execution fails after decrement; `RaffleActivityPartakeService.savePartakeOrderOnly()` calls `rollbackQuota` on order-save failure |
| **Local port impl** | `LocalActivityAccountPort.java` — delegates to `activityRepository.rollbackQuotaWithLedger()`; duplicate ledger inserts are caught inside `decrementQuotaWithLedger()` |
| **Remote port impl** | `AccountRemoteActivityAccountPort.java` — calls `accountQuotaService.rollbackQuota()` via Dubbo; RPC failures are logged and the remote path returns failure/no-op rather than silently falling back locally |
| **Current repo evidence** | `IActivityAccountPort` declares `decrementQuota` + `rollbackQuota`; `LocalActivityAccountPort` implements both via ledger; `AccountRemoteActivityAccountPort` implements both via Dubbo; `RaffleActivityPartakeService` sagas `rollbackQuota` on order failure |
| **Remaining EXTERNAL-GATED** | DBA: apply `proposed-quota-decrement-ledger.sql`; Ops: verify account-service Dubbo registration; Engineering: staging idempotency validation; Oncall: quota integrity monitoring |

## 2. Credit Award Outbox Dispatch

| Field | Value |
|-------|-------|
| **Business operation** | `dispatchTask(CreditAwardTaskEntity)` — dispatches pending credit-award tasks to account-service |
| **Owning service** | `big-market-message-job-service` (dispatcher), `big-market-account-service` (credit write) |
| **Legacy path** | `SendMessageTaskJob` → shared `task` table → MQ → `CreditAdjustSuccessMessageEvent` |
| **Future path** | `DispatchCreditAwardTaskJob` → `credit_award_task` outbox → `IAccountCreditWriteAdapter.createOrder()` |
| **Idempotency key (outbox)** | `award_order_id` (= `UserAwardRecordEntity.orderId`) |
| **DB unique key (outbox)** | `credit_award_task.uq_award_order_id (user_id, award_order_id)` |
| **Idempotency key (credit write)** | `outBusinessNo` (= `task.getAwardOrderId()` passed through `TradeEntity.outBusinessNo`) |
| **Retry behavior (outbox)** | Pending tasks polled with Redis lock per DB shard; on dispatch failure, `retry_count` incremented; max retries → marked `failed` |
| **Retry behavior (credit write)** | `AccountRemoteCreditWriteAdapter` calls Dubbo → on failure falls back to `ICreditAdjustService` locally; `CreditRepository` catches `DuplicateKeyException` on `user_credit_order` INSERT → idempotent return |
| **Rollback behavior** | No explicit rollback; the outbox pattern ensures at-least-once delivery. The credit write is idempotent via `outBusinessNo` at the account-service layer |
| **Dual-dispatch defense** | Redisson distributed lock per DB shard prevents concurrent scans; `uq_award_order_id` prevents duplicate outbox rows |
| **Current repo evidence** | `DispatchCreditAwardTaskJob.java` uses `task.getAwardOrderId()` as `outBusinessNo`; `AwardRepository.java` catches `DuplicateKeyException` on `credit_award_task` INSERT (2 sites); `CreditRepository.java` catches `DuplicateKeyException` on `user_credit_order` INSERT |
| **Remaining EXTERNAL-GATED** | DBA: apply `proposed-credit-award-task-outbox.sql`; Ops: register `DispatchCreditAwardTaskJob_DB1/DB2` in XXL-Job; Engineering: staging dispatch validation; Oncall: credit drift monitoring |

## 3. Award Fulfillment / Award Record

| Field | Value |
|-------|-------|
| **Business operation** | `saveUserAwardRecord(UserAwardRecordEntity)` — persists an award record for a user during raffle execution |
| **Owning service** | `big-market-fulfillment-service` |
| **Legacy path** | `LocalAwardFulfillmentPort` → `IAwardService.saveUserAwardRecord()` |
| **Future path** | Remote award fulfillment port (gated behind activity draw cutover) |
| **Idempotency key** | `orderId` (= raffle order ID, used as `award_order_id` in outbox) |
| **DB unique key** | `user_award_record.uq_order_id (order_id)` in the dev-ops baseline schema; still EXTERNAL-GATED for staging/prod DBA confirmation |
| **Retry behavior** | `AwardRepository.saveGiveOutPrizesAggregate()` catches `DuplicateKeyException` on award record INSERT and outbox INSERT; treats both as idempotent success |
| **Rollback behavior** | No explicit rollback for award records; the draw saga uses `rollbackQuota` for quota restoration; award records are append-only |
| **Current repo evidence** | `AwardRepository.java` has 2 `DuplicateKeyException` catch sites (lines ~108, ~176, ~199); `LocalAwardFulfillmentPort.java` delegates to `awardService.saveUserAwardRecord()` |
| **Remaining EXTERNAL-GATED** | Product/DBA/Ops/Engineering/Oncall activity draw cutover evidence; fulfillment-service Dubbo provider verification |

## 4. Rebate Create Order

| Field | Value |
|-------|-------|
| **Business operation** | `createOrder(BehaviorEntity)` — creates a behavior rebate order (calendar sign, payment, etc.) |
| **Owning service** | `big-market-rebate-service` |
| **Legacy path** | `RebateServiceRPC` (Dubbo) + `LocalRebateOrderAdapter` → `IBehaviorRebateService.createOrder()` |
| **Future path** | `RebateRemoteCreateOrderAdapter` → `IRebateService.rebate()` via Dubbo |
| **Idempotency key** | Business input `outBusinessNo`; persisted uniqueness is `bizId = userId + "_" + rebateType + "_" + outBusinessNo` |
| **DB unique key** | `user_behavior_rebate_order.uq_biz_id (biz_id)` in the dev-ops baseline schema; still EXTERNAL-GATED for staging/prod DBA confirmation |
| **Retry behavior** | `BehaviorRebateRepository.saveUserRebateRecord()` catches `DuplicateKeyException` on INSERT and raises `INDEX_DUP`; callers that need retry-as-success must handle that explicitly |
| **Rollback behavior** | No explicit rollback; duplicate creation is blocked by `uq_biz_id`, and read paths can query by `outBusinessNo` |
| **Dual-provider defense** | `FlagMutualExclusionValidator` prevents `REBATE_LEGACY_RPC_PROVIDER_ENABLED=true` AND `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true` simultaneously |
| **Current repo evidence** | `BehaviorRebateRepository.java` catches `DuplicateKeyException` at line 87; `RebateRemoteCreateOrderAdapter.java` passes `outBusinessNo` via Dubbo; `RaffleActivityController.java` uses deterministic `outBusinessNo` (userId + sku + date) |
| **Remaining EXTERNAL-GATED** | DBA/Ops/Engineering/Oncall/Product rebate write cutover evidence; rebate outbox DDL; legacy provider disable confirmation |

## 5. Rebate Read (Calendar Sign Check)

| Field | Value |
|-------|-------|
| **Business operation** | `isCalendarSignRebate(userId, outBusinessNo)` — checks if a user already signed on a given date |
| **Owning service** | `big-market-rebate-service` |
| **Legacy path** | `LocalRebateReadAdapter` → `BehaviorRebateService.queryOrderByOutBusinessNo()` |
| **Future path** | `RebateRemoteReadAdapter` → `IRebateService.isCalendarSignRebate()` via Dubbo |
| **Idempotency key** | `outBusinessNo` (= date string "yyyyMMdd") |
| **DB unique key** | Read-only operation — no unique key requirement |
| **Retry behavior** | `RebateRemoteReadAdapter` falls back to local `BehaviorRebateService.queryOrderByOutBusinessNo()` on RPC failure |
| **Rollback behavior** | N/A — read-only operation |
| **Current repo evidence** | `LocalRebateReadAdapter.java` delegates to `behaviorRebateService.queryOrderByOutBusinessNo()`; `RebateRemoteReadAdapter.java` passes `outBusinessNo` via Dubbo with local fallback |
| **Remaining EXTERNAL-GATED** | rebate read parity and latency evidence |

## 6. Credit Trade (Adjust / Payment)

| Field | Value |
|-------|-------|
| **Business operation** | `createOrder(TradeEntity)` — adjusts user credit balance (forward/reverse) |
| **Owning service** | `big-market-account-service` |
| **Legacy path** | `IAccountCreditWriteAdapter` → local `ICreditAdjustService` |
| **Future path** | `AccountRemoteCreditWriteAdapter` → `AccountCreditServiceRPC` via Dubbo |
| **Idempotency key** | `outBusinessNo` (= `tradeEntity.getOutBusinessNo()`, e.g., `awardOrderId` or `chat_xxx` for chatbot) |
| **DB unique key** | `user_credit_order.uq_out_business_no (out_business_no)` in the dev-ops baseline schema; still EXTERNAL-GATED for staging/prod DBA confirmation |
| **Retry behavior** | `CreditRepository.saveUserCreditTradeOrder()` catches `DuplicateKeyException` on INSERT and raises `INDEX_DUP`; higher-level callers such as chatbot refund convert that duplicate into idempotent success where appropriate. `AccountRemoteCreditWriteAdapter` falls back to local on RPC failure. |
| **Rollback behavior** | Chatbot refund path: `RaffleActivityController.chatRefundCredit()` creates a REVERSE trade with `outBusinessNo = "chat_refund_" + originalRequestId` — idempotent, refunds only once |
| **Current repo evidence** | `CreditRepository.java` catches `DuplicateKeyException` (line 105); `CreditAdjustSuccessMessageEvent.java` carries `outBusinessNo`; chatbot refund uses deterministic idempotency key |
| **Remaining EXTERNAL-GATED** | account credit write staging/prod evidence; no credit drift validation |

## 7. SKU Exchange (Activity Order)

| Field | Value |
|-------|-------|
| **Business operation** | `createOrder(SkuRechargeEntity)` + `updateOrder(DeliveryOrderEntity)` — exchanges SKU for credit |
| **Owning service** | `big-market-account-service` (quota), `big-market-market-service` (orchestration) |
| **Legacy path** | `RaffleActivityController` → `IRaffleActivityAccountQuotaService` → local `ActivityRepository` |
| **Future path** | `AccountRemoteQuotaWriteAdapter` → `AccountQuotaServiceRPC` via Dubbo |
| **Idempotency key** | `outBusinessNo` (= `userId + "_" + sku + "_" + date`, deterministic per user/sku/day) |
| **DB unique key** | `raffle_activity_order.uq_out_business_no (out_business_no)` in the dev-ops baseline schema; still EXTERNAL-GATED for staging/prod DBA confirmation |
| **Retry behavior** | `ActivityRepository` catches `DuplicateKeyException` at multiple sites (6+); `RaffleActivityController` detects existing order → skips payment, proceeds to delivery compensation |
| **Rollback behavior** | On payment failure: `RaffleActivityController` restores SKU stock via `IRaffleStock.clearStock()`; on delivery failure: MQ re-delivers `credit_adjust_success` event |
| **Current repo evidence** | `RaffleActivityController.java` lines 617-665 implement the full idempotent exchange flow with deterministic `outBusinessNo`, stock restoration on payment failure, and delivery compensation |
| **Remaining EXTERNAL-GATED** | account quota write staging/prod evidence; SKU exchange idempotency validation |

## 8. Shared Task Fallback (SendMessageTaskJob)

| Field | Value |
|-------|-------|
| **Business operation** | Scans shared `task` table for pending MQ messages and dispatches them |
| **Owning service** | Legacy: `big-market-trigger` (shared fallback) |
| **Legacy path** | `SendMessageTaskJob` → scans `task` table → sends MQ → marks completed |
| **Future path (credit-award)** | `DispatchCreditAwardTaskJob` → scans `credit_award_task` outbox → dispatch via adapter |
| **Future path (rebate)** | Per-domain `rebate_task_outbox` dispatcher (future) |
| **Future path (credit trade)** | Per-domain `credit_trade_task_outbox` dispatcher (future) |
| **Future path (award dispatch)** | Per-domain `award_dispatch_task_outbox` dispatcher (future) |
| **Idempotency key (shared task)** | `message_id` (= MQ message ID, unique per task row) |
| **DB unique key (shared task)** | `task.uq_message_id (message_id)` in the dev-ops baseline schema |
| **Per-domain outbox keys** | `credit_award_task.uq_award_order_id`; `rebate_task_outbox.uq_user_message_id`; `credit_trade_task_outbox.uq_user_message_id`; `award_dispatch_task_outbox.uq_user_message_id` |
| **Dual-dispatch risk** | When per-domain outbox is enabled but shared task fallback is not disabled for that domain, both `SendMessageTaskJob` and the per-domain dispatcher process the same work item through different tables |
| **Dual-dispatch defense** | `JobMutualExclusionValidator` refuses startup when outbox enabled + shared-fallback.credit-award-disabled is false; proposed `job.shared-task-fallback.credit-award-disabled` flag must be set true when outbox is enabled |
| **Retry behavior** | On send failure: `SendMessageTaskJob.updateTaskSendMessageFail()` marks task as `fail`; `DispatchCreditAwardTaskJob.updateRetryFailed()` increments `retry_count` |
| **Current repo evidence** | `SendMessageTaskJob.java` uses `(userId, messageId)` for idempotent completion marking; `DispatchCreditAwardTaskJob.java` uses `awardOrderId` as `outBusinessNo`; `JobMutualExclusionValidator.java` prevents dual-dispatch at startup |
| **Remaining EXTERNAL-GATED** | All per-domain outbox DDLs applied; all per-domain outbox dispatchers implemented and validated; per-domain shared-fallback disabled flags enabled; 7-day drain evidence; 30-day removal evidence |

---

## Unique Key Summary

| Table | Unique key name | Columns | Proposed DDL |
|-------|----------------|---------|--------------|
| `credit_award_task` | `uq_award_order_id` | `(user_id, award_order_id)` | `proposed-credit-award-task-outbox.sql` |
| `raffle_quota_decrement_ledger` | `uq_user_activity_biz` | `(user_id, activity_id, out_business_no)` | `proposed-quota-decrement-ledger.sql` |
| `rebate_task_outbox` | `uq_user_message_id` | `(user_id, message_id)` | `proposed-rebate-task-outbox.sql` |
| `credit_trade_task_outbox` | `uq_user_message_id` | `(user_id, message_id)` | `proposed-credit-trade-task-outbox.sql` |
| `award_dispatch_task_outbox` | `uq_user_message_id` | `(user_id, message_id)` | `proposed-award-dispatch-task-outbox.sql` |

---

## Rollback Coverage Summary

| Flow | Has rollback? | Mechanism | Guard |
|------|-------------|-----------|-------|
| Quota decrement (draw) | Yes | `rollbackQuota` → ledger status update + count restoration | `uq_user_activity_biz` prevents double-rollback |
| Credit award outbox | No (at-least-once) | `outBusinessNo` idempotency at credit write layer | `uq_award_order_id` prevents duplicate outbox rows |
| Award fulfillment | No (append-only) | `DuplicateKeyException` blocks duplicate award record/outbox inserts | Baseline `user_award_record.uq_order_id`; DBA evidence required |
| Rebate create order | Duplicate-blocked | `DuplicateKeyException` on INSERT raises `INDEX_DUP`; read path can query existing orders | Baseline `user_behavior_rebate_order.uq_biz_id`; DBA evidence required |
| Credit trade | Caller-mediated idempotency | `DuplicateKeyException` on INSERT raises `INDEX_DUP`; chatbot refund treats duplicate refund as success | Baseline `user_credit_order.uq_out_business_no`; DBA evidence required |
| SKU exchange | Yes (stock + payment) | Stock restoration on payment failure; MQ re-delivery on delivery failure | Deterministic `outBusinessNo` |
| Shared task fallback | No (at-least-once) | MQ re-delivery via task retry | Baseline `task.uq_message_id`; per-domain outbox DDL uses `(user_id, message_id)` |

---

## Cross-References

- Cutover conflict matrix: `docs/microservices-phase-8-cutover-conflict-matrix.md`
- Legacy cleanup inventory: `docs/microservices-legacy-cleanup-inventory.md`
- Proposed DDL: `docs/sql/proposed-*.sql` (5 files)
- Phase 8 runtime safety: `scripts/validate-microservices-phase-8-runtime-safety.sh`
- This matrix's validator: `scripts/validate-microservices-phase-8-idempotency-rollback-matrix.sh`
