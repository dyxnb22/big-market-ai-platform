# Microservices Split Completion Index

Last revised: 2026-06-11.

Status summary: Phase 7 is repo-complete. Phase 8 repo readiness is complete,
but staging and production cutover remain EXTERNAL-GATED. No DDL has been
applied from this repository, no production traffic flag is enabled by default,
and no external DBA/Ops/oncall approval is implied by a green repo validator.

## Completion Snapshot

| Area | Status | Tag / validator |
|------|--------|-----------------|
| Phase 1 runtime split | Complete | `phase-1-complete` history in `docs/microservices-roadmap.md` |
| Phase 2 account + fulfillment readiness | Repo-ready; external cutovers gated | B17/B18/B23 evidence templates and validators |
| Phase 3 rebate-service boundary | Repo-ready; traffic gated | `phase-3-rebate-read-adapter-boundary` and Phase 3 validators |
| Phase 4 strategy-service read boundary | Repo-ready; read traffic gated | `phase-4-strategy-read-adapter-boundary` |
| Phase 5 activity-service scaffold and draw saga design | Repo-ready scaffold; draw cutover gated | `phase-5-activity-service-dark-launch-scaffold`, `phase-5-activity-draw-saga-outbox-scaffold` |
| Phase 6 DAO and package ownership gates | Complete | `phase-6-dao-ownership-matrix`, `phase-6-package-ownership-boundaries` |
| Phase 7 data/outbox boundary prep | Repo-complete | `phase-7-complete-phase-8-readiness` |
| Phase 8 cutover readiness pack | Repo readiness complete; external cutover gated | `phase-8-cutover-readiness-pack`; `scripts/validate-microservices-phase-8-cutover-readiness.sh` |
| Phase 8 cutover evidence execution pack | Repo-only evidence templates and GO/NO-GO checklist ready; external evidence still gated | `docs/evidence/phase-8-staging-cutover-evidence-template.md`, `docs/evidence/phase-8-production-cutover-evidence-template.md`, `docs/evidence/phase-8-go-no-go-checklist.md`, `scripts/validate-microservices-phase-8-cutover-evidence-pack.sh` |
| Phase 8 staging evidence intake prep | Repo-only missing-evidence detector ready; staging remains EXTERNAL-GATED | `docs/evidence/phase-8-staging-evidence-intake-checklist.md`, `scripts/validate-microservices-phase-8-staging-evidence-intake.sh` |
| Phase 8 hardening gates | Repo-only regression gates | `scripts/validate-microservices-split-all-gates.sh` |
| Phase 8 external evidence and cleanup gates | Repo-only intake/readiness scaffolds; all evidence missing and EXTERNAL-GATED | `scripts/validate-microservices-phase-8-external-evidence-intake.sh`, `scripts/validate-microservices-legacy-cleanup-readiness.sh`, `scripts/validate-microservices-post-cutover-cleanup-gates.sh` |

## AL-1 Through AL-11 Status

