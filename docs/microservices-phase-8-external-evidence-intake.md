# Phase 8 External Evidence Intake Index

Last revised: 2026-06-11.

Status: repo-only intake scaffold. All missing DBA, Ops, Engineering, Oncall,
and Product evidence is EXTERNAL-GATED. This index does not prove staging or
production cutover completion and does not authorize DDL, traffic, outbox, or
legacy cleanup changes.

## Intake Rules

- Keep every missing evidence item marked `EXTERNAL-GATED`.
- Attach real evidence only after the owning team produces it from a staging or
  production window.
- Do not set remote, outbox, cutover, or cleanup flags true from this document.
- Do not disable legacy providers or remove fallback paths until the 7-day and
  30-day gates in `docs/microservices-legacy-cleanup-inventory.md` are satisfied.
- Validators consume this document only as a readiness index. Green validators
  mean placeholders and links are present, not that external cutover happened.

## Cutover Evidence Pack Mapping

The execution evidence templates are:

- Staging template: `docs/evidence/phase-8-staging-cutover-evidence-template.md`.
- Production template: `docs/evidence/phase-8-production-cutover-evidence-template.md`.
- GO/NO-GO checklist: `docs/evidence/phase-8-go-no-go-checklist.md`.
- Pack validator: `scripts/validate-microservices-phase-8-cutover-evidence-pack.sh`.

Each external gate below must be satisfied by replacing the matching
EXTERNAL-GATED field in a later evidence batch. These mappings do not mark any
gate complete.

| External gate group | Staging evidence fields | Production evidence fields | GO/NO-GO fields |
|---------------------|-------------------------|----------------------------|-----------------|
| DBA DDL and grants | STG-1.1, STG-1.2, STG-1.3, STG-1.4, STG-1.5, STG-1.6, STG-1.7 | PROD-1.1, PROD-1.2, PROD-1.3, PROD-1.4, PROD-1.5, PROD-1.6, PROD-1.7 | GNG-1, GNG-7, GNG-D4 |
| Ops deploy, provider discovery, jobs, MQ, config | STG-2.1, STG-2.2, STG-2.3, STG-2.4, STG-2.5, STG-2.6, STG-2.7, STG-2.8 | PROD-2.1, PROD-2.2, PROD-2.3, PROD-2.4, PROD-2.5, PROD-2.6, PROD-2.7, PROD-2.8 | GNG-2, GNG-8, GNG-D5 |
| Engineering flow validation, canary, rollback | STG-3.1, STG-3.2, STG-3.3, STG-3.4, STG-3.5, STG-3.6, STG-3.7 | PROD-3.1, PROD-3.2, PROD-3.3, PROD-3.4, PROD-3.5, PROD-3.6, PROD-3.7 | GNG-3, GNG-9, GNG-D6 |
| Oncall metrics, logs, observations | STG-4.1, STG-4.2, STG-4.3, STG-4.4, STG-4.5 | PROD-4.1, PROD-4.2, PROD-4.3, PROD-4.4, PROD-4.5 | GNG-4, GNG-10, GNG-D7 |
| Product approval or exemption | STG-5.1, STG-5.2, STG-5.3, STG-5.4, STG-5.5 | PROD-5.1, PROD-5.2, PROD-5.3, PROD-5.4, PROD-5.5 | GNG-5, GNG-11, GNG-D8 |
| Staging final decision | STG-6.1, STG-6.2, STG-6.3, STG-6.4, STG-6.5, STG-6.6 | PROD-0.2 must link this before production evidence is accepted | GNG-6, GNG-D9 |
| Production final decision and stability clock | EXTERNAL-GATED until production batch | PROD-6.1, PROD-6.2, PROD-6.3, PROD-6.4, PROD-6.5, PROD-6.6, PROD-6.7 | GNG-12, GNG-D9, GNG-D10 |

## DBA Gates

