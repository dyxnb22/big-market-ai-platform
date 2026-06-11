# Phase 5-G — Activity Draw Saga / Outbox Design

> This document is the Phase 5-G deliverable: the saga/outbox design for the
> draw orchestration, boundary decisions for each draw step, and the scaffold
> port contracts that gate Phase 8-E activity-service cutover.
>
> No draw execution moves in this batch. No remote flag is introduced. No traffic
> is enabled. No DDL is executed. The document and scaffold contracts exist so
> that Phase 7-D (activity outbox DDL proposal) and Phase 8-E (activity-service
> cutover) can be planned against a concrete, auditable design.
>
> Last revised: 2026-06-11.

---

## 1. Problem Statement

`RaffleApplicationService.executeDraw` is a three-step orchestrator that currently
runs inside a single JVM process in `big-market-market-service`. The steps are:

```
Step 1: IRaffleActivityPartakeService.createOrder
        → writes raffle_activity_account*, user_raffle_order (local @Transactional)

Step 2: IStrategyDecisionPort.performRaffle
        → decrements Redis award stock (non-reversible, async DB sync)
        → in-process only; no DB write at this step

Step 3: IAwardFulfillmentPort.saveUserAwardRecord
        → writes user_award_record + task outbox row (local @Transactional)
```

Moving any step cross-service requires answering:
1. Which saga pattern? (choreography vs. orchestration)
2. What is the idempotency key?
3. How does each step's failure trigger compensation?
4. What outbox table is needed and where does it live?
5. What are the ordering guarantees?

---

## 2. Saga Pattern Decision: Orchestration Saga

**Decision: orchestration saga, not choreography.**

Rationale:
- The draw path is already an explicit sequential orchestrator (`RaffleApplicationService`).
  Converting it to choreography would distribute control flow across three services
  with event correlation chains that are harder to observe and debug.
- The failure modes are asymmetric: step 2 (Redis stock decrement) is non-reversible
  at the application level; compensation requires DB-level reconciliation, not event
  rollback. An orchestrator that knows the saga state explicitly is safer.
- The Phase 2.2 account quota saga (B11–B14) already uses the orchestration pattern
  successfully. Reusing the pattern is lower risk than introducing a new one.
- Choreography is appropriate when the steps are truly independent events with no
  ordering requirement. The draw steps have strict ordering: quota must be confirmed
  before draw decision, draw decision must complete before award persistence.

**Chosen pattern:** `RaffleApplicationService` remains the orchestrator. When activity-service
extracts the draw execution, `RaffleApplicationService` moves with it and calls the remote
strategy and fulfillment services through the existing `IStrategyDecisionPort` and
`IAwardFulfillmentPort` seams.

---

## 3. Idempotency Key

**Key:** `user_raffle_order.order_id` (UUID, generated at the start of Step 1).

The `orderId` is:
- Created by `AbstractRaffleActivityPartake.createOrder` before any write.
- Stored in `user_raffle_order` in Step 1's local transaction.
- Carried through `UserRaffleOrderEntity` into Step 2 and Step 3.
- Used as the `messageId` for the task outbox row in Step 3.

Any future remote step **MUST** receive `orderId` as a correlation key and use it
for at-least-once deduplication. The remote service stores the `orderId` in its own
idempotency guard table before executing the write, so duplicate deliveries are
safe-to-retry.

---

## 4. Per-Step Boundary Decisions

### Step 1: `createOrder` — Participation Order and Quota Decrement

| Property | Decision |
|----------|----------|
| Owner | `activity-service` (future) / `market-service` (current) |
| Table writes | `raffle_activity_account*`, `user_raffle_order` |
| Transaction boundary | Single local `@Transactional` — must stay atomic |
| Remote move precondition | Activity table datasource isolation complete (Phase 7) |
| Saga role | Opens the saga; `orderId` is minted here |
| Compensation | Delete `user_raffle_order` where `order_id=?` AND state=create (quota restore via existing compensating SQL) |
| Current state | In-process. `IActivityAccountPort` port boundary already in place (Phase 2.2 B11–B14) |

**Key invariant:** the quota decrement ledger (`raffle_quota_decrement_ledger`) is written
inside the same transaction as `user_raffle_order` when `account.award-credit-outbox.enabled=true`.
This invariant must be preserved when the step moves cross-service.

### Step 2: `performRaffle` — Strategy Decision and Redis Stock Decrement

