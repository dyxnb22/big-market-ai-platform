# Phase 8 Cutover Readiness Runbook

Status: repo readiness complete when validators pass. External cutover is not
complete. All staging DDL, production canaries, approvals, and live evidence are
EXTERNAL-GATED.

Local learning-mode note: the local learning project is LEARNING-MODE-COMPLETE
when `docs/microservices-learning-mode-closure.md`,
`docs/evidence/phase-8-local-learning-cutover-evidence.md`, and
`scripts/validate-microservices-learning-mode-closure.sh` pass. That path uses
LOCAL-LEARNING-EVIDENCE and SIMULATED-CUTOVER-EVIDENCE only; it does not claim
real staging or production readiness. Production readiness is not proven by
this local learning-mode closure.

## Common Rules

- Do not apply DDL from this repo automatically.
- Do not enable production, remote, or outbox flags by default.
- Repo-only hardening validators:
  `scripts/validate-microservices-service-module-ownership.sh`,
  `scripts/validate-microservices-production-flag-matrix.sh`, and
  `scripts/validate-microservices-split-all-gates.sh`.
- Completion index: `docs/microservices-split-completion-index.md`.
- External evidence intake index:
  `docs/microservices-phase-8-external-evidence-intake.md`.
- Legacy cleanup inventory:
  `docs/microservices-legacy-cleanup-inventory.md`.
- Staging cutover evidence template:
  `docs/evidence/phase-8-staging-cutover-evidence-template.md`.
- Staging evidence intake checklist:
  `docs/evidence/phase-8-staging-evidence-intake-checklist.md`.
- Production cutover evidence template:
  `docs/evidence/phase-8-production-cutover-evidence-template.md`.
- GO/NO-GO checklist:
  `docs/evidence/phase-8-go-no-go-checklist.md`.
- Proposed DDL files: `docs/sql/proposed-credit-award-task-outbox.sql`,
  `docs/sql/proposed-quota-decrement-ledger.sql`,
  `docs/sql/proposed-rebate-task-outbox.sql`,
  `docs/sql/proposed-credit-trade-task-outbox.sql`,
  `docs/sql/proposed-award-dispatch-task-outbox.sql`.
- Required external approvals: DBA, Ops, Engineering, Oncall, Product where user-visible behavior changes.
- Evidence template: `docs/evidence/phase-8-cutover-readiness-template.md`.
- Cutover evidence pack validator:
  `scripts/validate-microservices-phase-8-cutover-evidence-pack.sh`.
- Staging evidence intake validator:
  `scripts/validate-microservices-phase-8-staging-evidence-intake.sh`.
- Staging evidence consistency validator:
  `scripts/validate-microservices-phase-8-staging-evidence-consistency.sh`.
- Learning-mode closure validator:
  `scripts/validate-microservices-learning-mode-closure.sh`.

## Execution Order After Repo Readiness

1. External evidence intake: collect DBA, Ops, Engineering, Oncall, and Product
   evidence references in `docs/microservices-phase-8-external-evidence-intake.md`.
2. Staging cutover evidence: fill
   `docs/evidence/phase-8-staging-cutover-evidence-template.md` and
   `docs/evidence/phase-8-go-no-go-checklist.md` only after
   `docs/evidence/phase-8-staging-evidence-intake-checklist.md` has concrete
   DBA/Ops/Engineering/Oncall/Product references from a real staging window.
3. Production cutover evidence: fill
   `docs/evidence/phase-8-production-cutover-evidence-template.md` only after
   staging GO evidence exists and before any production traffic claim is made.
4. 7-day stable legacy-provider disable: only after real evidence and a clean
   oncall window may an environment disable legacy providers.
5. 30-day obsolete-path removal: only after the removal gate may a repo batch
   delete compatibility code, shared mapper copies, or fallback adapters.

All five steps are EXTERNAL-GATED. Repo-only validators do not satisfy them.

## account-service Write Cutover

Prerequisites: account-service deployed, Phase 6/7 validators green, proposed
DDL reviewed, secrets prepared for account DB user, `account.remote-write.enabled=false`,
`account.award-credit-outbox.enabled=false`, quota decrement flags default false.

Staging validation: DBA applies required proposed DDL; Ops configures staging
secrets; Engineering runs account write, quota, credit-award idempotency, and
rollback rehearsals; Oncall reviews dashboards.

