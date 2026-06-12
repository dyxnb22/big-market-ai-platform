# Phase 8 External Evidence Readiness Pack

Last revised: 2026-06-12.

Status: repo-only readiness artifact. Every evidence requirement below remains
EXTERNAL-GATED. No remote, outbox, or cutover flag defaults to `true` in this
repository. No DDL has been applied from this repository. No real staging or
production cutover has occurred.

This document is the authoritative readiness reference for
`scripts/validate-microservices-phase-8-external-evidence-readiness-pack.sh`.

## Purpose

This pack defines the exact evidence required from each external stakeholder
before any Phase 8 cutover flag can be enabled. It translates the cutover
conflict matrix and idempotency/rollback matrix into executable checklists
that DBA, Ops, Engineering, Oncall, and Product teams must complete.

Each checklist item is EXTERNAL-GATED until the owning team attaches real
staging or production evidence.

---

## 1. Stakeholder Checklists

### 1.1 DBA — DDL Verification

| ID | Evidence required | Proposed DDL | High-risk flow | Status |
|----|------------------|-------------|----------------|--------|
| DBA-1 | Apply `credit_award_task` outbox DDL to staging | `docs/sql/proposed-credit-award-task-outbox.sql` | Credit award outbox dispatch | EXTERNAL-GATED |
| DBA-2 | Verify `uq_award_order_id (user_id, award_order_id)` unique key on staging | `docs/sql/proposed-credit-award-task-outbox.sql` | Credit award outbox dispatch | EXTERNAL-GATED |
| DBA-3 | Apply `raffle_quota_decrement_ledger` DDL to staging | `docs/sql/proposed-quota-decrement-ledger.sql` | Quota decrement / rollback | EXTERNAL-GATED |
| DBA-4 | Verify `uq_user_activity_biz (user_id, activity_id, out_business_no)` unique key on staging | `docs/sql/proposed-quota-decrement-ledger.sql` | Quota decrement / rollback | EXTERNAL-GATED |
| DBA-5 | Apply `rebate_task_outbox` DDL to staging | `docs/sql/proposed-rebate-task-outbox.sql` | Rebate create/read | EXTERNAL-GATED |
| DBA-6 | Verify `uq_user_message_id (user_id, message_id)` unique key on staging for rebate outbox | `docs/sql/proposed-rebate-task-outbox.sql` | Rebate create/read | EXTERNAL-GATED |
| DBA-7 | Apply `credit_trade_task_outbox` DDL to staging | `docs/sql/proposed-credit-trade-task-outbox.sql` | Credit trade | EXTERNAL-GATED |
| DBA-8 | Verify `uq_user_message_id (user_id, message_id)` unique key on staging for credit trade outbox | `docs/sql/proposed-credit-trade-task-outbox.sql` | Credit trade | EXTERNAL-GATED |
| DBA-9 | Apply `award_dispatch_task_outbox` DDL to staging | `docs/sql/proposed-award-dispatch-task-outbox.sql` | Award fulfillment | EXTERNAL-GATED |
| DBA-10 | Verify `uq_user_message_id (user_id, message_id)` unique key on staging for award dispatch outbox | `docs/sql/proposed-award-dispatch-task-outbox.sql` | Award fulfillment | EXTERNAL-GATED |
| DBA-11 | Verify baseline unique keys intact on staging: `user_credit_order.uq_out_business_no`, `user_behavior_rebate_order.uq_biz_id`, `user_award_record.uq_order_id`, `raffle_activity_order.uq_out_business_no`, `task.uq_message_id` | N/A (baseline) | Credit trade, Rebate, Award fulfillment, SKU exchange, Shared task fallback | EXTERNAL-GATED |
| DBA-12 | Apply per-service MySQL users and restricted grants per `docs/archive/phases/microservices-phase-7-db-users-grants-plan.md` to staging | N/A (grants plan) | All flows | EXTERNAL-GATED |
| DBA-13 | Apply all DDL from DBA-1 through DBA-12 to production after staging GO | All proposed DDL files | All flows | EXTERNAL-GATED |
| DBA-14 | Provide rollback plan (DDL rollback or data isolation) for each DDL applied | All proposed DDL files | All flows | EXTERNAL-GATED |

