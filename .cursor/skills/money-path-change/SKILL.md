---
name: money-path-change
description: >-
  Safely changes credit, quota, local award dispatch, rebate, SKU/award stock,
  outbox, DLQ, or account remote write/reconcile paths in Big Market. Use when editing
  debit/credit, draw stock, send_award/send_rebate, pending remote write, chat
  billing, or any idempotency/outbox behavior.
---

# Money-path change

## Mandatory reads

- `docs/data-and-outbox.md`
- `docs/learning/archive/risky-changes-remediation.md`
- `.cursor/rules/money-path-safety.mdc`

## Checklist before merge-quality work

1. **Idempotency key** identified and unique-constrained (or documented why not).
2. **Side-effect order**: durable idempotent row / reservation **before** Redis DECR or remote debit when redelivery is possible.
3. **Terminal states**: SUCCESS / REJECTED / UNKNOWN; timeouts are UNKNOWN → query by bizNo.
4. **Completion vs dispatch**: do not mark award/credit completed before the effect lands (or use intermediate state).
5. **Shard routing**: pending / chat_session / confirm tasks use user shard, not default db00.
6. **Stock queue**: DB success then ACK; failure requeues.
7. **Strategy takeover**: finite awards still reserve stock (BM-005).
8. **Tests**: at least one duplicate-delivery or timeout case covering the changed path.
9. **Topology constraint**: do not reintroduce retired standalone remote-mode flags; document any remaining account remote-write behavior.

## High-risk files (examples)

- `AbstractRaffleStrategy`, `RuleStockLogicTreeNode`, `*Stock*Job`
- `AwardCreditGrantSupport`, `DispatchCreditAwardTaskJob`, `SendAwardConsumer`
- `RebateMessageApplicationService`, `ActivitySkuStockActionChain`
- `AccountRemoteCreditWriteAdapter`, `PendingRemoteWrite*`, `RemoteWriteReconcileJob`
- `ChatbotApplicationService`, `ChatCredit*`, `ChatRefundReconcileJob`

## Final-topology boundary

- Strategy and rebate execute through market-local ports/adapters.
- Award credit dispatch executes in message-job through the local outbox.
- Account remains the remote credit/quota boundary; UNKNOWN results must use query/reconcile semantics.

## Do not

- Blindly “compensate” a payment debit after the user-facing API already rolled back stock.
- Auto-replay DLQ without idempotency review.
- Change outbox ownership while dispatch jobs are running without a stop/migrate note.
