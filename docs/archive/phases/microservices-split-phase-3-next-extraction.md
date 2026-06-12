> **Archived (2026-06-12):** Phase 1-7 historical implementation record. See `docs/MICROSERVICES.md` for current status.

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

## 9. Code-Level Step Implemented (Batch 1: rebate-service module boundary)

This batch adds `big-market-rebate-service` as a Maven module:

- `RebateServiceApplication` scans only `com.dyx.market.rebate`, `com.dyx.market.domain.rebate`, and shared infrastructure.
- `com.dyx.market.rebate.provider.RebateServiceRPC` implements `IRebateService` and delegates to `IBehaviorRebateService`.
- The module includes only rebate-required mapper XMLs: `daily_behavior_rebate_mapper.xml`, `user_behavior_rebate_order_mapper.xml`, and `task_mapper.xml`.
- The module does not depend on `big-market-trigger`, does not scan `trigger.http`, `trigger.listener`, `trigger.job`, or `trigger.rpc`, and does not change any caller.
- No Docker service, gateway route, Nacos traffic, DB connection, MQ execution, or dangerous flag enablement is introduced.

## 10. Code-Level Step Implemented (Batch 2: rebate adapter boundary)

This batch introduces an HTTP caller isolation boundary between `RaffleActivityController` and the rebate domain:

### New adapter interface (`big-market-trigger`)

`com.dyx.market.trigger.adapter.IRebateOrderAdapter` — single method `createOrder(BehaviorEntity)`.

### Local adapter (`big-market-trigger`)

`com.dyx.market.trigger.adapter.LocalRebateOrderAdapter`:
- Annotated `@ConditionalOnMissingBean(IRebateOrderAdapter.class)`.
- Delegates directly to `IBehaviorRebateService.createOrder(...)`.
- Active by default in all launchers that do not provide a remote adapter bean.
- Preserves all existing business semantics, idempotency key (`userId + SIGN + outBusinessNo = date`), and response behavior.

### Remote-capable adapter (`big-market-market-service`)

`com.dyx.market.market.config.RebateRemoteCreateOrderAdapter`:
- Annotated `@Component` — overrides the local adapter in market-service (same pattern as `AccountRemoteCreditWriteAdapter`).
- Guarded by `rebate.service.remote-create-order.enabled` (default `false`, env `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED`).
- Supplies the existing `IRebateService` credential contract with `rebate.service.remote-create-order.app-id` (default `chatgpt-data`) and resolves `appToken` from `appTokenMap`.
- When flag=false: falls through to `IBehaviorRebateService.createOrder` (unchanged behavior).
- When flag=true: calls `IRebateService.rebate(...)` via `@DubboReference(version="1.0", check=false)`.
- On remote failure: logs the error and falls back to local domain service.
- Returns empty list on remote success (`IRebateService.rebate` returns `Boolean`; callers only log the list size).

### Controller wiring (`RaffleActivityController`)

`calendarSignRebate` now injects `IRebateOrderAdapter rebateOrderAdapter` and calls `rebateOrderAdapter.createOrder(behaviorEntity)` instead of `behaviorRebateService.createOrder(...)` directly.
`isCalendarSignRebate` continues to use `IBehaviorRebateService.queryOrderByOutBusinessNo` directly (read-only, not in scope for this batch).

### Flag defaults

`rebate.service.remote-create-order.enabled=false` set in:
- `big-market-market-service/src/main/resources/application.yml`
- `docker-compose.yml` (`REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED:-false`)

### Remaining cutover blockers before enabling the remote flag

1. **Duplicate provider risk**: both `market-service` (legacy `trigger.rpc.RebateServiceRPC`) and `big-market-rebate-service` export `IRebateService` version 1.0 to Nacos. The legacy provider must be disabled or the Nacos service name must be disambiguated before enabling `rebate.service.remote-create-order.enabled=true`.
2. **Shared task outbox ownership**: rebate order creation writes to the generic `task` table also used by award and activity flows. Ownership must be clarified before the rebate domain is fully extracted.
3. **No DB/schema ownership enforcement**: `user_behavior_rebate_order` and `daily_behavior_rebate` tables are not yet behind a separate datasource or access control.
4. **RebateMessageConsumer unchanged**: MQ-driven rebate result processing remains in `message-job-service` and is not in scope for this batch.

## 11. Code-Level Step Implemented (Batch 3: legacy rebate RPC provider ownership gate)

### Problem: duplicate IRebateService provider

