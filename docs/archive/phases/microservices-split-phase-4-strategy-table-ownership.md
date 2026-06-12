> **Archived (2026-06-12):** Phase 1-7 historical implementation record. See `docs/MICROSERVICES.md` for current status.

# Phase 4-F — Strategy Table Ownership Mapping

> This document feeds Phase 7 (data ownership and outbox boundary work).
> It records the current physical state of strategy/rule table access
> across all launchers, documents cross-table coupling that constrains
> Phase 6/7 isolation, and states what Phase 4 has and has not done.
>
> Last revised: 2026-06-11.
> Status anchor: Phase 4-D/E complete (tag `phase-4-strategy-read-adapter-boundary`).

---

## 1. Strategy-Owned Tables

The following six tables are logically owned by the strategy bounded context
and will be physically owned by `big-market-strategy-service` after Phase 7
datasource isolation:

| Table | Mapper interface | Repository class |
|-------|-----------------|------------------|
| `strategy` | `IStrategyDao` | `StrategyRepository` |
| `strategy_award` | `IStrategyAwardDao` | `StrategyRepository` |
| `strategy_rule` | `IStrategyRuleDao` | `StrategyRepository` |
| `rule_tree` | `IRuleTreeDao` | `StrategyRepository` |
| `rule_tree_node` | `IRuleTreeNodeDao` | `StrategyRepository` |
| `rule_tree_node_line` | `IRuleTreeNodeLineDao` | `StrategyRepository` |

All six DAO interfaces live in
`big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/dao/`.
All six are implemented through `StrategyRepository` at
`big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/StrategyRepository.java`.

---

## 2. Mapper XML Locations Across Launchers

The six strategy/rule mapper XMLs are currently duplicated across every
launcher that includes `big-market-infrastructure` and scans the full domain.
The strategy-service is the only launcher whose mapper set is *restricted*
to these six tables.

| Launcher module | Mapper XMLs present | Notes |
|-----------------|---------------------|-------|
| `big-market-strategy-service` | `strategy_mapper.xml`, `strategy_award_mapper.xml`, `strategy_rule_mapper.xml`, `rule_tree_mapper.xml`, `rule_tree_node_mapper.xml`, `rule_tree_node_line_mapper.xml` | **Only these six** — no activity, award, rebate, account, or task mappers |
| `big-market-app` | All mappers including the six above | Legacy monolith; retains all mappers as single-process fallback |
| `big-market-market-service` | All mappers including the six above | Market-service executes draw + activity + award paths that need strategy data |
| `big-market-message-job-service` | All mappers including the six above | Owns `UpdateAwardStockJob` which reads strategy_award via `queryOpenActivityStrategyAwardList` |
| `big-market-account-service` | All mappers including the six above | Scans full infrastructure; strategy mappers are unused by its owned paths (over-inclusion) |
| `big-market-rebate-service` | `daily_behavior_rebate_mapper.xml`, `task_mapper.xml`, `user_behavior_rebate_order_mapper.xml` only | **Already narrowed** — no strategy mappers present |

Path pattern for strategy-service:
`big-market-strategy-service/src/main/resources/mybatis/mapper/mysql/`

Path pattern for other launchers:
`<module>/src/main/resources/mybatis/mapper/mysql/`

---

## 3. Access Classification: Read-Only vs Write/Setup vs Draw Decision

| Path | Type | Tables touched | Owning launcher today |
|------|------|---------------|----------------------|
| `IRaffleAward.queryRaffleAwardList` / `queryRaffleStrategyAwardListByActivityId` | Read | `strategy_award`, `strategy` | market-service (via IStrategyReadAdapter) |
| `IRaffleRule.queryAwardRuleLockCount` / `queryAwardRuleWeight` | Read | `strategy_rule`, `rule_tree`, `rule_tree_node`, `rule_tree_node_line` | market-service (via IStrategyReadAdapter) |
| `IRaffleAward.queryOpenActivityStrategyAwardList` | Read (crosses boundary) | `strategy_award` JOIN `raffle_activity` | message-job-service (`UpdateAwardStockJob`) |
| `IStrategyArmory.assembleLotteryStrategy` / `assembleLotteryStrategyByActivityId` | Write/setup (Redis) | `strategy`, `strategy_award`, `strategy_rule`, `rule_tree*` | market-service (admin-triggered HTTP) |
| `IStrategyDispatch.subtractionAwardStock` | Write (Redis) | no DB table; Redis only | market-service (draw path) |
| `IRaffleStock.updateStrategyAwardStock` | Write (DB sync) | `strategy_award` | message-job-service (`UpdateAwardStockJob`) |
| `IRaffleStrategy.performRaffle` | Draw decision | `strategy_rule`, `rule_tree*` (read); `strategy_award` stock decrement (Redis) | market-service (`RaffleApplicationService`) |

