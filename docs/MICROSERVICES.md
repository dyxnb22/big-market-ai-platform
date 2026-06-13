# Microservices Decomposition

Last revised: 2026-06-13.

This is a personal learning / resume portfolio project. It demonstrates a
monolith-to-microservices decomposition with repo-only validators and a Phase 8
cutover readiness pack. It does not claim real staging or production readiness.
All remote/outbox/cutover flags default false; external evidence is
EXTERNAL-GATED.

This file is the sole authoritative entry point for the current microservices
status; historical details live under `docs/archive/`.

## Status

| Area | Status |
|------|--------|
| Phase 1 runtime split | Complete |
| Phase 2 account / fulfillment dark launch | Repo-ready; cutover gated |
| Phase 3 rebate-service | Repo-ready; flags false |
| Phase 4 strategy-service | Repo-ready; read flag false |
| Phase 5 activity-service | Scaffold/design only; draw not moved |
| Phase 6 DAO ownership | Complete |
| Phase 7 data/outbox boundary prep | Repo-complete |
| Phase 8 cutover readiness | Repo-ready; EXTERNAL-GATED |
| Local learning lane | LEARNING-MODE-COMPLETE using LOCAL-LEARNING-EVIDENCE / SIMULATED-CUTOVER-EVIDENCE only |

## Services

| Service | Port | Status |
|---------|------|--------|
| `big-market-gateway` | 8080 | Stable |
| `big-market-auth-service` | 8081 | Stable |
| `big-market-admin-service` | 8082 | Stable |
| `big-market-market-service` | 8083 | Active legacy/API host |
| `big-market-chatbot-service` | 8084 | Stable |
| `big-market-message-job-service` | 8085 | Active job/MQ host |
| `big-market-account-service` | 8086 | Dark launch |
| `big-market-fulfillment-service` | 8087 | Dark launch |
| `big-market-rebate-service` | 8088 | Dark launch |
| `big-market-strategy-service` | 8089 | Dark launch |

## Boundaries

AL-1 through AL-11 repository/DAO couplings are resolved through ports and
adapters. Runtime table isolation and real cutover remain external-gated. See
`docs/microservices-dao-ownership.md`.

## Phase 8 Active Docs

- `docs/microservices-phase-8.md` — cutover runbook, external evidence intake, conflict matrix, idempotency/rollback matrix
- `docs/microservices-dao-ownership.md` — DAO/table ownership matrix (AL-1 through AL-11)
- `docs/microservices-legacy-cleanup-inventory.md` — legacy path removal checklist
- `docs/evidence/phase-8-evidence-pack.md` — consolidated staging/prod evidence (all rows EXTERNAL-GATED)
- `docs/sql/proposed-*.sql` — proposed DDL for outbox/ledger tables

## Commands

```bash
mvn clean package -DskipTests
./scripts/validate-microservices-split-all-gates.sh
./scripts/validate-microservices-phase-8-runtime-safety.sh
./scripts/validate-microservices-stack.sh
```

## External Gates

DBA DDL/grants, Ops Dubbo/XXL-Job/MQ registration, Engineering canaries,
Oncall dashboards/alerts, Product signoff, rollback rehearsal, 7-day stable
legacy-provider disable, and 30-day obsolete-path removal are all
EXTERNAL-GATED.

## Archive

Historical details are consolidated under `docs/archive/`:

- `docs/archive/microservices-history.md` — executive summary, completion index, roadmap, learning-mode closure
- `docs/archive/phases.md` — Phase 1–7 implementation notes and validator compatibility markers
- `docs/archive/microservices-historical-docs-index.md` — index of archived documents
