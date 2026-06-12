> **Archived (2026-06-12):** Phase 1-7 historical implementation record. See `docs/MICROSERVICES.md` for current status.

# Phase 5-A — Activity / Draw Orchestration Map

> This document is the Phase 5-A deliverable: a complete map of the draw
> orchestration call graph, table touchpoints, MQ/job wiring, candidate
> adapter boundaries, and the explicit non-goals that constrain this batch.
>
> Nothing moves in this batch. No remote flag is enabled. No new module is
> created. The document exists so that Phase 5-B/C/D can be planned and
> sequenced against a shared, auditable baseline.
>
> Last revised: 2026-06-11.
> Status anchor: Phase 4-D/E/F complete. Phase 5-A docs-only. Phase 5-B draw-command
> boundary design doc complete. Phase 5-C account/quota port re-verification doc complete.
> Phase 5-D local strategy decision port introduced. Phase 5-E local award fulfillment
> port introduced. RaffleApplicationService now uses both ports. Phase 5-F
> big-market-activity-service dark-launch scaffold introduced at port 8090 (tag
> phase-5-activity-service-dark-launch-scaffold): scan boundary enforced; no draw
> execution moved; no RPC provider, HTTP controller, MQ consumer, or job handler added.
> Phase 5-G draw saga/outbox design complete (tag phase-5-activity-draw-saga-outbox-scaffold):
> orchestration saga pattern chosen; IDrawOutboxPort + DrawOutboxEvent + LocalDrawOutboxPort
> scaffold contracts introduced; design doc committed; port NOT wired into draw hot-path
> (requires Phase 7-D DDL + Phase 8-E cutover approval). Phase 5 complete.
> Recommended next batch: Phase 6-A (DAO ownership matrix).

---

## 1. Draw Request Entry Point

**HTTP entry point:**
`RaffleActivityController.draw(ActivityDrawRequestDTO)`
`big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`

The controller is a `@RestController` AND a `@DubboService(version = "1.0")`
(implements `IRaffleActivityService`). It applies rate limiting
(`@RateLimiterAccessInterceptor`) and circuit breaking (`@HystrixCommand`)
before delegating to `RaffleApplicationService.executeDraw`.

A degradation DCC flag (`degradeSwitch`) can short-circuit the entire draw
path before any domain call is made.

---

## 2. RaffleApplicationService Call Graph

`RaffleApplicationService.executeDraw` is the draw orchestrator.
Source: `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`

```
executeDraw(userId, activityId)
│
├── [Step 1] IRaffleActivityPartakeService.createOrder(userId, activityId)
│     → AbstractRaffleActivityPartake → RaffleActivityPartakeService
│     → Reads raffle_activity, raffle_activity_count, raffle_activity_sku
│     → Reads/writes raffle_activity_account, raffle_activity_account_day,
│       raffle_activity_account_month (quota decrement)
│     → Writes user_raffle_order (participation record)
│     → Returns UserRaffleOrderEntity (includes strategyId, endDateTime)
│
├── [Step 2] IStrategyDecisionPort.performRaffle(RaffleFactorEntity)
│     → LocalStrategyDecisionPort → IRaffleStrategy.performRaffle
│     → DefaultRaffleStrategy → AbstractRaffleStrategy
│     → rule-chain evaluation (domain.strategy.service.rule.chain.impl.*)
│       reads strategy_rule, strategy, strategy_award (Redis armory lookup)
│     → rule-tree evaluation (domain.strategy.service.rule.tree.impl.*)
│       reads rule_tree, rule_tree_node, rule_tree_node_line
│     → IStrategyDispatch.getRandomAwardId (reads Redis probability table)
│     → IStrategyDispatch.subtractionAwardStock (decrements Redis stock counter)
│     → enqueues stock decrement key for UpdateAwardStockJob
│     → Returns RaffleAwardEntity (awardId, awardTitle, awardConfig, sort)
│
└── [Step 3] IAwardFulfillmentPort.saveUserAwardRecord(UserAwardRecordEntity)
      → LocalAwardFulfillmentPort → IAwardService.saveUserAwardRecord
      → AwardService → AwardRepository.saveUserAwardRecord
      → Writes user_award_record (award state = create)
      → Writes task outbox row (topic = send_award) in shared `task` table
      → Local transaction: user_award_record + task row committed together
```

