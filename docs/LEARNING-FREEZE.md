# Learning freeze — interview portfolio (2026-07-16)

**Status:** frozen for resume / interview demo. Not a production release.

**Git tag:** `v0.1.0-interview-freeze`

## Verified on freeze

```bash
./scripts/acceptance.sh --strategy --secure
```

Covers: `cargo test` (domain closed-loop + strategy unit), clippy `-D warnings`, API smoke (idempotent SKU/chat, draw `orderId`+`strategyTrace`, credit task query), strategy smoke (activity `100402` locks + chain blacklist), security smoke (JWT revoke, internal token, `BM_SECURE` boot).

Default backend: `BM_BACKEND=file` with embedded worker.

## Topology

| Process | Port |
| --- | ---: |
| `bm-gateway` | 8080 |
| `bm-app` | 8083 |
| `bm-worker` | 8085 (optional) |

## Honest boundaries (do not oversell)

- Strategy is **lite** (weights + `tree_lock_*` + optional env chain), not a full rule-tree engine.
- Chatbot: **real debit/refund**, **local echo** replies (no external LLM).
- Jobs: `WorkerScheduler` / `JOB_CATALOG`, not XXL Admin.
- MySQL path: award outbox may write `credit_award_task` directly (skip local queue); chat idempotency cache is in-process for MySQL.
- Sign-in reward is **+1.00** credit.
- Default UI stage is `100401` (deterministic). Lock UI lights up on `100402` (`c02`/`s02`) — use `--strategy` or switch channel/source.
- Not verified as freeze gates: empty Docker volumes cold-start, full secure overlay HA, MySQL/Rabbit as default CI, Playwright e2e (optional `--e2e`).

## Money-path reminders

See `docs/DATA.md`. Proof of award payout = `credit_award_task.state=dispatched` / balance move — **not** `award_state=completed`.

## After freeze

Prefer bugfix + docs honesty over new features. If changing money paths, follow `.cursor/skills/money-path-change/SKILL.md`.
