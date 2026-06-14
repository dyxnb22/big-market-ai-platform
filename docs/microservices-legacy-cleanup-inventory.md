# Old Path Cleanup Inventory

This is a learning inventory for compatibility paths that should be understood
before removing files. It is not a timed production cleanup plan.

## RPC Providers Kept For Compatibility

- `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RebateServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/ErpOperateController.java`

## Local Implementation Adapters

- `LocalAccountReadAdapter`
- `LocalAccountCreditWriteAdapter`
- `LocalAccountQuotaWriteAdapter`
- `LocalAwardDispatchAdapter`
- `LocalRebateOrderAdapter`
- `LocalRebateReadAdapter`
- `LocalStrategyReadAdapter`

## Local Port Implementations

- `LocalActivityAccountPort`
- `LocalStrategyActivityAccountPort`
- `LocalStrategyActivityMappingPort`
- `LocalStrategyDecisionPort`
- `LocalAwardFulfillmentPort`
- `LocalAwardCreditWritePort`
- `LocalCreditAwardTaskDispatchPort`
- `LocalAwardActivityOrderPort`
- `LocalDrawOutboxPort`

## Mapper Compatibility Copies

Compatibility mapper directories exist in several service modules so each
service can run in the local learning stack with shared infrastructure classes.
Do not remove a mapper until `rg` proves no active resource path loads it and
`mvn clean package -DskipTests` still passes.

Key directories:

- `big-market-market-service/src/main/resources/mybatis/mapper/mysql`
- `big-market-message-job-service/src/main/resources/mybatis/mapper/mysql`
- `big-market-account-service/src/main/resources/mybatis/mapper/mysql`
- `big-market-rebate-service/src/main/resources/mybatis/mapper/mysql`
- `big-market-strategy-service/src/main/resources/mybatis/mapper/mysql`
- `big-market-fulfillment-service/src/main/resources/mybatis/mapper/mysql`

## Cleanup Rule

For this learning project, cleanup is complete when a path is unused by code,
configuration, documentation, and scripts, and the local build/smoke checks
still pass after removal.
