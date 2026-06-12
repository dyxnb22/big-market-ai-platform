# Microservices Decomposition

Last revised: 2026-06-12.

## 0. Project Mode

This is a **personal learning / resume portfolio project**. The repo
demonstrates a progressive monolith→microservices decomposition, repo-only
validators, and a Phase 8 cutover readiness pack. It does **not** claim real
staging or production deployment. All remote/outbox/cutover feature flags
default to `false`. No DDL has been applied from this repository. Every
external-facing evidence row (DBA, Ops, Engineering, Oncall, Product) remains
`EXTERNAL-GATED`.

## 1. Authoritative Status

This document is the **sole authoritative entry point** for the big-market
microservices decomposition. Historical summary docs (master plan, roadmap,
completion index, next-execution roadmap, learning-mode closure) live under
`docs/archive/microservices-history/` and remain symlinked at the docs root
so existing validator scripts continue to resolve them.

Phase 1-7 historical implementation records live under
`docs/archive/phases/`. Redundant Phase 2 evidence templates, pre-microservices
notes, and one-off review notes were removed during portfolio cleanup because
they are superseded by the active Phase 8 evidence pack and this index.

**Critical disclaimer:** the local learning-mode closure uses
`LOCAL-LEARNING-EVIDENCE` and `SIMULATED-CUTOVER-EVIDENCE` from local Docker,
Maven, and validator commands. It does **not** prove real staging or
production readiness.

---

## 2. Current Architecture State

### 2.1 Service Inventory

| Service | Port | Status | Summary |
|---------|------|--------|---------|
| `big-market-gateway` | 8080 | Stable | Spring Cloud Gateway with circuit breakers + trace ID propagation |
| `big-market-auth-service` | 8081 | Stable | Stateless JWT login + token verify |
| `big-market-admin-service` | 8082 | Stable | Platform runtime config (Nacos-synced) |
| `big-market-chatbot-service` | 8084 | Stable | AI chatbot (DeepSeek or local rule engine) |
| `big-market-market-service` | 8083 | Active | HTTP API + legacy Dubbo providers; draw orchestration still in-process |
| `big-market-message-job-service` | 8085 | Active | MQ consumers + XXL-Job handlers |
| `big-market-account-service` | 8086 | Dark launch | Dubbo provider for credit + quota; write/outbox flags default false |
| `big-market-fulfillment-service` | 8087 | Dark launch | Dubbo provider for award fulfillment; outbox flag default false |
| `big-market-rebate-service` | 8088 | Dark launch | Dubbo provider for rebate; remote flags default false |
| `big-market-strategy-service` | 8089 | Dark launch | Dubbo provider for strategy reads; remote-read flag default false |
| `big-market-activity-service` | 8090 | Scaffold only | Dark-launch scaffold; no controller/provider/MQ/job surface; no draw execution moved |

### 2.2 Bounded Context Cutover Status

| Bounded context | Owning service | Boundary status | Cutover status |
|-----------------|----------------|-----------------|----------------|
| account / credit | account-service | Ports/adapters in place; AL-4/6/7/11 resolved | Repo-ready; flags default false |
| account / quota | account-service | Remote quota adapter + ledger saga exist; AL-2/3 resolved | Repo-ready; flags default false |
| fulfillment / award | fulfillment-service | Fulfillment provider + credit-award outbox exist; AL-5/6/10/11 resolved | Repo-ready; flags default false |
| rebate | rebate-service | Read/write adapters exist; AL-8 resolved | Repo-ready; flags default false |
| strategy | strategy-service | Read provider + adapters exist; AL-1/2/3 resolved | Repo-ready; read flag default false |
| activity / draw | activity-service | Scaffold only; no controller/provider/MQ/job | Design-ready; draw traffic stays in market-service |
| task / outbox | per-domain owners | Task-outbox ports route through local adapters with shared `task` fallback | Repo-ready; physical split not cut over |
| auth | auth-service | Stateless | Complete |
| admin / config | admin-service | Stable | Complete |
| chatbot | chatbot-service | Stable | Complete |

