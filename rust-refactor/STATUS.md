# Big Market Rust track — status

**Date:** 2026-07-16  
**Branch:** `cursor/rust-refactor-plan-6ece`  
**Stack:** Axum / Tokio / **file** backend default; optional **full MySQL** (sqlx) + Redis (fred) + RabbitMQ (lapin)

## Milestone checklist

| Milestone | Status | Evidence |
| --- | --- | --- |
| M0–M7 base rewrite | ✅ | `big-market-rs/` workspace + cutover docs |
| File persistence (`BM_BACKEND=file`) | ✅ | `state.json` + persist loop; app/worker share `BM_DATA_DIR` |
| Frontend API parity (`big-market-web`) | ✅ | strategy/admin/ERP/DCC/chatbot routes + smoke |
| Playwright vs Rust | ✅ | `acceptance-rust-e2e.sh` — 17 PASS ×2 (legacy :8098 skipped) |
| Weighted strategy + stock | ✅ | DB-backed when `BM_BACKEND=mysql`; file/memory fallback |
| Award-list lock + chain lite | ✅ | `isAwardUnlock` / `waitUnLockCount`; `BM_STRATEGY_CHAIN` |
| Redis JWT revoke | ✅ | `fred` adapter when `BM_REDIS_URL` set (fail-open → memory) |
| MySQL full port | ✅ | All domain stores on sqlx when `BM_BACKEND=mysql` (no file companion) |
| Activity soft-stock flush | ✅ | MySQL `activity_soft_stock` via `StockStore::flush_dirty` |
| RabbitMQ bridge (optional) | ✅ | `BM_RABBIT_URL` → `bm.send_award` / `bm.send_rebate`; `smoke-rust-rabbit.sh` |
| Embed worker mutex | ✅ | Rabbit URL disables embed unless `BM_EMBED_WORKER_FORCE=1` |
| Embedded + standalone worker | ✅ | `BM_EMBED_WORKER=1` in app; `bm-worker` for dispatch/reconcile/stock |
| Gateway rate limit + health | ✅ | `bm-gateway` proxy + `/actuator/health` |
| Secure overlay hook | ✅ | `BM_SECURE=1` + `run-rust-secure.sh` / `smoke-rust-security.sh` |
| Prometheus metrics | ✅ | `bm-app` + `bm-worker` `/metrics` |
| Bench snapshot | ✅ | `rust-refactor/bench/RESULTS.md` (~12 MiB RSS, ~9 ms cold ready) |
| Dual-stack contract check | ✅ | `acceptance-dual-stack.sh` (+ `--dual` on acceptance-rust) |
| CI MySQL smoke | ✅ | `.github/workflows/rust.yml` `mysql-smoke` job |
| Rust learning freeze doc | ✅ | `docs/RUST-LEARNING-FREEZE.md` |

## Verified commands

```bash
./scripts/acceptance-rust.sh          # test + clippy + API smoke
./scripts/acceptance-rust.sh --e2e    # + Playwright 17×2
./scripts/acceptance-rust.sh --secure # + smoke-rust-security
./scripts/acceptance-rust.sh --mysql  # + smoke-rust-mysql (needs :13306)
./scripts/acceptance-rust.sh --rabbit # + smoke-rust-rabbit (needs :5672)
./scripts/acceptance-rust.sh --dual   # + dual-stack contract (optional JAVA_API_BASE)
./scripts/acceptance-dual-stack.sh
./scripts/smoke-rust-mysql.sh
./scripts/smoke-rust-rabbit.sh
./scripts/bench-rust.sh
```

## Backend selection

| `BM_BACKEND` | Behavior |
| --- | --- |
| `file` (default) | JSON snapshot under `BM_DATA_DIR` |
| `memory` | Pure RAM (tests) |
| `mysql` | **All stores** via sqlx (sharded + `big_market` catalog); no `state.json` persist |

## Honest limits vs Java

| Area | Rust track | Java learning stack |
| --- | --- | --- |
| Strategy | DB weights + `tree_lock_N` list/draw + optional chain lite | Full DB rule-tree engine |
| MQ / jobs | Optional lapin + `JOB_CATALOG`; embed disabled when Rabbit URL set | RabbitMQ + XXL-Job handlers |
| Chatbot | Local echo tools (1 credit) | Optional OpenAI integration |
| MySQL | Full learning schema subset + `activity_soft_stock` flush | Same tables + Java ORM |
| Legacy :8098 | Skipped in Rust Playwright profile | Java compose maps 8098→gateway |
| Secure compose | `BM_SECURE=1` + `docker-compose.rust.secure.yml` | `docker-compose.secure.yml` |

Java modules remain **legacy**对照. Default demo path is Rust.

## Remaining depth phases (post M0–M7)

Tracked in **[NEXT-PHASES.md](./NEXT-PHASES.md)** / freeze: [`docs/RUST-LEARNING-FREEZE.md`](../docs/RUST-LEARNING-FREEZE.md).

| Phase | Status | Focus |
| --- | --- | --- |
| Arch A | ✅ | Modular monolith authoritative |
| Arch B | ✅ | `tree_lock_N` + unified `WorkerScheduler` |
| **C** | ✅ | Award-list lock fields + chain lite (`BM_STRATEGY_CHAIN`) |
| **D** | ✅ | Rabbit smoke, embed mutex when `BM_RABBIT_URL`, worker metrics/jobs |
| **E** | ✅ | `activity_soft_stock` flush write-back + mysql smoke reconcile note |
| **F** | ✅ | `docs/RUST-LEARNING-FREEZE.md` + bench P99 exemption |

## Definition of Done (ROADMAP D1–D7)

| # | Status | Notes |
| --- | --- | --- |
| D1 | ✅ | `run-rust-stack.sh` / README default Rust |
| D2 | ✅ | `acceptance-rust --e2e` (17×2 Playwright + smoke closed loop) |
| D3 | ✅ | `big-market-web` unchanged; API at `:8080` |
| D4 | ✅ | Idempotency keys aligned `docs/data-and-outbox.md` |
| D5 | ✅ | Bench numbers in `rust-refactor/bench/RESULTS.md` |
| D6 | ✅ | README + CUTOVER point to Rust |
| D7 | ✅ | `.github/workflows/rust.yml` (test + e2e + mysql-smoke) |
