> **Archived (2026-06-12):** Phase 1-7 historical implementation record. See `docs/MICROSERVICES.md` for current status.

# Phase 4 Strategy-Service Read-First Extraction

## 1. Current Strategy Module / Package Ownership

The strategy bounded context lives entirely inside `big-market-domain` and
`big-market-infrastructure` — shared jars with no Maven enforcement of ownership:

| Package | Content |
|---------|---------|
| `com.dyx.market.domain.strategy.service.armory` | `IStrategyArmory`, `IStrategyDispatch`, `StrategyArmoryDispatch` — probability-table assembly and dispatch |
| `com.dyx.market.domain.strategy.service.raffle` | `DefaultRaffleStrategy` — draw-decision execution |
| `com.dyx.market.domain.strategy.service` | `IRaffleAward`, `IRaffleRule`, `IRaffleStrategy`, `IRaffleStock`, `AbstractRaffleStrategy` |
| `com.dyx.market.domain.strategy.service.rule` | Rule-chain and rule-tree composites (chain: `impl/`, tree: `impl/`, `factory/`) |
| `com.dyx.market.domain.strategy.model` | `entity/`, `valobj/`, `aggregate/` strategy model objects |
| `com.dyx.market.domain.strategy.repository` | `IStrategyRepository` — strategy persistence port |
| `com.dyx.market.infrastructure.adapter.repository` | `StrategyRepository` — implements `IStrategyRepository` |
| `com.dyx.market.infrastructure.dao` | `IStrategyDao`, `IStrategyAwardDao`, `IStrategyRuleDao` — MyBatis mappers |

Service interface summary:

| Interface | Read or Write | Notes |
|-----------|---------------|-------|
| `IRaffleAward.queryRaffleStrategyAwardList` | Read | Returns award list for a strategyId |
| `IRaffleAward.queryRaffleStrategyAwardListByActivityId` | Read | Returns award list via activity→strategy mapping |
| `IRaffleAward.queryOpenActivityStrategyAwardList` | Read | Award list for all open activities (stock job) |
| `IRaffleRule.queryAwardRuleLockCount` | Read | Lock count per rule-tree; used for award unlock display |
| `IRaffleRule.queryAwardRuleWeight` | Read | Rule-weight VO list by strategyId |
| `IRaffleRule.queryAwardRuleWeightByActivityId` | Read | Rule-weight VO list by activityId |
| `IStrategyArmory.assembleLotteryStrategy` | Write/setup | Loads probability table into Redis; triggered by admin or activity approval |
| `IStrategyArmory.assembleLotteryStrategyByActivityId` | Write/setup | Same via activityId |
| `IStrategyDispatch.getRandomAwardId` | Decision | Reads Redis probability table; step inside draw execution |
| `IStrategyDispatch.subtractionAwardStock` | Write | Decrements award stock in Redis |
| `IRaffleStrategy.performRaffle` | Write/decision | Full draw execution — rule-chain + rule-tree evaluation |

## 2. Strategy Tables and Mappers

| Table | Mapper interface | Logical owner |
|-------|-----------------|---------------|
| `strategy` | `IStrategyDao` | strategy-service |
| `strategy_award` | `IStrategyAwardDao` | strategy-service |
| `strategy_rule` | `IStrategyRuleDao` | strategy-service |
| `rule_tree` | `IRuleTreeDao` | strategy-service |
| `rule_tree_node` | `IRuleTreeNodeDao` | strategy-service |
| `rule_tree_node_line` | `IRuleTreeNodeLineDao` | strategy-service |

Mapper XMLs (all in `big-market-app/src/main/resources/mybatis/mapper/mysql/`):
`strategy_mapper.xml`, `strategy_award_mapper.xml`, `strategy_rule_mapper.xml`,
`rule_tree_mapper.xml`, `rule_tree_node_mapper.xml`, `rule_tree_node_line_mapper.xml`.

These tables are self-contained: no join to `raffle_activity*`, `user_award_record`,
or `user_behavior_rebate_order`. The `strategy_award` table cross-references
`raffle_activity` in one query (`queryOpenActivityStrategyAwardList`) used by
the stock-job path, which is NOT in scope for Phase 4 read extraction.

## 3. Read Paths from Controllers

### `RaffleStrategyController` (HTTP) + Legacy RPC Provider

| HTTP endpoint | Domain services called | Cross-domain? |
|---------------|------------------------|---------------|
| `strategyArmory` | `IStrategyArmory.assembleLotteryStrategy` | No; but it is a write/setup, excluded from Phase 4 read contract |
| `queryRaffleAwardListByToken` | `IAuthService` (auth), `IRaffleAward`, `IRaffleRule`, `IAccountReadAdapter` (activity/account) | Yes: IAuthService + account read adapter |
| `queryRaffleAwardList` | `IRaffleAward`, `IRaffleRule`, `IAccountReadAdapter.queryRaffleActivityAccountDayPartakeCount` | Yes: account read adapter for unlock status |
| `queryRaffleStrategyRuleWeight` | `IRaffleRule`, `IAccountReadAdapter.queryRaffleActivityAccountPartakeCount` | Yes: account read adapter for total use count |
| `randomRaffle` | `IRaffleStrategy.performRaffle` | No (within strategy), but this is draw execution — excluded |

