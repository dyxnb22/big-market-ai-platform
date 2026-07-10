---
name: audit-remediation
description: >-
  Executes Big Market audit remediation by BM id and phase order from
  docs/audit-remediation-plan.md. Use when fixing BM-001–BM-017, closed-loop
  gaps, P0 boot failures, demo readiness, or when the user mentions the audit
  report / remediation plan / 整改计划.
---

# Audit remediation

## Before coding

1. Read `docs/audit-remediation-plan.md` (phases, BM index, PR slices, §8 progress).
2. Confirm which BM ids / phase the user wants. Default: earliest incomplete phase.
3. Do not skip Phase 1 (BM-001/002/003) to chase P1 features unless user explicitly overrides.

## Per-item workflow

1. Locate evidence paths listed in the plan / audit.
2. Implement the smallest fix; keep money-path rules (`money-path-change` skill).
3. Add or update the phase’s acceptance tests (Context / Mapper / XXL / path tests).
4. Run `local-verify` skill commands relevant to the change.
5. If behavior or readiness changed, use `docs-sync` skill.
6. Update plan §8 checkboxes only when acceptance items are actually met.

## Phase cheat sheet

| Phase | BM | Done when |
| ---: | --- | --- |
| 1 | 001–003 | market + message-job Context up; XXL appname/seed aligned |
| 2 | 004–010 | draw/sign/exchange/award/stock/chat semantics correct on backend |
| 3 | 011–015 | frontend stage/logout, DCC/ERP, secure defaults usable for demo |
| 4 | 016–017 | metrics/gates/architecture debt — non-blocking for demo |

## Out of scope while remediating

Market further split, physical DB-per-service, HA/SLO, full CVE audit — see plan §6.