Stakeholder signoff: DBA-Staging / DBA-Production — EXTERNAL-GATED.

### 1.2 Ops — Dubbo Provider/Consumer, XXL-Job, MQ Registration

| ID | Evidence required | Related service | High-risk flow | Status |
|----|------------------|----------------|----------------|--------|
| OPS-1 | Verify account-service Dubbo provider registered in staging Nacos | account-service | Quota decrement / rollback, Credit award outbox dispatch, Credit trade, SKU exchange | EXTERNAL-GATED |
| OPS-2 | Verify fulfillment-service Dubbo provider registered in staging Nacos | fulfillment-service | Award fulfillment | EXTERNAL-GATED |
| OPS-3 | Verify rebate-service Dubbo provider registered in staging Nacos; confirm no duplicate provider with legacy `RebateServiceRPC` | rebate-service | Rebate create/read | EXTERNAL-GATED |
| OPS-4 | Verify strategy-service Dubbo provider registered in staging Nacos; confirm no duplicate provider with legacy `RaffleStrategyServiceRPC` | strategy-service | Strategy read | EXTERNAL-GATED |
| OPS-5 | Register `DispatchCreditAwardTaskJob_DB1` and `DispatchCreditAwardTaskJob_DB2` in staging XXL-Job admin | message-job-service | Credit award outbox dispatch | EXTERNAL-GATED |
| OPS-6 | Register per-domain outbox dispatcher handlers for rebate, credit trade, and award dispatch in staging XXL-Job admin (if applicable) | message-job-service | Rebate create/read, Credit trade, Award fulfillment | EXTERNAL-GATED |
| OPS-7 | Verify MQ consumer bindings for `credit_adjust_success` and other Phase 8 topics in staging | message-job-service | Credit award outbox dispatch, Credit trade, SKU exchange | EXTERNAL-GATED |
| OPS-8 | Verify service configs (env vars, secrets, DB connection pools) for all dark-launch services in staging | All services | All flows | EXTERNAL-GATED |
| OPS-9 | Confirm all remote/outbox/cutover flags remain `false` in staging config until canary window | All services | All flows | EXTERNAL-GATED |
| OPS-10 | Rehearse rollback command: set all remote/outbox flags to `false` and verify service restart within SLA | All services | All flows | EXTERNAL-GATED |

Stakeholder signoff: Ops-Staging — EXTERNAL-GATED.

### 1.3 Engineering — Staging Canary

