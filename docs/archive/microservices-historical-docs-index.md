# Microservices Historical Docs Index

Last revised: 2026-06-12.

Status: archive index.

## Active Source of Truth

The single authoritative entry point — the active source of truth — for
microservices decomposition is now:

- **`docs/MICROSERVICES.md`** — authoritative current status, completed phases, active Phase 8, remaining external gates, operational notes, evidence map, and archive map.

Active Phase 8 documents:

- `docs/microservices-phase-8-cutover-runbook.md`
- `docs/microservices-phase-8-external-evidence-intake.md`
- `docs/microservices-dao-ownership.md`
- `docs/microservices-legacy-cleanup-inventory.md`

## Archived Documents

### Summary documents (superseded)
- `docs/archive/microservices-history/microservices-roadmap.md`
- `docs/archive/microservices-history/microservices-decomposition-master-plan.md`
- `docs/archive/microservices-history/microservices-split-completion-index.md`
- `docs/archive/microservices-history/microservices-next-execution-roadmap.md`
- `docs/archive/microservices-history/microservices-learning-mode-closure.md`

### Phase 1–7 historical implementation records
- `docs/archive/phases/` (16 files)

## Historical Reference Sets

- Phase 8 active evidence templates under `docs/evidence/phase-8-*`.
- Proposed SQL files under `docs/sql/proposed-*.sql`.
- Dev-ops configs under `docs/dev-ops/`.

## Cleanup Notes

- 2026-06-12 (initial): historical documents were archived under
  `docs/archive/microservices-history/` and `docs/archive/phases/`;
  `docs/MICROSERVICES.md` became the single authoritative entry point.
  Summary docs and Phase 5/7 design docs are **archived by reference**
  — they remain symlinked at the docs root so existing validators
  continue to resolve them. No physical files were moved in this pass.
- 2026-06-12 (portfolio cleanup): redundant Phase 2 evidence templates,
  generated evidence snapshots, pre-microservices notes, and one-off review
  notes were removed. No SQL, no validator, no feature-flag defaults, and no
  Java runtime behavior changed. Repo-only gates remain green; external
  evidence remains EXTERNAL-GATED.
