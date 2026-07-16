# Big Market

Marketing raffle demo for interviews / portfolio: login → buy draw chances with credit → spin → award outbox credits account; daily sign-in and a billing-aware local chatbot.

**Stack:** Rust modular monolith (`big-market-rs/`) + static web (`big-market-web/`).

## 2-minute demo

```bash
./scripts/run-stack.sh
./scripts/acceptance.sh          # cargo test + clippy + API smoke
./scripts/web-start.sh           # UI http://127.0.0.1:5173
```

Demo users: `xiaofuge` / `demo`, `admin` / `admin`.

API: `http://127.0.0.1:8080/api/v1`

| Process | Port | Role |
| --- | ---: | --- |
| `bm-gateway` | 8080 | HTTP edge + rate limit |
| `bm-app` | 8083 | Auth, raffle, SKU, chat billing, admin |
| `bm-worker` | 8085 | Optional jobs (`docker compose --profile worker`) |

## Design (what to say in an interview)

1. **Modular monolith, not a microservice zoo** — one domain crate, three processes; worker can embed in app for local outbox.
2. **Money path is idempotent** — SKU `out_business_no={user}_{sku}_{requestId}`; award credit keyed by `award_order_id`; chat by `requestId`.
3. **`award_state=completed` ≠ paid** — proof is `credit_award_task` → `dispatched` / balance move (`POST .../query_credit_award_task_by_token`).
4. **Strategy lite** — weights + `tree_lock_N`; optional chain (`BM_STRATEGY_CHAIN`) blacklist / weight buckets. Activity `100401` is deterministic smoke; `100402` (`c02/s02`) demos locks.

## Docs

| Doc | Content |
| --- | --- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Processes, crates, strategy lite chain |
| [`docs/FLOWS.md`](docs/FLOWS.md) | Business flows |
| [`docs/DATA.md`](docs/DATA.md) | Idempotency & outbox |
| [`docs/OPERATIONS.md`](docs/OPERATIONS.md) | Run, smoke flags, MySQL, secure |

## Verify

```bash
./scripts/acceptance.sh                 # baseline
./scripts/acceptance.sh --strategy      # lock demo + chain blacklist
./scripts/acceptance.sh --secure        # JWT revoke / internal token
./scripts/acceptance.sh --e2e           # Playwright (optional)
```

## Honest boundaries

- Strategy is **lite** (not a full rule-tree engine).
- Chatbot replies are **local echo**; debit/refund path is real.
- Jobs are `WorkerScheduler` / `JOB_CATALOG`, not XXL Admin.
- Not a production / HA / multi-tenant system — interview learning project.
