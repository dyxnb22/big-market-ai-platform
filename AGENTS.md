# AGENTS.md — Big Market AI Platform

Guidance for Cursor / Codex agents working in this repository.

## What this repo is

**Rust** marketing raffle learning/portfolio project (`big-market-rs/`): modular monolith
`bm-gateway` + `bm-app` + optional `bm-worker`; shared crates `bm-domain` / `bm-infra` / `bm-api` / `bm-types`;
frontend `big-market-web` (static HTML/JS).

**Java Spring sources have been removed** from the tree (see `rust-refactor/JAVA-DELETION-LEDGER.md`).

## Authoritative docs

| Doc | Use for |
| --- | --- |
| `docs/MICROSERVICES-RUST.md` | **Architecture (authoritative)** |
| `docs/RUST-LEARNING-FREEZE.md` | Verified commands, limits |
| `docs/data-and-outbox.md` | Outbox, idempotency keys |
| `docs/learning/` | Conceptual guides (may cite historical Java names) |
| `rust-refactor/STATUS.md` | Rust track status |
| `rust-refactor/JAVA-DELETION-LEDGER.md` | What was deleted and why |

**Doc vs code:** prefer **code + config + Docker init SQL**.

## Service map

| Process | Port | Owns |
| --- | ---: | --- |
| bm-gateway | 8080 | Routing, rate limit |
| bm-app | 8083 | JWT, raffle, SKU, chat, admin/DCC/ERP |
| bm-worker | 8085 | Outbox consume, credit dispatch, reconcile, stock flush |

Frontend: `http://127.0.0.1:5173` via `./scripts/web-start.sh` → API `http://127.0.0.1:8080/api/v1`.

## Default agent behavior

1. Prefer the smallest change that fixes the stated bug.
2. Money-like paths: preserve idempotency keys; see skill `money-path-change`.
3. **`bm-app` must not** register MQ consumers or credit-dispatch loops — those belong in **`bm-worker`**.
4. Verification: `./scripts/acceptance-rust.sh` (never claim closed loop from `/health` alone).
5. Commit only when the user asks.

## Useful commands

```bash
cd big-market-rs && cargo test --workspace
./scripts/run-rust-stack.sh
./scripts/acceptance-rust.sh
./scripts/acceptance-rust.sh --e2e
./scripts/acceptance-rust.sh --mysql
./scripts/web-start.sh
```