Both `market-service` (`trigger.rpc.RebateServiceRPC`) and `big-market-rebate-service`
(`rebate.provider.RebateServiceRPC`) export `IRebateService` version 1.0 to Nacos.
With two providers registered, Dubbo consumers can load-balance across both at random.
Enabling `rebate.service.remote-create-order.enabled=true` while the legacy provider is
still live would risk the remote adapter calling the legacy market-service provider instead
of the new rebate-service, defeating the extraction.

### Change: `@ConditionalOnProperty` on legacy `trigger.rpc.RebateServiceRPC`

`big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java`:
- Added `@ConditionalOnProperty(name = "rebate.legacy-rpc-provider.enabled", havingValue = "true", matchIfMissing = true)`.
- `matchIfMissing = true` preserves existing behavior: the legacy provider is active in all
  launchers unless `rebate.legacy-rpc-provider.enabled` is explicitly set to `false`.

### Explicit config defaults

`big-market-market-service/src/main/resources/application.yml`:
```yaml
rebate:
  legacy-rpc-provider:
    enabled: ${REBATE_LEGACY_RPC_PROVIDER_ENABLED:true}
```

`docker-compose.yml` (`big-market-market-service` env):
```
REBATE_LEGACY_RPC_PROVIDER_ENABLED=${REBATE_LEGACY_RPC_PROVIDER_ENABLED:-true}
```

### Future cutover order (no traffic enabled in this batch)

1. Deploy `big-market-rebate-service` to Nacos and verify it exports `IRebateService` version 1.0.
2. Set `REBATE_LEGACY_RPC_PROVIDER_ENABLED=false` on `big-market-market-service` and redeploy.
3. Verify only `big-market-rebate-service` is registered as the `IRebateService` version 1.0 provider in Nacos.
4. Run staging smoke tests on `RaffleActivityController.calendarSignRebate` with the local adapter still active.
5. Only then consider setting `REBATE_SERVICE_REMOTE_CREATE_ORDER_ENABLED=true`.

No traffic is enabled in this batch. The legacy provider default remains `true`.

## 12. Validation

Run:

```bash
bash scripts/validate-microservices-phase-3-next-extraction.sh
bash scripts/validate-microservices-phase-3-rebate-adapter.sh
bash scripts/validate-microservices-phase-3-rebate-provider-ownership.sh
bash scripts/validate-microservices-phase-3-rebate-read-adapter.sh
bash scripts/validate-microservices-phase-3-rebate-dependency-narrowing.sh
bash scripts/validate-microservices-phase-3-rebate-cutover-readiness.sh
```

`validate-microservices-phase-3-next-extraction.sh` checks Maven wiring, expected files, forbidden dependencies, package scans, mapper presence, provider contract, dangerous flag defaults, generated evidence status, and this document.

`validate-microservices-phase-3-rebate-adapter.sh` checks the adapter boundary: interface, local adapter, controller wiring, remote adapter, flag defaults, and cutover blocker documentation.

`validate-microservices-phase-3-rebate-provider-ownership.sh` checks the legacy provider ownership gate: `@ConditionalOnProperty` presence, correct property name, `matchIfMissing=true`, config defaults, docker-compose env wiring, remote-create-order still false, and docs mention of duplicate provider risk and cutover order.

`validate-microservices-phase-3-rebate-read-adapter.sh` checks the read adapter boundary: `IRebateReadAdapter`, `LocalRebateReadAdapter`, `RebateRemoteReadAdapter`, `IRebateService.isCalendarSignRebate`, `RebateOrderQueryRequestDTO`, both RPC provider implementations, controller wiring (adapter injected, direct domain call removed), flag defaults, and remaining blocker documentation.

`validate-microservices-phase-3-rebate-dependency-narrowing.sh` (Phase 3-C) checks the structural dependency narrowing audit: module wiring, forbidden scan packages, forbidden provider imports, adapter gating, flag safety, mapper allow-list, and generated evidence tracking.

`validate-microservices-phase-3-rebate-cutover-readiness.sh` (Phase 3-E) is a dry-run only cutover rehearsal: verifies provider existence, both methods implemented, legacy gate in place with default true, both remote adapters defaulting false, safety gate rejects any live flag deviation, and outbox ownership doc present. Prints the ordered cutover steps but enables nothing.

## 14. Code-Level Step Implemented (Batch 5: Phase 3-C/D/E — dependency narrowing, outbox ownership, cutover rehearsal)

### Phase 3-C — Rebate dependency narrowing audit