**Within-transaction guarantee at step 3:**
The `user_award_record` INSERT and the `task` outbox INSERT are in the same
local DB transaction (sharded by userId). Loss of either is a visible anomaly.
This is the primary consistency invariant that constrains any future move of
step 3 out of process.

---

## 3. Domain Dependencies

| Domain | Role in draw path | Key interfaces | Tables touched |
|--------|------------------|----------------|----------------|
| `activity` / partake | Creates participation record; decrements quota | `IRaffleActivityPartakeService` → `AbstractRaffleActivityPartake` | `raffle_activity`, `raffle_activity_account`, `raffle_activity_account_day`, `raffle_activity_account_month`, `raffle_activity_count`, `raffle_activity_sku`, `user_raffle_order` |
| `strategy` / raffle | Full draw decision: rule-chain, rule-tree, random award | `IRaffleStrategy` → `DefaultRaffleStrategy` | `strategy_rule`, `rule_tree`, `rule_tree_node`, `rule_tree_node_line`, `strategy_award` (stock decrement async) — plus Redis probability tables |
| `award` | Persists award record and outbox message | `IAwardFulfillmentPort` → `LocalAwardFulfillmentPort` → `IAwardService` → `AwardService` → `AwardRepository` | `user_award_record`, `task` (shared outbox) |
| `task` / outbox | Async MQ publish gate | `ITaskService` → `SendMessageTaskJob` | `task` (shared; sharded by userId) |
| `credit` / account | Quota decrement ledger (gated, Phase 2.2) | `IActivityAccountPort` | `raffle_quota_decrement_ledger` (when flag=true) |

---

## 4. Current Transaction and Consistency Assumptions

| Assumption | Current implementation | Phase 5 concern |
|------------|----------------------|-----------------|
| Participation order + quota decrement are atomic | `AbstractRaffleActivityPartake.createOrder` uses `@Transactional`; writes `user_raffle_order` + account rows together | Moving quota decrement to account-service requires saga coordination (B11–B14 already done; Phase 5-C re-verifies) |
| Award record + outbox are atomic | `AwardRepository.saveUserAwardRecord` writes `user_award_record` + `task` in one transaction | Moving award persistence to fulfillment-service requires the outbox row to move too; credit-award outbox path already handles credit; general award path does not yet have a cross-service outbox |
| Draw decision is in-process | `IStrategyDecisionPort.performRaffle` delegates to the local `IRaffleStrategy` bean; no network hop | Remote decision remains blocked; `strategy.service.remote-decision.enabled` is not introduced |
| Award persistence is in-process | `IAwardFulfillmentPort.saveUserAwardRecord` delegates to local `IAwardService`; no network hop | Remote award fulfillment remains blocked until Phase 5-G saga/outbox design |
| Stock decrement is Redis-only + async DB sync | `subtractionAwardStock` writes to Redis; `UpdateAwardStockJob` flushes to `strategy_award` table | Stock sync job must remain co-located with strategy tables until datasource isolation (Phase 7) |

---

## 5. Tables Touched by the Draw and Participation Flow