| Property | Decision |
|----------|----------|
| Owner | `strategy-service` (future) / in-process (current) |
| State writes | Redis stock counter (decrement); async DB sync via `UpdateAwardStockJob` |
| Transaction boundary | None — Redis write is atomic per-key; DB sync is eventually consistent |
| Remote move precondition | `IStrategyDecisionPort` remote implementation wired; strategy table datasource isolation; P99 latency baseline met (< +20% vs in-process) |
| Saga role | Middle step; no DB write, so no rollback needed at the DB level |
| Compensation | Redis stock is NOT compensated at the application level. The DB `strategy_award.stock_count` is the source of truth; `UpdateAwardStockJob` reconciles. If award persistence (Step 3) fails, the stock counter is over-decremented by 1 — the reconciliation job corrects this on the next run. This is an accepted invariant documented in Phase 5-A §4. |
| Current state | In-process via `LocalStrategyDecisionPort`. No remote flag introduced |

**Hard rule:** the Redis stock decrement fires during Step 2. If Step 3 fails, the Redis
counter is already consumed. Do NOT introduce a compensating Redis increment in the saga;
rely on the `UpdateAwardStockJob` DB reconciliation path already in place.

### Step 3: `saveUserAwardRecord` — Award Persistence and Outbox

| Property | Decision |
|----------|----------|
| Owner | `fulfillment-service` (future) / in-process (current) |
| Table writes | `user_award_record` + `task` outbox row (same local transaction) |
| Transaction boundary | Single local `@Transactional` in `AwardRepository.saveUserAwardRecord`; both writes must remain atomic |
| Remote move precondition | Phase 7-D activity outbox DDL applied; `IAwardFulfillmentPort` remote impl wired; `award.service.remote-fulfillment.enabled` flag flipped in Phase 8-E |
| Saga role | Terminal step; saga is complete when this step commits |
| Compensation | Delete `user_award_record` where `order_id=?` AND state=create (safe idempotent delete before fulfillment dispatch fires) |
| Current state | In-process via `LocalAwardFulfillmentPort`. No remote flag |

**Outbox ownership:** when Step 3 moves to fulfillment-service, the `task` outbox row must
move with it OR be replaced by a `draw_saga_outbox` row in an activity-owned table. The
`IDrawOutboxPort` port (introduced in this batch) is the routing seam for that future write.

### Redis Stock Decrement — Special Case

The Redis decrement in Step 2 is the most nuanced boundary:

```
subtractionAwardStock(strategyId, awardId) → Redis DECR
  ↓
enqueue stock sync key
  ↓
UpdateAwardStockJob (async) → UPDATE strategy_award SET stock_count_surplus = (Redis value)
```

This path must NOT be disrupted by any cross-service move. The `UpdateAwardStockJob`
reads Redis keys that were written by the strategy service; it must be co-located with
the strategy service's Redis namespace until Phase 7 datasource isolation separates
the Redis keyspace.

---

## 5. IDrawOutboxPort — Scaffold Contract

Introduced in this batch. Defined in:

```
big-market-domain/.../domain/activity/adapter/port/IDrawOutboxPort.java
```

```java
interface IDrawOutboxPort {
    void publishDrawSagaStep(DrawOutboxEvent event);
}
```

`DrawOutboxEvent` (also introduced in this batch) carries:
`userId`, `activityId`, `strategyId`, `orderId`, `awardId`, `awardTitle`,
`awardTime`, `awardConfig`, `sagaStep` (enum: CREATE_ORDER, PERFORM_RAFFLE,
SAVE_AWARD_RECORD, COMPLETE, COMPENSATE).

**Local implementation:** `LocalDrawOutboxPort` — no-op / logging only.
The existing `task` table outbox inside `AwardRepository` already provides durability
for the local path. No behavior change.

**NOT wired into `RaffleApplicationService`** in this batch. Wiring requires:
1. Phase 7-D activity outbox DDL applied.
2. Phase 8-E cutover approval gate passed.
3. `activity.service.draw-outbox.enabled` flag defaulting false and explicitly flipped.

---

## 6. Proposed Activity Draw Outbox Table (Phase 7-D)

The proposed DDL for the activity-service draw saga outbox is deferred to Phase 7-D.
It is **not** created in this batch. This section records the design intent.

Table name: `draw_saga_outbox_{000..003}` (sharded by `userId` mod 4, matching existing shard convention).

Proposed columns:

```sql
-- docs/sql/proposed-draw-saga-outbox.sql (Phase 7-D deliverable, NOT applied here)
CREATE TABLE draw_saga_outbox_000 (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    VARCHAR(32)  NOT NULL COMMENT 'idempotency key = user_raffle_order.order_id',
    user_id     VARCHAR(32)  NOT NULL,
    activity_id BIGINT       NOT NULL,
    strategy_id BIGINT       NOT NULL,
    award_id    INT          NOT NULL,
    saga_step   VARCHAR(32)  NOT NULL COMMENT 'DrawSagaStep enum value',
    state       VARCHAR(16)  NOT NULL DEFAULT 'create' COMMENT 'create|completed|fail',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Shards 001, 002, 003 identical.
```

