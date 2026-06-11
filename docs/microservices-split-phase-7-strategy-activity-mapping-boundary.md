# Phase 7-A: Strategy–Activity Mapping Boundary (AL-1)

> Design doc for the final Phase 7-A StrategyRepository cross-boundary removal.
> Implements the `IStrategyActivityMappingPort` seam so `StrategyRepository`
> no longer imports `IRaffleActivityDao` directly.
>
> Authored: 2026-06-11. Status: **resolved** — tag `phase-7-strategy-activity-mapping-port`.

---

## 1. Problem Statement

`StrategyRepository` (strategy context) directly imports `IRaffleActivityDao`
(activity context) to resolve the bidirectional `activityId ↔ strategyId`
mapping stored in the `raffle_activity` table. This is violation **AL-1** in
the Phase 6-B cross-boundary allowlist.

This coupling blocks:
- Isolation of `raffle_activity` to activity-service (Phase 8-E).
- Clean strategy-service module boundary (Phase 8-D).

---

## 2. Physical Table and Ownership

| Item | Value |
|------|-------|
| Physical table | `raffle_activity` |
| Columns used | `activity_id` (PK), `strategy_id` (FK) |
| Current owner (Phase 6-A) | `ActivityRepository`, `StrategyRepository`† |
| Target owner | activity-service (`big-market-activity-service`) |
| Current callers of AL-1 | `StrategyRepository.queryStrategyIdByActivityId`, `StrategyRepository.queryTodayUserRaffleCount`, `StrategyRepository.queryActivityAccountTotalUseCount` |
| Migration phase | Phase 8-E (activity-service cutover) |

†: StrategyRepository's cross-access is the violation being removed here.

---

## 3. Why AL-1 Cannot Be Treated Exactly Like AL-2/AL-3

AL-2 and AL-3 involved **shard-routed** queries on `raffle_activity_account`
and `raffle_activity_account_day` — both tables are sharded by `userId` and
required `IDBRouterStrategy.doRouter(userId)` in the port implementation.

AL-1 involves **non-sharded, primary-key lookups** on `raffle_activity`:
- `queryStrategyIdByActivityId(activityId)` — single row by `activity_id`
- `queryActivityIdByStrategyId(strategyId)` — single row by `strategy_id`

The structural pattern of the port seam is identical, but the implementation
is simpler: `LocalStrategyActivityMappingPort` delegates straight to
`IRaffleActivityDao` with no shard routing setup or teardown.

The two methods in AL-1 are also used *intermediately*: after resolving
`activityId` from `strategyId`, `StrategyRepository` passes that value to
`IStrategyActivityAccountPort` for further account reads. The port seam cleanly
encapsulates this lookup without touching the subsequent account reads.

---

## 4. Ownership Decision

**Owner: activity context** (future: activity-service).

`raffle_activity` is an activity-context table. Strategy-service has a
*read-only, ID-mapping* need. The port correctly models this as: strategy
context requests a cross-context read from the activity boundary.

No alternative ownership (strategy context or shared read-model) is justified:
- Strategy context does not own any row in `raffle_activity`.
- A shared read-model would add unnecessary indirection for two simple lookups.

**Mapping direction (both directions needed):**
- `activityId → strategyId`: used by `queryStrategyIdByActivityId` — called
  from orchestration to find which strategy an activity uses.
- `strategyId → activityId`: used by `queryTodayUserRaffleCount` and
  `queryActivityAccountTotalUseCount` — resolves the activity for quota reads.

---

## 5. Design

### 5.1 New Domain Port

**File:** `big-market-domain/.../domain/strategy/adapter/port/IStrategyActivityMappingPort.java`

```java
public interface IStrategyActivityMappingPort {
    Long queryStrategyIdByActivityId(Long activityId);
    Long queryActivityIdByStrategyId(Long strategyId);
}
```

Lives in the strategy domain adapter layer, parallel to `IStrategyActivityAccountPort`.