Production canary: enable one account write path for a limited cohort only
after approvals. Monitor duplicate keys, credit balance drift, quota ledger
drift, MQ lag, and error rate.

Rollback: set account write/outbox flags false, route traffic to legacy local
path, keep DDL in place, replay or inspect pending outbox rows before retry.

Acceptance criteria: no balance drift, no unbounded retries, rollback tested,
and legacy path remains available.

7-day cleanup gate: freeze new legacy account writes. 30-day cleanup gate:
remove compatibility grants and obsolete local paths only with evidence.

EXTERNAL-GATED: staging DDL, production DDL, flag enablement, approvals, canary,
and cleanup signoff.

## fulfillment-service Cutover

Prerequisites: fulfillment-service deployed, `fulfillment.remote.enabled=false`,
`account.award-credit-outbox.enabled=false`, `docs/sql/proposed-credit-award-task-outbox.sql`
reviewed, `docs/sql/proposed-award-dispatch-task-outbox.sql` reviewed.

Staging validation: validate award record writes, send_award dispatch, credit
award outbox idempotency, and rollback to legacy provider.

Production canary: enable fulfillment remote path for a small cohort after DBA
and oncall approval. Monitor award record completion, send_award MQ lag, and
credit award dispatch lag.

Rollback: disable fulfillment remote and outbox flags, resume legacy provider,
keep outbox rows for inspection, and drain or replay according to oncall plan.

Acceptance criteria: no missing awards, no duplicate dispatch, no credit drift.

7-day cleanup gate: stop adding legacy fulfillment writes. 30-day cleanup gate:
remove compatibility mapper/grants.

EXTERNAL-GATED: all DDL, traffic, approvals, and cleanup signoff.

## rebate-service Cutover

Prerequisites: rebate-service deployed, `rebate.remote-create-order.enabled=false`,
`rebate.service.remote-read.enabled=false`, `docs/sql/proposed-rebate-task-outbox.sql`
reviewed, AL-8 direct DAO coupling resolved through `IRebateTaskOutboxPort`.

Staging validation: validate calendar sign rebate write/read, out_business_no
idempotency, rebate MQ publish, and shared task fallback rollback.

Production canary: enable remote rebate write/read in separate steps. Monitor
rebate order duplicates, MQ publish failures, retry count, and user-visible
calendar sign state.

Rollback: disable rebate remote flags, return to local legacy provider, keep
outbox DDL and rows untouched for inspection.

Acceptance criteria: no duplicate rebate orders and no lost rebate messages.

7-day cleanup gate: freeze legacy rebate writes. 30-day cleanup gate: remove
compatibility grants and mapper copies.

EXTERNAL-GATED: DDL, remote flags, canary, and approvals.

## strategy-service Read Cutover

Prerequisites: strategy-service deployed, strategy read adapters present,
`strategy.service.remote-read.enabled=false`, `strategy.service.remote-decision.enabled=false`.

Staging validation: compare strategy, award, rule tree, and rule weight reads
between local and remote paths. Keep draw decision in-process unless separately approved.

Production canary: enable read-only strategy traffic first. Do not enable remote
decision by default.

Rollback: disable strategy remote read flag and return to local reads.

Acceptance criteria: read parity, latency within agreed threshold, zero decision
path changes unless approved.

7-day cleanup gate: remove stale read compatibility only after parity evidence.
30-day cleanup gate: narrow grants.

EXTERNAL-GATED: staging parity evidence, production read canary, approvals.

## activity-service Draw Cutover

Prerequisites: Phase 5-G draw saga/outbox approval and Phase 7-D activity outbox
approval. `activity.service.remote-draw.enabled=false`. No draw traffic moves
from market/app without Product, Engineering, DBA, Ops, and Oncall approval.

Staging validation: run saga idempotency, draw latency, quota decrement, award
fulfillment, rollback, and replay tests.

Production canary: only after Phase 5-G/7-D external approval. Canary a small
cohort, monitor draw success, duplicate orders, quota drift, award dispatch, MQ
lag, and P99 latency.

Rollback: disable remote draw flag, resume legacy draw path, preserve saga/outbox
rows for inspection and replay.

Acceptance criteria: no duplicate draw orders, no quota drift, no missing awards,
and latency threshold met.

7-day cleanup gate: freeze legacy draw writes. 30-day cleanup gate: remove
legacy provider and compatibility grants only with evidence.

EXTERNAL-GATED: this entire cutover until Phase 5-G/7-D approvals exist.
