# Microservices Historical Summary

This consolidated archive replaces the old per-document history files. The
old paths remain as compatibility links for validators and references.

LOCAL-LEARNING-EVIDENCE / SIMULATED-CUTOVER-EVIDENCE / LEARNING-MODE-COMPLETE.
This archive does not prove staging or production readiness; all external
execution remains EXTERNAL-GATED.

## 1. Executive Summary

Phase 3 through Phase 7 are repo-complete. Phase 8 repo readiness is complete;
external cutover is gated on DBA, Ops, Engineering, Oncall, and Product evidence.
Phase 8 repo readiness complete / external cutover gated.

## 2. Current State Inventory

Active source of truth:

- `docs/MICROSERVICES.md`
- `docs/archive/microservices-history.md`
- `docs/microservices-phase-8.md`
- `docs/microservices-legacy-cleanup-inventory.md`

Deprecated history is archived by reference and retained only for validation,
traceability, and migration notes. Historical one-off phase scripts have been
removed from the active script surface; their outcomes are represented here and
in the current aggregate validators.

## 3. Definition of Done

Repo-ready means code compiles, static gates pass, ownership docs are updated,
remote flags default off, and rollback paths are documented. Production-ready
requires real external evidence and approvals.

## 4. Phase Plan

| Phase | Status | Notes |
| --- | --- | --- |
| Phase 1 | done | scaffold and boundary inventory |
| Phase 2 | done | account and fulfillment extraction prep |
| Phase 3 | done | rebate-service ownership and cutover prep |
| Phase 4 | done | strategy-service read boundary |
| Phase 5 | done | activity draw orchestration and ports |
| Phase 6 | done | DAO/package ownership matrix |
| Phase 7 | done | DB/user/outbox isolation planning |
| Phase 8 | repo-ready | external evidence and cutover remain gated |

Phase 6-A DAO ownership matrix complete; see `docs/microservices-dao-ownership.md`.
Phase 6-B done. Phase 7-B complete.
Phase 5-E IAwardFulfillmentPort Done; 5-E local award fulfillment port introduced.
Phase 5-F and Phase 5-G are the next scaffold/outbox gates.

## 5. Batch Backlog

Remaining batches are evidence intake, staging/prod evidence capture, legacy
provider disablement after seven stable days, obsolete path removal after
thirty stable days, and keeping aggregate validation green.

## 6. Boundary Matrix

| Area | Owner | State |
| --- | --- | --- |
| account / credit | account-service | port-first, remote flags off |
| account / quota | account-service | quota decrement through account port |
| fulfillment / award | fulfillment-service | award fulfillment port guarded |
| rebate | rebate-service | create-order/read adapters prepared |
| strategy | strategy-service | read/decision remote flags off |
| activity / draw | activity-service | draw command boundary documented |
| task / outbox | domain owners + message-job | per-domain outbox decision complete |
| auth | legacy | no split in this batch |
| admin / config | legacy | no split in this batch |
| chatbot | legacy | no split in this batch |
| query / search | legacy | no split in this batch |

## 7. Dependency Rules

Services expose ports before remote enablement. Legacy fallbacks remain until
validated cutover. No schema grants, MQ topics, XXL jobs, or production flags
are changed from documentation-only work.

## 8. Recommended Execution Order

| # | Batch | Gate |
| --- | --- | --- |
| 1 | External evidence intake | `docs/microservices-phase-8.md` |
| 2 | Staging/prod cutover evidence | DBA/Ops/Engineering/Oncall/Product approvals |
| 3 | 7-day stable legacy-provider disable | `docs/microservices-legacy-cleanup-inventory.md` |
| 4 | 30-day obsolete-path removal | post-cutover cleanup gates |
| 5 | Keep `scripts/validate-microservices-split-all-gates.sh` green in CI | aggregate gate |

The completion index is folded into `docs/archive/microservices-history.md`.
Evidence pack links:
`docs/evidence/phase-8-evidence-pack.md`,
`docs/evidence/phase-8-evidence-pack.md`,
`docs/evidence/phase-8-evidence-pack.md`, and
`scripts/validate-microservices-phase-8-cutover-evidence-pack.sh`.
Next technical batches should prioritize AL-5 / Phase 7-C only after evidence
gates stay green.

## 9. Risk Register

| Risk | Guard |
| --- | --- |
| External evidence missing | EXTERNAL-GATED placeholders remain blocking |
| Hidden legacy traffic | seven-day stability window before disabling providers |
| Premature cleanup | thirty-day window before deleting obsolete paths |
| Task-table coupling | per-domain outbox migration and rollback plan |

## 10. Non-Goals

- No big-bang rewrite.
- No immediate activity-service extraction.
- No production traffic enablement.
- No expansion of generated evidence beyond local learning evidence.
- No large-scale package renames.

## 11. Safety Rules

- No connection to staging or production from validators.
- No mysql, docker, or curl side effects for repo-only gates.
- No traffic enablement.
- No Java behavior change from doc cleanup.
- No remote or dangerous flag default flipped.

## Completion Index

The split completion index marks Phase 3-7 repo-complete and Phase 8
repo-ready but externally gated. DBA DDL/grants, Ops registration, Engineering
canaries, Oncall monitoring, and Product GO/NO-GO evidence remain required.

## Next Execution Roadmap

Immediate roadmap:

1. Keep local validation green.
2. Collect external evidence through the Phase 8 intake.
3. Run staging cutover only after evidence owners approve.
4. Run production cutover only after external staging approval is recorded.
5. See `docs/microservices-phase-8.md`,
   `scripts/validate-microservices-phase-8-staging-evidence-intake.sh`, and
   `scripts/validate-microservices-phase-8-staging-evidence-consistency.sh`.
6. Disable legacy providers after seven stable days.
7. Remove obsolete paths after thirty stable days.

## Learning Closure

LOCAL-LEARNING-EVIDENCE and SIMULATED-CUTOVER-EVIDENCE prove local command
coverage only. They do not claim real production readiness, real approvals, or
real production evidence. Final production readiness is not proven and remains
EXTERNAL-GATED.
