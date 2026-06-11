# DAO Ownership Matrix

> Phase 6-A artifact. Inventories every DAO, mapper XML, and repository in
> `big-market-infrastructure` against bounded context ownership. Feeds Phase 7
> table isolation planning and the Phase 6-B package-ownership validator.
>
> Last revised: 2026-06-11. Status: complete inventory; no DAO files moved;
> all findings are documentation-only.

---

## 1. Legend

| Column | Meaning |
|--------|---------|
| DAO interface | Java interface in `big-market-infrastructure/…/dao/I*.java` |
| PO class | `big-market-infrastructure/…/dao/po/*.java` |
| Mapper XML (app) | `big-market-app/src/main/resources/mybatis/mapper/mysql/` |
| Mapper XML (service) | Service-specific copy, e.g. `big-market-account-service/…/mapper/mysql/` |
| Physical table | MySQL table name |
| Current repository | `big-market-infrastructure/…/adapter/repository/*.java` that owns this DAO |
| Caller modules | Service modules that transitively call this repository via domain service |
| Target service | Intended owning service after Phase 7 isolation |
| Phase | Decomposition phase that gates table isolation |
| Risk | Low / Medium / High — impact if access is blocked before migration |
| Blockers | Prerequisites that must land before DAO can be isolated |

---

## 2. Bounded Context Summary

| Bounded context | Target service | Tables | DAOs | Migration phase |
|-----------------|----------------|--------|------|-----------------|
| activity / draw | activity-service | 6 | 6 | Phase 7-A |
| account / quota | account-service | 4 | 4 | Phase 7-A (B18) |
| credit | account-service | 2 + 1 outbox | 3 | Phase 7-A (B18) |
| fulfillment / award | fulfillment-service | 2 | 3 | Phase 7-A (B23-E) |
| rebate | rebate-service | 2 | 2 | Phase 7-A (Phase 8-C) |
| strategy | strategy-service | 6 | 6 | Phase 7-A (Phase 8-D) |
| task / outbox | shared (decision Phase 7-B) | 1 | 1 | Phase 7-B |
| query / ES | activity-service or strategy-service | ES index | 1 | Phase 4/5 decision |
| auth | auth-service | — | — | stable (stateless) |
| admin / config | admin-service | — | — | stable |
| chatbot | chatbot-service | — | — | stable |

---

## 3. Full DAO Inventory

### 3.1 Activity / Draw Context

Target service: `big-market-activity-service` (scaffold at port 8090; draw execution remains in market-service until Phase 8-E).

| DAO interface | PO | Mapper XML (app) | Mapper XML (service) | Physical table | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|----|-----------------|---------------------|----------------|-------------------|----------------|----------------|-------|------|----------|
| `IRaffleActivityDao` | `RaffleActivity` | `raffle_activity_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity` | `ActivityRepository`, `StrategyRepository`† | market-service (draw, partake), strategy-service (ID lookup)† | activity-service | 7-A | High | Phase 5-D strategy decision port; StrategyRepository cross-access must be removed first |
| `IRaffleActivityCountDao` | `RaffleActivityCount` | `raffle_activity_count_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity_count` | `ActivityRepository` | market-service | activity-service | 7-A | Medium | Phase 8-E activity cutover |
| `IRaffleActivitySkuDao` | `RaffleActivitySku` | `raffle_activity_sku_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity_sku` | `ActivityRepository` | market-service, message-job-service | activity-service | 7-A | Medium | Phase 8-E activity cutover |
| `IRaffleActivityStageDao` | `RaffleActivityStage` | `raffle_activity_stage_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity_stage` | `ActivityRepository` | market-service | activity-service | 7-A | Medium | Phase 8-E activity cutover |
| `IRaffleActivityOrderDao` | `RaffleActivityOrder` | `raffle_activity_order_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity_order` | `ActivityRepository` | market-service | activity-service | 7-A | High | Phase 8-E activity cutover; outbox saga Phase 5-G |
| `IUserRaffleOrderDao` | `UserRaffleOrder` | `user_raffle_order_mapper.xml` | account-service, market-service, message-job-service | `user_raffle_order` | `ActivityRepository`, `AwardRepository`† | market-service, fulfillment-service†  | activity-service | 7-A | High | AwardRepository cross-access must be removed; Phase 8-E |