| External gate | Required evidence | Owning team | Related service | Related proposed DDL | Related flags | Validator or runbook that consumes it | Cleanup eligibility unlocked |
|---------------|-------------------|-------------|-----------------|----------------------|---------------|---------------------------------------|------------------------------|
| DBA-account-credit-outbox-DDL | EXTERNAL-GATED: reviewed change ticket, applied schema diff, table/index verification for `credit_award_task` shards, rollback note | DBA | account-service, fulfillment-service, message-job-service | `docs/sql/proposed-credit-award-task-outbox.sql` | `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` until approved | `scripts/validate-production-ddl.sh`, `docs/microservices-phase-8-cutover-runbook.md` | After staging/prod evidence plus 7-day stability: credit-award local write fallback can be considered for disablement; after 30 days: obsolete mapper/grant cleanup can be proposed |
| DBA-quota-ledger-DDL | EXTERNAL-GATED: reviewed change ticket, applied schema diff, sharded ledger table verification, idempotency key verification | DBA | account-service, market-service | `docs/sql/proposed-quota-decrement-ledger.sql` | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED=false` until approved | `scripts/validate-quota-decrement-b12.sh`, `scripts/validate-quota-decrement-b13.sh`, `scripts/validate-quota-decrement-b14.sh`, runbook | After 7 days stable: local quota decrement fallback can be reviewed; after 30 days: obsolete quota compatibility grants can be proposed |
| DBA-rebate-outbox-DDL | EXTERNAL-GATED: reviewed change ticket, applied schema diff, `rebate_task_outbox_000..003` verification, backfill/drain plan | DBA | rebate-service, message-job-service | `docs/sql/proposed-rebate-task-outbox.sql` | `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=false`, `REBATE_SERVICE_REMOTE_READ_ENABLED=false` until approved | `scripts/validate-microservices-phase-3-rebate-cutover-readiness.sh`, runbook | After 7 days stable: legacy rebate provider disablement can be proposed; after 30 days: shared `task` fallback removal for rebate can be proposed |
| DBA-credit-trade-outbox-DDL | EXTERNAL-GATED: reviewed change ticket, applied schema diff, `credit_trade_task_outbox_000..003` verification, drain plan | DBA | account-service, message-job-service | `docs/sql/proposed-credit-trade-task-outbox.sql` | account credit/trade outbox flags remain default false | `scripts/validate-microservices-phase-7-task-outbox-proposed-ddl.sh`, runbook | After 30 days stable: generic `task` fallback for credit trade can be proposed for removal |
| DBA-award-dispatch-outbox-DDL | EXTERNAL-GATED: reviewed change ticket, applied schema diff, `award_dispatch_task_outbox_000..003` verification, drain plan | DBA | fulfillment-service, message-job-service | `docs/sql/proposed-award-dispatch-task-outbox.sql` | `ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false` until approved | `scripts/validate-microservices-phase-7-task-outbox-proposed-ddl.sh`, runbook | After 30 days stable: generic `task` fallback for award dispatch can be proposed for removal |

## Ops Gates

| External gate | Required evidence | Owning team | Related service | Related proposed DDL | Related flags | Validator or runbook that consumes it | Cleanup eligibility unlocked |
|---------------|-------------------|-------------|-----------------|----------------------|---------------|---------------------------------------|------------------------------|
| Ops-account-deploy-and-secrets | EXTERNAL-GATED: deployment version, Nacos/Dubbo provider listing, DB secret rollout, config diff, rollback command | Ops | account-service, market-service, message-job-service | credit award outbox and quota ledger DDL where applicable | account remote/outbox flags remain false until canary | Phase 8 runbook and production flag matrix | Starts 7-day clock only after Engineering and Oncall signoff also exist |
| Ops-fulfillment-deploy-and-jobs | EXTERNAL-GATED: deployment version, provider listing, send_award/dispatch job registration, config diff, rollback command | Ops | fulfillment-service, message-job-service | credit award and award dispatch outbox DDL where applicable | `ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false`, `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=false` until canary | Phase 8 runbook and B23 validators | Starts 7-day clock only after successful fulfillment canary evidence |
| Ops-rebate-deploy-and-provider-switch | EXTERNAL-GATED: deployment version, provider listing proving duplicate-provider risk handled, config diff, rollback command | Ops | rebate-service, market-service | rebate outbox DDL | rebate remote flags false; `REBATE_LEGACY_RPC_PROVIDER_ENABLED=true` until approved disablement | `scripts/validate-microservices-phase-3-rebate-cutover-readiness.sh`, runbook | Legacy rebate provider can be disabled only after 7-day stable gate |
| Ops-strategy-deploy-and-provider-switch | EXTERNAL-GATED: deployment version, provider listing, read-only routing config, rollback command | Ops | strategy-service, market-service | none | `STRATEGY_SERVICE_REMOTE_READ_ENABLED=false`, `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED=true` until approved | Phase 8 runbook and strategy validators | Legacy strategy provider can be disabled only after 7-day stable read parity |
| Ops-activity-deploy-and-jobs | EXTERNAL-GATED: deployment version, saga/outbox job registration, MQ binding evidence, rollback command | Ops | activity-service, market-service, message-job-service | activity draw outbox DDL when approved; not present as executable DDL in this repo | activity remote/draw/outbox flags remain false | Phase 8 runbook | No cleanup eligibility until Product, DBA, Engineering, and Oncall evidence also exist |

## Engineering Gates

| External gate | Required evidence | Owning team | Related service | Related proposed DDL | Related flags | Validator or runbook that consumes it | Cleanup eligibility unlocked |
|---------------|-------------------|-------------|-----------------|----------------------|---------------|---------------------------------------|------------------------------|
| Engineering-account-staging-and-prod-cutover | EXTERNAL-GATED: staging parity results, idempotency test results, canary metrics, rollback rehearsal, production diff | Engineering | account-service, market-service, message-job-service | credit award outbox, quota ledger | all account remote/outbox flags | B17/B18 validators, Phase 8 runbook | 7-day stable gate can start for account local fallbacks |
| Engineering-fulfillment-staging-and-prod-cutover | EXTERNAL-GATED: award write parity, duplicate dispatch check, credit drift check, rollback rehearsal, production diff | Engineering | fulfillment-service, message-job-service | credit award and award dispatch outbox DDL | fulfillment remote/outbox flags | B23-C/D/E validators, Phase 8 runbook | 7-day stable gate can start for fulfillment local dispatch fallback |
| Engineering-rebate-staging-and-prod-cutover | EXTERNAL-GATED: calendar sign write/read parity, duplicate order check, outbox publish check, rollback rehearsal | Engineering | rebate-service, market-service, message-job-service | rebate outbox DDL | rebate remote flags and legacy provider flag | Phase 3 rebate readiness validator, Phase 8 runbook | 7-day stable gate can start for rebate legacy provider disablement |
| Engineering-strategy-read-cutover | EXTERNAL-GATED: read parity for strategy/rule/award data, latency comparison, rollback rehearsal | Engineering | strategy-service, market-service | none | strategy remote read and legacy provider flags | Phase 4 validators, Phase 8 runbook | 7-day stable gate can start for strategy legacy provider disablement |
| Engineering-activity-draw-cutover | EXTERNAL-GATED: saga idempotency, draw latency, quota drift, award dispatch, replay, rollback rehearsal | Engineering | activity-service, market-service, account-service, fulfillment-service | activity draw outbox DDL when approved | activity draw/outbox flags; account/fulfillment dependent flags | Phase 5/7 validators, Phase 8 runbook | No cleanup until Product approval and 30-day stable evidence exist |

## Oncall Gates

| External gate | Required evidence | Owning team | Related service | Related proposed DDL | Related flags | Validator or runbook that consumes it | Cleanup eligibility unlocked |
|---------------|-------------------|-------------|-----------------|----------------------|---------------|---------------------------------------|------------------------------|
| Oncall-account-monitoring | EXTERNAL-GATED: dashboard links, alert thresholds, rollback owner, incident checklist, 7-day clean window summary | Oncall | account-service | credit award outbox, quota ledger | account remote/outbox flags | Phase 8 runbook | Required before account legacy fallback disablement |
| Oncall-fulfillment-monitoring | EXTERNAL-GATED: dashboard links, MQ lag alert thresholds, rollback owner, 7-day clean window summary | Oncall | fulfillment-service | credit award and award dispatch outbox DDL | fulfillment remote/outbox flags | Phase 8 runbook | Required before fulfillment fallback disablement |
| Oncall-rebate-monitoring | EXTERNAL-GATED: dashboard links, duplicate-order alert, MQ retry alert, 7-day clean window summary | Oncall | rebate-service | rebate outbox DDL | rebate remote and legacy provider flags | Phase 8 runbook | Required before rebate legacy provider disablement |
| Oncall-strategy-monitoring | EXTERNAL-GATED: dashboard links, read error/latency thresholds, rollback owner, 7-day clean window summary | Oncall | strategy-service | none | strategy remote and legacy provider flags | Phase 8 runbook | Required before strategy legacy provider disablement |
| Oncall-activity-monitoring | EXTERNAL-GATED: dashboard links, draw error/latency thresholds, quota drift alert, award dispatch alert, 7-day clean window summary | Oncall | activity-service | activity draw outbox DDL when approved | activity draw/outbox flags | Phase 8 runbook | Required before any activity legacy cleanup |

## Product Gates

| External gate | Required evidence | Owning team | Related service | Related proposed DDL | Related flags | Validator or runbook that consumes it | Cleanup eligibility unlocked |
|---------------|-------------------|-------------|-----------------|----------------------|---------------|---------------------------------------|------------------------------|
| Product-account-user-visible-credit | EXTERNAL-GATED: acceptance note for credit balance/order behavior if user-visible | Product | account-service | credit award outbox, quota ledger | account remote/outbox flags | Phase 8 runbook | Product-visible rollback acceptance required before 7-day cleanup clock starts |
| Product-fulfillment-award-delivery | EXTERNAL-GATED: acceptance note for award delivery behavior and user support plan | Product | fulfillment-service | credit award and award dispatch outbox DDL | fulfillment remote/outbox flags | Phase 8 runbook | Required before award fallback cleanup |
| Product-rebate-calendar-sign | EXTERNAL-GATED: acceptance note for calendar sign rebate behavior and support plan | Product | rebate-service | rebate outbox DDL | rebate remote flags | Phase 8 runbook | Required before rebate fallback cleanup when user-visible behavior changes |
| Product-strategy-read-only | EXTERNAL-GATED: explicit exemption or acceptance note because cutover is read-only and should not alter draw decisions | Product | strategy-service | none | strategy remote read flag | Phase 8 runbook | Required only if user-visible strategy read behavior changes |
| Product-activity-draw | EXTERNAL-GATED: explicit approval for draw orchestration cutover, cohort plan, customer support plan, rollback acceptance | Product | activity-service | activity draw outbox DDL when approved | activity draw/outbox flags | Phase 8 runbook | Required before activity cleanup eligibility |

## Service Cutover Evidence Placeholders

### account-service cutover evidence

Evidence status: EXTERNAL-GATED.

Required placeholders:
- DBA evidence: EXTERNAL-GATED.
- Ops evidence: EXTERNAL-GATED.
- Engineering evidence: EXTERNAL-GATED.
- Oncall evidence: EXTERNAL-GATED.
- Product evidence or exemption: EXTERNAL-GATED.
- 7-day stable evidence: EXTERNAL-GATED.
- 30-day removal evidence: EXTERNAL-GATED.

### fulfillment-service cutover evidence

Evidence status: EXTERNAL-GATED.

Required placeholders:
- DBA evidence: EXTERNAL-GATED.
- Ops evidence: EXTERNAL-GATED.
- Engineering evidence: EXTERNAL-GATED.
- Oncall evidence: EXTERNAL-GATED.
- Product evidence or exemption: EXTERNAL-GATED.
- 7-day stable evidence: EXTERNAL-GATED.
- 30-day removal evidence: EXTERNAL-GATED.

### rebate-service cutover evidence

Evidence status: EXTERNAL-GATED.

Required placeholders:
- DBA evidence: EXTERNAL-GATED.
- Ops evidence: EXTERNAL-GATED.
- Engineering evidence: EXTERNAL-GATED.
- Oncall evidence: EXTERNAL-GATED.
- Product evidence or exemption: EXTERNAL-GATED.
- 7-day stable evidence: EXTERNAL-GATED.
- 30-day removal evidence: EXTERNAL-GATED.

### strategy-service cutover evidence

Evidence status: EXTERNAL-GATED.

Required placeholders:
- DBA evidence or exemption: EXTERNAL-GATED.
- Ops evidence: EXTERNAL-GATED.
- Engineering evidence: EXTERNAL-GATED.
- Oncall evidence: EXTERNAL-GATED.
- Product evidence or exemption: EXTERNAL-GATED.
- 7-day stable evidence: EXTERNAL-GATED.
- 30-day removal evidence: EXTERNAL-GATED.

### activity-service cutover evidence

Evidence status: EXTERNAL-GATED.

Required placeholders:
- DBA evidence: EXTERNAL-GATED.
- Ops evidence: EXTERNAL-GATED.
- Engineering evidence: EXTERNAL-GATED.
- Oncall evidence: EXTERNAL-GATED.
- Product evidence: EXTERNAL-GATED.
- 7-day stable evidence: EXTERNAL-GATED.
- 30-day removal evidence: EXTERNAL-GATED.

## Cleanup Decision Order

1. External evidence intake: populate this index with DBA/Ops/Engineering/Oncall/Product evidence references.
2. Staging and production cutover evidence: keep remote/outbox flags default false in repo until real cutover batches justify environment overrides.
3. 7-day stable legacy-provider disable: only then propose disabling a legacy provider in the relevant environment.
4. 30-day obsolete-path removal: only then propose deleting compatibility code, mapper copies, local fallbacks, or generic task fallbacks.
