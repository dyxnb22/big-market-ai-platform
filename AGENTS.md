# AGENTS.md — Big Market AI Platform

Guidance for Cursor / Codex agents working in this repository.

## What this repo is

**Default path:** Rust modular monolith in `big-market-rs/` (gateway + app + worker).  
**Legacy path:** Java Spring microservices (gateway, auth, admin, market, chatbot, message-job, account, …) for对照 / rollback.

Frontend: `big-market-web` (static HTML/JS, not React).

## Authoritative docs (read before large changes)

| Doc | Use for |
| --- | --- |
| **`docs/MICROSERVICES-RUST.md`** | **Rust default architecture, ports, flows, limits** |
| `rust-refactor/STATUS.md` | Rust track readiness and verified commands |
| `docs/LEARNING-FREEZE.md` | Java stack readiness (conditional freeze 2026-07-11) |
| `docs/MICROSERVICES.md` | **Java legacy** architecture entry |
| `docs/audit/2026-07-11-learning-freeze-audit.md` | Current independent audit evidence and P0–P3 findings |
| `docs/audit-remediation-plan.md` | Historical BM backlog; clue only, not current status |
| `docs/data-and-outbox.md` | Outbox, idempotency keys, duplicate handling |
| `docs/microservices-dao-ownership.md` | Table/DAO ownership (logical; not hard-enforced) |
| `docs/operations-checklist.md` | Local ops checks |
| `docs/learning/archive/risky-changes-remediation.md` | Money-path change constraints |
| `docs/learning/` | Learning guides (01–19); prefer final-state docs over `docs/archive/` |

**Doc vs code:** prefer **code + config + Docker init SQL**. If docs claim “stable / completed closed loop” but code cannot boot, treat docs as stale and fix code first (or update docs).

## Current readiness (learning freeze 2026-07-11)

- Result: **conditional learning freeze**. Reused default stack passed Maven, static gates, real raffle → award outbox → account credit, Chat refund/reconcile, security smoke, and 18 Playwright tests twice.
- Not verified in that audit: fresh empty volumes, full secure overlay, dedicated rebate/strategy, remote/external awards, production HA/capacity/security.
- Do not infer current readiness from BM numbers or historical PASS records. Re-run the target working tree and preserve the verified/unverified boundary.

## Service map (Rust default)

| Process | Port | Owns |
| --- | ---: | --- |
| `bm-gateway` | 8080 | Routing, rate limit, health |
| `bm-app` | 8083 | Auth, raffle, chat, admin/DCC, embedded worker (default) |
| `bm-worker` | 8085 | Outbox dispatch, rebate/chat reconcile, stock flush (optional standalone) |

Frontend: `http://127.0.0.1:5173` via `./scripts/web-start.sh` → API `http://127.0.0.1:8080/api/v1`.

## Service map (Java legacy)

| Service | Port | Owns |
| --- | ---: | --- |
| gateway | 8080 | Routing, CB fallback |
| auth | 8081 | JWT login/verify/logout |
| admin | 8082 | Platform config / Nacos |
| market | 8083 | Raffle HTTP + embedded rebate/strategy |
| chatbot | 8084 | Chat + credit charge |
| message-job | 8085 | MQ consumers + XXL-Job |
| account | 8086 | Credit/quota RPC |
| fulfillment | 8087 | Award fulfillment RPC (optional) |
| rebate | 8088 | Dedicated rebate (optional) |
| strategy | 8089 | Dedicated strategy (optional) |

## Cursor project assets

- Rules: `.cursor/rules/*.mdc`
- Skills: `.cursor/skills/*/SKILL.md`
- Index: `.cursor/README.md`

## Default agent behavior

1. Prefer the smallest change that fixes the stated bug; the historical BM phase order is not an active backlog.
2. Money-like paths (credit, quota, award, rebate, SKU/award stock): preserve idempotency keys; see skill `money-path-change`.
3. **`bm-app` must not** register MQ consumers or credit-dispatch loops — those belong in **`bm-worker`** (see `docs/MICROSERVICES-RUST.md`).
4. Java `market-service` must **not** scan `trigger.job` / `trigger.listener`; those belong to `message-job-service`.
5. Java mapper XML is copied per launcher — change one service’s copy carefully; avoid duplicate MyBatis statement ids.
6. Verification: Rust — `acceptance-rust.sh`; Java — Context tests + `acceptance.sh --reuse`.
7. Commit only when the user asks.

## Useful commands

```bash
# Rust (default)
./scripts/run-rust-stack.sh
./scripts/acceptance-rust.sh
./scripts/acceptance-rust.sh --e2e

# Java (legacy)
mvn clean package -DskipTests
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
./scripts/acceptance.sh --reuse
./scripts/web-start.sh
```