| ID | Evidence required | Flag(s) exercised | High-risk flow | Status |
|----|------------------|-------------------|----------------|--------|
| ENG-1 | Enable `account.service.remote-credit-write.enabled=true` on single staging instance; validate credit write parity, no duplicate orders, no credit drift | `account.service.remote-credit-write.enabled` | Credit trade | EXTERNAL-GATED |
| ENG-2 | Enable `account.service.remote-quota-write.enabled=true` on single staging instance; validate quota write parity, no duplicate allocations | `account.service.remote-quota-write.enabled` | SKU exchange | EXTERNAL-GATED |
| ENG-3 | Enable `account.service.remote-quota-decrement.enabled=true` on single staging instance; validate quota decrement idempotency, ledger row creation, no quota exhaustion drift | `account.service.remote-quota-decrement.enabled` | Quota decrement / rollback | EXTERNAL-GATED |
| ENG-4 | Enable `account.fulfillment.remote-award.enabled=true` on single staging instance; validate award dispatch, no missing/duplicate awards | `account.fulfillment.remote-award.enabled` | Award fulfillment | EXTERNAL-GATED |
| ENG-5 | Enable `account.award-credit-outbox.enabled=true` with `job.shared-task-fallback.credit-award-disabled=true` on single staging instance; validate outbox dispatch, no dual-dispatch, no duplicate credit | `account.award-credit-outbox.enabled`, `job.shared-task-fallback.credit-award-disabled` | Credit award outbox dispatch vs shared task fallback | EXTERNAL-GATED |
| ENG-6 | Enable `rebate.service.remote-create-order.enabled=true` (with `REBATE_LEGACY_RPC_PROVIDER_ENABLED=false`) on single staging instance; validate rebate create parity, no duplicate orders | `rebate.service.remote-create-order.enabled`, `REBATE_LEGACY_RPC_PROVIDER_ENABLED` | Rebate create/read | EXTERNAL-GATED |
| ENG-7 | Enable `rebate.service.remote-read.enabled=true` on single staging instance; validate read parity and latency | `rebate.service.remote-read.enabled` | Rebate create/read | EXTERNAL-GATED |
| ENG-8 | Enable `strategy.service.remote-read.enabled=true` (with `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED=false`) on single staging instance; validate read parity and latency | `strategy.service.remote-read.enabled`, `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED` | Strategy read | EXTERNAL-GATED |
| ENG-9 | Rollback rehearsal: set all enabled flags back to `false`, verify all flows revert to local path with no data corruption | All flags | All flows | EXTERNAL-GATED |
| ENG-10 | Production single-instance canary (after staging GO): repeat ENG-1 through ENG-9 on single production instance with monitoring | All flags | All flows | EXTERNAL-GATED |

Stakeholder signoff: Engineering-Staging / Engineering-Production — EXTERNAL-GATED.

### 1.4 Oncall — Dashboards and Alerts

| ID | Evidence required | Related high-risk flow | Status |
|----|------------------|----------------------|--------|
| ONC-1 | Quota integrity dashboard: error rate, latency, quota drift for account-service remote quota decrement path | Quota decrement / rollback | EXTERNAL-GATED |
| ONC-2 | Credit drift dashboard: credit balance reconciliation between local and remote credit write paths | Credit trade, Credit award outbox dispatch | EXTERNAL-GATED |
| ONC-3 | Award dispatch dashboard: duplicate/missing award detection, dispatch latency, MQ lag for award dispatch outbox | Award fulfillment | EXTERNAL-GATED |
| ONC-4 | Rebate dashboard: duplicate order detection, calendar sign rebate latency, rebate outbox MQ lag | Rebate create/read | EXTERNAL-GATED |
| ONC-5 | Strategy read dashboard: read error rate, latency P99, comparison local vs remote | Strategy read | EXTERNAL-GATED |
| ONC-6 | SKU exchange dashboard: payment failure rate, stock restoration rate, delivery compensation rate | SKU exchange | EXTERNAL-GATED |
| ONC-7 | Shared task fallback monitoring: `SendMessageTaskJob` success/fail/retry rate; per-domain outbox dispatch vs shared fallback comparison | Shared task fallback vs per-domain outbox | EXTERNAL-GATED |
| ONC-8 | Define alert thresholds for all dashboards above | All flows | EXTERNAL-GATED |
| ONC-9 | Assign rollback owner and incident commander for each high-risk flow | All flows | EXTERNAL-GATED |
| ONC-10 | Produce 7-day clean window summary for each cutover domain after canary | All flows | EXTERNAL-GATED |

Stakeholder signoff: Oncall-Staging / Oncall-Production — EXTERNAL-GATED.

### 1.5 Product — GO/NO-GO Signoff

