# Operations Checklist

Use this checklist for the local learning stack.

## Startup Checks

- `docker compose -f docs/dev-ops/docker-compose-environment.yml ps`
- `docker compose ps`
- `curl -s http://127.0.0.1:8080/actuator/health`
- Check application service health on ports `8081` through `8086`; there are no additional provider ports.

## Interface Checks

- Login: `POST /api/v1/auth/login`
- Verify: `GET /api/v1/auth/verify`
- Admin config: `GET /api/v1/admin/config/list`
- Activity query: `GET /api/v1/raffle/activity/query_stage_activity_id`
- Draw auth failure: `POST /api/v1/raffle/activity/draw_by_token` without token
- Chatbot: `POST /api/v1/chatbot/ask`

Script: `scripts/smoke-api.sh`.

## Task Checks

MQ 消费者与 XXL handlers 运行在 **`big-market-message-job-service`**；market 日志只覆盖 HTTP/RPC。排障时先看 message-job。

- XXL executor `appname` must be **`big-market-message-job`** (see `docs/dev-ops/mysql/sql/xxl_job.sql` and `message-job-service` `application.yml`). Handler catalog (seed id, default `trigger_status`, money-replay notes): `docs/xxl-job-handlers.md`.
- Do not stop at XXL Admin HTTP health: its executor group must contain a non-empty registered address. `acceptance.sh` enforces this.
- `SendMessageTaskJob` scans shared task rows.
- `UpdateActivitySkuStockJob` flushes activity SKU stock.
- `UpdateAwardStockJob` flushes award stock.
- `DispatchCreditAwardTaskJob` dispatches award-credit outbox rows.
- `StrategyAwardStockConfirmJob` confirms pending `strategy_award_stock_confirm_task` rows after award save.
- `CreditPayDeliveryReconcileJob` retries stuck `wait_pay` credit-exchange deliveries (CAS `compensating` before refund/SKU restore).
- `DlqReplayJob` is disabled by default. After idempotency/terminal-state review,
  an operator moves selected `mq_dead_letter` rows to `reviewed`, enables
  `JOB_DLQ_REPLAY_ENABLED=true`, and manually enables/triggers the stopped XXL seed.
- `RemoteWriteReconcileJob` retries `pending_remote_write_task` RPC writes.
- `ChatRefundReconcileJob` retries chat `refund_state=pending` sessions.

Check consumer/job logs primarily from `big-market-message-job-service`; use `big-market-market-service` for draw/HTTP path only.

## DDL / Init

- Outbox: `docs/sql/credit-award-task-outbox.sql` (Docker: `docs/dev-ops/mysql/sql/z-credit-award-task-outbox.sql`)
- Reconcile tables: `docs/sql/reconcile-tables.sql` (Docker: `docs/dev-ops/mysql/sql/z-reconcile-tables.sql`)
- Stock confirm outbox: `docs/sql/strategy-award-stock-confirm-task.sql`
- DLQ reference: `docs/sql/mq-dead-letter.sql`

## Message Checks

- `send_award` is consumed by `SendAwardConsumer`.
- `send_rebate` is consumed by `RebateMessageConsumer`.
- `credit_adjust_success` is consumed by `CreditAdjustSuccessConsumer`.
- Stock-zero events are consumed by `ActivitySkuStockZeroConsumer`.
- DLQ topology (`RabbitMQDlqConfig`): DLX `dlx` → `activity_sku_stock_zero.dlq`, `credit_adjust_success.dlq`, `send_rebate.dlq`, `send_award.dlq`.
- DLQ persistence + `DlqReplayJob` in `RabbitMQDlqConfig` / `mq_dead_letter` table (`business_message_id` reactivation on re-DLQ; replay only `reviewed`).
- Draw rate limit: Admin `system.rateLimiterSwitch` syncs to DCC; `RaffleDrawApplicationService.draw` uses `@RateLimiterAccessInterceptor`.

## Log Checks

- Gateway logs contain `traceId`.
- Service logs contain propagated `traceId`.
- MQ failures produce task state changes or DLQ logs.
- Job locks log skipped executions rather than duplicate writes.

## Metric Checks

