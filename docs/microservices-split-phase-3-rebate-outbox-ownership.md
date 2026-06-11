# Phase 3-D: Rebate Task / Outbox Ownership Decision

> Scope: decomposition-only analysis. No DDL is executed. All proposed schema
> changes are marked **proposed-only** and require a DBA window to apply.

Last revised: 2026-06-11.
Status anchor: Phase 3-A/B/C complete (tag `phase-3-rebate-read-adapter-boundary`).

---

## 1. Current Rebate Write Path

```
HTTP POST /raffle/activity/calendar_sign_rebate
  └── RaffleActivityController.calendarSignRebate(userId)
        └── IRebateOrderAdapter.createOrder(BehaviorEntity)        ← adapter boundary (Phase 3 Batch 2)
              ├── [flag=false] LocalRebateOrderAdapter
              │     └── IBehaviorRebateService.createOrder(BehaviorEntity)
              │           └── BehaviorRebateRepository.saveUserRebateRecord(...)
              │                 ├── INSERT daily_behavior_rebate      (rebate-owned)
              │                 ├── INSERT user_behavior_rebate_order (rebate-owned)
              │                 └── INSERT task                       (shared generic outbox)
              └── [flag=true] RebateRemoteCreateOrderAdapter
                    └── IRebateService.rebate(...) via DubboReference(check=false)
                          └── big-market-rebate-service.RebateServiceRPC.rebate(...)
                                └── IBehaviorRebateService.createOrder(BehaviorEntity)
                                      └── BehaviorRebateRepository.saveUserRebateRecord(...)
                                            ├── INSERT daily_behavior_rebate
                                            ├── INSERT user_behavior_rebate_order
                                            └── INSERT task
```

The `task` row is published by `SendMessageTaskJob` (in `message-job-service`), which polls the `task` table
and publishes the rebate confirmation event to RabbitMQ. `RebateMessageConsumer` (also in `message-job-service`)
then receives the event and updates rebate status.

---

## 2. Tables Logically Owned by Rebate-Service

| Table | Logical owner | Shard count | Notes |
|-------|---------------|-------------|-------|
| `daily_behavior_rebate` | rebate-service | 2 (`_000`, `_001`) | Rebate config per behavior type per date; read-heavy |
| `user_behavior_rebate_order` | rebate-service | 2 (`_000`, `_001`) | Per-user rebate order record; idempotency key = `userId + SIGN + outBusinessNo` |

These two tables are logically owned by `rebate-service`. No other bounded context writes to them.
However, they are currently accessed via the shared `big-market-infrastructure` jar with no Maven boundary
enforcing that ownership — any launcher that scans `com.dyx.market.infrastructure` can call the rebate DAOs.

---

## 3. Shared Task Table: Current Coupling Point

`task` is a generic outbox table shared across multiple bounded contexts:

| Context writing `task` | Purpose |
|------------------------|---------|
| rebate | Publish rebate confirmation event after `user_behavior_rebate_order` write |
| award | Publish award fulfillment event after `user_award_record` write (via `credit_award_task` outbox — Phase 2.3 separate path) |
| activity | Some activity-related task rows (to be confirmed in Phase 5-A) |

`task` is polled by `SendMessageTaskJob` in `message-job-service`, which publishes to RabbitMQ regardless
of which domain produced the row. This means:

- Rebate-service cannot independently evolve its outbox schema without coordinating with all other consumers.
- A failure in rebate's outbox handling can block or delay non-rebate task processing if the job is blocked.
- There is no per-service retry / dead-letter isolation for rebate task rows.

The `credit_award_task` table (Phase 2.3) is the precedent for per-domain outbox isolation: a separate table
with its own schema, its own job, and its own retry loop. Rebate should eventually follow the same pattern.

---

## 4. Decision: Option A — Keep Shared Task Table with Explicit Ownership Rules (Phase 3)

**Chosen for Phase 3.** Rationale:

- Introducing a new `rebate_task_outbox` table requires DDL execution, a DBA staging window, and changes to
  `BehaviorRebateRepository`, `SendMessageTaskJob`, and `RebateMessageConsumer` — all of which are outside
  the scope of a repo-only batch.
- The existing rebate write path is low-volume (calendar sign-in, once per user per day). The shared `task`
  table is not a performance bottleneck in this traffic profile.
- Phase 2.3 already established `credit_award_task` as the precedent. The rebate outbox split can follow
  that pattern in Phase 7-C without design risk.

**Explicit ownership rules accepted for Phase 3:**

1. Only `big-market-domain/domain.rebate` code (`BehaviorRebateRepository`) writes rebate rows to `task`.
2. The `task` table is not considered rebate-service-owned; it is shared and will remain in `big-market-infrastructure`.
3. `task_mapper.xml` is intentionally included in `big-market-rebate-service`'s mapper resources. This is a
   temporary coupling accepted until Phase 7-C introduces a dedicated outbox table.
