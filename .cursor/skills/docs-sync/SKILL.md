---
name: docs-sync
description: >-
  Updates Big Market docs and agent constraints to match code after behavior,
  topology, or readiness changes. Use when fixing BM items that contradict
  README/MICROSERVICES, changing defaults (DCC, XXL, outbox), physically
  removing a service, or when the user asks to sync documentation.
---

# Docs sync

## Rule

Code/config/SQL win. Update docs in the same change set when behavior or **readiness claims** change.

## What to touch (pick only what applies)

| Change | Docs |
| --- | --- |
| Boot fixed / still broken | `README.md`, `docs/MICROSERVICES.md`, `docs/LEARNING-FREEZE.md` readiness wording |
| XXL appname/handlers | `docs/operations-checklist.md`, `docs/dev-ops/mysql/sql/xxl_job.sql` comments if any |
| Outbox / award completion / stock confirm | `docs/data-and-outbox.md` |
| DAO/port boundaries | `docs/microservices-dao-ownership.md` (avoid overstating “resolved” if still shared infra) |
| Frontend activityId / logout | `docs/learning/02-business-flows-and-diagrams.md`, learning frontend notes |
| DCC default on/off | `docs/MICROSERVICES.md`, `docs/operations-checklist.md`, learning 07 if needed |
| Money-path semantics | `docs/learning/archive/risky-changes-remediation.md` if constraints change |
| Physical service removal / topology convergence | `pom.xml`, Compose files, `AGENTS.md`, `.cursor/rules/`, `.cursor/skills/`, `.claude/README.md`, `README.md`, `docs/MICROSERVICES.md`, `docs/LEARNING-FREEZE.md` |

## Do not

- Rewrite all of `docs/learning/` for a small fix.
- Do not claim runtime closed-loop verification from a clean build or static gates alone; record Docker/acceptance limitations explicitly.
- Do not leave active instructions that mention retired service topology or embedded/remote Provider switches.
- Treat `docs/LEARNING-FREEZE.md`, `docs/MICROSERVICES.md`, and the current
  learning guides as the source of truth.

## Style

- Prefer short factual updates over marketing language.
- Link to BM ids or plan sections when documenting known gaps.
