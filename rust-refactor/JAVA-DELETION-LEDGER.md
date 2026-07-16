# Java deletion ledger

**Rule (historical):** delete only with Rust coverage for the demo path.  
**Final decision (2026-07-16):** remove **all** Java/Maven application sources. Rust modular monolith is the sole runnable app stack. Capabilities that remain “lite” vs historical Java (full rule-tree graph, XXL Admin, Nacos, OpenAI, Dubbo) are **intentionally out of scope** — documented in [`docs/RUST-LEARNING-FREEZE.md`](../docs/RUST-LEARNING-FREEZE.md) — not reasons to keep Spring sources.

## Batch 1 — optional dedicated launchers

Deleted `big-market-rebate-service`, `big-market-strategy-service` (see git history `ebeed48`).

## Batch final — complete Java removal

Deleted every remaining Maven module and root `pom.xml` / `Dockerfile.service`:

| Removed module | Rust (or doc) replacement |
| --- | --- |
| `big-market-gateway` | `bm-gateway` |
| `big-market-auth-service` | `bm-app` auth (`AuthFacade` / JWT) |
| `big-market-admin-service` | `bm-app` admin + `platform_config` |
| `big-market-market-service` | `bm-app` raffle/SKU/ERP/DCC HTTP |
| `big-market-chatbot-service` | `bm-app` chatbot (local echo) |
| `big-market-message-job-service` | `bm-worker` + `WorkerScheduler` / `JOB_CATALOG` |
| `big-market-account-service` | `CreditStore` / `QuotaStore` in-process |
| `big-market-fulfillment-service` | local credit award path in app/worker |
| `big-market-trigger` | `bm-app` HTTP + `bm-worker` jobs/consumers |
| `big-market-domain` | `bm-domain` (lite strategy/rebate/award/…) |
| `big-market-infrastructure` | `bm-infra` (sqlx / redis / rabbit / memory) |
| `big-market-api` | `bm-api` DTOs |
| `big-market-types` | `bm-types` |
| `big-market-management` | admin config / ENV (no Nacos) |
| `big-market-starter-*` | Rust middleware / figment / gateway rate limit |

**Kept (non-Java):** `big-market-rs/`, `big-market-web/`, `docs/` (incl. SQL + learning guides), Rust scripts, infra compose under `docs/dev-ops/`.

## Compose / CI

- Default `docker-compose.yml` → Rust services only.
- `./scripts/acceptance.sh` → forwards to `acceptance-rust.sh`.
- GitHub `build-verify.yml` → Cargo + `acceptance-rust.sh`.
- Former Java smoke/validate scripts → retired stubs or Rust forwards.

## Intentionally not ported 1:1 (do not re-add Java for these)

| Area | Status |
| --- | --- |
| Full `rule_tree` engine | Rust lite: weights + `tree_lock_N` + optional chain |
| XXL-Job Admin / all handlers | `JOB_CATALOG` tick only |
| Nacos | ENV / `platform_config` |
| OpenAI chatbot / award | Local echo |
| Dubbo RPC | In-process traits |

## Verification

```bash
./scripts/acceptance-rust.sh
find . -name '*.java'   # expect 0
```