`scripts/validate-microservices-phase-3-rebate-dependency-narrowing.sh` provides a deterministic
repo-only structural audit of the rebate-service boundary. Checks verified:

- `big-market-rebate-service` module exists and is wired in root `pom.xml`.
- `big-market-rebate-service` does not declare `big-market-trigger` as a dependency.
- `RebateServiceApplication` does not scan `trigger.http`, `trigger.listener`, `trigger.job`, or `message.job`.
- `RebateServiceApplication` scans only `com.dyx.market.rebate` and `com.dyx.market.domain.rebate` (plus shared infrastructure).
- Rebate provider does not import `activity`, `strategy`, `award`, `account`, `credit`, `fulfillment`, `auth`, `admin`, or `chatbot` domain packages.
- `RaffleActivityController` wires `IRebateOrderAdapter` for the write path and `IRebateReadAdapter` for the read path; no direct `IBehaviorRebateService` import.
- Both remote adapters have `@DubboReference(check=false)` and default false.
- Legacy provider is gated by `rebate.legacy-rpc-provider.enabled` with `matchIfMissing=true`.
- All three rebate flags are correctly wired in `docker-compose.yml` and `application.yml`.
- No dangerous Phase 2/3 remote flag is hardcoded true.
- Rebate mapper XMLs are limited to `daily_behavior_rebate`, `user_behavior_rebate_order`, and `task` (accepted coupling).
- No forbidden mapper XMLs from other bounded contexts are present in rebate-service.
- `docs/evidence/generated` is not tracked.

### Phase 3-D — Rebate task / outbox ownership decision

`docs/microservices-split-phase-3-rebate-outbox-ownership.md` documents:

- Current rebate write path from HTTP controller through adapter to `BehaviorRebateRepository`.
- Tables logically owned by rebate-service: `daily_behavior_rebate`, `user_behavior_rebate_order`.
- Why `task` remains a shared coupling point and what that means for rebate extraction.
- **Decision: Option A** — keep shared `task` table with explicit ownership rules for Phase 3. Rationale: low traffic volume, no DDL scope in this batch, `credit_award_task` precedent already established.
- **Proposed DDL (not applied): Option B** — `rebate_task_outbox_{000..003}` for Phase 7-C, clearly marked as proposed-only.
- `RebateMessageConsumer` and `SendMessageTaskJob` ownership concerns.
- Explicit list of conditions that must be met before rebate-service is independently data-owned.

### Phase 3-E — Rebate dry-run cutover rehearsal

`scripts/validate-microservices-phase-3-rebate-cutover-readiness.sh` is a repo-only dry-run script:

- Verifies rebate-service module and provider exist.
- Verifies `IRebateService` exposes both `rebate(...)` and `isCalendarSignRebate(...)`.
- Verifies both legacy and rebate-service providers implement both methods.
- Verifies legacy provider gate is present with `matchIfMissing=true` (can be disabled, default remains true).
- Verifies both remote adapters have `@DubboReference(check=false)` and default false.
- **Safety gate:** fails if any rebate remote flag deviates from safe defaults in any config file.
- Verifies outbox ownership decision doc is present.
- On pass: prints the six-step ordered cutover procedure (deploy → verify → disable legacy → enable read → enable create-order → monitor/rollback) without enabling anything.

### Remaining blockers before actual rebate-service traffic cutover

1. **Staging provider verification** (external): `big-market-rebate-service` must be deployed to staging Nacos and both `rebate(...)` and `isCalendarSignRebate(...)` RPCs verified end-to-end before any remote flag is enabled.
2. **Legacy provider disablement**: `REBATE_LEGACY_RPC_PROVIDER_ENABLED=false` must be set on `big-market-market-service` before remote flags are enabled, to avoid Dubbo load-balancing across the legacy and new providers.
3. **Shared task outbox coupling**: `BehaviorRebateRepository` still writes to the generic `task` table; this remains an accepted coupling until Phase 7-C introduces `rebate_task_outbox`.
4. **RebateMessageConsumer ownership**: MQ-driven rebate result processing remains in `message-job-service`; consumer ownership decision is Phase 7-B/8-C work.
5. **Datasource/table ownership enforcement**: `user_behavior_rebate_order` and `daily_behavior_rebate` are not yet behind a separate DB user or schema; this is Phase 7-E/F work.
6. **Phase 8 approval gate**: actual traffic cutover requires DBA + Ops + Engineering + Oncall sign-off as described in `docs/microservices-decomposition-master-plan.md` §4.6.

