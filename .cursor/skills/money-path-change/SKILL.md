---
name: money-path-change
description: >-
  Safely changes credit, quota, award, rebate, SKU/award stock, outbox, or chat
  billing in Big Market Rust. Use when editing debit/credit, draw stock,
  send_award/send_rebate, chat billing, or idempotency/outbox behavior.
---

# Money-path change

## Read first

- `docs/DATA.md`
- `.cursor/rules/money-path-safety.mdc`

## Checklist

1. Idempotency key identified (and unique where persisted).
2. Side-effect order: durable record / outbox before non-idempotent remote or DECR when redelivery possible.
3. Terminal vs in-flight states clear; timeouts are UNKNOWN → query by business key.
4. Do not treat award `completed` as “paid.”
5. MySQL shard key = user id for user-owned tables.
6. Add/keep a duplicate-delivery test for the changed path.
7. Prefer feature flags when changing embed vs Rabbit worker ownership.

## Hot paths

- `bm-domain` `raffle.rs` / `worker.rs` / `strategy.rs`
- `bm-infra` `mysql_*.rs`, `memory.rs`
- `bm-app` HTTP handlers that debit/credit

## Do not

- Compensate blindly after the API already rolled back.
- Register MQ consumers inside `bm-app` while `bm-worker` also consumes the same queue.