**Cross-domain coupling summary for the two safe read paths:**
- `queryRaffleAwardList` and `queryRaffleStrategyRuleWeight` both enrich their
  responses with account-participation counts from `IAccountReadAdapter`.
- The strategy domain services (`IRaffleAward`, `IRaffleRule`) are pure reads
  with no cross-domain dependencies.
- The account enrichment (unlock status, use count) is presentation-layer logic;
  the strategy-service dark-launch scaffold defaults these to 0 until Phase 4-D
  introduces an account-participation port.

## 4. Draw Decision / Write / Orchestration Paths That Must NOT Move Yet

| Path | Why it must stay in market-service for now |
|------|-------------------------------------------|
| `IRaffleStrategy.performRaffle` | Core draw execution: invokes rule-chain, rule-tree, award decision, and stock decrement in sequence. Must remain co-located with `RaffleApplicationService` until Phase 5. |
| `IStrategyArmory.assembleLotteryStrategy` / `assembleLotteryStrategyByActivityId` | Writes probability table to Redis; triggered by admin or activity approval. Moving this requires an admin→strategy event bridge not in scope for Phase 4. |
| `IStrategyDispatch.subtractionAwardStock` | Mutates Redis stock counter; called inside draw path. |
| `IStrategyDispatch.getRandomAwardId` | Reads Redis probability table; tightly coupled to armory and draw in the same request. |
| `IRaffleStock` | Award stock scanning and decrement for the stock-clearance job; owned by message-job-service. |
| `strategyArmory` HTTP/RPC | Admin-triggered armory; included in `IRaffleStrategyService` but is a write operation. |
| `randomRaffle` HTTP/RPC | Arbitrary draw; included in `IRaffleStrategyService`; excluded from `IStrategyReadService`. |

## 5. Coupling with Activity / Draw

- `RaffleApplicationService` (activity domain) is the draw orchestrator.
  It calls `IStrategyDispatch` for the random award ID, then passes the result
  to the award and fulfillment paths. This call graph makes strategy write paths
  inseparable from activity/draw until a `IStrategyDecisionAdapter` is introduced
  (Phase 5-D).
- `queryRaffleStrategyAwardListByActivityId` requires an activity→strategy ID
  mapping; this mapping lives in `raffle_activity.strategy_id`. The strategy
  repository performs this join internally. This is acceptable for Phase 4 reads
  (the strategy-service can read `raffle_activity` to resolve the mapping) but is
  noted as a Phase 6 table-ownership concern.
- `queryOpenActivityStrategyAwardList` joins `strategy_award` with `raffle_activity`
  to identify active-strategy awards for the stock-job. This path is NOT included
  in `IStrategyReadService` and stays in message-job-service.

## 6. Proposed Read-First Extraction Target

**Target: `IStrategyReadService` with two methods:**

1. `queryRaffleAwardList(RaffleAwardListRequestDTO)` — award list for an activity.
2. `queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO)` — rule-weight
   unlock progress for an activity.

**Why these two:**
- Both are pure reads with no side effects.
- Both are the most consumer-visible strategy queries (user-facing award display and
  unlock progress).
- Both delegate only to `IRaffleAward` and `IRaffleRule` — no draw execution.
- Account-enrichment fields (unlock status, total use count) can be conservatively
  defaulted in the dark-launch scaffold and replaced in Phase 4-D.

**What is explicitly excluded from `IStrategyReadService`:**
- `strategyArmory` (write/setup).
- `randomRaffle` / `performRaffle` (draw execution).
- `queryRaffleAwardListByToken` (requires auth service cross-domain call).
- `queryOpenActivityStrategyAwardList` (stock-job path; owned by message-job-service).

## 7. Explicit Non-Goal: No Draw Decision Migration in This Batch

Phase 4 does **not** move the draw decision path (`performRaffle`, rule-chain
evaluation, rule-tree evaluation, stock decrement). These remain in-process in
`market-service` / `RaffleApplicationService`. Moving them requires:

1. A `IStrategyDecisionAdapter` with local and remote implementations (Phase 5-D).
2. A saga / idempotency design for the draw→award→outbox transaction boundary (Phase 5-G).
3. Phase 5 sign-off by Engineering + Oncall.

Any change that routes `performRaffle` calls out of process before Phase 5 sign-off
**violates the hard safety boundary** in the master plan.

## 8. Phase 4-D Adapter Design

### IStrategyReadAdapter (trigger.adapter)

Boundary interface with two methods matching `IStrategyReadService`:
- `queryRaffleAwardList(RaffleAwardListRequestDTO)` → `List<RaffleAwardListResponseDTO>`
- `queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO)` → `List<RaffleStrategyRuleWeightResponseDTO>`

**LocalStrategyReadAdapter** (`@ConditionalOnMissingBean`, no `@DubboReference`):
Preserves the exact local behavior from `RaffleStrategyController` before Phase 4-D.
Uses `IRaffleAward`, `IRaffleRule`, and `IAccountReadAdapter` (which itself routes
to account-service when `account.service.remote-read.enabled=true`).

