# Big Market AI Platform

Big Market is a Java microservices learning and portfolio project for a
marketing raffle platform. It includes gateway routing, authentication,
admin/config APIs, raffle/activity APIs, chatbot credit charging, account and
quota services, award fulfillment, rebate, strategy reads, RabbitMQ messages,
XXL-Job tasks, MySQL, Redis, Nacos, Prometheus, and Grafana.

## Services

| Service | Port | Responsibility |
| --- | ---: | --- |
| `big-market-gateway` | 8080 | Gateway routing, trace id propagation, circuit-breaker response |
| `big-market-auth-service` | 8081 | Login, JWT issuing, token verification, logout revocation |
| `big-market-admin-service` | 8082 | Admin configuration APIs |
| `big-market-market-service` | 8083 | Core raffle, activity, ERP, DCC, and strategy HTTP APIs |
| `big-market-chatbot-service` | 8084 | Chatbot API and credit charge/refund integration |
| `big-market-message-job-service` | 8085 | MQ consumers, XXL-Job handlers, retry dispatch |
| `big-market-account-service` | 8086 | Credit and quota RPC provider |
| `big-market-fulfillment-service` | 8087 | Award fulfillment RPC provider |
| `big-market-rebate-service` | 8088 | Rebate create/read RPC provider (dedicated deployment) |
| `big-market-strategy-service` | 8089 | Strategy read RPC provider (dedicated deployment) |

Shared modules such as `big-market-domain`, `big-market-infrastructure`,
`big-market-api`, `big-market-types`, and starter modules are reused as JAR
dependencies.

> **Legacy note:** the pre-split monolith launcher has been removed. Use the
> microservice stack above for local development and tests.

> **Deployment note:** `big-market-rebate-service` and `big-market-strategy-service` are not
> included in the default `docker-compose.yml` stack. By default, `big-market-market-service`
> hosts their Dubbo providers internally via embedded provider beans
> (`rebate.embedded-rpc-provider.enabled=true`, `strategy.embedded-rpc-provider.enabled=true`).
> The dedicated service containers are available for service-oriented deployment — set the
> corresponding `embedded-rpc-provider.enabled=false` and start the dedicated service to switch modes.

## Build

```bash
mvn clean package -DskipTests
```

## Start

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
```

Gateway address: `http://127.0.0.1:8080`.

## Verify

```bash
mvn -pl big-market-market-service,big-market-message-job-service,big-market-domain,big-market-infrastructure -am test \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
./scripts/validate-microservices-stack.sh
./scripts/smoke-api.sh
./scripts/smoke-test-microservices.sh
./scripts/web-start.sh   # separate terminal
npm test                 # Playwright; needs gateway + web on 8080/5173
```

`validate-microservices-runtime-safety.sh` is a coarse guardrail only — it can pass while Spring Context or XXL wiring is still broken. Prefer the Maven tests above plus stack/smoke scripts after audit remediation (see `docs/audit-remediation-plan.md`).

**Readiness (2026-07-10):** Boot fixes (BM-001–003) and money-path/demo code (BM-004–015) are in tree with unit/slice context tests. **Do not** claim demo closed loop until Docker + smoke/Playwright pass.

## Frontend

```bash
./scripts/web-start.sh
open http://127.0.0.1:5173/login.html
```

Frontend API calls use `http://127.0.0.1:8080/api/v1` by default.

## Documentation

- [AGENTS.md](AGENTS.md) - guidance for Cursor/Codex agents (rules & skills under `.cursor/`)
- [docs/MICROSERVICES.md](docs/MICROSERVICES.md) - authoritative architecture entry
- [docs/audit-remediation-plan.md](docs/audit-remediation-plan.md) - audit fix backlog and phases
- [docs/learning/README.md](docs/learning/README.md) - final-state learning guide
- [docs/production-readiness-learning.md](docs/production-readiness-learning.md) - learning readiness notes
- [docs/operations-checklist.md](docs/operations-checklist.md) - local operations checklist
- [docs/data-and-outbox.md](docs/data-and-outbox.md) - data, outbox, idempotency, duplicate handling
- [docs/microservices-dao-ownership.md](docs/microservices-dao-ownership.md) - DAO/table ownership matrix

## Scope

This repository is a learning environment. Build success, local smoke tests,
guardrail scripts, and code/documentation consistency are the completion
standard. It does not include a real production canary or observation period.