### 2.3 Coupling Resolution (AL-1 through AL-11)

All 11 cross-boundary repository DAO couplings (AL-1 through AL-11) are
resolved through ports and adapters. Runtime physical table isolation remains
Phase 8 external-gated. See `docs/microservices-dao-ownership.md` for the
full DAO ownership matrix.

---

## 3. Completed Phases

| Phase | Scope | Result | Archive |
|-------|-------|--------|---------|
| Phase 1 | Runtime split — 5 services as Spring Boot launchers | Complete | `docs/archive/phases/microservices-split-phase-1.md` |
| Phase 2.1 | Message-job extraction (MQ + XXL-Job) | Complete | Part of Phase 2 docs |
| Phase 2.2 | Account-service dark launch + credit/quota adapters (batches B1–B21) | Repo-ready; cutover gated | `docs/archive/phases/microservices-split-phase-2-2-account-service.md` |
| Phase 2.3 | Fulfillment-service dark launch + credit-award outbox | Repo-ready; cutover gated | `docs/archive/phases/microservices-split-phase-2-3-fulfillment-service.md` |
| Phase 3 | Rebate-service boundary (read/write adapters, dependency audit, outbox decision) | Repo-ready; traffic gated | `docs/archive/phases/microservices-split-phase-3-*.md` |
| Phase 4 | Strategy-service read-first extraction (read-only API, dark-launch module, table ownership) | Repo-ready; read flag off | `docs/archive/phases/microservices-split-phase-4-*.md` |
| Phase 5 | Activity-service scaffold + draw saga/outbox design (no draw execution moved) | Design-ready | `docs/archive/phases/microservices-split-phase-5-*.md` |
| Phase 6 | DAO ownership matrix + package-ownership validator | Complete | `docs/microservices-dao-ownership.md` |
| Phase 7 | Data/outbox boundary prep (AL-1–AL-11 DAO coupling resolution, task-outbox ports, DB grants/schema plans) | Repo-complete | `docs/archive/phases/microservices-phase-7-*.md`, `docs/archive/phases/microservices-split-phase-7-*.md` |

---

## 4. Phase 8: Cutover Readiness (Active)

### 4.1 Repo-Ready (Complete)

- Phase 8 cutover evidence execution pack: staging/production evidence
  templates + GO/NO-GO checklist + validators created.
- Phase 8 staging evidence intake prep: repo-only missing-evidence detector
  and intake checklist ready.
- External evidence intake: `docs/microservices-phase-8-external-evidence-intake.md`
- Cutover runbook: `docs/microservices-phase-8-cutover-runbook.md`
- External evidence readiness pack: `docs/microservices-phase-8-external-evidence-readiness-pack.md`
- Cutover conflict matrix: `docs/microservices-phase-8-cutover-conflict-matrix.md`
- Idempotency & rollback matrix: `docs/microservices-phase-8-idempotency-rollback-matrix.md`
- Legacy cleanup inventory: `docs/microservices-legacy-cleanup-inventory.md`
- Aggregate gate: `scripts/validate-microservices-split-all-gates.sh`

### 4.2 Local Learning-Mode Closure

**Status: LEARNING-MODE-COMPLETE.**

The local learning-mode lane uses actual local commands and simulated role
equivalents (Docker, Maven, validator scripts). Evidence type:
`LOCAL-LEARNING-EVIDENCE` / `SIMULATED-CUTOVER-EVIDENCE`.

Evidence files:
- `docs/evidence/phase-8-local-learning-cutover-evidence.md`
- `scripts/validate-microservices-learning-mode-closure.sh`

**This does not claim real staging or production readiness.**

### 4.3 Remaining External Gates (ALL EXTERNAL-GATED)