| Table | Operation | Step | Owning bounded context |
|-------|-----------|------|------------------------|
| `raffle_activity` | SELECT (validate activity state) | Partake | activity |
| `raffle_activity_count` | SELECT (resolve count config) | Partake | activity |
| `raffle_activity_sku` | SELECT (stock validation) | Partake | activity |
| `raffle_activity_account` | SELECT + UPDATE (total quota) | Partake | activity/account |
| `raffle_activity_account_day` | SELECT + INSERT/UPDATE | Partake | activity/account |
| `raffle_activity_account_month` | SELECT + INSERT/UPDATE | Partake | activity/account |
| `user_raffle_order` | INSERT | Partake | activity |
| `strategy_rule` | SELECT (rule-chain config) | Draw decision | strategy |
| `rule_tree` | SELECT | Draw decision | strategy |
| `rule_tree_node` | SELECT | Draw decision | strategy |
| `rule_tree_node_line` | SELECT | Draw decision | strategy |
| `strategy_award` | SELECT (award config); async UPDATE via job | Draw decision / stock-job | strategy |
| `user_award_record` | INSERT | Award persist | award/fulfillment |
| `task` | INSERT (outbox row) | Award persist | shared outbox |
| `raffle_quota_decrement_ledger` | INSERT (when Phase 2.2 quota flag=true) | Partake (account-service path) | account |
| `user_credit_account` | UPDATE (when credit award path) | Fulfillment (credit-award outbox) | account/credit |

---

## 6. MQ/Job Touchpoints

| Name | Type | Trigger | Domain calls | Tables |
|------|------|---------|--------------|--------|
| `ActivitySkuStockZeroConsumer` | RabbitMQ listener | `activity_sku_stock_zero` topic | `IRaffleActivitySkuStockService.clearActivitySkuStock` | `raffle_activity_sku` (UPDATE stock_count_surplus=0) |
| `SendAwardConsumer` | RabbitMQ listener | `send_award` topic | `IAwardDispatchAdapter.distributeAward` | `user_award_record` (status update) + credit path |
| `UpdateAwardStockJob` | XXL-Job | scheduled | `IRaffleAward.queryOpenActivityStrategyAwardList` (strategy_award JOIN raffle_activity), `IRaffleStock.takeQueueValue` + `updateStrategyAwardStock` | `strategy_award` (UPDATE stock_count_surplus), `raffle_activity` (SELECT) |
| `SendMessageTaskJob` | XXL-Job (two shards: DB1, DB2) | scheduled | `ITaskService.queryNoSendMessageTaskList` + `sendMessage` + `updateTaskSendMessageCompleted` | `task` (SELECT + UPDATE; shared outbox) |
| `DispatchCreditAwardTaskJob` | XXL-Job (in message-job-service) | scheduled | credit-award outbox dispatch | `credit_award_task` |
| `CreditAdjustSuccessConsumer` | RabbitMQ listener | `credit_adjust_success` | credit domain | `user_credit_account`, `user_credit_order` |
| `RebateMessageConsumer` | RabbitMQ listener | `rebate_message` | `IBehaviorRebateService.createOrder` or adapter | rebate tables |

---

## 7. Candidate Future Adapters

These adapter interfaces do not exist yet. They are named here so that
Phase 5-B/C/D can be planned against consistent names.

### 7.1 IStrategyDecisionPort (Phase 5-D) — DONE

Replaces the direct `IRaffleStrategy.performRaffle` call in
`RaffleApplicationService`. Introduced as a domain-side port (not trigger-side
adapter) to avoid a dependency inversion violation.

```
interface IStrategyDecisionPort {
    RaffleAwardEntity performRaffle(RaffleFactorEntity factor);
}
```

- `IStrategyDecisionPort`:
  `big-market-domain/.../domain/activity/adapter/port/IStrategyDecisionPort.java`
- `LocalStrategyDecisionPort`:
  `big-market-infrastructure/.../infrastructure/adapter/port/LocalStrategyDecisionPort.java`
  Default bean (`@ConditionalOnMissingBean`). Delegates to in-process `IRaffleStrategy`.
- `RaffleApplicationService` now injects `IStrategyDecisionPort` instead of `IRaffleStrategy`.
- `StrategyRemoteDecisionPort` (future, in market-service config):
  NOT introduced in this batch. Will be guarded by `strategy.service.remote-decision.enabled=false`.
- Gate: `strategy.service.remote-decision.enabled` not introduced yet.
  Must not be introduced until Phase 5-G saga design is approved.

### 7.2 IActivityAccountPort / quota saga port (Phase 5-C re-verify)

