# Big Market

Marketing raffle demo: users log in, buy draw chances with credit, spin for awards, sign in daily, and chat with a billing-aware assistant.

**Stack:** Rust modular monolith (`big-market-rs/`) + static web (`big-market-web/`).

## Quick start

```bash
./scripts/run-stack.sh
./scripts/acceptance.sh
./scripts/web-start.sh
```

| Process | Port | Role |
| --- | ---: | --- |
| `bm-gateway` | 8080 | HTTP edge |
| `bm-app` | 8083 | API + domain |
| `bm-worker` | 8085 | Optional async jobs (`docker compose --profile worker`) |

API base: `http://127.0.0.1:8080/api/v1`

## Docs

| Doc | Content |
| --- | --- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Processes, crates, backends |
| [`docs/FLOWS.md`](docs/FLOWS.md) | Business flows |
| [`docs/DATA.md`](docs/DATA.md) | Idempotency & outbox |
| [`docs/OPERATIONS.md`](docs/OPERATIONS.md) | Run, smoke, MySQL, secure |

## Build

```bash
cd big-market-rs && cargo build --release
./scripts/acceptance.sh --e2e
```