These gates require real external execution and cannot be satisfied by
repo-only work:

| Gate | Owner | What's needed |
|------|-------|---------------|
| DBA DDL application | DBA | Apply proposed DDL from `docs/sql/proposed-*.sql` to staging and production |
| DB users/grants rollout | DBA | Per-service MySQL users with restricted grants per `docs/archive/phases/microservices-phase-7-db-users-grants-plan.md` |
| Nacos/Dubbo provider verification | Ops | Verify service registration in staging Nacos |
| XXL-Job handler registration | Ops | Register `DispatchCreditAwardTaskJob_DB1/DB2` etc. in XXL-Job admin |
| MQ operational registration | Ops | Register MQ consumers where new job paths are enabled |
| Staging flag canary | Engineering | Enable remote flags on single staging instance, validate business flows |
| Production single-instance canary | Engineering | Enable remote flags on single production instance with monitoring |
| Rollback rehearsal | Engineering + Ops | Prove flag=false instant rollback works |
| Monitoring + alerting | Oncall | Verify error rates, latency, quota integrity in staging and production |
| Product approval | Product | Sign off on user-visible draw behavior changes (activity-service only) |

**Every row in staging/production evidence templates remains `EXTERNAL-GATED`
until real staging or production references are attached.**

### 4.4 Recommended Execution Order (Reference Only)

1. **Staging cutover evidence** — Fill `docs/evidence/phase-8-staging-cutover-evidence-template.md` after external DBA/Ops/Engineering windows.
2. **Production cutover evidence** — Fill `docs/evidence/phase-8-production-cutover-evidence-template.md` after staging GO.
3. **7-day legacy-provider disable** — After 7 clean production days, disable legacy providers (`REBATE_LEGACY_RPC_PROVIDER_ENABLED=false`, etc.).
4. **30-day obsolete-path removal** — After 30 clean days, remove obsolete local fallbacks and compatibility mapper copies.
5. **Final decomposition closure** — Tag `microservices-decomposition-complete`.

---

## 5. Operational Notes

### 5.1 Build and Validate

```bash
# Build all modules (no tests)
mvn clean package -DskipTests

# Run all repo-only split gates (no Docker/DB required)
./scripts/validate-microservices-split-all-gates.sh

# Run Phase 8 runtime safety checks in isolation
./scripts/validate-microservices-phase-8-runtime-safety.sh

# Full local stack validation (build + Docker + smoke)
./scripts/validate-microservices-stack.sh
```

### 5.2 Local Stack

```bash
# Start infrastructure (MySQL, Redis, RabbitMQ, Nacos, etc.)
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d

# Start all services
docker compose up --build -d

# Smoke test (health checks + functional checks)
./scripts/smoke-test-phase-1.sh
```

### 5.3 Key Files for External Operators

- Proposed DDL: `docs/sql/proposed-*.sql` (5 files)
- External evidence readiness pack: `docs/microservices-phase-8-external-evidence-readiness-pack.md`
- Staging cutover template: `docs/evidence/phase-8-staging-cutover-evidence-template.md`
- Production cutover template: `docs/evidence/phase-8-production-cutover-evidence-template.md`
- GO/NO-GO checklist: `docs/evidence/phase-8-go-no-go-checklist.md`
- Cutover runbook: `docs/microservices-phase-8-cutover-runbook.md`

---

## 6. Documentation Index

### Active (root of `docs/`)

| File | Purpose |
|------|---------|
| `MICROSERVICES.md` | Authoritative entry point (this file) |
| `microservices-dao-ownership.md` | AL-1..AL-11 DAO ownership matrix |
| `microservices-legacy-cleanup-inventory.md` | Post-cutover legacy removal inventory |
| `microservices-phase-8-cutover-runbook.md` | Phase 8 runbook |
| `microservices-phase-8-external-evidence-intake.md` | Phase 8 external evidence intake |
| `microservices-phase-8-external-evidence-readiness-pack.md` | Phase 8 readiness pack |
| `microservices-phase-8-cutover-conflict-matrix.md` | Phase 8 flag/conflict matrix |
| `microservices-phase-8-idempotency-rollback-matrix.md` | Phase 8 idempotency & rollback matrix |