### 5.2 Local Implementation

**File:** `big-market-infrastructure/.../adapter/port/LocalStrategyActivityMappingPort.java`

Delegates directly to `IRaffleActivityDao`:
- `queryStrategyIdByActivityId` → `raffleActivityDao.queryStrategyIdByActivityId(activityId)`
- `queryActivityIdByStrategyId` → `raffleActivityDao.queryActivityIdByStrategyId(strategyId)`

No shard routing. No behavior change. `IRaffleActivityDao` remains in infrastructure;
only the direct injection into `StrategyRepository` is removed.

### 5.3 StrategyRepository Update

`StrategyRepository` replaces:
```java
@Resource
private IRaffleActivityDao raffleActivityDao;
```
with:
```java
@Resource
private IStrategyActivityMappingPort strategyActivityMappingPort;
```

All three call sites updated:
| Method | Before | After |
|--------|--------|-------|
| `queryStrategyIdByActivityId` | `raffleActivityDao.queryStrategyIdByActivityId(activityId)` | `strategyActivityMappingPort.queryStrategyIdByActivityId(activityId)` |
| `queryTodayUserRaffleCount` | `raffleActivityDao.queryActivityIdByStrategyId(strategyId)` | `strategyActivityMappingPort.queryActivityIdByStrategyId(strategyId)` |
| `queryActivityAccountTotalUseCount` | `raffleActivityDao.queryActivityIdByStrategyId(strategyId)` | `strategyActivityMappingPort.queryActivityIdByStrategyId(strategyId)` |

---

## 6. Migration Risks

| Risk | Mitigation |
|------|-----------|
| Circular Spring dependency | `IStrategyActivityMappingPort` is in `domain.strategy.adapter.port`; `LocalStrategyActivityMappingPort` is in `infrastructure.adapter.port`. No circularity — same direction as all other local port impls. |
| Behavior regression | Both methods are simple pass-through delegates. Behavior is identical; no field transformation, no null handling changes. |
| Shard routing gap | `raffle_activity` is not sharded. No routing required. |
| `IRaffleActivityDao` still needed by `ActivityRepository` | `IRaffleActivityDao` is not removed — only the StrategyRepository injection is removed. The DAO remains in infrastructure and is still scanned. |

---

## 7. Remaining Cutover Conditions

This batch establishes the port seam only. The following remain required before
strategy-service and activity-service can be fully isolated:

1. **Phase 8-D** — strategy-service cutover: all strategy DAOs must be exclusive
   to strategy-service; `StrategyRepository` must route `IStrategyActivityMappingPort`
   to an activity-service remote read API.
2. **Phase 8-E** — activity-service cutover: `raffle_activity` table access must
   be gated behind the activity-service remote API; `IRaffleActivityDao` can then
   be removed from `LocalStrategyActivityMappingPort` and replaced with the
   remote implementation.
3. **Remote port implementation** — `ActivityRemoteStrategyActivityMappingPort`
   will call the activity-service API (once Phase 8-E endpoints are wired).
   This is NOT introduced in this batch; the remote flag would be
   `activity.service.remote-strategy-mapping.enabled=false`.

---

## 8. Non-Goals

- No mapper XMLs moved.
- No DAO files moved.
- No remote flags introduced.
- No activity-service HTTP controller or Dubbo provider added.
- No strategy rule evaluation behavior changed.
- No draw hot-path altered.
- No DDL.

---

## 9. Validation

Run `scripts/validate-microservices-phase-7-strategy-activity-mapping-boundary.sh` to verify:
- Design doc present.
- AL-1 resolved: `StrategyRepository` does not reference `IRaffleActivityDao`.
- `IStrategyActivityMappingPort` and `LocalStrategyActivityMappingPort` present.
- AL-2, AL-3, AL-4 remain resolved.
- No new forbidden DAO imports.
- Phase 6-B validator still green.
- Remote flags still disabled.
