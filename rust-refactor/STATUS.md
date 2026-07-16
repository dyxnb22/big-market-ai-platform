# Big Market Rust track — status

**Date:** 2026-07-16  
**Branch:** `cursor/rust-refactor-plan-6ece`  
**Stack:** Axum / Tokio / in-memory domain ports (MySQL/Redis/RabbitMQ adapters feature-gated)

## Milestone checklist

| Milestone | Status | Evidence |
| --- | --- | --- |
| M0 skeleton | ✅ | `big-market-rs/` workspace builds |
| M1 auth + gateway | ✅ | login/verify/logout + `bm-gateway` proxy |
| M2 raffle→outbox→credit | ✅ | unit closed_loop + `smoke-rust-api.sh` |
| M3 SKU/chat/rebate | ✅ | exchange/chat/sign-in implemented |
| M4 admin/DCC/metrics | ✅ | `/api/v1/admin/config`, `/api/v1/dcc/value`, `/metrics` |
| M5 acceptance-rust | ✅ | `./scripts/acceptance-rust.sh` |
| M6 default cutover | ✅* | Docs + scripts default to Rust for new path; Java compose retained as `legacy` |
| M7 archive Java | ✅* | Documented legacy; Java modules kept for对照, not deleted |

\* Cutover is **documentation + runner default for Rust track**. Physical Java module deletion is deferred so business对照与回滚仍可用。权威本地演示命令见下方。

## Verified commands

```bash
./scripts/acceptance-rust.sh
# cargo test --workspace  (7 tests)
# smoke: login → exchange -5 → draw 101 → credit +5 → chat deduct/refund → logout revoke
```

## Business parity notes

Preserved:
- Envelope `{code,info,data}`
- JWT HS256 + `openId`/`jti` + Redis-style revoke (memory denylist)
- SKU idempotency `{userId}_{sku}_{requestId}`
- Award credit outbox `pending`→`dispatched` via `award_order_id`
- Chat `chat_{userId}_{requestId}` / refund state machine
- Deterministic stage award 101 for activity 100401

Not yet wired to production MySQL/Redis/RabbitMQ (ports exist; default memory).  
Java stack remains in-repo under original modules for对照; use `docker compose` (Java) only when explicitly needed.