† denotes a cross-boundary caller — see §5.

---

### 3.2 Account / Quota Context

Target service: `big-market-account-service` (dark-launch; cutover gate B18).

| DAO interface | PO | Mapper XML (app) | Mapper XML (service) | Physical table | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|----|-----------------|---------------------|----------------|-------------------|----------------|----------------|-------|------|----------|
| `IRaffleActivityAccountDao` | `RaffleActivityAccount` | `raffle_activity_account_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity_account` | `ActivityRepository`, `StrategyRepository`† | market-service, strategy (rule evaluation)† | account-service | 7-A | High | StrategyRepository cross-access must be removed before Phase 7; B18 cutover |
| `IRaffleActivityAccountDayDao` | `RaffleActivityAccountDay` | `raffle_activity_account_day_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity_account_day` | `ActivityRepository`, `StrategyRepository`† | market-service, strategy† | account-service | 7-A | High | StrategyRepository cross-access; B18 cutover |
| `IRaffleActivityAccountMonthDao` | `RaffleActivityAccountMonth` | `raffle_activity_account_month_mapper.xml` | account-service, market-service, message-job-service | `raffle_activity_account_month` | `ActivityRepository` | market-service | account-service | 7-A | Medium | B18 cutover |
| `IRaffleQuotaDecrementLedgerDao` | `RaffleQuotaDecrementLedger` | *(account-service only)* | account-service | `raffle_quota_decrement_ledger` | `ActivityRepository` | market-service (flag-gated) | account-service | 7-A | Low | B18 cutover; flag already default false |

---

### 3.3 Credit Context

Target service: `big-market-account-service` (dark-launch; cutover gate B18).

| DAO interface | PO | Mapper XML (app) | Mapper XML (service) | Physical table | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|----|-----------------|---------------------|----------------|-------------------|----------------|----------------|-------|------|----------|
| `IUserCreditAccountDao` | `UserCreditAccount` | `user_credit_account_mapper.xml` | account-service, market-service, message-job-service | `user_credit_account` | `CreditRepository`, `ActivityRepository`†, `AwardRepository`† | market-service (credit exchange), fulfillment, activity† | account-service | 7-A | Critical | ActivityRepository and AwardRepository cross-access must be removed; B18 cutover |
| `IUserCreditOrderDao` | `UserCreditOrder` | `user_credit_order_mapper.xml` | account-service, market-service, message-job-service | `user_credit_order` | `CreditRepository` | market-service | account-service | 7-A | Medium | B18 cutover |
| `ICreditAwardTaskDao` | `CreditAwardTask` | `credit_award_task_mapper.xml` | account-service, message-job-service | `credit_award_task` | `AwardRepository`, `DispatchCreditAwardTaskJob`†‡ | fulfillment-service†, message-job-service†‡ | account-service | 7-A | High | DispatchCreditAwardTaskJob must route via account-service API (currently flag-gated at `account.award-credit-outbox.enabled=false`); B18 cutover |

† denotes a cross-boundary caller.
‡ `DispatchCreditAwardTaskJob` in `big-market-message-job-service` directly imports `ICreditAwardTaskDao` from `big-market-infrastructure` — a hard cross-module DAO coupling (see §5.3).

---

### 3.4 Fulfillment / Award Context

Target service: `big-market-fulfillment-service` (dark-launch; cutover gate B23-E).