| ID | Original coupling | Current repo status | Remaining gate |
|----|-------------------|---------------------|----------------|
| AL-1 | `StrategyRepository` -> `IRaffleActivityDao` | Resolved through `IStrategyActivityMappingPort` | Runtime activity table isolation remains Phase 8-gated |
| AL-2 | `StrategyRepository` -> `IRaffleActivityAccountDao` | Resolved through `IStrategyActivityAccountPort` | Account/quota cutover evidence |
| AL-3 | `StrategyRepository` -> `IRaffleActivityAccountDayDao` | Resolved through `IStrategyActivityAccountPort` | Account/quota cutover evidence |
| AL-4 | `ActivityRepository` -> `IUserCreditAccountDao` | Resolved through `IActivityAccountPort` | Account-service runtime cutover |
| AL-5 | `AwardRepository` -> `IUserRaffleOrderDao` | Resolved through `IAwardActivityOrderPort` | Activity/fulfillment runtime table isolation |
| AL-6 | `AwardRepository` -> `IUserCreditAccountDao` | Resolved through `IAwardCreditWritePort` | Credit-award outbox cutover evidence |
| AL-7 | `DispatchCreditAwardTaskJob` -> `ICreditAwardTaskDao` | Resolved through `ICreditAwardTaskDispatchPort` | Account-owned dispatch boundary cutover |
| AL-8 | `BehaviorRebateRepository` -> `ITaskDao` | Resolved through `IRebateTaskOutboxPort`; local adapter preserves shared `task` fallback | DBA-applied `rebate_task_outbox` DDL and cutover |
| AL-9 | `CreditRepository` -> `ITaskDao` | Resolved through `ICreditTradeTaskOutboxPort`; local adapter preserves shared `task` fallback | DBA-applied `credit_trade_task_outbox` DDL and cutover |
| AL-10 | `AwardRepository` -> `ITaskDao` | Resolved through `IAwardDispatchTaskOutboxPort`; local adapter preserves shared `task` fallback | DBA-applied `award_dispatch_task_outbox` DDL and cutover |
| AL-11 | `AwardRepository` -> `ICreditAwardTaskDao` | Resolved through `IAwardCreditWritePort` | Credit-award outbox flag and dispatch cutover |

## Bounded Context Cutover Matrix

| Bounded context | Owning service | Owned tables | Current repo boundary status | Cutover readiness status | External dependencies |
|-----------------|----------------|--------------|------------------------------|--------------------------|-----------------------|
| account / credit | account-service | `user_credit_account`, `user_credit_order`, `credit_award_task` | Ports/adapters in place; direct AL-4/AL-6/AL-7/AL-11 repository/job DAO couplings resolved | Repo-ready; write/outbox flags default false | DBA DDL review, account DB grants, Ops deployment, staging parity, Oncall approval |
| account / quota | account-service | `raffle_activity_account`, `raffle_activity_account_day`, `raffle_activity_account_month`, `raffle_quota_decrement_ledger` | Remote quota adapter scaffold exists; AL-2/AL-3 resolved | Repo-ready; quota write/decrement flags default false | DBA ledger DDL, quota rollback rehearsal, staging evidence, Oncall approval |
| fulfillment / award | fulfillment-service | `award`, `user_award_record` | Fulfillment provider exists; AL-5/AL-6/AL-10/AL-11 direct couplings resolved | Repo-ready; fulfillment remote/outbox flags default false | DBA outbox DDL, fulfillment canary, MQ/job monitoring, Oncall approval |
| rebate | rebate-service | `daily_behavior_rebate`, `user_behavior_rebate_order`, future `rebate_task_outbox` | Read/write adapters exist; AL-8 direct coupling resolved | Repo-ready; rebate remote flags default false | DBA rebate outbox DDL, duplicate provider disablement, staging provider verification |
| strategy | strategy-service | `strategy`, `strategy_award`, `strategy_rule`, `rule_tree`, `rule_tree_node`, `rule_tree_node_line` | Read provider and adapters exist; AL-1/AL-2/AL-3 resolved | Repo-ready; read flag default false | Staging read parity, Nacos provider verification, Oncall approval |
| activity / draw | activity-service | `raffle_activity`, `raffle_activity_count`, `raffle_activity_sku`, `raffle_activity_stage`, `raffle_activity_order`, `user_raffle_order` | Dark-launch scaffold only; no controller/provider/MQ/job/mapper runtime surface | Design-ready only; draw traffic remains in market-service | Product/Engineering approval, DBA activity outbox DDL, saga rehearsal, canary approval |
| task / outbox compatibility | per-domain owners plus message-job compatibility | Legacy `task`; future `rebate_task_outbox`, `credit_trade_task_outbox`, `award_dispatch_task_outbox` | Repository writes route through task-outbox ports; local adapters keep shared `task` fallback | Repo-ready; physical split not cut over | DBA proposed DDL application, backfill/drain plan, Ops job scheduling, Oncall rollback plan |
| auth | auth-service | none in shared business DB | Stable stateless service | Complete | Normal deployment controls |
| admin / config | admin-service | platform config outside split scope | Stable service | Complete | Normal deployment controls |
| chatbot | chatbot-service | none in split scope | Stable service | Complete | Normal deployment controls |
| query / search | activity-service or strategy-service, deferred | Elasticsearch projection/index | Ownership decision deferred to post-cutover cleanup | Not a Phase 8 blocker | Projection ownership decision and ES ops plan |

