# 12 Risky Changes And Final Remediation

## Final-State Rule

The project is kept stable for learning. Changes that alter money-like credit,
quota, award, or rebate semantics must be verified by local DDL, smoke tests,
idempotency checks, and rollback checks before they are treated as complete.

## Risk Areas

| Area | Why risky | Current learning treatment |
| --- | --- | --- |
| Remote quota decrement | Draw depends on exactly-once quota consumption | Keep ledger idempotency and rollback paths documented and testable |
| Award-credit outbox | Changes award completion from direct write to async dispatch | Keep DDL, dispatcher, and duplicate-key behavior documented |
| Per-domain task outboxes | Changes task ownership and retry surfaces | Keep outbox DDL references and shared task compatibility paths documented |
| DLQ replay | Automatic replay can duplicate credit or award effects | Keep DLQ logging and require manual idempotency review before replay |
| Real user system | Replaces config users with persistent accounts | Outside current portfolio scope |

## Rollback Principles

- Prefer config rollback for service route and write-adapter choices.
- Preserve idempotency keys: `out_business_no`, `award_order_id`,
  `message_id`, `requestId`, and quota ledger keys.
- Stop dispatch jobs before changing outbox ownership.
- Verify account balances, quota surplus, award records, and task state after
  any retry or rollback exercise.

Code paths:

- `big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`
- `docs/data-and-outbox.md`