Already exists from Phase 2.2 (B11–B14):
`IActivityAccountPort` in `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/`.
`LocalActivityAccountPort` in `big-market-infrastructure/adapter/port/`.
Phase 5-C validates these invariants still hold after Phase 4 ordering.

### 7.3 IAwardFulfillmentPort (Phase 5-E) — DONE

Replaces the direct `IAwardService.saveUserAwardRecord` call in
`RaffleApplicationService` with a domain-side port. This keeps award record
persistence and the existing `task` outbox write in-process while isolating
the future fulfillment boundary.

```
interface IAwardFulfillmentPort {
    void saveUserAwardRecord(UserAwardRecordEntity record);
}
```

- `LocalAwardFulfillmentPort`: delegates to `IAwardService.saveUserAwardRecord`.
- No remote implementation and no remote award fulfillment flag are introduced
  in this batch.
- Gate: remote award fulfillment must wait for Phase 5-G saga/outbox design.

### 7.4 IDrawOutboxPort (Phase 5-G)

Encapsulates the task outbox publication used inside `AwardRepository`.
Required if the award persistence step moves cross-service.
Only introduced after Phase 5-G saga design is approved.

---

## 8. Recommended Phase 5-B/C/D Order

| Sub-batch | Title | Dependency | Risk |
|-----------|-------|------------|------|
| 5-B | Draw-command boundary design doc | This doc (5-A) | Medium — decision shapes all subsequent batches |
| 5-C | Re-verify `IActivityAccountPort` (B11–B14) still holds after Phase 4 | Phase 4 complete | Low — validator-only |
| 5-D | `IStrategyDecisionPort` (local default; no remote flag) | 5-B decided; 5-C green | Medium — new adapter in draw hot path |
| 5-E | Add `IAwardFulfillmentPort` coverage for raffle award persistence | 5-D stable | Done |
| 5-F | `big-market-activity-service` scaffold decision/prep (NO orchestration moved) | 5-D + 5-E stable | Medium |
| 5-G | Activity orchestration target design (saga vs workflow) | 5-F dark-launch stable | High — gates any synchronous write move |

**Hard rule:** no synchronous write call moves out of market-service until 5-G
is approved. `performRaffle`, `createOrder` (partake), and `saveUserAwardRecord`
all remain in-process until then.

---

## 9. Explicit Non-Goals for This Batch (Phase 5-A)

1. **No draw execution migration.** `performRaffle`, `randomRaffle`,
   `strategyArmory`, stock decrement, and armory assembly do not move.
2. **No remote draw command.** No `strategy.service.remote-decision.enabled`
   flag is introduced in this batch.
3. **No activity-service scaffold.** `big-market-activity-service` module is
   not created until Phase 5-F (after 5-D + 5-E adapters are stable).
4. **No saga design.** Phase 5-G gates any design that would move writes
   across services.
5. **No `strategy.service.remote-read.enabled` default change.** Flag remains
   false; this is a Phase 8-D cutover gate.
6. **No dangerous Phase 2/3/4 flags hardcoded true.** All remote flags remain
   at their established false defaults.
7. **No new activity-service DB or outbox.** `docs/sql/proposed-*` DDL for
   activity is Phase 7-D work (only if 5-G commits to async draw orchestration).

---

## 10. Open Questions for Phase 5-B

1. **Orchestration boundary location:** Should `RaffleApplicationService`
   eventually live in a new `activity-service` module, or remain in
   `market-service` and call remote adapters for strategy, account, and award?
   The former requires moving HTTP ingress; the latter keeps orchestration
   co-located with the HTTP controller.

2. **Idempotency key for draw:** Each draw already produces a `user_raffle_order`
   as an idempotency record. If the strategy decision step moves remote, the
   order ID must be carried through as a correlation key.

3. **Stock decrement timing:** `subtractionAwardStock` (Redis) is called before
   `saveUserAwardRecord` (DB). If award persistence fails, the Redis stock is
   already consumed. Current mitigation: Redis stock is asynchronously synced;
   DB stock is the source of truth for reconciliation. Phase 5-D must not
   change this invariant.
