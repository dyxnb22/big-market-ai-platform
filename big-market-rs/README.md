# Big Market Rust (`big-market-rs`)

Rust rewrite of the Big Market raffle platform. Default backend is **file-backed**
(`BM_DATA_DIR/state.json`, no Docker required). Optional MySQL/Redis/RabbitMQ
adapters wire through the same domain ports.

## Architecture

**Default stack:** modular monolith — see [`docs/MICROSERVICES-RUST.md`](../docs/MICROSERVICES-RUST.md) (not a 1:1 Java microservice clone).

| Binary | Port | Role |
| --- | ---: | --- |
| `bm-gateway` | 8080 | Reverse proxy to `bm-app` |
| `bm-app` | 8083 | Auth + raffle + credit + chat + admin (+ embedded worker) |
| `bm-worker` | 8085 | Standalone outbox dispatcher (use with shared durable store) |

## Quick start (memory)

```bash
cd big-market-rs
cargo build --release
./target/release/bm-app &
./target/release/bm-gateway &
# API via gateway
curl -s http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}'
```

Or: `../scripts/run-rust-stack.sh`

## Tests

```bash
cargo test --workspace
cargo clippy --workspace -- -D warnings
../scripts/smoke-rust-api.sh
```

## Demo credentials

- `xiaofuge` / `demo`
- `admin` / `admin`

Seeded: activity `100401`, SKU `9901` (5 credits → 1 draw), deterministic award `101` (+5 credits).

## Config (env)

| Env | Default |
| --- | --- |
| `BM_BACKEND` | `file` (`memory` / `mysql`) |
| `BM_DATA_DIR` | `data/bm-rs` |
| `BM_PORT` | 8083 |
| `BM_JWT_SECRET` | `change-me-in-dev-only` |
| `BM_DEV_USERS` | `xiaofuge:demo,admin:admin` |
| `BM_INITIAL_CREDIT` | `100.00` |
| `BM_EMBED_WORKER` | `1` |
| `BM_INTERNAL_TOKEN` | `dev-internal-token` |
| `BM_MYSQL_URL` | unset |
| `BM_REDIS_URL` | unset |
| `BM_RABBIT_URL` | unset (enables `bm.send_award` / `bm.send_rebate` in `bm-worker`) |
| `BM_SECURE` | `0` (set `1` to reject default JWT/internal token) |
| `BM_DEV_SLOW_DRAW_MS` | unset (E2E sets `300` so Playwright can observe draw UI) |
| `BM_GW_PORT` | 8080 |
| `BM_GW_APP_URL` | `http://127.0.0.1:8083` |
