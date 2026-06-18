# 09 Code Map

## Gateway

- `big-market-gateway/src/main/resources/application.yml`
- `big-market-gateway/src/main/java/com/dyx/market/gateway/filter/TraceIdGlobalFilter.java`
- `big-market-gateway/src/main/java/com/dyx/market/gateway/fallback/FallbackController.java`

## Authentication

- `big-market-auth-service/src/main/java/com/dyx/market/auth/AuthAccessController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/AuthService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/config/TokenRevocationConfig.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/RedisTokenRevocationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/InMemoryTokenRevocationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/util/JwtTokenUtils.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/DefaultCredentialGuard.java`
- `big-market-market-service/src/main/java/com/dyx/market/market/config/TokenAuthInterceptor.java`
- `big-market-admin-service/src/main/java/com/dyx/market/admin/service/config/AdminAuthInterceptor.java`

## Raffle

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/AbstractRaffleActivityPartake.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/raffle/DefaultRaffleStrategy.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`

## Account And Credit

- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountCreditService.java`
- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountQuotaService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountCreditServiceRPC.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java`

## Rebate

- `big-market-domain/src/main/java/com/dyx/market/domain/rebate/service/BehaviorRebateService.java`
- `big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`

## Strategy

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/armory/StrategyArmoryDispatch.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/rule/chain/factory/DefaultChainFactory.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/rule/tree/factory/DefaultTreeFactory.java`
- `big-market-strategy-service/src/main/java/com/dyx/market/strategy/provider/StrategyReadServiceRPC.java`

## Award Fulfillment

- `big-market-domain/src/main/java/com/dyx/market/domain/award/service/AwardService.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`

## Tasks, Outbox, And Operations

- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateActivitySkuStockJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateAwardStockJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`
- `docs/data-and-outbox.md`
- `docs/operations-checklist.md`

## Admin And Chatbot

- `big-market-admin-service/src/main/java/com/dyx/market/admin/AdminConfigController.java`
- `big-market-management/src/main/java/com/dyx/market/management/config/PlatformConfigService.java`
- `big-market-chatbot-service/src/main/java/com/dyx/market/chatbot/ChatbotController.java`
