# Big Market

Rust raffle platform: `bm-gateway` + `bm-app` + optional `bm-worker`. Frontend is static HTML/JS in `big-market-web/`.

## Docs to read first

- `docs/LEARNING-FREEZE.md` — freeze status & honesty bounds
- `docs/ARCHITECTURE.md` — topology + strategy lite
- `docs/FLOWS.md` — raffle / credit / chat flows
- `docs/DATA.md` — idempotency keys & outbox
- `docs/OPERATIONS.md` — verification commands

## Rules

1. Smallest change that fixes the bug.
2. Money paths (credit, quota, stock, outbox): keep idempotency keys; see skill `money-path-change` and `docs/DATA.md`.
3. `bm-app` must **not** run MQ consumers or credit-dispatch loops — that is `bm-worker` (or embed worker only for local outbox).
4. Verify with `./scripts/acceptance.sh`, not `/health` alone.
5. Commit only when asked.

## Commands

```bash
./scripts/run-stack.sh
./scripts/acceptance.sh
./scripts/acceptance.sh --strategy
./scripts/acceptance.sh --secure
./scripts/web-start.sh
```
