# AGENTS.md — Big Market AI Platform

Guidance for Cursor / Codex agents working in this repository.

## What this repo is

Java microservices **marketing raffle** learning/portfolio project: gateway, auth, admin, market, chatbot, message-job, account; shared `domain` / `infrastructure` / `api` / `types` / starters; frontend `big-market-web` (static HTML/JS, not React). Rebate and strategy stay as local bounded capabilities, and award credit dispatch stays in message-job; the application topology is fixed.

## Authoritative docs (read before large changes)

| Doc | Use for |
| --- | --- |
| `docs/LEARNING-FREEZE.md` | **Current readiness, verified commands, limits, freeze constraints** |
| `docs/MICROSERVICES.md` | Architecture entry, service ports, core flows |
| `docs/audit/2026-07-17-learning-freeze-audit.md` | Historical pre-full-acceptance audit evidence; current boundary is in `docs/LEARNING-FREEZE.md` |
| `docs/data-and-outbox.md` | Outbox, idempotency keys, duplicate handling |
| `docs/microservices-dao-ownership.md` | Table/DAO ownership (logical; not hard-enforced) |
| `docs/operations-checklist.md` | Local ops checks |
| `docs/learning/archive/risky-changes-remediation.md` | Money-path change constraints |
| `docs/learning/` | Learning guides (01–19); prefer final-state docs over `docs/archive/` |

**Doc vs code:** prefer **code + config + Docker init SQL**. If docs claim “stable / completed closed loop” but code cannot boot, treat docs as stale and fix code first (or update docs).

## Current readiness (2026-07-19)

- Result: **conditional learning freeze** on **Java 17 + Spring Boot 3.5.16 + Spring Cloud 2025.0.3**. The final seven-service topology passes clean Maven verification, Context tests, Mapper/DDL, Compose configuration, full reuse acceptance, raffle-award E2E, chat-refund E2E, and Playwright (18 tests, two consecutive runs) on `main`.
- Middleware defaults: MySQL 8.4.5, Redis 7.4.9, RabbitMQ 4.3.2, Nacos 3.2.3, XXL-Job 2.5.0. See `docs/adr/2026-07-18-stack-upgrade.md`.
- Fresh volumes and full secure overlay still require separate Docker runtime verification; production HA/capacity/security are out of scope.
- Do not infer current readiness from BM numbers or historical PASS records. Re-run the target working tree and preserve the verified/unverified boundary.

## Service map (default ports)

| Service | Port | Owns |
| --- | ---: | --- |
| gateway | 8080 | Routing, CB fallback |
| auth | 8081 | JWT login/verify/logout |
| admin | 8082 | Platform config / Nacos |
| market | 8083 | Raffle/activity HTTP + local rebate/strategy capabilities |
| chatbot | 8084 | Chat + credit charge |
| message-job | 8085 | MQ consumers + XXL-Job |
| account | 8086 | Credit/quota RPC |

Frontend: `http://127.0.0.1:5173` via `./scripts/web-start.sh` → API `http://127.0.0.1:8080/api/v1`.

## Cursor project assets

- Rules: `.cursor/rules/*.mdc`
- Skills: `.cursor/skills/*/SKILL.md`
- Index: `.cursor/README.md`

## Default agent behavior

1. Prefer the smallest change that fixes the stated bug; the historical BM phase order is not an active backlog.
2. Money-like paths (credit, quota, award, rebate, SKU/award stock): preserve idempotency keys; see skill `money-path-change`.
3. `market-service` must **not** scan `trigger.job` / `trigger.listener`; those belong to `message-job-service`.
4. Mapper XML is copied per launcher — change one service’s copy carefully; avoid duplicate MyBatis statement ids.
5. XXL executor `appname` must match `docs/dev-ops/mysql/sql/xxl_job.sql` group; new `@XxlJob` needs a deliberate seed/status and an executor-registration check.
6. Verification: never trust a static validator or health endpoint alone. Prefer Context tests + `acceptance.sh`, including the real raffle-award E2E.
7. Commit only when the user asks.

## Useful commands

```bash
mvn clean package -DskipTests
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
docker compose up --build -d
./scripts/validate-microservices-stack.sh
./scripts/smoke-test-microservices.sh
./scripts/smoke-api.sh
./scripts/smoke-raffle-award-e2e.sh
./scripts/acceptance.sh --reuse
./scripts/web-start.sh
npm test
```
