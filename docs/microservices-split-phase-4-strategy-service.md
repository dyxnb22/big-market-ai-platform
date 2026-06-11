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

### `RaffleStrategyController` (HTTP + legacy `@DubboService`)

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

## 8. Remaining Blockers Before Remote Strategy Read Traffic Can Be Enabled

1. **Dark-launch only — no Nacos registration yet**: `big-market-strategy-service` is a
   repo-ready module but is not deployed to staging Nacos. No consumer references it.
2. **Account enrichment port missing**: `isAwardUnlock` and `waitUnLockCount` in
   `queryRaffleAwardList`, and `userActivityAccountTotalUseCount` in
   `queryRaffleStrategyRuleWeight`, require an account-participation read port that
   crosses into the activity/account domain. Phase 4-D work.
3. **Market-service read adapters not wired**: `IStrategyReadAdapter` (local + remote)
   has not been introduced in market-service. HTTP callers still call strategy domain
   services directly. Phase 4-D work.
4. **Legacy strategy provider in `RaffleStrategyController`**: `@DubboService(version="1.0")`
   still exports `IRaffleStrategyService` from market-service. A `@ConditionalOnProperty`
   gate (analogous to `rebate.legacy-rpc-provider.enabled`) must be added before
   `strategy.service.remote-read.enabled` can be turned on.
5. **Table ownership not enforced**: `strategy*` and `rule_tree*` tables are accessed
   by market-service, message-job-service, and the new strategy-service. Phase 6
   dependency-narrowing work.
6. **`strategy.service.remote-read.enabled` defaults false**: no traffic is enabled
   in this batch and must not be enabled until blockers 1–5 are resolved and DBA +
   Ops + Engineering + Oncall sign off (Phase 8-D approval gate).

## 9. Phase 4 Sub-Batch Implementation Status

| Sub-batch | Title | Status |
|-----------|-------|--------|
| 4-A | Strategy boundary assessment doc (this document) | **Done** |
| 4-B | `IStrategyReadService` in `big-market-api` | **Done** — narrow read-only contract with `queryRaffleAwardList` + `queryRaffleStrategyRuleWeight` |
| 4-C | `big-market-strategy-service` dark-launch module | **Done** — port 8089, Dubbo port 20884, provider scanned from `com.dyx.market.strategy.provider` only |
| 4-D | Market-service `IStrategyReadAdapter` (local + remote) | Pending |
| 4-E | Strategy scan / mapper / dependency narrowing validator | Pending (partial — Phase 4 validator covers module boundary and provider contract) |
| 4-F | Strategy table ownership mapping doc | Pending (Phase 7 feeds) |
