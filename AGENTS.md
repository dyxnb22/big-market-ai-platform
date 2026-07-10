# AGENTS.md — Big Market AI Platform

Guidance for Cursor / Codex agents working in this repository.

## What this repo is

Java microservices **marketing raffle** learning/portfolio project: gateway, auth, admin, market, chatbot, message-job, account, fulfillment, rebate, strategy; shared `domain` / `infrastructure` / `api` / `types` / starters; frontend `big-market-web` (static HTML/JS, not React).

## Authoritative docs (read before large changes)

| Doc | Use for |
| --- | --- |
| `docs/MICROSERVICES.md` | Architecture entry, service ports, core flows |
| `docs/audit-remediation-plan.md` | **Current fix backlog** (BM-001…); phase order |
| `docs/data-and-outbox.md` | Outbox, idempotency keys, duplicate handling |
| `docs/microservices-dao-ownership.md` | Table/DAO ownership (logical; not hard-enforced) |
| `docs/operations-checklist.md` | Local ops checks |
| `docs/learning/archive/risky-changes-remediation.md` | Money-path change constraints |
| `docs/learning/` | Learning guides (01–19); prefer final-state docs over `docs/archive/` |

**Doc vs code:** prefer **code + config + Docker init SQL**. If docs claim “stable / completed closed loop” but code cannot boot, treat docs as stale and fix code first (or update docs).

## Current readiness (as of audit remediation 2026-07-10)

- **Phase 1 (boot P0)** code + slice tests: BM-001/002/003; message-job `IAccountReadAdapter`; full `@SpringBootTest` on entire apps still partial.
- **Phase 2–3** code in tree (BM-004–015); **demo closed loop** needs Docker + smoke/Playwright.
- Phase 4 (BM-016/017) deferred. Track in `docs/audit-remediation-plan.md` §8.

## Service map (default ports)

| Service | Port | Owns |
| --- | ---: | --- |
| gateway | 8080 | Routing, CB fallback |
| auth | 8081 | JWT login/verify/logout |
| admin | 8082 | Platform config / Nacos |
| market | 8083 | Raffle/activity HTTP + embedded rebate/strategy providers by default |
| chatbot | 8084 | Chat + credit charge |
| message-job | 8085 | MQ consumers + XXL-Job |
| account | 8086 | Credit/quota RPC |
| fulfillment | 8087 | Award fulfillment RPC |
| rebate | 8088 | Dedicated rebate (optional; often embedded in market) |
| strategy | 8089 | Dedicated strategy (optional; often embedded in market) |

Frontend: `http://127.0.0.1:5173` via `./scripts/web-start.sh` → API `http://127.0.0.1:8080/api/v1`.

## Cursor project assets

- Rules: `.cursor/rules/*.mdc`
- Skills: `.cursor/skills/*/SKILL.md`
- Index: `.cursor/README.md`

## Default agent behavior

1. Prefer smallest change that fixes the stated BM / bug; follow remediation **phase order** unless user overrides.
2. Money-like paths (credit, quota, award, rebate, SKU/award stock): preserve idempotency keys; see skill `money-path-change`.
3. `market-service` must **not** scan `trigger.job` / `trigger.listener`; those belong to `message-job-service`.
4. Mapper XML is copied per launcher — change one service’s copy carefully; avoid duplicate MyBatis statement ids.
5. XXL executor `appname` must match `docs/dev-ops/mysql/sql/xxl_job.sql` group; new `@XxlJob` needs seed + enable.
6. Verification: do not trust `validate-microservices-runtime-safety.sh` alone (known false green). Prefer Context tests + stack/smoke scripts; see skill `local-verify`.
7. Commit only when the user asks.

## Useful commands

```bash
mvn clean package -DskipTests
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
./scripts/validate-microservices-stack.sh
./scripts/smoke-test-microservices.sh
./scripts/smoke-api.sh
./scripts/web-start.sh
npm test
```
