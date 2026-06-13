# DAO Ownership Matrix

Compact source for Phase 6-A / Phase 6-B validators. Current status:
direct DAO coupling resolved; physical runtime table isolation remains Phase 8 external-gated.

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
| AL-1 | StrategyRepository -> IRaffleActivityDao | RESOLVED Phase 7-A AL-1 |
| AL-2 | StrategyRepository -> IRaffleActivityAccountDao | resolved via IStrategyActivityAccountPort; AL-2/AL-3 |
| AL-3 | StrategyRepository -> IRaffleActivityAccountDayDao | resolved via IStrategyActivityAccountPort; AL-2/AL-3 |
| AL-4 | ActivityRepository -> IUserCreditAccountDao | RESOLVED Phase 7-A prep |
| AL-5 | AwardRepository -> IUserRaffleOrderDao | RESOLVED Phase 7-A prep |
| AL-6 | AwardRepository -> IUserCreditAccountDao | AL-6 resolved via IAwardCreditWritePort |
| AL-7 | DispatchCreditAwardTaskJob -> ICreditAwardTaskDao | RESOLVED Phase 7-A prep |
| AL-8 | BehaviorRebateRepository -> ITaskDao | decision complete; resolved via IRebateTaskOutboxPort |
| AL-9 | CreditRepository -> ITaskDao | decision complete; resolved via ICreditTradeTaskOutboxPort |
| AL-10 | AwardRepository -> ITaskDao | decision complete; resolved via IAwardDispatchTaskOutboxPort |
| AL-11 | AwardRepository -> ICreditAwardTaskDao | AL-11 resolved via IAwardCreditWritePort |

## Enforcement

Phase 6-B / phase-6-package enforcement requires this file to list DAO owners
before new DAO or repository boundary changes. Phase 7-B decision doc:
`docs/archive/phases.md`.

## Cross-References

- Current index: `docs/MICROSERVICES.md`
- Historical summary: `docs/archive/microservices-history.md`
