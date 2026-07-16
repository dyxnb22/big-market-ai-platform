# Big Market Rust track — status

**Date:** 2026-07-16  
**Branch:** `cursor/rust-refactor-plan-6ece`  
**Stack:** Axum / Tokio / **file** backend default; optional MySQL (sqlx) + Redis (fred) + RabbitMQ (lapin)

## Milestone checklist

| Milestone | Status | Evidence |
| --- | --- | --- |
| M0–M7 base rewrite | ✅ | `big-market-rs/` workspace + cutover docs |
| File persistence (`BM_BACKEND=file`) | ✅ | `state.json` + persist loop; app/worker share `BM_DATA_DIR` |
| Frontend API parity (`big-market-web`) | ✅ | strategy/admin/ERP/DCC/chatbot routes + smoke |
| Weighted strategy + stock | ✅ | `strategy` + `StockStore`; armory seeds counters; flush loop |
| Redis JWT revoke | ✅ | `fred` adapter when `BM_REDIS_URL` set (fail-open → memory) |
| MySQL credit/outbox | ✅ | `sqlx` adapter when `BM_BACKEND=mysql` + `BM_MYSQL_URL` |
| RabbitMQ bridge (optional) | ✅ | `BM_RABBIT_URL` → `bm.send_award` / `bm.send_rebate` (dedicated queues) |
| Embedded + standalone worker | ✅ | `BM_EMBED_WORKER=1` in app; `bm-worker` for dispatch/reconcile/stock |
| Gateway rate limit + health | ✅ | `bm-gateway` proxy + `/actuator/health` |
| Secure overlay hook | ✅ | `BM_SECURE=1` rejects default JWT/internal token |
| Prometheus metrics | ✅ | `bm-app` `/metrics` |

## Verified commands

```bash
./scripts/acceptance-rust.sh
# BM_BACKEND=file (default) — durable across restarts without Docker
# BM_BACKEND=mysql BM_MYSQL_URL=mysql://root:123456@127.0.0.1:13306/big_market
# BM_REDIS_URL=redis://127.0.0.1:16379
# BM_RABBIT_URL=amqp://guest:guest@127.0.0.1:5672/%2f
# BM_SECURE=1 BM_JWT_SECRET=... BM_INTERNAL_TOKEN=...
```

## Backend selection

| `BM_BACKEND` | Behavior |
| --- | --- |
| `file` (default) | JSON snapshot under `BM_DATA_DIR` |
| `memory` | Pure RAM (tests) |
| `mysql` | sqlx credit + credit_award_task; companion file for catalog/quota/chat |

## Honest limits vs Java

| Area | Rust track | Java learning stack |
| --- | --- | --- |
| MQ / jobs | Optional lapin + in-process outbox; no full XXL job set | RabbitMQ + XXL-Job handlers |
| Strategy | Hardcoded weights / deterministic demo | DB rule-tree |
| Chatbot | Local echo tools (1 credit) | Optional OpenAI integration |
| MySQL | Credit + `credit_award_task` subset | Full table set + sharding |
| Playwright | Not wired to Rust CI yet | 18×2 PASS on freeze audit |
| Secure compose | `BM_SECURE=1` env gate | `docker-compose.secure.yml` |

Java modules remain **legacy**对照. Default demo path is Rust (`README.md`, `CUTOVER.md`).

## Definition of Done (ROADMAP D1–D7)

| # | Status | Notes |
| --- | --- | --- |
| D1 | ✅ | `run-rust-stack.sh` / README default Rust |
| D2 | ⚠️ partial | `acceptance-rust` PASS; no Playwright vs Rust yet |
| D3 | ✅ | `big-market-web` unchanged; API at `:8080` |
| D4 | ✅ | Idempotency keys aligned `docs/data-and-outbox.md` |
| D5 | ⏳ | Bench report placeholder in `rust-refactor/bench/RESULTS.md` |
| D6 | ✅ | README + CUTOVER point to Rust |
| D7 | ✅ | `.github/workflows/rust.yml` on PR |