| ID | Evidence required | Related high-risk flow | Status |
|----|------------------|----------------------|--------|
| PRD-1 | Acceptance note for credit balance/order behavior changes (user-visible) | Credit trade | EXTERNAL-GATED |
| PRD-2 | Acceptance note for award delivery behavior and customer support plan | Award fulfillment | EXTERNAL-GATED |
| PRD-3 | Acceptance note for calendar sign rebate behavior and support plan | Rebate create/read | EXTERNAL-GATED |
| PRD-4 | Explicit exemption or acceptance for strategy read-only cutover (should not alter draw decisions) | Strategy read | EXTERNAL-GATED |
| PRD-5 | Explicit approval for activity draw orchestration cutover, cohort plan, customer support plan, rollback acceptance | Shared task fallback vs per-domain outbox | EXTERNAL-GATED |
| PRD-6 | Aggregate GO/NO-GO decision for staging cutover window | All flows | EXTERNAL-GATED |
| PRD-7 | Aggregate GO/NO-GO decision for production cutover window | All flows | EXTERNAL-GATED |

Stakeholder signoff: Product-Staging / Product-Production — EXTERNAL-GATED.

---

## 2. High-Risk Flow → Evidence Mapping

Each high-risk flow maps to the checklist items above. No flow can proceed
to cutover until every mapped item has real evidence attached.

### 2.1 Quota Decrement / Rollback

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-3, DBA-4, DBA-11, DBA-12 | DBA | `raffle_quota_decrement_ledger` DDL applied; `uq_user_activity_biz` verified; baseline keys intact |
| Ops | OPS-1, OPS-8, OPS-9, OPS-10 | Ops | Account-service Dubbo provider registered; configs deployed; flags default false; rollback rehearsed |
| Engineering | ENG-3, ENG-9, ENG-10 | Engineering | Staging canary: remote quota decrement idempotency validated; rollback rehearsal passed |
| Oncall | ONC-1, ONC-8, ONC-9, ONC-10 | Oncall | Quota integrity dashboard; alert thresholds; rollback owner assigned; 7-day clean window |
| Product | PRD-6, PRD-7 | Product | Aggregate GO/NO-GO for staging and production |

Flag gating this flow: `account.service.remote-quota-decrement.enabled` (default `false`).

### 2.2 Credit Award Outbox Dispatch

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-1, DBA-2, DBA-11, DBA-12 | DBA | `credit_award_task` DDL applied; `uq_award_order_id` verified; baseline keys intact |
| Ops | OPS-1, OPS-5, OPS-7, OPS-8, OPS-9, OPS-10 | Ops | Account-service provider; `DispatchCreditAwardTaskJob_DB1/DB2` registered in XXL-Job; MQ consumers verified; flags default false |
| Engineering | ENG-5, ENG-9, ENG-10 | Engineering | Staging canary: outbox dispatch validated; shared-task-fallback disabled; no dual-dispatch; rollback rehearsal passed |
| Oncall | ONC-2, ONC-7, ONC-8, ONC-9, ONC-10 | Oncall | Credit drift dashboard; shared-task vs outbox comparison; 7-day clean window |
| Product | PRD-6, PRD-7 | Product | Aggregate GO/NO-GO |

Flags gating this flow: `account.award-credit-outbox.enabled` (default `false`), `job.shared-task-fallback.credit-award-disabled` (must be `true` when outbox enabled).

### 2.3 Award Fulfillment

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-9, DBA-10, DBA-11, DBA-12 | DBA | `award_dispatch_task_outbox` DDL applied; `uq_user_message_id` verified; baseline `user_award_record.uq_order_id` intact |
| Ops | OPS-2, OPS-6, OPS-7, OPS-8, OPS-9, OPS-10 | Ops | Fulfillment-service provider; outbox dispatcher registered; MQ consumers verified; flags default false |
| Engineering | ENG-4, ENG-9, ENG-10 | Engineering | Staging canary: award dispatch validated; no missing/duplicate awards; rollback rehearsal passed |
| Oncall | ONC-3, ONC-8, ONC-9, ONC-10 | Oncall | Award dispatch dashboard; MQ lag alerts; 7-day clean window |
| Product | PRD-2, PRD-6, PRD-7 | Product | Award delivery acceptance; aggregate GO/NO-GO |

Flag gating this flow: `account.fulfillment.remote-award.enabled` (default `false`).

