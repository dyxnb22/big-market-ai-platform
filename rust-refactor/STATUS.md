# Big Market Rust track — status

**Date:** 2026-07-16  
**Branch:** `cursor/rust-refactor-plan-6ece`  
**Stack:** Axum / Tokio / **file** backend default; optional MySQL (sqlx) + Redis (fred)

## Milestone checklist

| Milestone | Status | Evidence |
| --- | --- | --- |
| M0–M7 base rewrite | ✅ | prior commit |
| File persistence (`BM_BACKEND=file`) | ✅ | `state.json` + persist loop; app/worker share `BM_DATA_DIR` |
| Weighted strategy + stock | ✅ | `strategy` + `StockStore`; armory seeds counters; flush loop |
| Redis JWT revoke | ✅ | `fred` adapter when `BM_REDIS_URL` set (fail-open → memory) |
| MySQL credit/outbox | ✅ | `sqlx` adapter when `BM_BACKEND=mysql` + `BM_MYSQL_URL` |
| Internal worker tick / stock query | ✅ | `/api/v1/internal/worker/tick`, `/api/v1/raffle/activity/query_stock` |

## Verified commands

```bash
./scripts/acceptance-rust.sh
# BM_BACKEND=file (default) — durable across restarts without Docker
# BM_BACKEND=mysql BM_MYSQL_URL=mysql://root:123456@127.0.0.1:13306/big_market
# BM_REDIS_URL=redis://127.0.0.1:16379
```

## Backend selection

| `BM_BACKEND` | Behavior |
| --- | --- |
| `file` (default) | JSON snapshot under `BM_DATA_DIR` |
| `memory` | Pure RAM (tests) |
| `mysql` | sqlx credit + credit_award_task; companion file for catalog/quota/chat |

Java modules remain legacy对照. Playwright full UI against Rust optional follow-up.