### Active Evidence (`docs/evidence/`)

| File | Type | Status |
|------|------|--------|
| `phase-8-staging-cutover-evidence-template.md` | Template | All fields EXTERNAL-GATED |
| `phase-8-production-cutover-evidence-template.md` | Template | All fields EXTERNAL-GATED |
| `phase-8-go-no-go-checklist.md` | Gate index | All items EXTERNAL-GATED |
| `phase-8-staging-evidence-intake-checklist.md` | Intake | All rows EXTERNAL-GATED |
| `phase-8-local-learning-cutover-evidence.md` | Local-only | LEARNING-MODE-COMPLETE (simulated) |
| `phase-8-cutover-readiness-template.md` | Template | Reference only |
| `phase-8-external-evidence-readiness-pack.md` | (Pack lives at `docs/`) | See active root |

### Proposed DDL (`docs/sql/`)

`docs/sql/proposed-*.sql` (5 files). No DDL has been applied from this repo.

### Symlinked Historical Docs (at docs root → archive)

These are kept as symlinks at the docs root so existing validators continue
to resolve them, while the actual content lives under
`docs/archive/microservices-history/` or `docs/archive/phases/`:

- `microservices-decomposition-master-plan.md`
- `microservices-split-completion-index.md`
- `microservices-next-execution-roadmap.md`
- `microservices-learning-mode-closure.md`
- `microservices-split-phase-5-account-quota-port-reverification.md`
- `microservices-split-phase-5-activity-draw-orchestration.md`
- `microservices-split-phase-5-draw-command-boundary.md`
- `microservices-split-phase-7-strategy-activity-mapping-boundary.md`
- `microservices-split-phase-7-task-outbox-ownership.md`
- `microservices-phase-7-db-users-grants-plan.md`
- `microservices-phase-7-sharded-schema-isolation-plan.md`

---

## 7. Archive Map

| Archive location | Contents |
|-----------------|----------|
| `docs/archive/phases/` | Phase 1-7 historical implementation records |
| `docs/archive/microservices-history/` | Superseded summary docs (roadmap, master-plan, completion-index, next-execution-roadmap, learning-mode-closure) |
| `docs/archive/microservices-historical-docs-index.md` | Index of all historical docs |

---

## 8. Repository Cleanup Note (2026-06-12)

A documentation cleanup pass removed redundant generated evidence snapshots,
Phase 2 evidence templates, pre-microservices notes, and one-off review notes.
`MICROSERVICES.md` became the single docs index. No SQL, no validator
behavior, no feature-flag defaults, and no Java runtime behavior were changed.
Repo-only gates remain green; external evidence remains EXTERNAL-GATED.

---

## 9. Cross-References

- Active Phase 8 runbook: `docs/microservices-phase-8-cutover-runbook.md`
- Cutover conflict matrix: `docs/microservices-phase-8-cutover-conflict-matrix.md`
- Idempotency & rollback matrix: `docs/microservices-phase-8-idempotency-rollback-matrix.md`
- Active external evidence intake: `docs/microservices-phase-8-external-evidence-intake.md`
- External evidence readiness pack: `docs/microservices-phase-8-external-evidence-readiness-pack.md`
- DAO ownership matrix: `docs/microservices-dao-ownership.md`
- Legacy cleanup inventory: `docs/microservices-legacy-cleanup-inventory.md`
- Proposed SQL DDL: `docs/sql/proposed-*.sql`
- Dev-ops configs: `docs/dev-ops/`
- Aggregate gate script: `scripts/validate-microservices-split-all-gates.sh`