4. No other launcher's rebate flows may write directly to `user_behavior_rebate_order` or `daily_behavior_rebate`
   except through the `IBehaviorRebateService` domain interface or the `IRebateService` Dubbo contract.

---

## 5. Option B — Rebate-Owned Outbox Table (Phase 7-C, Proposed Only)

**Not applied in this batch. Proposed-only.**

When the team is ready (after rebate-service traffic cutover is stable), introduce:

```sql
-- PROPOSED ONLY. DO NOT EXECUTE. Apply in a DBA staging window as part of Phase 7-C.
-- rebate_task_outbox_{000..003} mirrors the generic task table schema but is owned
-- exclusively by rebate-service, matching the credit_award_task precedent from Phase 2.3.

CREATE TABLE rebate_task_outbox_000 (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     VARCHAR(32) NOT NULL COMMENT 'User ID',
    topic       VARCHAR(256) NOT NULL COMMENT 'MQ topic',
    message_id  VARCHAR(128) NOT NULL COMMENT 'Idempotency key',
    message     VARCHAR(4096) NOT NULL COMMENT 'Serialized event payload',
    state       VARCHAR(16) NOT NULL COMMENT 'create | completed | fail',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uidx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Rebate task outbox shard 000';

-- Repeat for _001, _002, _003 if sharding is required to match user_behavior_rebate_order shards.
```

When this DDL is applied, the following code changes are also required (Phase 7-C scope):

- `BehaviorRebateRepository.saveUserRebateRecord` writes to `rebate_task_outbox_{shard}` instead of `task`.
- A new `RebateTaskOutboxJob` is added to `big-market-rebate-service` (or a dedicated `rebate-job-service`).
- `SendMessageTaskJob` no longer polls rebate rows (filter by topic or remove rebate topic from its poll scope).
- `RebateMessageConsumer` remains in its current position or moves into `big-market-rebate-service` depending on
  Phase 7-B job ownership decision.

---

## 6. RebateMessageConsumer and Job Ownership Concerns

`RebateMessageConsumer` currently lives in `big-market-message-job-service` and processes the MQ event
published by `SendMessageTaskJob` after a rebate order is written. This creates two job ownership concerns:

**Concern A — Polling job ownership:** `SendMessageTaskJob` is a shared job that polls `task` rows for all
contexts. Rebate's task rows are polled by the same job that handles award and activity task rows. Once a
dedicated `rebate_task_outbox` table exists (Phase 7-C), rebate polling must be moved or filtered.

**Concern B — Consumer ownership:** `RebateMessageConsumer` handles the downstream MQ event and updates
rebate order status. Logically it belongs to `rebate-service`, but moving it requires:
1. `big-market-rebate-service` to host a RabbitMQ listener (adding `spring-boot-starter-amqp` — already in
   the rebate-service pom).
2. The consumer routing to be verified in staging before any rollover.
3. A Phase 7/8 cutover batch where `message-job-service`'s `RebateMessageConsumer` is disabled.

**Decision for Phase 3:** both job and consumer remain in `message-job-service`. The coupling is accepted
as a temporary state, explicitly documented here.

---

## 7. Conditions for Rebate-Service to Be Considered Independently Data-Owned

All of the following must be complete before `rebate-service` is considered independently data-owned:

| Condition | Phase |
|-----------|-------|
| `user_behavior_rebate_order` and `daily_behavior_rebate` accessible only through `rebate-service` DB credentials | Phase 7-E/F |
| `rebate_task_outbox_{000..003}` DDL applied in staging and production (DBA window) | Phase 7-C |
| `BehaviorRebateRepository` writes to `rebate_task_outbox` instead of `task` | Phase 7-C |
| `SendMessageTaskJob` no longer polls rebate rows from `task` | Phase 7-C |
| `RebateMessageConsumer` moved or confirmed as staying in `message-job-service` with explicit ownership notation | Phase 7-B / 8-C |
| `task_mapper.xml` removed from `big-market-rebate-service` mapper resources | Phase 7-C follow-on |

---

## 8. Validation

The following scripts confirm the repo-only structural constraints accepted in this doc:

```bash
bash scripts/validate-microservices-phase-3-rebate-dependency-narrowing.sh
bash scripts/validate-microservices-phase-3-rebate-cutover-readiness.sh
```

The dependency narrowing validator confirms:
- `task_mapper.xml` is present in rebate-service (accepted coupling).
- No forbidden mapper XMLs are in rebate-service.
- No dangerous remote flags are hardcoded true.
- No generated evidence files are tracked.
