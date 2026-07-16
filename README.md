# Big Market AI Platform

Big Market is a marketing raffle learning/portfolio platform.

## Default local stack (Rust)

The **default demo path** is the Rust rewrite under `big-market-rs/`
(gateway + app + embedded outbox worker). It preserves the HTTP envelope,
JWT auth, SKU/chat idempotency, and raffle→award-credit closed loop.

```bash
./scripts/run-rust-stack.sh
./scripts/acceptance-rust.sh
./scripts/web-start.sh   # frontend → http://127.0.0.1:8080/api/v1
```

Details: [`big-market-rs/README.md`](big-market-rs/README.md), roadmap
[`rust-refactor/ROADMAP.md`](rust-refactor/ROADMAP.md), status
[`rust-refactor/STATUS.md`](rust-refactor/STATUS.md).

## Legacy Java microservices

The original Spring Boot stack remains in-repo for对照 and rollback
(`docker compose` + `./scripts/acceptance.sh`). See cutover notes in
[`rust-refactor/CUTOVER.md`](rust-refactor/CUTOVER.md).

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
> microservice stack above for Java local development and tests.

> **Deployment note:** `big-market-rebate-service` and `big-market-strategy-service` are not
> included in the default `docker-compose.yml` stack. By default, `big-market-market-service`
> hosts their Dubbo providers internally via embedded provider beans
> (`rebate.embedded-rpc-provider.enabled=true`, `strategy.embedded-rpc-provider.enabled=true`).
> The dedicated service containers are available for service-oriented deployment — set the
> corresponding `embedded-rpc-provider.enabled=false` and start the dedicated service to switch modes.

## Build

### Rust (default)

```bash
cd big-market-rs && cargo build --release
```

### Java (legacy)

```bash
mvn clean package -DskipTests
```

## Start

### Rust

```bash
./scripts/run-rust-stack.sh
```

### Java + infra

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
```

Gateway address: `http://127.0.0.1:8080`.

## Verify

### Rust

```bash
./scripts/acceptance-rust.sh
```

### Java (legacy)

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
| Date | 2026-07-16 |
| Track | Rust `big-market-rs` (memory backend) |
| Command | `./scripts/acceptance-rust.sh` |
| Result | **PASS** — cargo test, clippy, HTTP smoke (login → SKU −5 → draw 101 → credit +5 → chat refund → logout revoke) |

Java learning-freeze evidence (2026-07-11) remains valid for the legacy stack; see [docs/LEARNING-FREEZE.md](docs/LEARNING-FREEZE.md).

## Frontend

```bash
./scripts/web-start.sh
```

API base: `http://127.0.0.1:8080/api/v1`.
