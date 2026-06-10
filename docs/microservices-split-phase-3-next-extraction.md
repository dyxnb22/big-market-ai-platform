# Phase 3 Next Extraction: Rebate-Service Boundary

## 1. Current Service and Module Shape

The repository now has these Spring Boot launchers:

| Launcher | Current ownership intent |
|----------|--------------------------|
| `big-market-gateway` | External route entry point |
| `big-market-auth-service` | JWT login and verification |
| `big-market-admin-service` | Runtime config management |
| `big-market-chatbot-service` | Chatbot API |
| `big-market-market-service` | Main HTTP API surface plus legacy shared Dubbo providers |
| `big-market-message-job-service` | MQ consumers and XXL-Job handlers |
| `big-market-account-service` | Dark-launch account credit and activity quota provider |
| `big-market-fulfillment-service` | Dark-launch award fulfillment provider |
| `big-market-rebate-service` | Phase 3 dark-launch behavior rebate provider |

Shared modules still carry most behavior:

| Module | Role | Decomposition concern |
|--------|------|-----------------------|
| `big-market-domain` | All domain contexts in one jar: activity, strategy, award, credit, rebate, task, auth | Service launchers can still scan or compile against unrelated domains |
| `big-market-infrastructure` | All DAO, repository, Redis, MQ, Elasticsearch, and gateway adapters | Table ownership is not yet enforced by module boundaries |
| `big-market-trigger` | HTTP controllers, MQ listeners, XXL-Job handlers, local adapters, legacy RPC provider | Trigger adapters mix market HTTP, jobs, consumers, and Dubbo providers |
| `big-market-api` | External Dubbo contracts and DTOs | Some contracts already exist, but ownership is not always matched to service launcher |
| `big-market-types` | Shared response codes, exceptions, constants | Acceptable shared kernel if kept small |

## 2. Remaining Monolith Coupling Points

- `big-market-market-service` still depends on `big-market-trigger` and scans `trigger.http` plus `trigger.rpc`, so the HTTP API process also exports the legacy `IRebateService` provider.
- `big-market-message-job-service` still scans all `com.dyx.market.domain` and all `com.dyx.market.infrastructure`, even though it should only own consumers/jobs.
- `big-market-account-service` scans all domain packages and all infrastructure packages, not only credit/quota packages.
- `big-market-fulfillment-service` narrows the domain scan to `domain.award`, but still scans the full infrastructure package.
- `big-market-infrastructure` has one DAO/repository jar for all tables, so service-owned persistence is logical, not enforced by Maven.
- `big-market-domain` contains direct cross-domain orchestration: `RaffleApplicationService` imports activity, strategy, and award services; award fulfillment still needs the account-credit outbox to avoid cross-service partial credit.

## 3. Direct Cross-Domain Dependencies

| Call site | Dependency | Concern |
|-----------|------------|---------|
| `RaffleApplicationService` | activity -> strategy -> award | Draw orchestration is still monolithic and will need an application-service boundary before strategy or activity extraction |
| `AwardRepository.saveGiveOutPrizesAggregate` | award writes `user_award_record`; default path also writes `user_credit_account` | Account/fulfillment split is blocked until outbox DDL and job validation are complete |
| `RebateMessageConsumer` | rebate MQ result -> account quota/credit adapters | Already mediated by account write adapters; flags remain false by default |
| `BehaviorRebateRepository.saveUserRebateRecord` | rebate order + generic `task` outbox | Rebate owns `user_behavior_rebate_order`, but still shares the generic `task` table and MQ publisher infrastructure |
| `RaffleActivityController.calendarSignRebate` | HTTP activity endpoint directly calls `IBehaviorRebateService` | Future rebate cutover should add a local/remote rebate adapter before routing this caller |

## 4. Database and Table Ownership Concerns

