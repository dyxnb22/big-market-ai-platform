# Production Readiness Learning Notes

This document translates production-readiness topics into the local learning
standard for this portfolio project. Completion is based on build success,
local smoke tests, validators, and explainable rollback/idempotency behavior.

## DDL

Core schema examples live under `docs/dev-ops/mysql/sql/`. Additional learning
DDL references for outbox and idempotency tables live under `docs/sql/`:

- `docs/sql/quota-decrement-ledger.sql`
- `docs/sql/credit-award-task-outbox.sql`
- `docs/sql/rebate-task-outbox.sql`
- `docs/sql/credit-trade-task-outbox.sql`
- `docs/sql/award-dispatch-task-outbox.sql`

Learning completion: table purpose, shard key, unique key, state field, and
retry behavior can be explained from SQL to repository/job code.

## Service Registration

Dubbo/Nacos registration is configured in each service `application.yml` and
`spring-config.xml`. Provider implementations live in:

- `big-market-account-service/src/main/java/com/dyx/market/account/provider`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider`
- `big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider`
- `big-market-strategy-service/src/main/java/com/dyx/market/strategy/provider`

Learning completion: services start in the local stack and expose health
endpoints; RPC contracts compile against `big-market-api`.

## MQ And XXL-Job

RabbitMQ exchanges, queues, DLQ behavior, and job handlers are represented by:

- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RabbitMQDlqConfig.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/XxlJobConfig.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job`

Learning completion: send, consume, retry, fail, and DLQ paths are identifiable
for award, rebate, credit-adjust, and stock events.

## Monitoring And Alerts

Local monitoring is represented by actuator endpoints, Micrometer Prometheus
integration, Prometheus scrape config, Grafana provisioning, structured logs,
and trace id propagation.

Code/config paths:

- `docs/dev-ops/prometheus/prometheus.yml`
- `docs/dev-ops/grafana`
- `big-market-gateway/src/main/java/com/dyx/market/gateway/filter/TraceIdGlobalFilter.java`
- service `TraceIdFilter.java` classes

Learning completion: health, metrics, logs, and trace ids can be checked during
local smoke tests.

## Acceptance

Run:

```bash
mvn clean package -DskipTests
./scripts/validate-microservices-runtime-safety.sh
./scripts/validate-microservices-stack.sh
```

If Docker or middleware is unavailable, record the missing dependency and run
the static/build validators that do not require that dependency.

## Rollback

Local rollback means restoring configuration defaults, stopping dispatch jobs
before changing outbox ownership, preserving idempotency keys, and rerunning
smoke checks. The rollback surface is documented in
`docs/operations-checklist.md` and `docs/data-and-outbox.md`.

## Old Path Cleanup

Old path cleanup is treated as a learning inventory, not as a timed production
removal process. Keep local adapters and shared mapper copies when they are
referenced by active modules. Remove only after build, smoke tests, and code
references prove they are unused.