**Read-adapter-ready paths (Phase 4-D complete):**
- `queryRaffleAwardList`, `queryRaffleStrategyRuleWeight` — routed through
  `IStrategyReadAdapter`; `LocalStrategyReadAdapter` is the default;
  `StrategyRemoteReadAdapter` is wired in market-service config and enabled
  only when `strategy.service.remote-read.enabled=true` (default false).

**Paths that remain in market-service / message-job-service (NOT moved):**
- All write/setup, draw decision, and stock-sync paths listed above.

---

## 4. Known Cross-Table Coupling

### 4.1 strategy_award → raffle_activity (stock-job path)

`IStrategyAwardDao.queryOpenActivityStrategyAwardList` joins `strategy_award`
with `raffle_activity` to identify which strategies belong to currently-open
activities. This query is called by `UpdateAwardStockJob` in message-job-service.

Impact:
- Phase 7 cannot isolate `strategy_award` to a separate datasource/schema
  without breaking this join or replacing it with an event-projected view.
- Candidate solution (Phase 6/7): emit an activity-open event from
  activity-service; strategy-service projects a local `active_strategy_ids`
  cache; `queryOpenActivityStrategyAwardList` becomes a local read on that cache.
- Until this is resolved, the stock-job path must remain in a launcher that
  has both strategy and activity mappers.

### 4.2 activity → strategyId mapping

`queryRaffleStrategyAwardListByActivityId` and
`queryRaffleStrategyRuleWeightByActivityId` require resolving an `activityId`
to a `strategyId`. `StrategyRepository` performs this lookup by reading
`raffle_activity.strategy_id`.

Impact:
- strategy-service already has this query in its mapper set because it includes
  the full `IStrategyAwardDao` and `StrategyRepository`.
- This is acceptable for Phase 4 reads: strategy-service can read `raffle_activity`
  to resolve the mapping (shared DB access).
- Phase 6/7 resolution: introduce an activity→strategy projection table in
  strategy-service that is populated by an activity-activation event; remove
  the cross-table read.

### 4.3 Account participation counts in read enrichment

`queryRaffleAwardList` and `queryRaffleStrategyRuleWeight` enrich responses
with day-partake and total-use counts. These come from `IAccountReadAdapter`,
which calls account-service when `account.service.remote-read.enabled=true`.
This is NOT a strategy-table concern but is documented here because it is
the remaining enrichment coupling on the strategy read path.

---

## 5. Phase 4 Conclusion

| Dimension | Status |
|-----------|--------|
| Read adapter boundary | **Done** — `IStrategyReadAdapter` + local/remote impls wired; flag false |
| Strategy-service mapper scope | **Done** — strategy-service contains only the six strategy/rule XMLs |
| Legacy provider gate | **Done** — `RaffleStrategyServiceRPC` gated with `@ConditionalOnProperty(matchIfMissing=true)` |
| Table/datasource isolation | **Not done** — all launchers share `big_market_01`/`big_market_02`; no DB user or schema boundary |
| Draw/write path ownership | **Not moved** — all write paths remain in market-service/message-job-service |

Phase 4 establishes that strategy reads are adapter-ready. The physical table
boundary (datasource isolation, DB user grants) is Phase 7 work.

---

## 6. Phase 6/7 Follow-Up Items

| Item | Phase | Description |
|------|-------|-------------|
| Mapper narrowing for account-service | 6-A | Remove unused strategy/rule mapper XMLs from account-service |
| Mapper narrowing for message-job-service | 6-A | Restrict to strategy_award + raffle_activity (stock-job path) or replace join |
| Activity→strategy projection event | 6/7 | Decouple `queryOpenActivityStrategyAwardList` from raffle_activity join |
| Datasource isolation | 7-F | Separate schema or DB user for strategy tables |
| DB user enforcement | 7-E | strategy-service DB user grants only SELECT on strategy/rule tables |
| Proposed DDL review | 7-A | Include strategy table set in per-service table ownership matrix |
