# Phase 7-B - Generic Task Outbox Ownership

> Decision artifact for the shared generic `task` table. This batch is
> documentation and guardrails only: no DDL, no mapper movement, no DAO movement,
> no service traffic, and no runtime task write/read behavior changes.
>
> Last revised: 2026-06-11.

---

## 1. Current Usage

`ITaskDao` maps the physical `task` table and is currently used as a generic
transactional outbox by three bounded contexts:

| Allowlist | Caller | Domain write in same transaction | Outbox operation | Current poll/update path |
|-----------|--------|----------------------------------|------------------|--------------------------|
| AL-8 | `BehaviorRebateRepository` | `user_behavior_rebate_order` | inserts a `task` row for rebate order events | publishes immediately through `EventPublisher`, then updates `task` send state; `SendMessageTaskJob` remains the compensation poller |
| AL-9 | `CreditRepository` | `user_credit_account`, `user_credit_order` | inserts a `task` row for credit order events | publishes immediately through `EventPublisher`, then updates `task` send state; `SendMessageTaskJob` remains the compensation poller |
| AL-10 | `AwardRepository` | `user_award_record` and `user_raffle_order` state update | inserts a `task` row for award dispatch events | publishes immediately through `EventPublisher`, then updates `task` send state; `SendMessageTaskJob` remains the compensation poller |

`TaskRepository` also owns the generic task-domain read/update surface used by
the compensation job. All of these paths still target the same logical `task`
table today, with mapper copies in the legacy app and dark-launch service
modules.

---

## 2. Ownership Ambiguity

The `task` table is not a bounded-context table. It contains messages emitted
by rebate, credit, and award flows, while the actual domain rows are owned by
different future services:

| Domain event source | Target service | Domain table owner | Shared table today |
|---------------------|----------------|--------------------|--------------------|
| rebate order event | rebate-service | rebate-service | `task` |
| credit trade event | account-service | account-service | `task` |
| award dispatch event | fulfillment-service | fulfillment-service | `task` |

That ambiguity blocks data isolation because a service cannot receive exclusive
DB credentials if its local transaction must also write a table shared with
other services. The ambiguity also makes mapper placement unclear: keeping
`task_mapper.xml` everywhere preserves dark-launch compatibility, but it does
not express a future owner.

---

## 3. Why A Shared Task Table Blocks Isolation

The current outbox pattern is correct locally: domain row and outbox row commit
in one transaction, then message delivery is retried until the send state is
updated. The problem is the table boundary, not the pattern.

If `task` stays shared:

1. Rebate, account, and fulfillment services all need write permission to the
   same table after datasource isolation.
2. The compensation poller cannot filter by a strong ownership boundary unless
   the table gains domain columns and per-domain job semantics.
3. Mapper XMLs and `ITaskDao` stay required in multiple services, preserving the
   exact infrastructure coupling Phase 7 is meant to remove.
4. Sharded DB ownership remains mixed: a row in `task_000` could correspond to
   a rebate, credit, or award domain transaction on the same user shard.

The existing `credit_award_task` path shows the safer pattern: a table with a
single business owner, a narrow schema, an idempotency key tied to that business
event, and an explicit poller contract.

---

## 4. Decision

**Chosen strategy: split the generic `task` table into per-domain outbox/task
tables.**

`task` remains the compatibility table until each domain has a proposed DDL
file, staging evidence, and a traffic batch. AL-8, AL-9, and AL-10 are therefore
**decision-complete but not runtime-resolved** in Phase 7-B.

Rejected alternative: create a shared `message-outbox-service`.

Reason: a shared outbox service centralizes polling but does not preserve local
transaction atomicity unless every domain writes through a distributed protocol
or a shared database credential. That adds operational coupling without solving
the table ownership problem. It is only worth revisiting if multiple domains
need cross-domain ordering guarantees, which they do not today.

---

## 5. Proposed Future Table Names

Concrete DDL is deferred to proposed SQL batches. The intended table ownership
is:

| Current coupling | Future table | Owner | Notes |
|------------------|--------------|-------|-------|
| AL-8 `BehaviorRebateRepository -> ITaskDao` | `rebate_task_outbox_{000..003}` | rebate-service | Follows the Phase 3-D decision and replaces rebate writes to `task` after DBA-applied DDL |
| AL-9 `CreditRepository -> ITaskDao` | `credit_trade_task_outbox_{000..003}` | account-service | Carries credit trade events from `saveUserCreditTradeOrder`; separate from `credit_award_task` |
| AL-10 `AwardRepository -> ITaskDao` | `award_dispatch_task_outbox_{000..003}` | fulfillment-service | Carries normal award dispatch events from `saveUserAwardRecord` |
| existing credit-award flow | `credit_award_task_{000..003}` | account-service | Existing precedent for fulfillment-to-account credit award dispatch |
| future draw saga | `draw_saga_outbox_{000..003}` | activity-service | Deferred to Phase 7-D per the Phase 5-G saga design |

