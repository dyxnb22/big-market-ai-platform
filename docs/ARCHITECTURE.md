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
   ├── chat billing + chatbot (local echo)
   ├── rebate (calendar sign)
   └── admin / DCC / ERP
        │
        ▼  (outbox rows / local queue)
   bm-worker :8085   (optional; app can embed the same scheduler)
```

Default path: gateway + app with `BM_EMBED_WORKER=1`.  
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

## Strategy (lite) chain

Order when evaluating a draw:

```text
1. rule_blacklist   (only if BM_STRATEGY_CHAIN=1 and BM_RULE_BLACKLIST hits user)
2. rule_weight      (optional BM_RULE_WEIGHT buckets by prior draws)
3. tree_lock_N      (filter awards until prior_draws >= N)
4. weighted pick
```

| Activity | Channel/source | Purpose |
| --- | --- | --- |
| `100401` | `c01` / `s01` | Deterministic smoke (single award 101) — skips chain env |
| `100402` | `c02` / `s02` | Multi-weight + `tree_lock_1` / `tree_lock_3` interview demo |

Draw responses include `orderId` + `strategyTrace` (`priorDraws`, `poolBefore`/`poolAfter`, `rulesApplied`, `pickedRuleModel`).

Not included: full rule-tree graph engine, XXL console, Nacos, external LLM.
