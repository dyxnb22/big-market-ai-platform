# Evidence Directory

Last revised: 2026-06-12.

This directory holds only the **active Phase 8 cutover evidence templates**
and the local learning-mode evidence. Historical Phase 2 evidence templates
and generated snapshots were removed during portfolio cleanup; Phase 8
readiness is now represented by the active evidence files below and the
readiness pack at `docs/microservices-phase-8-external-evidence-readiness-pack.md`.

## Active Phase 8 Evidence

| File | Type | Status |
|------|------|--------|
| `phase-8-staging-cutover-evidence-template.md` | Template | All fields EXTERNAL-GATED |
| `phase-8-production-cutover-evidence-template.md` | Template | All fields EXTERNAL-GATED |
| `phase-8-go-no-go-checklist.md` | Gate index | All items EXTERNAL-GATED |
| `phase-8-staging-evidence-intake-checklist.md` | Intake checklist | All rows EXTERNAL-GATED |
| `phase-8-cutover-readiness-template.md` | Template | Blank, for live evidence |
| `phase-8-local-learning-cutover-evidence.md` | Local-only | LEARNING-MODE-COMPLETE (simulated) |

## Authoritative Source

For the authoritative microservices decomposition status, see
`docs/MICROSERVICES.md`. This repo is a personal learning / portfolio
project: only repo-only gates are claimed; real staging and production
cutover remain EXTERNAL-GATED.