Each table remains sharded by `userId` with the existing 2-DB / 4-table router
pattern unless Phase 7-F deliberately changes the schema topology.

---

## 6. Migration Order

1. **Phase 7-B complete:** record this decision and enforce guardrails. No
   runtime behavior changes.
2. **Phase 7-C:** add proposed-only SQL for `rebate_task_outbox_{000..003}` and
   a validator that proves it is not wired or applied.
3. **Phase 7-C2:** add proposed-only SQL for `credit_trade_task_outbox_{000..003}`.
4. **Phase 7-C3:** add proposed-only SQL for `award_dispatch_task_outbox_{000..003}`.
5. Add per-domain DAO/PO/mapper scaffolds behind default-false flags only after
   proposed SQL is reviewed.
6. In staging, DBA applies one domain outbox DDL at a time. The corresponding
   service writes both old and new rows only if a dedicated dual-write batch is
   approved; otherwise it switches under a default-false flag after backfill is
   deemed unnecessary.
7. Move each compensation poller from generic `task` to the domain outbox after
   staging proves pending rows drain normally.
8. Retire the generic `task` table only after all domains have zero pending rows
   and no service imports `ITaskDao` for write paths.

---

## 7. Compatibility Strategy

Until a domain cutover batch is approved:

- `BehaviorRebateRepository`, `CreditRepository`, `AwardRepository`, and
  `TaskRepository` continue using `ITaskDao`.
- `task_mapper.xml` stays in existing mapper resource locations.
- `SendMessageTaskJob` continues to handle generic `task` compensation.
- All remote and production traffic flags remain default false.
- No service receives exclusive DB grants that would break the current local
  transaction.

During a future per-domain migration, compatibility must be explicit:

- table creation is proposed in `docs/sql/proposed-*.sql` and applied by DBA,
- mapper and DAO scaffolds are introduced without default-on traffic,
- old `task` rows are drained before a service stops polling them,
- message idempotency keys stay the same as the current `task.message_id`.

---

## 8. Rollback Strategy

Phase 7-B rollback is documentation-only: revert this doc and validator.

Future runtime batches must use flag rollback:

| Stage | Rollback |
|-------|----------|
| proposed SQL only | remove or revise the proposal; no database action |
| DDL applied, not wired | leave empty outbox tables in place or DBA drops them in a separate approved window |
| new writer enabled in staging | flip the writer flag false and resume generic `task` writes |
| poller moved in staging | flip poller flag false and resume `SendMessageTaskJob` for generic `task` rows |
| production cutover | flip writer and poller flags false; pending rows in the new domain outbox remain retryable when re-enabled |

No rollback path should require deleting committed business-domain rows.

---

## 9. Validation Gates Before DDL Or Traffic Move

Before any DDL or traffic change for AL-8, AL-9, or AL-10:

1. `scripts/validate-microservices-phase-7-task-outbox-ownership.sh` passes.
2. Proposed SQL exists only under `docs/sql/proposed-*.sql` and is clearly
   marked proposed-only.
3. No mapper XMLs, DAOs, repositories, jobs, listeners, controllers, or Dubbo
   providers are moved in the proposal batch.
4. Phase 7-A resolved boundaries stay resolved: AL-1, AL-2, AL-3, and AL-4 do
   not regress.
5. Phase 6-B package boundary validator passes.
6. Remote/outbox/production flags remain default false in service resources and
   Docker Compose.
7. Staging DBA evidence confirms table existence, indexes, and shard suffixes
   before any writer flag can be enabled.
8. The owning service has an idempotency key and retry policy documented before
   the poller moves.

---

## 10. Phase 7-B Status

| Coupling | Status after Phase 7-B | Runtime state |
|----------|------------------------|---------------|
| AL-8 `BehaviorRebateRepository -> ITaskDao` | decision complete | still allowlisted; still writes `task` |
| AL-9 `CreditRepository -> ITaskDao` | decision complete | still allowlisted; still writes `task` |
| AL-10 `AwardRepository -> ITaskDao` | decision complete | still allowlisted; still writes `task` |

Recommended next batch: either Phase 7-C proposed DDL for
`rebate_task_outbox_{000..003}` or AL-5 `AwardRepository -> IUserRaffleOrderDao`
boundary prep. If the goal is to maximize data isolation progress, do AL-5 first
because it removes a live fulfillment-to-activity DAO dependency. If the goal is
to unblock rebate cutover planning, do Phase 7-C first.