## 13. Code-Level Step Implemented (Batch 4: rebate read adapter boundary — Phase 3-A/B)

### Motivation

`RaffleActivityController.isCalendarSignRebate` still directly called `IBehaviorRebateService.queryOrderByOutBusinessNo`.
This was the last direct rebate domain read dependency in the market-service HTTP layer. Removing it completes
the rebate read/write adapter pattern and makes the controller independent of the rebate domain at the source level.

### New adapter interface (`big-market-trigger`)

`com.dyx.market.trigger.adapter.IRebateReadAdapter` — single method `isCalendarSignRebate(String userId, String outBusinessNo)`.

### Local adapter (`big-market-trigger`)

`com.dyx.market.trigger.adapter.LocalRebateReadAdapter`:
- Annotated `@ConditionalOnMissingBean(IRebateReadAdapter.class)`.
- Delegates to `IBehaviorRebateService.queryOrderByOutBusinessNo(...)` and returns `!list.isEmpty()`.
- Active by default in all launchers that do not provide a remote adapter bean.
- Preserves all existing business semantics exactly.

### Read RPC contract extension (`big-market-api`)

`IRebateService` extended with:
```java
Response<Boolean> isCalendarSignRebate(Request<RebateOrderQueryRequestDTO> request);
```

New DTO `RebateOrderQueryRequestDTO` (fields: `userId`, `outBusinessNo`; `Serializable`).

### Both RPC providers updated

- `big-market-trigger/trigger/rpc/RebateServiceRPC` (legacy, ownership-gated) — implements `isCalendarSignRebate`; validates null request, blank fields, appId/appToken; delegates to `IBehaviorRebateService.queryOrderByOutBusinessNo`; returns `SUCCESS+true` when order exists, `SUCCESS+false` when not.
- `big-market-rebate-service/rebate/provider/RebateServiceRPC` (dark-launch) — same implementation.

### Remote read adapter (`big-market-market-service`)

`com.dyx.market.market.config.RebateRemoteReadAdapter`:
- `@Component` — overrides `LocalRebateReadAdapter` in market-service (same pattern as `RebateRemoteCreateOrderAdapter`).
- Guarded by `rebate.service.remote-read.enabled` (default `false`, env `REBATE_SERVICE_REMOTE_READ_ENABLED`).
- Uses `rebate.service.remote-read.app-id` (default `chatgpt-data`) and resolves `appToken` from `appTokenMap`.
- `@DubboReference(version="1.0", check=false)`.
- flag=false: local `IBehaviorRebateService.queryOrderByOutBusinessNo` fallback.
- flag=true + remote success: returns `Boolean` from `IRebateService.isCalendarSignRebate`.
- remote failure: logs and falls back to local.

### Controller wiring

`RaffleActivityController.isCalendarSignRebate`:
- Now injects `IRebateReadAdapter rebateReadAdapter`.
- Calls `rebateReadAdapter.isCalendarSignRebate(userId, LocalDate.now().format(DATE_FORMAT_DAY))`.
- Direct `IBehaviorRebateService.queryOrderByOutBusinessNo` call removed.
- `IBehaviorRebateService` field and import removed from controller (no longer needed).
- `calendarSignRebate` write path unchanged.
- Response semantics preserved exactly.

### Flag defaults

`rebate.service.remote-read.enabled=false` set in:
- `big-market-market-service/src/main/resources/application.yml`
- `docker-compose.yml` (`REBATE_SERVICE_REMOTE_READ_ENABLED:-false`)

### Remaining cutover blockers before enabling either remote rebate flag

1. **Duplicate provider risk**: both `market-service` (legacy `trigger.rpc.RebateServiceRPC`) and `big-market-rebate-service` export `IRebateService` version 1.0. The legacy provider must be disabled (`REBATE_LEGACY_RPC_PROVIDER_ENABLED=false`) before enabling either remote flag.
2. **Shared task outbox ownership**: rebate order creation writes to the generic `task` table also used by award and activity flows.
3. **RebateMessageConsumer ownership**: MQ-driven rebate result processing remains in `message-job-service`.
4. **Staging provider verification**: `big-market-rebate-service` must be deployed to staging and both `rebate(...)` and `isCalendarSignRebate(...)` RPCs verified before any remote flag is enabled.
5. **Datasource/table ownership enforcement**: `user_behavior_rebate_order` and `daily_behavior_rebate` are not yet behind a separate datasource.