| DAO interface | PO | Mapper XML (app) | Mapper XML (service) | Physical table | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|----|-----------------|---------------------|----------------|-------------------|----------------|----------------|-------|------|----------|
| `IAwardDao` | `Award` | `award_mapper.xml` | account-service, market-service, message-job-service | `award` | `AwardRepository` | fulfillment-service, message-job-service (SendAwardConsumer) | fulfillment-service | 7-A | Medium | B23-E cutover |
| `IUserAwardRecordDao` | `UserAwardRecord` | `user_award_record_mapper.xml` | account-service, market-service, message-job-service | `user_award_record` | `AwardRepository` | fulfillment-service | fulfillment-service | 7-A | Medium | B23-E cutover |

---

### 3.5 Rebate Context

Target service: `big-market-rebate-service` (dark-launch; cutover gate Phase 8-C).

| DAO interface | PO | Mapper XML (app) | Mapper XML (service) | Physical table | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|----|-----------------|---------------------|----------------|-------------------|----------------|----------------|-------|------|----------|
| `IDailyBehaviorRebateDao` | `DailyBehaviorRebate` | `daily_behavior_rebate_mapper.xml` | rebate-service, market-service, message-job-service | `daily_behavior_rebate` | `BehaviorRebateRepository` | market-service (calendarSignRebate), rebate-service | rebate-service | 7-A | Low | Phase 8-C cutover |
| `IUserBehaviorRebateOrderDao` | `UserBehaviorRebateOrder` | `user_behavior_rebate_order_mapper.xml` | rebate-service, market-service, message-job-service | `user_behavior_rebate_order` | `BehaviorRebateRepository` | market-service, rebate-service | rebate-service | 7-A | Low | Phase 8-C cutover |

---

### 3.6 Strategy Context

Target service: `big-market-strategy-service` (not yet created; Phase 4-C scaffold exists).

| DAO interface | PO | Mapper XML (app) | Mapper XML (service) | Physical table | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|----|-----------------|---------------------|----------------|-------------------|----------------|----------------|-------|------|----------|
| `IStrategyDao` | `Strategy` | `strategy_mapper.xml` | strategy-service, market-service, message-job-service | `strategy` | `StrategyRepository` | market-service (draw, raffle), strategy-service | strategy-service | 7-A | Medium | Phase 8-D cutover |
| `IStrategyAwardDao` | `StrategyAward` | `strategy_award_mapper.xml` | strategy-service, market-service, message-job-service | `strategy_award` | `StrategyRepository` | market-service, strategy-service | strategy-service | 7-A | Medium | Phase 8-D cutover |
| `IStrategyRuleDao` | `StrategyRule` | `strategy_rule_mapper.xml` | strategy-service, market-service, message-job-service | `strategy_rule` | `StrategyRepository` | market-service, strategy-service | strategy-service | 7-A | Medium | Phase 8-D cutover |
| `IRuleTreeDao` | `RuleTree` | `rule_tree_mapper.xml` | strategy-service, market-service, message-job-service | `rule_tree` | `StrategyRepository` | market-service, strategy-service | strategy-service | 7-A | Medium | Phase 8-D cutover |
| `IRuleTreeNodeDao` | `RuleTreeNode` | `rule_tree_node_mapper.xml` | strategy-service, market-service, message-job-service | `rule_tree_node` | `StrategyRepository` | market-service, strategy-service | strategy-service | 7-A | Medium | Phase 8-D cutover |
| `IRuleTreeNodeLineDao` | `RuleTreeNodeLine` | `rule_tree_node_line_mapper.xml` | strategy-service, market-service, message-job-service | `rule_tree_node_line` | `StrategyRepository` | market-service, strategy-service | strategy-service | 7-A | Medium | Phase 8-D cutover |

---

### 3.7 Task / Outbox Context

Status: shared across multiple bounded contexts; ownership decision deferred to Phase 7-B.