Ownership: `big-market-activity-service` after Phase 7 datasource isolation. The poller
job (`DrawSagaOutboxDispatchJob`) is a future Phase 8-E deliverable.

**Hard rule (§7 DDL execution rule):** this DDL is not executed from this repo. The file
`docs/sql/proposed-draw-saga-outbox.sql` is the Phase 7-D deliverable and is applied by
the DBA in an explicit Phase 8 staging window.

---

## 7. Rollback / Compensation Plan

| Failure point | Compensating action | Who executes | Trigger |
|--------------|---------------------|-------------|---------|
| Step 1 fails (createOrder) | No write committed; saga never opened | n/a | Exception propagates to controller |
| Step 1 partial (quota decremented, order not written) | Impossible in current single-transaction impl; future: delete raffle_quota_decrement_ledger row by correlation key | activity-service saga job | Timeout or explicit NACK |
| Step 2 fails (performRaffle) | Redis stock is NOT yet decremented (exception before DECR); Step 1 order row remains (idempotency guard for retry) | n/a | Retry with same orderId; createOrder is idempotent |
| Step 2 fails AFTER Redis DECR | Redis over-decremented by 1; UpdateAwardStockJob reconciles on next run | UpdateAwardStockJob | Scheduled reconciliation |
| Step 3 fails (saveUserAwardRecord) | user_award_record not written; orderId guard ensures retries do not re-execute Step 1 or 2; retry Step 3 only | Caller / retry loop | Exception; task outbox not written |
| Step 3 times out (award written, task not sent) | task row is create state; SendMessageTaskJob retries MQ publish | SendMessageTaskJob | Scheduled poll |

**Retry strategy:** the existing `user_raffle_order` + `task` table durability pattern
already handles Steps 1 and 3 retries. No new retry infrastructure is needed in this
batch.

---

## 8. Ordering Guarantees and At-Least-Once Delivery

The draw orchestration must not produce duplicate awards for the same `orderId`.
Existing guards:

- `user_raffle_order.order_id` has a UNIQUE constraint (via service-layer duplicate check).
- `user_award_record` write checks for duplicate `order_id` before INSERT.
- `AwardRepository.saveGiveOutPrizesAggregate` uses `updateAwardRecordCompletedState` which
  checks state transition before updating — idempotent under duplicate delivery.

These guards must be preserved when any step moves cross-service. No new guard is needed
in this batch because no step moves.

---

## 9. Preconditions Before Any Synchronous Write Moves

The following must all be true before any draw step is moved out of `market-service`:

1. **This document (Phase 5-G) approved** — saga design reviewed by Engineering lead.
2. **Phase 7-D DDL applied by DBA** — `draw_saga_outbox_{000..003}` tables present.
3. **Phase 7 datasource isolation complete** — activity-service has exclusive DB credentials.
4. **Mapper XML migration complete** — activity table mapper XMLs moved to activity-service resources.
5. **P99 latency baseline measured** — in-process draw P99 documented; remote decision accepted P99 < in-process + 20ms.
6. **Rollback runbook approved by Oncall** — per-step compensation SQL verified in staging.
7. **Phase 8-E approval gate** — DBA + Ops + Engineering + Oncall + Product sign-off.
8. **`activity.service.draw-outbox.enabled` flag defaults false** — flipped only by Phase 8-E cutover batch.

---

## 10. Explicit Non-Goals for This Batch (Phase 5-G)

1. No draw execution migration.
2. No `strategy.service.remote-decision.enabled` flag introduced.
3. No `award.service.remote-fulfillment.enabled` flag introduced.
4. No `activity.service.draw-outbox.enabled` flag introduced.
5. `IDrawOutboxPort` is NOT wired into `RaffleApplicationService`.
6. No `draw_saga_outbox` DDL created or applied.
7. No `DrawSagaOutboxDispatchJob` introduced.
8. No mapper XML migration.
9. No `RaffleApplicationService` or `RaffleActivityController` move.
10. No MQ consumer or XXL-Job handler added to activity-service.
11. No changes to `docs/evidence/generated`.
12. No dangerous flag defaults changed.

---

## 11. Next Steps After Phase 5-G

| Batch | Title | Type | Prerequisite |
|-------|-------|------|-------------|
| 6-A | DAO ownership matrix | docs | Phase 5-G approved |
| 7-A | Per-service table ownership matrix | docs | 6-A complete |
| 7-D | Activity draw outbox proposed DDL | docs | 5-G saga design approved |
| 8-E | Activity-service draw cutover | ops | 7-D DDL applied; all preconditions in §9 met |

The recommended next batch is **Phase 6-A** (DAO ownership matrix) — it is docs-only,
low risk, and feeds Phase 7 so that table migration work can proceed in parallel with
activity-service infrastructure wiring.
