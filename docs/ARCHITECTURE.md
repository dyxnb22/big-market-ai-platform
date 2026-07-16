# Architecture

## Processes

```text
big-market-web (:5173)
        │
        ▼
   bm-gateway :8080
        │
        ▼
   bm-app :8083
   ├── auth (JWT)
   ├── raffle / SKU / strategy
   ├── chat billing + chatbot
   ├── rebate (calendar sign)
   └── admin / DCC / ERP
        │
        ▼  (outbox rows / local queue)
   bm-worker :8085   (optional; app can embed the same scheduler)
```

Default learning path: gateway + app with `BM_EMBED_WORKER=1`.  
If `BM_RABBIT_URL` is set, app disables embed worker unless `BM_EMBED_WORKER_FORCE=1`.

## Crates

| Crate | Role |
| --- | --- |
| `bm-types` | Errors, money helpers, shard utils |
| `bm-domain` | Domain services + store traits |
| `bm-infra` | file / memory / MySQL / Redis / Rabbit adapters |
| `bm-api` | HTTP DTOs |
| `bm-app` | Axum HTTP |
| `bm-worker` | Job tick + optional Rabbit bridge |
| `bm-gateway` | Reverse proxy + rate limit |

## Backends (`BM_BACKEND`)

| Value | Persistence |
| --- | --- |
| `file` (default) | `BM_DATA_DIR/state.json` |
| `memory` | Tests only |
| `mysql` | sqlx against learning schema (+ shards) |

## Strategy (lite)

- Weighted awards from `strategy_award` (or demo defaults for activity `100401`)
- `tree_lock_N` unlock based on prior draws
- Optional `BM_STRATEGY_CHAIN=1` + `BM_RULE_BLACKLIST` / `BM_RULE_WEIGHT`

Not included: full rule-tree graph engine, XXL console, Nacos, external LLM.