- Actuator health is enabled on each service.
- Prometheus scrape config is in `docs/dev-ops/prometheus/prometheus.yml`.
- Alert rules: `docs/dev-ops/prometheus/rules/big-market-alerts.yml` (pending remote write, DLQ, chat refund, strategy stock confirm).
- Grafana config is under `docs/dev-ops/grafana`; the learning stack has annotated
  dev-only credentials, while secure acceptance requires explicit non-default
  `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` overrides.

## Alert runbook (business gauges)

Gauges are published by `BusinessMetricsPublisher` on **message-job** (`big_market_*`).

| Alert | Metric | First checks | Mitigations |
| --- | --- | --- | --- |
| `ChatRefundPending` | `big_market_chat_refund_pending` | Confirm `ChatRefundReconcileJob` enabled/firing; inspect `chat_credit_session` rows with `refund_state=pending` | Fix account RPC / token; let reconcile retry; do not manually clear without matching credit ledger |
| `StrategyStockConfirmPending` | `big_market_strategy_stock_confirm_pending` | Confirm `StrategyAwardStockConfirmJob`; inspect `strategy_award_stock_confirm_task` pending rows vs award save failures | Restore DB/Redis connectivity; replay job after root cause; avoid double-confirming stock |
| `PendingRemoteWriteBacklog` / `MqDeadLetterPending` | existing rules | Remote-write reconcile / DLQ review flow above | Same as Task Checks: review before `DlqReplayJob` |

## Secure profile

- Learning/default compose: `SPRING_PROFILES_ACTIVE=docker` (or local equivalents) — permissive RPC/gateway defaults.
- Production-like demo:
  - `docker compose -f docker-compose.yml -f docker-compose.secure.yml up --build -d`
  - Requires non-default `JWT_SECRET`, `APP_INTERNAL_RPC_TOKEN`, `ADMIN_TOKEN`, `CHAT_INTERNAL_SERVICE_TOKEN`, `XXL_JOB_TOKEN`, `APP_AUTH_DEV_USERS`; secure acceptance also requires `DEMO_USER_ID` / `DEMO_USER_PASSWORD` and `DEMO_ADMIN_USER_ID` / `DEMO_ADMIN_PASSWORD` entries matching `APP_AUTH_DEV_USERS`.
  - `secure` overrides `docker` in `DefaultCredentialGuard` (defaults refuse to start).
  - All Dubbo services ship `application-secure.yml` with `app.internal-rpc.enforce=true`.
- Negative checks: `./scripts/smoke-security.sh`
- Acceptance: `./scripts/acceptance.sh` (default `--reuse`, **does not** auto-start Docker). Use `--start-stack` only when you want the script to `compose up` (CI bootstrap).
- `scripts/validate-microservices-runtime-safety.sh` is not sufficient alone; run Maven boot-related tests and stack smoke after changes.

## Acceptance entry (preferred)

Gates (Maven + health + DDL/XXL + real raffle-award/account closure + Chat compensation + Playwright twice; optional secure). **No implicit `docker compose up`.**

| Mode | Proves | Notes |
| --- | --- | --- |
| `--reuse` (default) | Old volumes still work | Stack must already be healthy, or pass `--start-stack` |
| `--fresh --confirm-destroy-volumes` | Full init from empty volumes | Destructive; still needs `--start-stack` to rebuild |
| `--secure` | Non-default credentials + `smoke-security.sh` | Requires `DEMO_*` / `ADMIN_TOKEN` / `GRAFANA_*` env |

```bash
# Manual start (recommended locally — avoids surprise containers)
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
./scripts/apply-stack-migrations.sh
docker compose up --build -d

./scripts/acceptance.sh --reuse
./scripts/acceptance.sh --secure --start-stack   # CI / bootstrap with secure overlay
./scripts/acceptance.sh --fresh --confirm-destroy-volumes --start-stack
```

Failure dumps go to `target/acceptance-artifacts/` (`compose ps`, service logs, health snapshot, summary).

Do not treat `validate-microservices-runtime-safety.sh` alone as closed-loop proof.

## Completion Standard

For this learning project, local build and smoke validation replace real
production observation windows. Record any unavailable dependency honestly and
rerun once the dependency is available. Prefer `./scripts/acceptance.sh`;
confirm Maven module tests green, Docker stack up, real raffle-award/account
closure, Chat compensation, and Playwright. Current verified/unverified
boundaries are recorded in `docs/LEARNING-FREEZE.md`.
