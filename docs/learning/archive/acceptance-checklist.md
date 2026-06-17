# 11 Final Review And Acceptance

## Acceptance Checklist

| Area | Local learning acceptance |
| --- | --- |
| Build | `mvn clean package -DskipTests` succeeds |
| Gateway | `/api/v1/auth/**`, `/api/v1/admin/**`, `/api/v1/chatbot/**`, and `/api/v1/raffle/**` route through gateway |
| Auth | Login, verify, logout, JWT expiration, and revocation paths are explainable |
| Raffle | Draw flow reaches activity quota, strategy decision, award record, and MQ task |
| Credit | Sign-in, exchange, chatbot charge/refund, and award-credit paths are explainable |
| Rebate | Rebate order, task, MQ consumer, and idempotent read are explainable |
| Jobs | Shared task retry, stock update jobs, and credit-award outbox dispatch are explainable |
| Monitoring | Actuator, Prometheus scrape config, Grafana config, trace id, and logs are present |
| Rollback | Gateway fallback, quota rollback, credit refund, task retry, and local config rollback are documented |
| Docs | `docs/MICROSERVICES.md`, `docs/learning/*`, and operational docs match code paths |

## Validation Commands

```bash
mvn clean package -DskipTests
./scripts/validate-microservices-runtime-safety.sh
./scripts/validate-microservices-stack.sh
./scripts/smoke-api.sh
```

`scripts/validate-microservices-runtime-safety.sh` validates final architecture
guardrails without requiring Docker or network access.

## Monitoring And Investigation

Use `docs/operations-checklist.md` for startup, interface, task, message, log,
and metric checks. Key code paths are `TraceIdGlobalFilter`, service
`TraceIdFilter` classes, `RabbitMQDlqConfig`, and Prometheus configuration
files under `docs/dev-ops/prometheus` and `docs/dev-ops/grafana`.