| DAO interface | PO | Mapper XML (app) | Mapper XML (service) | Physical table | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|----|-----------------|---------------------|----------------|-------------------|----------------|----------------|-------|------|----------|
| `ITaskDao` | `Task` | `task_mapper.xml` | rebate-service, market-service, message-job-service | `task` | `TaskRepository`, `BehaviorRebateRepository`†, `CreditRepository`† | message-job-service (SendMessageTaskJob), rebate writes, credit writes | TBD: per-domain outbox tables (Phase 7-B decision) | 7-B | Medium | Phase 7-B decision doc; `credit_award_task` outbox already precedent; `task` sharded across DB1/DB2 |

---

### 3.8 Query / Elasticsearch Context

Status: ES index; not a MySQL table; ownership tied to whichever service emits projected events.

| DAO interface | Mapper XML | ES index | Current repository | Caller modules | Target service | Phase | Risk | Blockers |
|---------------|-----------|----------|-------------------|----------------|----------------|-------|------|----------|
| `IElasticSearchUserRaffleOrderDao` | `user_raffle_order_mapper.xml` (ES) | `raffle_activity_sku` | `ESUserRaffleOrderRepository` | `big-market-queries` (query module, used by market-service) | activity-service or strategy-service (Phase 4/5 decision) | 4/5 | Low | Phase 4 query ownership decision |

---

### 3.9 Auth / Admin / Chatbot Contexts

No MySQL DAOs in `big-market-infrastructure` are owned by these contexts. These services are stateless (auth: JWT only) or rely on Nacos-synced platform config. No DAO migration required.

---

## 4. Repository Cross-Boundary Access Matrix

This section records every DAO call that crosses a bounded context boundary — i.e., a repository calls a DAO belonging to a different target service. **These must all be removed before Phase 7 table isolation can proceed.**

### 4.1 StrategyRepository → Activity / Quota tables

**AL-2 / AL-3 resolved — Phase 7-A prep (AL-2/AL-3), tag `phase-7-account-boundary-prep-strategy-account-port`.**
`StrategyRepository` no longer imports `IRaffleActivityAccountDao` or `IRaffleActivityAccountDayDao` directly.
Both reads are now routed through `IStrategyActivityAccountPort`:
- `queryActivityAccountTotalUseCount` → `IStrategyActivityAccountPort.queryTotalUseCount` → `LocalStrategyActivityAccountPort` → `IRaffleActivityAccountDao`
- `queryTodayUserRaffleCount` → `IStrategyActivityAccountPort.queryTodayRaffleCount` → `LocalStrategyActivityAccountPort` → `IRaffleActivityAccountDayDao`

**AL-1 remains** (`StrategyRepository` → `IRaffleActivityDao`): still a direct cross-boundary call for activity-ID/strategy-ID resolution. Remediation requires an activity-service read API and is deferred to Phase 7-A proper.

| Cross-boundary call | From context | Foreign context | Foreign table(s) | Method | Status |
|--------------------|--------------|-----------------|-----------------|--------|--------|
| `StrategyRepository` → `IRaffleActivityDao.queryStrategyIdByActivityId` | strategy | activity | `raffle_activity` | `queryStrategyIdByActivityId(activityId)` | **AL-1 open** — deferred to Phase 7-A |
| `StrategyRepository` → `IRaffleActivityDao.queryActivityIdByStrategyId` | strategy | activity | `raffle_activity` | `queryActivityIdByStrategyId(strategyId)` | **AL-1 open** — deferred to Phase 7-A |
| `StrategyRepository` → `IStrategyActivityAccountPort.queryTotalUseCount` → `IRaffleActivityAccountDao` | strategy | account/quota | `raffle_activity_account` | port-delegated | **AL-2 resolved** — `IStrategyActivityAccountPort` |
| `StrategyRepository` → `IStrategyActivityAccountPort.queryTodayRaffleCount` → `IRaffleActivityAccountDayDao` | strategy | account/quota | `raffle_activity_account_day` | port-delegated | **AL-3 resolved** — `IStrategyActivityAccountPort` |

Risk level: **HIGH** (AL-1 remains open) — `IRaffleActivityDao` cross-access still blocks full strategy-service isolation.

---

