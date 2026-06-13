# Microservices Legacy Cleanup Inventory

Status: EXTERNAL-GATED. External evidence required before disabling anything;
7-day stable gate before legacy-provider disable; 30-day removal gate before
mapper/fallback deletion.

## Legacy RPC Providers

RebateServiceRPC; RaffleStrategyServiceRPC; RaffleActivityController;
ErpOperateController.

## Default-Local Adapters

LocalAccountReadAdapter; LocalAccountCreditWriteAdapter;
LocalAccountQuotaWriteAdapter; LocalAwardDispatchAdapter;
LocalRebateOrderAdapter; LocalRebateReadAdapter; LocalStrategyReadAdapter.

## Local Fallback Ports

LocalActivityAccountPort; LocalStrategyActivityAccountPort;
LocalStrategyActivityMappingPort; LocalStrategyDecisionPort;
LocalAwardFulfillmentPort; LocalAwardCreditWritePort;
LocalCreditAwardTaskDispatchPort; LocalAwardActivityOrderPort;
LocalDrawOutboxPort.

## Shared Mapper Compatibility Copies

Compatibility directories:
`big-market-app/src/main/resources/mybatis/mapper/mysql`,
`big-market-market-service/src/main/resources/mybatis/mapper/mysql`,
`big-market-message-job-service/src/main/resources/mybatis/mapper/mysql`,
`big-market-account-service/src/main/resources/mybatis/mapper/mysql`,
`big-market-rebate-service/src/main/resources/mybatis/mapper/mysql`,
`big-market-strategy-service/src/main/resources/mybatis/mapper/mysql`.

`big-market-fulfillment-service` has service-owned mappers. (`big-market-activity-service` scaffold was removed.)

Compatibility set includes `strategy_rule_mapper.xml`,
`rule_tree_node_line_mapper.xml`, `raffle_activity_order_mapper.xml`,
`raffle_activity_sku_mapper.xml`, `award_mapper.xml`,
`user_credit_account_mapper.xml`, `user_award_record_mapper.xml`,
`daily_behavior_rebate_mapper.xml`, `task_mapper.xml`,
`credit_award_task_mapper.xml`.

## Generic Task and Outbox Fallbacks

SendMessageTaskJob; LocalRebateTaskOutboxPort;
LocalCreditTradeTaskOutboxPort; LocalAwardDispatchTaskOutboxPort.

## Current Cleanup Eligibility

No item is removable. External evidence required before disabling. 7-day stable
gate and 30-day removal gate remain EXTERNAL-GATED.
