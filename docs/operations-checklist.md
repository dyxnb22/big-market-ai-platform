# Operations Checklist

Use this checklist for the local learning stack.

## Startup Checks

- `docker compose -f docs/dev-ops/docker-compose-environment.yml ps`
- `docker compose ps`
- `curl -s http://127.0.0.1:8080/actuator/health`
- Check service health on ports `8081` through `8089`.

## Interface Checks

- Login: `POST /api/v1/auth/login`
- Verify: `GET /api/v1/auth/verify`
- Admin config: `GET /api/v1/admin/config/list`
- Activity query: `GET /api/v1/raffle/activity/query_stage_activity_id`
- Draw auth failure: `POST /api/v1/raffle/activity/draw_by_token` without token
- Chatbot: `POST /api/v1/chatbot/ask`

Script: `scripts/smoke-api.sh`.

## Task Checks

- XXL executor `appname` must be **`big-market-message-job`** (see `docs/dev-ops/mysql/sql/xxl_job.sql` and `message-job-service` `application.yml`). Handler seeds include `updateAwardStockJob`, stock confirm, remote-write reconcile, chat refund reconcile, DLQ replay, etc.
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

Check logs from `big-market-message-job-service` and `big-market-market-service`.

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
- DLQ persistence + `DlqReplayJob` in `RabbitMQDlqConfig` / `mq_dead_letter` table (`business_message_id` reactivation on re-DLQ).
- Draw rate limit: Admin `system.rateLimiterSwitch` syncs to DCC; `RaffleDrawApplicationService.draw` uses `@RateLimiterAccessInterceptor`.

## Log Checks

- Gateway logs contain `traceId`.
- Service logs contain propagated `traceId`.
- MQ failures produce task state changes or DLQ logs.
- Job locks log skipped executions rather than duplicate writes.

## Metric Checks

- Actuator health is enabled on each service.
- Prometheus scrape config is in `docs/dev-ops/prometheus/prometheus.yml`.
- Grafana config is under `docs/dev-ops/grafana`; the learning stack has annotated
  dev-only credentials, while secure acceptance requires explicit non-default
  `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` overrides.

## Secure profile (BM-015)

- Learning/default compose: `SPRING_PROFILES_ACTIVE=docker` (or local equivalents) — permissive RPC/gateway defaults.
- Production-like demo:
  - `docker compose -f docker-compose.yml -f docker-compose.secure.yml up --build -d`
  - Requires non-default `JWT_SECRET`, `APP_INTERNAL_RPC_TOKEN`, `ADMIN_TOKEN`, `CHAT_INTERNAL_SERVICE_TOKEN`, `XXL_JOB_TOKEN`, `APP_AUTH_DEV_USERS`; secure acceptance also requires `DEMO_USER_ID` / `DEMO_USER_PASSWORD` and `DEMO_ADMIN_USER_ID` / `DEMO_ADMIN_PASSWORD` entries matching `APP_AUTH_DEV_USERS`.
  - `secure` overrides `docker` in `DefaultCredentialGuard` (defaults refuse to start).
  - All Dubbo services ship `application-secure.yml` with `app.internal-rpc.enforce=true`.
- Negative checks: `./scripts/smoke-security.sh`
- One-click acceptance: `./scripts/acceptance.sh` (default `--reuse`) or `./scripts/acceptance.sh --fresh --confirm-destroy-volumes`
- `scripts/validate-microservices-runtime-safety.sh` is not sufficient alone; run Maven boot-related tests and stack smoke after changes.

## Acceptance entry (preferred)

One-click gate (Maven + Docker stack + DDL/XXL + smokes; optional Playwright/secure):

```bash
./scripts/acceptance.sh                  # --reuse (default)
./scripts/acceptance.sh --secure         # + smoke-security.sh
./scripts/acceptance.sh --fresh --confirm-destroy-volumes
```

Do not treat `validate-microservices-runtime-safety.sh` alone as closed-loop proof.

## Completion Standard

For this learning project, local build and smoke validation replace real
production observation windows. Record any unavailable dependency honestly and
rerun once the dependency is available. Prefer `./scripts/acceptance.sh` after
BM-001–015 and write-path closure; confirm Maven module tests green, Docker
stack up, `smoke-api.sh`, and Playwright (`npm test` with web on `:5173`).
