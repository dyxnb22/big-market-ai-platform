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
| `big-market-rebate-service` | 8088 | Rebate create/read RPC provider |
| `big-market-strategy-service` | 8089 | Strategy read RPC provider |

Shared modules such as `big-market-domain`, `big-market-infrastructure`,
`big-market-api`, `big-market-types`, and starter modules are reused as JAR
dependencies.

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
./scripts/smoke-test-phase-1.sh
./scripts/validate-microservices-stack.sh
./scripts/validate-microservices-phase-8-runtime-safety.sh
./scripts/smoke-api.sh
```

The script filenames are kept for compatibility with earlier local notes; their
current output validates the final local microservices architecture.

## Frontend

```bash
./scripts/web-start.sh
open http://127.0.0.1:5173/login.html
```

Frontend API calls use `http://127.0.0.1:8080/api/v1` by default.

## Documentation

- [docs/MICROSERVICES.md](docs/MICROSERVICES.md) - authoritative architecture entry
- [docs/learning/README.md](docs/learning/README.md) - final-state learning guide
- [docs/production-readiness-learning.md](docs/production-readiness-learning.md) - learning readiness notes
- [docs/operations-checklist.md](docs/operations-checklist.md) - local operations checklist
- [docs/data-and-outbox.md](docs/data-and-outbox.md) - data, outbox, idempotency, duplicate handling
- [docs/microservices-dao-ownership.md](docs/microservices-dao-ownership.md) - DAO/table ownership matrix

## Scope

This repository is a learning environment. Build success, local smoke tests,
guardrail scripts, and code/documentation consistency are the completion
standard. It does not include a real production canary or observation period.