### 4.2 ActivityRepository → Credit tables — **RESOLVED (Phase 7-A prep)**

| Cross-boundary call | From context | Foreign context | Foreign table(s) | Method | Impact | Status |
|--------------------|--------------|-----------------|-----------------|--------|--------|--------|
| `ActivityRepository` → `IUserCreditAccountDao` | activity | credit | `user_credit_account` | credit balance check during SKU credit purchase flow | Activity partition reads credit account balance to validate credit-exchange partake | **Resolved** — Phase 7-A prep |

**Resolution (Phase 7-A prep):** `IActivityAccountPort.queryUserCreditAccountAmount(String userId)` added. `ActivityRepository.queryUserCreditAccountAmount` now delegates to the port rather than calling `IUserCreditAccountDao` directly. `LocalActivityAccountPort` holds the DAO injection and performs the shard-routed query. Behavior is identical: same inputs, same `BigDecimal.ZERO` default, same shard routing via `IDBRouterStrategy`. No remote flags enabled. `AccountRemoteActivityAccountPort` carries a non-functional stub (remote credit-balance read deferred to Phase 8-B account-service API work).

New boundary path: `ActivityRepository` → `IActivityAccountPort` → `LocalActivityAccountPort` → `IUserCreditAccountDao` (`user_credit_account`)

Risk level: ~~HIGH~~ → **Resolved**. `user_credit_account` table isolation no longer blocked by this coupling.

---

### 4.3 AwardRepository → Activity / Credit tables (HIGH)

| Cross-boundary call | From context | Foreign context | Foreign table(s) | Method | Impact |
|--------------------|--------------|-----------------|-----------------|--------|--------|
| `AwardRepository` → `IUserRaffleOrderDao` | fulfillment | activity | `user_raffle_order` | Award flow reads raffle order status before writing award record | Fulfillment validates draw order is in correct state before dispatch |
| `AwardRepository` → `IUserCreditAccountDao` | fulfillment | credit | `user_credit_account` | Credit-award outbox path writes credit account in same local transaction | `saveGiveOutPrizesAggregate` (local tx path, outbox path flag-gated) |

**Required remediation before Phase 7-A:** For `IUserRaffleOrderDao`: route via activity-service API (read order status). For `IUserCreditAccountDao`: the `credit_award_task` outbox is the correct long-term pattern; `saveGiveOutPrizesAggregate` direct credit write must be fully replaced by the outbox before Phase 8-B fulfillment cutover.

Risk level: **HIGH** — blocks both fulfillment-service and account-service table isolation.

---

### 4.4 message-job-service → Credit infra DAO (HIGH)

| Cross-boundary call | From context | Foreign context | Foreign DAO | Caller class | Flag gate |
|--------------------|--------------|-----------------|-------------|--------------|-----------|
| `DispatchCreditAwardTaskJob` → `ICreditAwardTaskDao` | message-job-service | credit | `ICreditAwardTaskDao` | `big-market-message-job-service/…/DispatchCreditAwardTaskJob.java` | `account.award-credit-outbox.enabled=false` (default) |

**Required remediation before Phase 7-A:** `DispatchCreditAwardTaskJob` must route credit award dispatch via `account-service` Dubbo provider API instead of directly reading `credit_award_task` table. Currently mitigated by `@ConditionalOnProperty(…, havingValue = "true")` flag default false — the bean is never instantiated in production. Must be fixed before the flag is turned on (Phase 8-A).

Risk level: **HIGH** (flag currently guards it; becomes critical at Phase 8-A).

---

### 4.5 BehaviorRebateRepository / CreditRepository → Shared task table (MEDIUM)

| Cross-boundary call | From context | Shared resource | Notes |
|--------------------|--------------|----------------|-------|
| `BehaviorRebateRepository` → `ITaskDao` | rebate | `task` (shared) | Outbox publish for rebate order events |
| `CreditRepository` → `ITaskDao` | credit | `task` (shared) | Outbox publish for credit order events |