### 2.4 Rebate Create / Read

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-5, DBA-6, DBA-11, DBA-12 | DBA | `rebate_task_outbox` DDL applied; `uq_user_message_id` verified; baseline `user_behavior_rebate_order.uq_biz_id` intact |
| Ops | OPS-3, OPS-6, OPS-8, OPS-9, OPS-10 | Ops | Rebate-service provider; no duplicate provider with legacy `RebateServiceRPC`; flags default false |
| Engineering | ENG-6, ENG-7, ENG-9, ENG-10 | Engineering | Staging canary: rebate create and read parity validated; dual-provider flag mutual exclusion verified; rollback rehearsal passed |
| Oncall | ONC-4, ONC-8, ONC-9, ONC-10 | Oncall | Rebate dashboard; duplicate-order alert; 7-day clean window |
| Product | PRD-3, PRD-6, PRD-7 | Product | Calendar sign rebate acceptance; aggregate GO/NO-GO |

Flags gating this flow: `rebate.service.remote-create-order.enabled` (default `false`), `rebate.service.remote-read.enabled` (default `false`), `REBATE_LEGACY_RPC_PROVIDER_ENABLED` (default `true`; must be disabled when remote create is enabled).

### 2.5 Credit Trade

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-7, DBA-8, DBA-11, DBA-12 | DBA | `credit_trade_task_outbox` DDL applied; `uq_user_message_id` verified; baseline `user_credit_order.uq_out_business_no` intact |
| Ops | OPS-1, OPS-7, OPS-8, OPS-9, OPS-10 | Ops | Account-service provider; MQ consumers for credit trade events; flags default false |
| Engineering | ENG-1, ENG-9, ENG-10 | Engineering | Staging canary: credit write parity validated; no credit drift; rollback rehearsal passed |
| Oncall | ONC-2, ONC-8, ONC-9, ONC-10 | Oncall | Credit drift dashboard; 7-day clean window |
| Product | PRD-1, PRD-6, PRD-7 | Product | Credit behavior acceptance; aggregate GO/NO-GO |

Flag gating this flow: `account.service.remote-credit-write.enabled` (default `false`).

### 2.6 SKU Exchange

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-3, DBA-4, DBA-11, DBA-12 | DBA | `raffle_quota_decrement_ledger` DDL applied; baseline `raffle_activity_order.uq_out_business_no` intact |
| Ops | OPS-1, OPS-7, OPS-8, OPS-9, OPS-10 | Ops | Account-service provider; MQ consumers for delivery compensation; flags default false |
| Engineering | ENG-2, ENG-9, ENG-10 | Engineering | Staging canary: quota write parity validated; SKU exchange idempotency with deterministic `outBusinessNo` validated; rollback rehearsal passed |
| Oncall | ONC-6, ONC-8, ONC-9, ONC-10 | Oncall | SKU exchange dashboard; payment failure and stock restoration monitoring; 7-day clean window |
| Product | PRD-6, PRD-7 | Product | Aggregate GO/NO-GO |

Flag gating this flow: `account.service.remote-quota-write.enabled` (default `false`).

### 2.7 Shared Task Fallback vs Per-Domain Outbox

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-1, DBA-2, DBA-5 through DBA-12 | DBA | All per-domain outbox DDL applied; all unique keys verified; baseline `task.uq_message_id` intact |
| Ops | OPS-5, OPS-6, OPS-7, OPS-8, OPS-9, OPS-10 | Ops | All per-domain dispatcher handlers registered in XXL-Job; MQ consumers verified; shared-fallback disabled flags set when outbox enabled |
| Engineering | ENG-5, ENG-9, ENG-10 | Engineering | Staging canary: per-domain outbox validated; shared-task-fallback disabled for credit-award; no dual-dispatch; rollback rehearsal passed |
| Oncall | ONC-7, ONC-8, ONC-9, ONC-10 | Oncall | Shared task vs outbox comparison dashboard; dual-dispatch alert; 7-day clean window for each domain |
| Product | PRD-5, PRD-6, PRD-7 | Product | Activity draw orchestration approval; aggregate GO/NO-GO |

