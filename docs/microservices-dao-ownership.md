# DAO Ownership Matrix

Compact source for DAO ownership and service-boundary learning. Current status:
direct DAO coupling is resolved through ports/adapters for the local
microservices architecture.

## Contexts

activity / draw; account / quota; credit; fulfillment / award; rebate;
strategy; task / outbox; query / ES; auth; admin / config; chatbot.

## Table Coverage

`raffle_activity`, `raffle_activity_account`, `raffle_activity_account_day`,
`raffle_activity_account_month`, `raffle_activity_count`,
`raffle_activity_order`, `raffle_activity_sku`, `raffle_activity_stage`,
`raffle_quota_decrement_ledger`, `user_raffle_order`,
`user_credit_account`, `user_credit_order`, `credit_award_task`, `award`,
`user_award_record`, `daily_behavior_rebate`, `user_behavior_rebate_order`,
`strategy`, `strategy_award`, `strategy_rule`, `rule_tree`, `rule_tree_node`,
`rule_tree_node_line`, `task`.

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

Boundary enforcement requires this file to list DAO owners before new DAO or
repository boundary changes. Historical decision notes live in
`docs/archive/microservices-historical-docs-index.md`.

## Cross-References

- Current index: `docs/MICROSERVICES.md`
- Historical summary: `docs/archive/microservices-history.md`