**Required remediation:** Phase 7-B decision doc will determine whether `task` is replaced per-domain (preferred) or kept shared. `credit_award_task` is already the precedent for per-domain outbox. No immediate blocker — mitigated by Phase 7-B scope.

Risk level: **MEDIUM** — not a blocker for Phase 7-A; becomes a blocker for Phase 8-A/B/C if `task` rows mix domains on sharded DB.

---

## 5. Cross-Boundary DAO Access Summary (Priority Order)

| # | Violation | From | To | Tables | Must fix by | Risk |
|---|-----------|------|-----|--------|-------------|------|
| 1 | `StrategyRepository` reads activity + quota DAOs | strategy | activity + quota | `raffle_activity`, `raffle_activity_account`, `raffle_activity_account_day` | Before Phase 7-A strategy isolation | Critical |
| 2 | `ActivityRepository` reads credit DAO | activity | credit | `user_credit_account` | ~~Before Phase 7-A~~ **RESOLVED Phase 7-A prep** | ~~High~~ Resolved |
| 3 | `AwardRepository` reads activity DAO | fulfillment | activity | `user_raffle_order` | Before Phase 7-A fulfillment + activity isolation | High |
| 4 | `AwardRepository` directly writes credit DAO | fulfillment | credit | `user_credit_account` | Before Phase 8-B (outbox already gating this) | High |
| 5 | `DispatchCreditAwardTaskJob` imports credit infra DAO | message-job | credit | `credit_award_task` | Before Phase 8-A (flag guards currently) | High |
| 6 | `BehaviorRebateRepository` / `CreditRepository` share `task` table | rebate + credit | shared | `task` | Before Phase 8-A/B/C | Medium |

---

## 6. Mapper XML Duplication Status

All mapper XMLs in `big-market-infrastructure` currently have copies in `big-market-app` (the legacy monolith launcher) and in service-specific modules. This is intentional for the dark-launch pattern.

| Mapper group | Canonical source (will become) | Current copies |
|-------------|-------------------------------|----------------|
| `raffle_activity*`, `raffle_activity_order`, `user_raffle_order` | activity-service | big-market-app, big-market-account-service, big-market-market-service, big-market-message-job-service |
| `raffle_activity_account*`, `raffle_quota_decrement_ledger` | account-service | big-market-app, big-market-account-service, big-market-market-service, big-market-message-job-service |
| `user_credit_account`, `user_credit_order`, `credit_award_task` | account-service | big-market-app, big-market-account-service, big-market-market-service, big-market-message-job-service |
| `award`, `user_award_record` | fulfillment-service | big-market-app, big-market-market-service, big-market-message-job-service |
| `daily_behavior_rebate`, `user_behavior_rebate_order` | rebate-service | big-market-app, big-market-rebate-service, big-market-market-service, big-market-message-job-service |
| `strategy*`, `rule_tree*` | strategy-service | big-market-app, big-market-strategy-service, big-market-market-service, big-market-message-job-service |
| `task` | TBD per-domain outbox | big-market-app, big-market-rebate-service, big-market-market-service, big-market-message-job-service |

Mapper XML copies in non-owning service modules should be removed after Phase 7-A table isolation and Phase 8 cutover, not before.

---

## 7. Phase 7 Table Isolation Prerequisites (Derived)

The following conditions must be true before Phase 7-A can isolate any table group:

1. **Strategy cross-access removed** (§4.1): `StrategyRepository` must not import activity or quota DAOs. Estimated effort: small; add activity-service read API for ID mapping + pass day-count through orchestration layer.

2. **ActivityRepository credit read removed** (§4.2): ~~Replace `IUserCreditAccountDao` call in `ActivityRepository` with a credit-service read port.~~ **Done — Phase 7-A prep.** `IActivityAccountPort.queryUserCreditAccountAmount` introduced; `LocalActivityAccountPort` holds the DAO call; `ActivityRepository` no longer imports `IUserCreditAccountDao`.