Flags gating this flow: `account.award-credit-outbox.enabled` (default `false`), `job.shared-task-fallback.credit-award-disabled` (default `false`; must be `true` when outbox enabled).

### 2.8 Strategy Read

| Evidence class | Checklist IDs | Owning stakeholder | What must be verified |
|---------------|---------------|-------------------|----------------------|
| DBA | DBA-11, DBA-12 | DBA | Baseline strategy read tables and grants remain intact; no proposed DDL is required for this read-only cutover |
| Ops | OPS-4, OPS-8, OPS-9, OPS-10 | Ops | Strategy-service provider registered; no duplicate provider with legacy `RaffleStrategyServiceRPC`; configs deployed; flags default false |
| Engineering | ENG-8, ENG-9, ENG-10 | Engineering | Staging canary: strategy read parity and latency validated; dual-provider flag mutual exclusion verified; rollback rehearsal passed |
| Oncall | ONC-5, ONC-8, ONC-9, ONC-10 | Oncall | Strategy read dashboard; P99/error-rate alerts; rollback owner assigned; 7-day clean window |
| Product | PRD-4, PRD-6, PRD-7 | Product | Strategy read-only acceptance or exemption; aggregate GO/NO-GO |

Flags gating this flow: `strategy.service.remote-read.enabled` (default `false`), `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED` (default `true`; must be disabled when remote read is enabled).

---

## 3. Proposed DDL Coverage Map

Every proposed DDL file must be referenced in at least one stakeholder
checklist and at least one high-risk flow mapping.

| Proposed DDL file | DBA checklist | High-risk flow(s) | Unique key verified |
|-------------------|---------------|-------------------|---------------------|
| `docs/sql/proposed-credit-award-task-outbox.sql` | DBA-1, DBA-2 | Credit award outbox dispatch, Shared task fallback | `uq_award_order_id` |
| `docs/sql/proposed-quota-decrement-ledger.sql` | DBA-3, DBA-4 | Quota decrement / rollback, SKU exchange | `uq_user_activity_biz` |
| `docs/sql/proposed-rebate-task-outbox.sql` | DBA-5, DBA-6 | Rebate create/read, Shared task fallback | `uq_user_message_id` |
| `docs/sql/proposed-credit-trade-task-outbox.sql` | DBA-7, DBA-8 | Credit trade, Shared task fallback | `uq_user_message_id` |
| `docs/sql/proposed-award-dispatch-task-outbox.sql` | DBA-9, DBA-10 | Award fulfillment, Shared task fallback | `uq_user_message_id` |

---

## 4. Cutover Flag Default Safety

Every cutover flag in this repository defaults to `false`. This section
documents the current safe default and which stakeholder gate must clear
before the flag can be flipped to `true` in an environment override.

| Flag | Current default | Enables | Gated by |
|------|----------------|---------|----------|
| `account.service.remote-credit-write.enabled` | `false` | Remote credit write via Dubbo | DBA-7, DBA-8, OPS-1, ENG-1, ONC-2, PRD-1 |
| `account.service.remote-quota-write.enabled` | `false` | Remote quota write via Dubbo | DBA-3, DBA-4, OPS-1, ENG-2, ONC-6 |
| `account.service.remote-quota-decrement.enabled` | `false` | Remote quota decrement via Dubbo | DBA-3, DBA-4, OPS-1, ENG-3, ONC-1 |
| `account.fulfillment.remote-award.enabled` | `false` | Remote award dispatch via Dubbo | DBA-9, DBA-10, OPS-2, ENG-4, ONC-3, PRD-2 |
| `account.award-credit-outbox.enabled` | `false` | Per-domain credit award outbox dispatch | DBA-1, DBA-2, OPS-5, ENG-5, ONC-2, ONC-7 |
| `rebate.service.remote-create-order.enabled` | `false` | Remote rebate create via Dubbo | DBA-5, DBA-6, OPS-3, ENG-6, ONC-4, PRD-3 |
| `rebate.service.remote-read.enabled` | `false` | Remote rebate read via Dubbo | OPS-3, ENG-7, ONC-4 |
| `strategy.service.remote-read.enabled` | `false` | Remote strategy read via Dubbo | OPS-4, ENG-8, ONC-5, PRD-4 |
| `job.shared-task-fallback.credit-award-disabled` | `false` | Disables shared task fallback for credit-award (must be `true` when outbox enabled) | ENG-5, ONC-7 |