**StrategyRemoteReadAdapter** (market-service config):
Implements `IStrategyReadAdapter`. Guards remote traffic behind
`strategy.service.remote-read.enabled=false`. Overrides `LocalStrategyReadAdapter`
(`@ConditionalOnMissingBean` yields when this bean is present).
When remote is enabled and call fails: logs and falls back to the same local logic.

### IStrategyAccountParticipationPort (strategy-service port)

Narrow port introduced in `big-market-strategy-service` to supply real account
participation counts without importing the activity/account domain directly.
`LocalStrategyAccountParticipationPort` uses `@DubboReference(check=false) IAccountQuotaService`
— an existing API contract — to call account-service.
`check=false`: strategy-service startup succeeds even when account-service is unreachable.
Fallback: returns 0 (conservative; all locked awards remain locked; unlock thresholds appear unmet).

### Legacy Provider Gate

`RaffleStrategyController` remains an always-on HTTP controller. The legacy
Dubbo provider was split into `trigger.rpc.RaffleStrategyServiceRPC` and gated with:
```
@ConditionalOnProperty(name = "strategy.legacy-rpc-provider.enabled", havingValue = "true", matchIfMissing = true)
```
`matchIfMissing=true` preserves current behavior (legacy RPC provider active by default).
Set `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED=false` only after all `IRaffleStrategyService`
consumers are migrated or explicitly retired. This gate is not required for
`IStrategyReadService` remote-read routing because the new strategy read service uses
a different Dubbo contract.

### Remote-Read Flag Semantics

| Flag | Location | Default | Effect |
|------|----------|---------|--------|
| `strategy.service.remote-read.enabled` | market-service | `false` | Routes read through `IStrategyReadService` when `true` |
| `strategy.legacy-rpc-provider.enabled` | market-service | `true` | Registers `RaffleStrategyServiceRPC` as legacy `IRaffleStrategyService` provider |
| `STRATEGY_SERVICE_REMOTE_READ_ENABLED` | docker-compose | `false` | Overrides application.yml |
| `STRATEGY_LEGACY_RPC_PROVIDER_ENABLED` | docker-compose | `true` | Overrides application.yml |

### Response Parity Requirement

Remote reads must match local behavior before `strategy.service.remote-read.enabled=true`:
- `dayPartakeCount` in `queryRaffleAwardList` must equal `IAccountReadAdapter.queryRaffleActivityAccountDayPartakeCount`
- `userActivityAccountTotalUseCount` in `queryRaffleStrategyRuleWeight` must equal `IAccountReadAdapter.queryRaffleActivityAccountPartakeCount`

`IStrategyAccountParticipationPort` in strategy-service satisfies this by calling the same
`IAccountQuotaService` methods, but **only when account-service is reachable from strategy-service
in staging**. Until that network path is validated, fallback returns 0 (parity not guaranteed).

## 9. Remaining Blockers Before Remote Strategy Read Traffic Can Be Enabled

1. **Dark-launch only — no Nacos registration yet**: `big-market-strategy-service` is a
   repo-ready module but is not deployed to staging Nacos. No consumer references it.
2. **Account-service reachability from strategy-service not validated**: `IStrategyAccountParticipationPort`
   delegates to `IAccountQuotaService` via Dubbo, but the network path between strategy-service
   and account-service has not been validated in staging. Until it is, response parity is not
   guaranteed (fallback returns 0).
3. **Legacy `IRaffleStrategyService` consumers still need an owner**: the new read service
   uses `IStrategyReadService`, so remote-read cutover does not create a duplicate provider.
   The legacy provider gate is for later `IRaffleStrategyService` cleanup after remaining
   consumers are migrated or retired.
4. **Table ownership not enforced**: `strategy*` and `rule_tree*` tables are accessed
   by market-service, message-job-service, and the new strategy-service. Phase 6
   dependency-narrowing work.
5. **`strategy.service.remote-read.enabled` defaults false**: no traffic is enabled
   in this batch and must not be enabled until blockers 1–4 are resolved and DBA +
   Ops + Engineering + Oncall sign off (Phase 8-D approval gate).

## 10. Phase 4 Sub-Batch Implementation Status

| Sub-batch | Title | Status |
|-----------|-------|--------|
| 4-A | Strategy boundary assessment doc (this document) | **Done** |
| 4-B | `IStrategyReadService` in `big-market-api` | **Done** — narrow read-only contract |
| 4-C | `big-market-strategy-service` dark-launch module | **Done** — port 8089, Dubbo port 20884 |
| 4-D | `IStrategyReadAdapter` + local/remote impls + legacy gate + account participation port | **Done** — all defaults false/true; no traffic enabled |
| 4-E | Strategy dependency narrowing validators | **Done** — `validate-microservices-phase-4-strategy-read-adapter.sh` + `validate-microservices-phase-4-strategy-dependency-narrowing.sh` |
| 4-F | Strategy table ownership mapping doc | **Done** — `docs/microservices-split-phase-4-strategy-table-ownership.md`; validator `scripts/validate-microservices-phase-4-strategy-table-ownership.sh` |
