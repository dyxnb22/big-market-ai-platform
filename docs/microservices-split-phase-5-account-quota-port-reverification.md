# Phase 5-C — Account / Quota Port Re-Verification

> This document is the Phase 5-C deliverable: a re-verification that the
> `IActivityAccountPort` invariants established in Phase 2.2 (B11–B14) remain
> intact after the Phase 4 strategy-service work and the Phase 5-A orchestration
> mapping. No code changes are made in this batch. The document confirms the
> port is correctly positioned in the draw orchestration and lists the remaining
> blockers before the remote quota-decrement path can be enabled.
>
> Last revised: 2026-06-11.
> Status anchor: Phase 4-F complete. Phase 5-A map complete. Phase 5-C re-verify.

---

## 1. Current IActivityAccountPort Interface

**File:** `big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java`
**Package:** `com.dyx.market.domain.activity.adapter.port`
**Phase introduced:** Phase 2.2-B11

```java
public interface IActivityAccountPort {
    boolean decrementQuota(String userId, Long activityId, String outBusinessNo);
    void rollbackQuota(String userId, Long activityId, String outBusinessNo);
}
```

The interface defines two operations:

- `decrementQuota` — synchronous, pre-draw quota gate. Returns `true` if the
  quota was decremented (or was already decremented for this idempotency key).
  Returns `false` if quota is exhausted, causing the draw to be rejected.

- `rollbackQuota` — saga compensation. Restores a decremented slot when the
  downstream draw fails after quota was taken. Safe to call even if no
  matching `decrementQuota` was applied.

---

## 2. Implementations

### 2.1 Local implementation (default)

**File:** `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/port/LocalActivityAccountPort.java`
**Activation:** `@ConditionalOnProperty(name = "account.service.remote-quota-decrement.enabled", havingValue = "false", matchIfMissing = true)`

The local path delegates to `IActivityRepository.decrementQuotaWithLedger` and
`IActivityRepository.rollbackQuotaWithLedger`. This makes the local path
functionally equivalent to the remote path for testing, using the same ledger
table (`raffle_quota_decrement_ledger`) to enforce idempotency.

**Default behavior confirmed:** `account.service.remote-quota-decrement.enabled`
defaults `false` in all configuration files. `LocalActivityAccountPort` is the
active bean in all current deployments.

### 2.2 Remote implementation

**File:** `big-market-market-service/src/main/java/com/dyx/market/market/config/AccountRemoteActivityAccountPort.java`
**Activation:** `@ConditionalOnProperty(name = "account.service.remote-quota-decrement.enabled", havingValue = "true")`

The remote path routes `decrementQuota` and `rollbackQuota` to
`account-service` via `@DubboReference IAccountQuotaService`. This bean is
NOT activated in any current deployment. The flag default is:

```yaml
# big-market-market-service/src/main/resources/application.yml
account:
  service:
    remote-quota-decrement:
      enabled: ${ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:false}
```

---

## 3. Current Default Behavior in Draw Flow

`RaffleActivityPartakeService` (in `big-market-domain`) controls the draw
participation path. It reads a `remoteQuotaDecrementEnabled` flag at runtime.

When `account.service.remote-quota-decrement.enabled=false` (default):
- `saveCreatePartakeOrderAggregate` handles quota decrement directly via the
  existing repository method in the same local transaction as `user_raffle_order`.
- `IActivityAccountPort` is injected but not called on this path.

When `account.service.remote-quota-decrement.enabled=true`:
- `IActivityAccountPort.decrementQuota` is called.
- If the bean is `LocalActivityAccountPort`: calls the ledger-guarded repository.
- If the bean is `AccountRemoteActivityAccountPort`: routes to account-service Dubbo.

**Re-verification result:** this dual-path design is unchanged by Phase 4.
The Phase 4 work only touched the strategy-service read adapter and did not
modify `RaffleActivityPartakeService`, `IActivityAccountPort`, or either
implementation. ✓

---

## 4. Required Invariants from B11–B14 (Re-Verified)

| Invariant | Expected state | Re-verified state after Phase 4 |
|-----------|---------------|----------------------------------|
| Local default unchanged | `matchIfMissing=true` on `LocalActivityAccountPort` | ✓ Confirmed |
| Remote decrement disabled by default | `ACCOUNT_SERVICE_REMOTE_QUOTA_DECREMENT_ENABLED:-false` in market-service yml | ✓ Confirmed |
| Ledger idempotency | `decrementQuotaWithLedger` writes to `raffle_quota_decrement_ledger` with unique `(userId, activityId, outBusinessNo)` constraint | ✓ Unchanged |
| Rollback semantics | `rollbackQuotaWithLedger` sets ledger row state to rolled-back; safe on repeated call | ✓ Unchanged |
| No quota remote write traffic | `AccountRemoteActivityAccountPort` not activated; no live Dubbo calls to account-service quota endpoint | ✓ Confirmed |
| Phase 4 strategy changes isolated | Phase 4 adapters touch only `RaffleStrategyController` and strategy reads; no participation or quota paths modified | ✓ Confirmed |

---

## 5. How the Port Fits into Draw Orchestration

The draw flow in `RaffleApplicationService.executeDraw` has three steps:

```
Step 1: createOrder → IRaffleActivityPartakeService
          └── quota decrement is owned by createOrder (or optionally by IActivityAccountPort when flag=true)
Step 2: performRaffle → IRaffleStrategy (will become IStrategyDecisionPort in 5-D)
Step 3: saveUserAwardRecord → IAwardService
```

`IActivityAccountPort` is the seam for Step 1's quota-decrement sub-step.
It is invoked from `RaffleActivityPartakeService`, not from
`RaffleApplicationService` directly. This keeps the quota-decrement boundary
inside the partake domain rather than at the orchestrator level.

This design means that when the remote quota-decrement flag is enabled,
the `user_raffle_order` INSERT and the remote `decrementQuota` call are
**two separate operations** — the saga compensation via `rollbackQuota` must
handle any failure between them. This was the B11–B14 design intent and is
re-confirmed here.

---

## 6. Blockers Before Quota Decrement Can Be Remote in Draw Flow

All of the following must be resolved before
`account.service.remote-quota-decrement.enabled=true` in any environment:

1. **B12 DDL applied** — `raffle_quota_decrement_ledger` table must exist in
   the target environment's account-service database (Phase 8-A prerequisite).
2. **B13 account-service provider** — `AccountQuotaServiceRPC.decrementQuota`
   must not return `UN_ERROR` stub; full idempotency semantics must be
   implemented and verified.
3. **B14 saga compensation validated** — `rollbackQuota` must be verified
   end-to-end in a staging environment where a simulated draw failure triggers
   compensation.
4. **B17 + B18 staging templates** — the Phase 2.2 intake templates (DBA, Ops,
   Engineer, Oncall) must be completed per `docs/evidence/` requirements.
5. **Phase 5-G saga design approved** — `IActivityAccountPort` is used in
   `RaffleActivityPartakeService.createOrder`; if `createOrder` eventually moves
   to activity-service, the port's saga design must be re-validated in that
   context.
6. **No duplicate Dubbo provider** — `AccountQuotaServiceRPC` must be
   unambiguously registered in Nacos before any client routes to it.

---

## 7. Re-Verification Conclusion

The `IActivityAccountPort` boundary, its local and remote implementations, and
the invariants established in Phase 2.2 (B11–B14) are fully intact after Phase 4.

No code changes are required in this batch.

The validator `scripts/validate-microservices-phase-5-account-quota-port-reverification.sh`
confirms these invariants programmatically.
