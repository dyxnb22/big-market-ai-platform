# Phase 5-B — Draw-Command Boundary Design

> This document is the Phase 5-B deliverable: an architectural design for the
> future draw-command boundary, including command/response contract drafts,
> idempotency/rollback concerns, recommended orchestration option, and the
> explicit preconditions that must be met before any remote draw path is enabled.
>
> No remote draw command is introduced in this batch. No draw execution moves.
> This document shapes Phase 5-D (strategy decision port), 5-E (award
> fulfillment port), 5-F (activity-service scaffold), and 5-G (saga design).
>
> Last revised: 2026-06-11.
> Status anchor: Phase 5-A complete. Phase 5-B design doc only.

---

## 1. Current Draw Command Shape

The draw is initiated by `RaffleActivityController.draw(ActivityDrawRequestDTO)` in
`big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`.

The controller delegates to `RaffleApplicationService.executeDraw(ActivityDrawRequestEntity)`:

```
big-market-domain/src/main/java/com/dyx/market/domain/activity/application/
    RaffleApplicationService.java
    ActivityDrawRequestEntity.java
    ActivityDrawResponseEntity.java
```

### 1.1 Inbound draw parameters

| Field | Type | Source | Role |
|-------|------|--------|------|
| `userId` | String | HTTP request | Identifies the participant |
| `activityId` | Long | HTTP request | Identifies the active raffle activity |

### 1.2 Derived draw context (produced by Step 1 — createOrder)

| Field | Type | Origin | Role |
|-------|------|--------|------|
| `orderId` / `userRaffleOrderId` | String | `user_raffle_order.order_id` | Idempotency key for downstream strategy decision and award persistence |
| `strategyId` | Long | `raffle_activity.strategy_id` | Identifies the strategy to run |
| `endDateTime` | Date | `raffle_activity_count.end_date_time` | Time-bounds the strategy rule tree |
| `userId` | String | Propagated | Correlation key through all three steps |
| `activityId` | Long | Propagated | Correlation key through all three steps |

### 1.3 Current orchestration owner

`RaffleApplicationService` is the draw orchestrator. It lives in
`big-market-domain` and co-ordinates three in-process steps:

1. `IRaffleActivityPartakeService.createOrder` — participation record + quota decrement.
2. `IRaffleStrategy.performRaffle` — strategy rule-chain/tree evaluation → award id.
3. `IAwardService.saveUserAwardRecord` — award record + task outbox row in one transaction.

---

## 2. Target Options for Orchestration Boundary

### Option A — Keep orchestration in market-service; isolate remote adapters

`RaffleApplicationService` stays in `big-market-domain` (compiled into
`big-market-market-service`). Each of the three in-process calls is wrapped
in a port adapter interface:

- `IStrategyDecisionPort` (Phase 5-D) — wraps `IRaffleStrategy.performRaffle`.
- `IAwardFulfillmentPort` (Phase 5-E) — wraps `IAwardService.saveUserAwardRecord`.
- `IActivityPartakePort` (future) — wraps `IRaffleActivityPartakeService.createOrder`.

Remote implementations of each port are introduced behind feature flags
(`strategy.service.remote-decision.enabled`, `award.service.remote-fulfillment.enabled`,
etc.) and default false. The orchestrator itself never moves.

**Advantages:**
- Smallest incremental risk: each adapter is independently switchable.
- HTTP controller and orchestrator remain co-located; no network hop on the
  coordination path.
- Each adapter step can be validated and cut over separately.
- Does not require a new Spring Boot launcher until traffic justifies it.

**Disadvantages:**
- `big-market-market-service` retains a runtime dependency on strategy/award domain
  jars even after those contexts have their own services.
- Scaling market-service for high draw volume also scales the co-located orchestrator.

### Option B — Move orchestration to a new activity-service

`RaffleApplicationService` moves to `big-market-activity-service`. The HTTP
entry point (`RaffleActivityController.draw`) either remains in market-service
(forwarding to activity-service via Dubbo/HTTP) or moves entirely.

**Advantages:**
- Clean bounded-context ownership: activity-service owns the draw flow end-to-end.
- Enables independent scaling of the draw path.

**Disadvantages:**
- The orchestrator itself becomes a network hop caller; all three sub-calls must
  be fault-tolerant and idempotent before the move.
- Requires saga design (Phase 5-G) to be approved before any write step is
  moved; premature migration is the #1 risk in the master-plan risk register.
- Cannot be done before 5-D and 5-E adapters are stable (no adapter = no fallback).

---

## 3. Recommended Option

**Option A is recommended for Phase 5-B/C/D/E/F.**

Rationale:

1. Each adapter can be extracted and validated in isolation without touching
   the orchestrator; Option B requires moving the orchestrator atomically.
2. The saga design (Phase 5-G) is genuinely uncertain: if the result of 5-G
   is "keep synchronous" then Option A is the final state. Option B only becomes
   worth the risk if 5-G concludes that async draw orchestration is justified.
3. The adapter ports introduced in 5-D and 5-E are reusable in Option B — if
   5-G recommends activity-service orchestration, the ports become the Dubbo
   interfaces from the new service; no throw-away work.
4. The master-plan hard rule is: no synchronous write call moves out of
   market-service until Phase 5-G is signed off. Option A naturally enforces
   this rule; Option B creates pressure to move writes prematurely.

**Decision statement recorded here:** orchestration remains in market-service
through Phase 5-F inclusive. Phase 5-G may revise this decision if saga/workflow
evidence justifies the migration.

---

## 4. Command / Response Contract Draft

