# Big Market AI Platform

Marketing raffle learning/portfolio platform. **Application stack is Rust only** (`big-market-rs/`).

## Quick start

```bash
./scripts/run-rust-stack.sh
./scripts/acceptance-rust.sh
./scripts/web-start.sh   # frontend → http://127.0.0.1:8080/api/v1
```

| Process | Port | Role |
| --- | ---: | --- |
| `bm-gateway` | 8080 | Edge proxy, rate limit, health |
| `bm-app` | 8083 | HTTP API + domain (auth, raffle, chat, admin) |
| `bm-worker` | 8085 | Optional standalone outbox/jobs (`--profile worker`) |

Architecture: [`docs/MICROSERVICES-RUST.md`](docs/MICROSERVICES-RUST.md)  
Freeze boundary: [`docs/RUST-LEARNING-FREEZE.md`](docs/RUST-LEARNING-FREEZE.md)  
Java removal ledger: [`rust-refactor/JAVA-DELETION-LEDGER.md`](rust-refactor/JAVA-DELETION-LEDGER.md)

## Secure / MySQL / Rabbit

```bash
./scripts/run-rust-secure.sh
./scripts/acceptance-rust.sh --secure

docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
BM_BACKEND=mysql BM_MYSQL_URL=mysql://root:123456@127.0.0.1:13306/big_market \
  ./scripts/run-rust-stack.sh
./scripts/acceptance-rust.sh --mysql
./scripts/acceptance-rust.sh --rabbit   # skips if Rabbit down
```

## Docker

```bash
docker compose up --build -d
# secure:
# docker compose -f docker-compose.yml -f docker-compose.secure.yml up -d
```

## Build / test

```bash
cd big-market-rs && cargo build --release
./scripts/acceptance-rust.sh
./scripts/acceptance-rust.sh --e2e   # Playwright
```

## What was removed

All Spring Boot / Maven modules (gateway, auth, market, message-job, domain, …) were deleted.  
Learning docs under `docs/learning/` remain as conceptual guides; they may still mention historical Java class names.

Idempotency / outbox semantics: [`docs/data-and-outbox.md`](docs/data-and-outbox.md).
