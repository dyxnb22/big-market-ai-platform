# DAO Ownership Matrix

Compact source for DAO ownership and service-boundary learning. Current status:
domain-level DAO coupling is resolved through ports/adapters for the local
microservices architecture. That does **not** mean that every service launcher
is technically prevented from loading every shared Mapper or from performing
every cross-context write.

**Enforcement level:** this matrix is a **logical convention with partial
compile-time/resource checks**, not a complete database-per-service boundary.
Runtime boundaries rely on ports/adapters and Docker profiles. CI adds domain
and launcher ArchUnit rules plus Mapper/DDL gates in
`scripts/validate-mapper-ddl-gates.sh`; shared Mapper XML remains duplicated per
launcher and service-level SQL ownership is not yet fully enforced.

## Contexts

activity / draw; account / quota; credit; fulfillment / award; rebate;
strategy; task / outbox; chat billing / compensation; MQ / DLQ; query / ES;
auth; admin / config; chatbot.

## Owner rules

An Owner is the service responsible for a table's meaning, DDL, state machine,
write contract, and compatibility. The default rule is that other services use
typed RPC/ports for commands and may only use explicitly documented shared
queries. The following are deliberate learning-project exceptions rather than
proof of strict single-writer isolation:

| Owner | Controlled exceptions |
|---|---|
| account | `message-job` temporarily claims and transitions `raffle_activity_order` during payment-delivery reconciliation; account remains the Owner of the order and all credit/quota invariants. |
| market | `message-job` completes `user_award_record`, advances stock projections/ledgers, and reconciles chat session states; these writes are explicit compensating/projector operations protected by idempotency keys. `account` may read stable SKU/strategy configuration. |
| message-job | Producers may append to `task`, `pending_remote_write_task`, and stock-confirm tasks through typed Ports. `message-job` alone advances retry/failed/manual/done states and owns DLQ persistence. |

The shared physical MySQL container means these are logical boundaries, not
independent database permission or failure domains.

## Transaction and failure-domain notes

- Account decrement/rollback and its quota ledger are one account-local
  transaction. Market's Docker path calls that contract over RPC; it does not
  make the market request and account transaction atomic.
- An UNKNOWN account response is handed off with the original business key to
  central `big_market.pending_remote_write_task`; reconciliation calls the
  account rollback contract and then advances the task state. The central table
  is independent of a user shard and account JVM, but it still runs on the same
  MySQL host as `big_market_01`/`big_market_02`.
- `task` intentionally uses producer append plus message-job state transition;
  this is a controlled shared Outbox exception, not a strict single-writer
  table.
- Chatbot's direct session fallback and message-job's order/stock/award
  projection writes are transitional, registered exceptions. This learning
  freeze does not attempt to replace them with new internal RPCs.

## Table Coverage

`raffle_activity`, `raffle_activity_account`, `raffle_activity_account_day`,
`raffle_activity_account_month`, `raffle_activity_count`,
`raffle_activity_order`, `raffle_activity_sku`, `raffle_activity_stage`,
`raffle_quota_decrement_ledger`, `user_raffle_order`,
`user_credit_account`, `user_credit_order`, `credit_award_task`, `award`,
`user_award_record`, `daily_behavior_rebate`, `user_behavior_rebate_order`,
`strategy`, `strategy_award`, `strategy_rule`, `rule_tree`, `rule_tree_node`,
`rule_tree_node_line`, `task`, `chat_credit_session`, `mq_dead_letter`.

## AL Couplings

| AL | Historical coupling | Status |
|----|---------------------|--------|
| AL-1 | StrategyRepository -> IRaffleActivityDao | resolved through `IStrategyActivityMappingPort` |
| AL-2 | StrategyRepository -> IRaffleActivityAccountDao | resolved via IStrategyActivityAccountPort; AL-2/AL-3 |
| AL-3 | StrategyRepository -> IRaffleActivityAccountDayDao | resolved via IStrategyActivityAccountPort; AL-2/AL-3 |
| AL-4 | ActivityRepository -> IUserCreditAccountDao | resolved through account/credit ports |
| AL-5 | AwardRepository -> IUserRaffleOrderDao | resolved through award activity-order port |
| AL-6 | AwardRepository -> IUserCreditAccountDao | AL-6 resolved via IAwardCreditWritePort |
| AL-7 | DispatchCreditAwardTaskJob -> ICreditAwardTaskDao | resolved through credit award dispatch port |
| AL-8 | BehaviorRebateRepository -> ITaskDao | decision complete; resolved via IRebateTaskOutboxPort |
| AL-9 | CreditRepository -> ITaskDao | decision complete; resolved via ICreditTradeTaskOutboxPort |
| AL-10 | AwardRepository -> ITaskDao | decision complete; resolved via IAwardDispatchTaskOutboxPort |
| AL-11 | AwardRepository -> ICreditAwardTaskDao | AL-11 resolved via IAwardCreditWritePort |

## Enforcement

Before new DAO, repository, Mapper, or service-boundary changes, update this
file with the Owner and the exact exception. At minimum, check:

1. account-owned credit/quota commands still use account RPC in Docker;
2. market Docker does not install local account write adapters;
3. new `message-job` direct writes to account/market tables are added to the
   exception list with an idempotency key and state transition;
4. every duplicated Mapper statement is compared across launchers, while
   service-specific statements are explicitly documented;
5. fresh DDL and old-volume migrations agree on state widths, especially
   `pending_remote_write_task.state VARCHAR(24)`, task retry metadata, and
   chat `manual_pending`/`last_error` fields.

This checklist is **not** a claim that the shared infrastructure scan is
strictly owner-scoped. Physical single-source Mapper XML remains deferred
(BM-017 subset).
Decision notes are consolidated in `docs/MICROSERVICES.md` and
`docs/LEARNING-FREEZE.md`.

## Cross-References

- Current index: `docs/MICROSERVICES.md`
- Readiness boundary: `docs/LEARNING-FREEZE.md`
