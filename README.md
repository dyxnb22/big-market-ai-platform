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
| `big-market-fulfillment-service` | 8087 | Award fulfillment RPC (optional remote path; default credit awards use message-job outbox) |
| `big-market-rebate-service` | 8088 | Rebate RPC (optional dedicated; default embedded in market) |
| `big-market-strategy-service` | 8089 | Strategy read RPC (optional dedicated; default embedded in market) |

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
./scripts/validate-mapper-ddl-gates.sh
./scripts/validate-microservices-runtime-safety.sh
./scripts/validate-prometheus-config.sh
mvn -B verify -DfailIfNoTests=false
# Start stack yourself first (acceptance does NOT auto-start Docker):
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
npm install
npx playwright install chromium
./scripts/acceptance.sh --reuse
# Clean volumes: --fresh --confirm-destroy-volumes --start-stack
# CI bootstrap: add --start-stack
```

Focused unit/context tests:

```bash
mvn -pl big-market-market-service,big-market-message-job-service,big-market-domain,big-market-infrastructure -am test \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
./scripts/validate-microservices-stack.sh   # no auto-start; add --start-stack to compose up
./scripts/smoke-api.sh
./scripts/smoke-test-microservices.sh
./scripts/web-start.sh   # separate terminal
npx playwright test --workers=1
```

`validate-microservices-runtime-safety.sh` is a guardrail, not closed-loop proof. Prefer `./scripts/acceptance.sh` plus Maven tests. Docker Desktop should have at least 12 GB available for a reliable full-stack rebuild; an 8 GB allocation can OOM the XXL-Job container.

### Acceptance evidence

| Field | Value |
| --- | --- |
| Date | 2026-07-11 |
| Git | working tree on top of `8d51601` (learning-freeze changes uncommitted) |
| Command | `./scripts/acceptance.sh --reuse --skip-build` |
| Result | **PASS** — HTTP contracts, 21/21 microservice smoke, API smoke, XXL executor registration, real raffle → award outbox → account credit, Chat refund/reconcile, and 18 Playwright tests twice; 80 seconds. |

This proves the reused local volumes only. Fresh-volume and full secure-overlay acceptance were not run in this audit; see [docs/LEARNING-FREEZE.md](docs/LEARNING-FREEZE.md). Failure artifacts are written to `target/acceptance-artifacts/`.

## Frontend

```bash
./scripts/web-start.sh
open http://127.0.0.1:5173/login.html
```

Frontend API calls use `http://127.0.0.1:8080/api/v1` by default.

## Documentation

- [AGENTS.md](AGENTS.md) - guidance for Cursor/Codex agents (rules & skills under `.cursor/`)
- [docs/LEARNING-FREEZE.md](docs/LEARNING-FREEZE.md) - current learning baseline, evidence, and limits
- [docs/MICROSERVICES.md](docs/MICROSERVICES.md) - authoritative architecture entry
- [docs/audit/2026-07-11-learning-freeze-audit.md](docs/audit/2026-07-11-learning-freeze-audit.md) - independent freeze audit
- [docs/audit-remediation-plan.md](docs/audit-remediation-plan.md) - historical remediation backlog
- [docs/learning/README.md](docs/learning/README.md) - final-state learning guide
- [docs/production-readiness-learning.md](docs/production-readiness-learning.md) - learning readiness notes
- [docs/operations-checklist.md](docs/operations-checklist.md) - local operations checklist
- [docs/data-and-outbox.md](docs/data-and-outbox.md) - data, outbox, idempotency, duplicate handling
- [docs/microservices-dao-ownership.md](docs/microservices-dao-ownership.md) - DAO/table ownership matrix

## Scope

This repository is a learning environment. Build success, local smoke tests,
guardrail scripts, and code/documentation consistency are the completion
standard. It does not include a real production canary or observation period.