## Remaining External Gates

- DBA review and application of proposed DDL under `docs/sql/proposed-*.sql`.
- Per-service DB users/grants and secret rollout.
- Nacos/Dubbo provider verification in staging.
- MQ and XXL-Job operational registration where a cutover enables a new job path.
- Canary flag enablement, rollback rehearsal, and evidence capture.
- DBA, Ops, Engineering, Oncall, and Product approval where user-visible draw behavior changes.
- Staging and production evidence fields in
  `docs/evidence/phase-8-staging-cutover-evidence-template.md`,
  `docs/evidence/phase-8-production-cutover-evidence-template.md`, and
  `docs/evidence/phase-8-go-no-go-checklist.md`.
- Staging missing-evidence register in
  `docs/evidence/phase-8-staging-evidence-intake-checklist.md`; every row
  remains EXTERNAL-GATED until real staging references are attached.

## Next Executable Repo-Only Batches

1. External evidence intake: keep `docs/microservices-phase-8-external-evidence-intake.md` current while every missing item remains `EXTERNAL-GATED`.
2. Staging/prod cutover evidence: attach real DBA/Ops/Engineering/Oncall/Product references only after external windows run.
3. 7-day stable legacy-provider disable: propose environment-level disables only after real cutover evidence and a clean 7-day oncall window.
4. 30-day obsolete-path removal: propose repository cleanup only after the 30-day gate in `docs/microservices-legacy-cleanup-inventory.md`.
5. Keep `scripts/validate-microservices-split-all-gates.sh` in CI as the single repo-only split gate.

## Cross-Links

- Master plan: `docs/microservices-decomposition-master-plan.md`
- DAO ownership matrix: `docs/microservices-dao-ownership.md`
- Phase 8 runbook: `docs/microservices-phase-8-cutover-runbook.md`
- Phase 8 external evidence intake: `docs/microservices-phase-8-external-evidence-intake.md`
- Phase 8 staging evidence intake checklist: `docs/evidence/phase-8-staging-evidence-intake-checklist.md`
- Phase 8 staging evidence template: `docs/evidence/phase-8-staging-cutover-evidence-template.md`
- Phase 8 production evidence template: `docs/evidence/phase-8-production-cutover-evidence-template.md`
- Phase 8 GO/NO-GO checklist: `docs/evidence/phase-8-go-no-go-checklist.md`
- Legacy cleanup inventory: `docs/microservices-legacy-cleanup-inventory.md`
- Aggregate repo-only gate: `scripts/validate-microservices-split-all-gates.sh`
- Service module ownership gate: `scripts/validate-microservices-service-module-ownership.sh`
- Production flag matrix gate: `scripts/validate-microservices-production-flag-matrix.sh`
- External evidence intake gate: `scripts/validate-microservices-phase-8-external-evidence-intake.sh`
- Cutover evidence execution pack gate: `scripts/validate-microservices-phase-8-cutover-evidence-pack.sh`
- Staging evidence intake prep gate: `scripts/validate-microservices-phase-8-staging-evidence-intake.sh`
- Legacy cleanup readiness gate: `scripts/validate-microservices-legacy-cleanup-readiness.sh`
- Post-cutover cleanup gate: `scripts/validate-microservices-post-cutover-cleanup-gates.sh`
- Current readiness tag: `phase-7-complete-phase-8-readiness`
- Previous readiness tag: `phase-8-cutover-readiness-pack`
- Current evidence pack tag target: `phase-8-cutover-evidence-execution-pack`
- Current staging intake prep tag target: `phase-8-staging-evidence-intake-prep`