This section drafts the draw command and response contracts that would be
published if/when a draw command boundary crosses a service boundary. These
contracts are **not wired to any remote path** in this batch; they are
specifications for future Phase 5-E/F/G work.

### 4.1 DrawCommand (inbound to orchestrator)

```
DrawCommand {
    String  userId            // participant identifier; required
    Long    activityId        // activity identifier; required
    String  clientRequestId   // caller-supplied idempotency key; optional;
                              // if absent, orderId from createOrder serves as key
}
```

### 4.2 DrawResult (outbound from orchestrator)

```
DrawResult {
    String  orderId           // the raffle order created in Step 1
    Integer awardId           // awarded prize id
    String  awardTitle        // display title of the prize
    Integer awardIndex        // sort index (used for front-end rank display)
    String  awardConfig       // configuration payload specific to the award type
}
```

### 4.3 Correlation keys through the draw flow

| Step | Produces | Consumed by |
|------|----------|-------------|
| createOrder | `orderId` | performRaffle (endDateTime, strategyId), saveUserAwardRecord (orderId) |
| performRaffle | `awardId`, `awardTitle`, `awardConfig`, `sort` | saveUserAwardRecord |
| saveUserAwardRecord | `user_award_record` row + `task` outbox row | `SendMessageTaskJob` → MQ → fulfillment |

The `orderId` is the primary correlation key. It is generated in `createOrder`,
carried through `performRaffle` as context, and stored as the idempotency key
in `user_award_record.order_id`. Any future cross-service draw path must
propagate `orderId` as the saga correlation ID.

---

## 5. Idempotency and Rollback Concerns

### 5.1 createOrder idempotency

`user_raffle_order` has a unique constraint on `(user_id, activity_id, order_id)`.
A second `createOrder` call with the same `orderId` returns the existing record
rather than inserting a duplicate. This is the baseline idempotency primitive
for the draw flow.

### 5.2 performRaffle and stock decrement

`subtractionAwardStock` decrements a Redis counter before `saveUserAwardRecord`
commits to DB. If `saveUserAwardRecord` fails after `subtractionAwardStock`,
the Redis stock is under-counted until `UpdateAwardStockJob` reconciles from
the DB. This is the current accepted asymmetry.

**Phase 5-D constraint:** the `IStrategyDecisionPort` local implementation must
not change this timing. The port wraps the existing `IRaffleStrategy.performRaffle`
call; no new retry or pre-commit logic is added.

### 5.3 Award persistence and outbox atomicity

`AwardRepository.saveUserAwardRecord` commits `user_award_record` + `task` outbox
row in a single local transaction (sharded by userId). This is the primary
consistency invariant. It must not be broken until Phase 5-G provides a
cross-service outbox design.

### 5.4 Future saga compensation points

If any step is ever moved cross-service, the following compensation must be designed:

| Step moved | Compensation needed |
|------------|---------------------|
| createOrder → activity-service | `rollbackOrder(userId, orderId)` if downstream strategy or award steps fail |
| performRaffle → strategy-service | `rollbackStockDecrement(strategyId, awardId, orderId)` on failure |
| saveUserAwardRecord → fulfillment-service | `cancelAwardRecord(userId, orderId)` + re-emit task outbox |

None of these compensations exist yet. Phase 5-G must design them before any
write step crosses a service boundary.

---

## 6. Why No Remote Draw Command Is Introduced in This Batch

1. The saga / idempotency design required for any cross-service write step
   has not been approved (Phase 5-G).
2. `performRaffle` does a Redis stock decrement that is tightly coupled to
   the in-process award persistence; decoupling this safely requires the
   Phase 5-G outbox design.
3. The draw path latency baseline has not been measured; enabling a remote hop
   before the baseline is established is a risk-register item (Risk #4).
4. The `IStrategyDecisionPort` (Phase 5-D) must be stable for ≥1 batch before
   any remote implementation is considered.

---

## 7. Required Preconditions Before Any Remote Draw Path

The following must ALL be true before `strategy.service.remote-decision.enabled`
or any equivalent remote draw flag is set to true:

1. **Phase 5-D stable** — `IStrategyDecisionPort` has been in production (flag=false,
   local path) for at least one release without regression.
2. **Phase 5-E stable** — `IAwardFulfillmentPort` covers the raffle award persistence
   path (not just the credit-award outbox path).
3. **Phase 5-G approved** — saga/workflow design is written, reviewed, and signed off.
4. **Idempotency ledger** — a per-step idempotency ledger for remote strategy decisions
   exists (analogous to `raffle_quota_decrement_ledger` for quota).
5. **P99 latency baseline** — in-process baseline measured; remote-path P99 < +20%
   acceptance criterion documented.
6. **Staging validation** — `big-market-strategy-service` registered in staging Nacos
   and verified with end-to-end draw flow.
7. **Rollback runbooks** — compensation SQL for Redis stock and award record documented.
8. **DBA + Ops + Engineering + Oncall sign-off** — per the Phase 8 approval gate.

---

## 8. How This Design Feeds Subsequent Sub-Batches

| Sub-batch | Dependency on this design |
|-----------|--------------------------|
| 5-D | Introduces `IStrategyDecisionPort` as the local seam for Option A; local impl delegates to `IRaffleStrategy.performRaffle` |
| 5-E | Extends `IAwardFulfillmentPort` to the raffle award path; uses `orderId` from §4.3 as the correlation key |
| 5-F | `big-market-activity-service` scaffold can be defined once Option A adapter pattern is stable; no orchestration moves yet |
| 5-G | Saga design takes the compensation table from §5.4 as its starting requirements; `DrawCommand` contract from §4 informs the async command shape |
