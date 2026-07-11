---
name: docs-sync
description: >-
  Updates Big Market docs to match code after behavior or readiness changes.
  Use when fixing BM items that contradict README/MICROSERVICES, changing
  defaults (DCC, XXL, outbox, embedded RPC), or when the user asks to sync
  documentation.
---

# Docs sync

## Rule

Code/config/SQL win. Update docs in the same change set when behavior or **readiness claims** change.

## What to touch (pick only what applies)

| Change | Docs |
| --- | --- |
| Boot fixed / still broken | `README.md`, `docs/MICROSERVICES.md` readiness wording; `docs/audit-remediation-plan.md` §8 |
| XXL appname/handlers | `docs/operations-checklist.md`, `docs/dev-ops/mysql/sql/xxl_job.sql` comments if any |
| Outbox / award completion / stock confirm | `docs/data-and-outbox.md` |
| DAO/port boundaries | `docs/microservices-dao-ownership.md` (avoid overstating “resolved” if still shared infra) |
| Frontend activityId / logout | `docs/learning/02-business-flows-and-diagrams.md`, learning frontend notes |
| DCC default on/off | `docs/MICROSERVICES.md`, `docs/operations-checklist.md`, learning 07 if needed |
| Money-path semantics | `docs/learning/archive/risky-changes-remediation.md` if constraints change |

## Do not

- Rewrite all of `docs/learning/` for a small fix.
- Leave “稳定，已启用 / completed local stack / 可演示完整闭环” if Phase 1–3 acceptance is incomplete.
- Treat `docs/archive/` as current truth.

## Style

- Prefer short factual updates over marketing language.
- Link to BM ids or plan sections when documenting known gaps.
