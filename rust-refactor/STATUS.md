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
| Redis JWT revoke | ✅ | `fred` adapter when `BM_REDIS_URL` set (fail-open → memory) |
| MySQL full port | ✅ | All domain stores on sqlx when `BM_BACKEND=mysql` (no file companion) |
| RabbitMQ bridge (optional) | ✅ | `BM_RABBIT_URL` → `bm.send_award` / `bm.send_rebate` |
| Embedded + standalone worker | ✅ | `BM_EMBED_WORKER=1` in app; `bm-worker` for dispatch/reconcile/stock |
| Gateway rate limit + health | ✅ | `bm-gateway` proxy + `/actuator/health` |
| Secure overlay hook | ✅ | `BM_SECURE=1` + `run-rust-secure.sh` / `smoke-rust-security.sh` |
| Prometheus metrics | ✅ | `bm-app` `/metrics` |
| Bench snapshot | ✅ | `rust-refactor/bench/RESULTS.md` (~12 MiB RSS, ~9 ms cold ready) |
| Dual-stack contract check | ✅ | `acceptance-dual-stack.sh` (+ `--dual` on acceptance-rust) |
| CI MySQL smoke | ✅ | `.github/workflows/rust.yml` `mysql-smoke` job |

## Verified commands

```bash
./scripts/acceptance-rust.sh          # test + clippy + API smoke
./scripts/acceptance-rust.sh --e2e    # + Playwright 17×2
./scripts/acceptance-rust.sh --secure # + smoke-rust-security
./scripts/acceptance-rust.sh --mysql  # + smoke-rust-mysql (needs :13306)
./scripts/acceptance-rust.sh --dual   # + dual-stack contract (optional JAVA_API_BASE)
./scripts/acceptance-dual-stack.sh
./scripts/smoke-rust-mysql.sh
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
| MQ / jobs | Optional lapin + in-process outbox; no full XXL job set | RabbitMQ + XXL-Job handlers |
| Strategy | DB `strategy_award` weights when mysql; simplified lock rules | Full DB rule-tree engine |
| Chatbot | Local echo tools (1 credit) | Optional OpenAI integration |
| MySQL | Full learning schema subset via sqlx | Same tables + Java ORM |
| Legacy :8098 | Skipped in Rust Playwright profile | Java compose maps 8098→gateway |
| Secure compose | `BM_SECURE=1` + `docker-compose.rust.secure.yml` | `docker-compose.secure.yml` |

Java modules remain **legacy**对照. Default demo path is Rust.

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
