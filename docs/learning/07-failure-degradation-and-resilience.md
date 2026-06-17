# 07 Failure, Degradation, And Rollback

## Gateway Resilience

Gateway circuit breakers return a consistent JSON response when a downstream
service is unavailable.

Code paths:

- `big-market-gateway/src/main/resources/application.yml`
- `big-market-gateway/src/main/java/com/dyx/market/gateway/fallback/FallbackController.java`
- `big-market-gateway/src/main/java/com/dyx/market/gateway/filter/TraceIdGlobalFilter.java`

```mermaid
flowchart TD
    Client["Client"] --> Gateway["Gateway route"]
    Gateway --> Healthy{"Downstream healthy?"}
    Healthy -->|Yes| Service["Downstream service"]
    Healthy -->|No| Fallback["FallbackController code=0007"]
```

## Draw Rollback

If draw orchestration fails after quota is consumed, the application service
records the failed order path and restores quota through the configured account
port.

Code paths:

- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java`

## Task Retry And MQ Failure

Repositories write task/outbox rows with the business transaction. MQ send
success marks the row complete; send failure leaves a retryable task state for
XXL-Job scanning.

Code paths:

- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/BehaviorRebateRepository.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RabbitMQDlqConfig.java`

## Credit Refund

Chatbot charging uses explicit refund on AI call failure. The refund request
uses `chat_refund_{requestId}` as the idempotency key.

Code paths:

- `big-market-chatbot-service/src/main/java/com/dyx/market/chatbot/ChatbotController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java`

## Operational Rollback

For local learning, rollback means returning configuration to the default local
development values, stopping dispatch jobs before changing outbox behavior, and
rerunning smoke checks. Detailed steps are in `docs/operations-checklist.md`
and `docs/production-readiness-learning.md`.

## Risky Change Areas

The project is kept stable for learning. Changes that alter credit, quota,
award, or rebate semantics need DDL review, smoke tests, idempotency checks,
and rollback verification before they are treated as complete.

| Area | Why risky | Learning treatment |
| --- | --- | --- |
| Remote quota decrement | Draw depends on exactly-once quota consumption | Keep ledger idempotency and rollback paths documented and testable |
| Award-credit outbox | Award completion moves from direct write to async dispatch | Keep DDL, dispatcher, and duplicate-key behavior documented |
| Per-domain task outboxes | Changes task ownership and retry surfaces | Keep outbox DDL references and shared task dispatch paths documented |
| DLQ replay | Automatic replay can duplicate credit or award effects | Keep DLQ logging; require manual idempotency review before replay |
| Real user system | Replaces config users with persistent accounts | Outside current portfolio scope |

Rollback principles:

- Prefer config rollback for service route and write-adapter choices.
- Preserve idempotency keys: `out_business_no`, `award_order_id`,
  `message_id`, `requestId`, and quota ledger keys.
- Stop dispatch jobs before changing outbox ownership.
- Verify account balances, quota surplus, award records, and task state after
  any retry or rollback exercise.

See also `docs/data-and-outbox.md` and `archive/risky-changes-remediation.md`.
