# Rust learning freeze (2026-07-16)

Conditional freeze for the **Rust default demo track** (`big-market-rs/`).  
Java freeze evidence remains in [`LEARNING-FREEZE.md`](./LEARNING-FREEZE.md) (legacy).

## Result

**Conditional Rust learning freeze.** Default local path is gateway + app (file backend) with embedded worker. Depth phases **C–F** are implemented at lite scope (see [`rust-refactor/NEXT-PHASES.md`](../rust-refactor/NEXT-PHASES.md)).

## Verified

```bash
./scripts/acceptance-rust.sh              # cargo test + clippy + API smoke
./scripts/acceptance-rust.sh --e2e        # Playwright 17×2 (when run)
./scripts/acceptance-rust.sh --mysql      # skips if MySQL :13306 down
./scripts/acceptance-rust.sh --rabbit     # skips if Rabbit :5672 down
./scripts/bench-rust.sh                   # RSS / cold ready snapshot
```

Core closed loop (file): login → SKU exchange → draw → outbox → credit restore → chat deduct/refund → award list lock fields → logout revoke.

## Architecture (frozen defaults)

| Choice | Frozen as |
| --- | --- |
| Topology | Modular monolith: `bm-gateway` + `bm-app` + optional `bm-worker` |
| Not 1:1 Java | Do **not** split into 8–10 Rust binaries without evidence |
| Strategy | Weights + `tree_lock_N` + optional `BM_STRATEGY_CHAIN` blacklist/weight buckets |
| Jobs | `WorkerScheduler` + `JOB_CATALOG` (no XXL console) |
| MySQL | Full port via `BM_BACKEND=mysql`; activity soft-stock flush → `activity_soft_stock` |
| Rabbit | Optional; when `BM_RABBIT_URL` set, app **disables** embed worker unless `BM_EMBED_WORKER_FORCE=1` |

Authoritative doc: [`MICROSERVICES-RUST.md`](./MICROSERVICES-RUST.md).

## Not verified / out of scope

| Item | Status |
| --- | --- |
| Fresh empty Docker volumes end-to-end | Not claimed; use init SQL + `z-reconcile-tables.sql` |
| Full Java rule-tree graph engine | Out of scope (lite only) |
| XXL-Job Admin / all handlers | Out of scope |
| OpenAI chatbot | Out of scope (local echo); optional future feature flag |
| Side-by-side draw P99 vs Java on same host | Bench RSS done; P99 written exemption in `rust-refactor/bench/RESULTS.md` |
| Production HA / capacity / security certification | Out of scope |

## Honest money-path note

`user_award_record.award_state=completed` ≠ account credited. Proof requires `credit_award_task=dispatched` (or equivalent trade by `award_order_id`).

## Rollback

Java legacy stack remains in-repo:

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
./scripts/acceptance.sh --reuse
```