3. **AwardRepository activity read removed** (§4.3): Replace `IUserRaffleOrderDao` call in `AwardRepository` with an activity-service read port. Estimated effort: small.

4. **AwardRepository local credit write removed** (§4.3): `saveGiveOutPrizesAggregate` must no longer write `user_credit_account` directly. The `credit_award_task` outbox path must be the sole credit-write channel. Estimated effort: medium; requires Phase 8-B flag enabled + staging evidence.

5. **DispatchCreditAwardTaskJob decoupled** (§4.4): message-job-service must not directly import credit infra DAO. Must be moved before Phase 8-A flag is set. Estimated effort: small; route via account-service Dubbo API.

6. **Phase 7-B task outbox decision** (§4.5): `task` table ownership decision must be made and per-domain outbox tables proposed before Phase 7-A can proceed for rebate or credit.

---

## 8. Recommended Next Steps

Based on this inventory the lowest-risk next phases are:

| Recommended batch | Rationale |
|------------------|-----------|
| **Phase 6-B**: package-ownership validator script | Done — tag `phase-6-package-ownership-boundaries` |
| **Phase 7-A prep (AL-4)**: ActivityRepository credit-account boundary | Done — tag `phase-7-account-boundary-prep-activity-credit-port`; `ActivityRepository` no longer imports `IUserCreditAccountDao` |
| **Phase 7-A prep (AL-2/AL-3)**: StrategyRepository account DAO removal | Done — tag `phase-7-account-boundary-prep-strategy-account-port`; `StrategyRepository` no longer imports `IRaffleActivityAccountDao` or `IRaffleActivityAccountDayDao`; reads route through `IStrategyActivityAccountPort` (`LocalStrategyActivityAccountPort`) |
| **Phase 7-A**: account table ownership gate | B18 cutover; all activity/strategy cross-boundary couplings must be removed first |
| **Phase 7-B**: generic `task` table strategy decision doc | Required before rebate or credit Phase 8 cutover windows |

The highest-risk work requiring dedicated design is **§4.1** (StrategyRepository → activity/quota): it touches the raffle rule-evaluation hot path and requires either an API call in the draw critical path or a redesign where the orchestration layer injects the mapping. This is the primary blocker for strategy-service and account-service table isolation and should be scoped as a dedicated design doc before any code change.

---

## 9. Phase 6-B Enforcement

The cross-boundary violations in §4 and §5 are now enforced by a repeatable validator introduced in Phase 6-B (tag `phase-6-package-ownership-boundaries`):

- **Script:** `scripts/validate-microservices-phase-6-package-ownership-boundaries.sh`
- **What it checks:** each violation in §5 is explicitly allowlisted; any new DAO import not on the allowlist causes a CI failure; activity-service scope constraints and Phase 5-D/E/F/G port boundaries are re-verified on every run.
- **To remove a violation from the allowlist:** fix the cross-boundary coupling (route through an API port), then delete the corresponding `AL-N` entry from the script's allowlist section and update the `check_field_present` + `check_violation_in_doc` calls.
- **To add a new DAO without triggering a failure:** update `docs/microservices-dao-ownership.md` first (Phase 6-A requirement), then add the DAO to the owning repository's allowed-foreign list or to the allowlist if the cross-boundary access is intentional and documented.

---

## 10. Cross-References

- `docs/microservices-decomposition-master-plan.md` §6 Boundary Matrix — high-level service boundaries
- `docs/microservices-split-phase-4-strategy-table-ownership.md` — strategy table detail
- `docs/microservices-split-phase-5-activity-draw-orchestration.md` — activity draw call graph
- `docs/microservices-split-phase-5-activity-draw-saga-outbox.md` — outbox/saga design
- `scripts/validate-microservices-phase-6-dao-ownership-matrix.sh` — Phase 6-A matrix validator
- `scripts/validate-microservices-phase-6-package-ownership-boundaries.sh` — Phase 6-B boundary enforcement validator