| Table family | Logical owner | Current state |
|--------------|---------------|---------------|
| `user_credit_account`, `user_credit_order` | account-service | Account RPC exists; some local paths remain behind false flags |
| `raffle_activity_account*`, `raffle_activity_order`, `raffle_quota_decrement_ledger` | account-service or activity/account subdomain | Remote quota-decrement remains disabled by default |
| `user_award_record`, `award`, `credit_award_task` | fulfillment-service | Fulfillment service dark-launched; traffic blocked on outbox validation |
| `user_behavior_rebate_order`, `daily_behavior_rebate` | rebate-service | New dark-launch service boundary added in Phase 3 |
| `task` | shared message outbox | Still used by award/rebate/task flows; eventual split needs owner-specific outbox tables or a message-outbox service boundary |
| `strategy*`, `rule_tree*` | strategy-service candidate | Still embedded in market-service/domain/infrastructure |

## 5. RPC and API Contract Gaps

- `IRebateService` already exists in `big-market-api`, which makes rebate the safest next extraction target.
- `IAccountCreditService`, `IAccountQuotaService`, and fulfillment `IAwardService` already exist, but their traffic cutovers are blocked by staging and DDL gates.
- There is no dedicated strategy-service API yet for draw, stock, rule-weight, or award-list reads.
- There is no activity-service API yet for SKU stage, activity account, and draw orchestration.
- Rebate has no local/remote adapter for HTTP callers yet; this batch deliberately avoids routing traffic.

## 6. Job Ownership Concerns

- `big-market-message-job-service` remains the owner of all MQ consumers and XXL-Job handlers.
- `DispatchCreditAwardTaskJob` remains in message-job-service through Phase 2.3; this batch does not move it.
- Rebate order creation writes to `task`, and existing `SendMessageTaskJob` will still publish failed/create tasks. Rebate-service does not host job handlers in this batch.
- Any future move of `task` polling must be a separate extraction because `task` is shared by multiple domains.

## 7. Candidate Bounded Contexts

| Candidate | Fit | Risk |
|-----------|-----|------|
| Rebate-service | Existing `IRebateService` contract; small domain; clear tables; low traffic blast radius; no Phase 2 dangerous flags required | HTTP sign-in caller still local; generic `task` table remains shared |
| Strategy-service | Clear rule/strategy tables and read-heavy APIs | Draw path is deeply coupled to activity and account participation counts |
| Activity-service | Central market workflow owner | Highest blast radius; draw and quota paths are still under active account-service extraction |
| Task/outbox-service | Cross-cutting outbox and message dispatch | Shared semantics differ between MQ publish and account-credit dispatch; high coordination risk |

## 8. Recommended Target

**Target: rebate-service dark-launch boundary.**

Why:

- Low blast radius: it adds a provider module and does not route callers or traffic.
- Clear domain boundary: `domain.rebate`, `daily_behavior_rebate`, and `user_behavior_rebate_order`.
- Existing API contract: `IRebateService` already lives in `big-market-api`.
- Consistent pattern: mirrors account-service and fulfillment-service dark-launch modules.
- Repo-only validation: module wiring, provider package, mapper resources, forbidden trigger dependency, and dangerous flag safety are statically checkable.

## 9. Code-Level Step Implemented

This batch adds `big-market-rebate-service` as a Maven module:

- `RebateServiceApplication` scans only `com.dyx.market.rebate`, `com.dyx.market.domain.rebate`, and shared infrastructure.
- `com.dyx.market.rebate.provider.RebateServiceRPC` implements `IRebateService` and delegates to `IBehaviorRebateService`.
- The module includes only rebate-required mapper XMLs: `daily_behavior_rebate_mapper.xml`, `user_behavior_rebate_order_mapper.xml`, and `task_mapper.xml`.
- The module does not depend on `big-market-trigger`, does not scan `trigger.http`, `trigger.listener`, `trigger.job`, or `trigger.rpc`, and does not change any caller.
- No Docker service, gateway route, Nacos traffic, DB connection, MQ execution, or dangerous flag enablement is introduced.

## 10. Validation

Run:

```bash
bash scripts/validate-microservices-phase-3-next-extraction.sh
```

The validator checks Maven wiring, expected files, forbidden dependencies, package scans, mapper presence, provider contract, dangerous flag defaults, generated evidence status, and this document.