Legacy provider flags default to `true` and must remain `true` until their
remote counterpart is cut over and stable:

| Flag | Current default | Protects against | Disable gated by |
|------|----------------|------------------|-----------------|
| `REBATE_LEGACY_RPC_PROVIDER_ENABLED` | `true` | Dual-provider risk with rebate remote create | OPS-3, ENG-6, ONC-4, PRD-3, 7-day stable window |
| `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED` | `true` | Dual-provider risk with strategy remote read | OPS-4, ENG-8, ONC-5, PRD-4, 7-day stable window |

---

## 5. GO/NO-GO Decision Matrix

Before any Phase 8 cutover flag is enabled in staging or production, all
five stakeholders must explicitly sign off. No proxy, simulated evidence,
or local-learning evidence is accepted.

| Stakeholder | Staging GO criteria | Production GO criteria | Status |
|-------------|-------------------|----------------------|--------|
| DBA | DBA-1 through DBA-12 complete; all DDL verified; rollback plan filed | DBA-13, DBA-14 complete | EXTERNAL-GATED |
| Ops | OPS-1 through OPS-10 complete; all services registered; flags default false; rollback rehearsed | OPS-1 through OPS-10 repeated for production | EXTERNAL-GATED |
| Engineering | ENG-1 through ENG-9 complete; all canary flows validated; idempotency confirmed; rollback rehearsal passed | ENG-10 complete | EXTERNAL-GATED |
| Oncall | ONC-1 through ONC-9 complete; all dashboards live; alert thresholds defined; rollback owner assigned | ONC-10 complete (7-day clean window) | EXTERNAL-GATED |
| Product | PRD-1 through PRD-5 reviewed; explicit acceptance or exemption for each | PRD-6, PRD-7 complete (aggregate GO/NO-GO) | EXTERNAL-GATED |

**Final staging GO**: All five staging columns must be non-EXTERNAL-GATED.

**Final production GO**: All five production columns must be non-EXTERNAL-GATED
AND the staging GO must have been recorded.

---

## 6. Execution Order

1. **Pre-staging intake**: All stakeholders review this pack and confirm understanding.
2. **Staging cutover window**: DBA applies DDL → Ops registers services/jobs → Engineering runs canary → Oncall confirms dashboards → Product signs staging GO.
3. **Production cutover window** (after staging GO): Repeat for production with single-instance canary first.
4. **7-day stable clock**: After production GO, monitor for 7 clean days before disabling legacy providers.
5. **30-day cleanup clock**: After 7-day stable, wait 30 clean days before removing legacy code.

---

## 7. Cross-References

- Cutover conflict matrix: `docs/microservices-phase-8-cutover-conflict-matrix.md`
- Idempotency & rollback matrix: `docs/microservices-phase-8-idempotency-rollback-matrix.md`
- Legacy cleanup inventory: `docs/microservices-legacy-cleanup-inventory.md`
- External evidence intake: `docs/microservices-phase-8-external-evidence-intake.md`
- Cutover runbook: `docs/microservices-phase-8-cutover-runbook.md`
- Proposed DDL: `docs/sql/proposed-*.sql` (5 files)
- This pack's validator: `scripts/validate-microservices-phase-8-external-evidence-readiness-pack.sh`
- Aggregate gate: `scripts/validate-microservices-split-all-gates.sh`
