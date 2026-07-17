# 09 代码地图

> **运行时归属：** `trigger.http` / `trigger.rpc` / `trigger.application` 由 **market-service** 扫描；`trigger.listener` / `trigger.job` 仅由 **message-job-service** 扫描。源码仍在 `big-market-trigger` JAR 中，勿在 market 日志里找 MQ 消费者。

## Gateway

- `big-market-gateway/src/main/resources/application.yml`
- `big-market-gateway/src/main/java/com/dyx/market/gateway/filter/TraceIdGlobalFilter.java`
- `big-market-gateway/src/main/java/com/dyx/market/gateway/fallback/FallbackController.java`

## Authentication

- `big-market-auth-service/src/main/java/com/dyx/market/auth/AuthAccessController.java`
- `big-market-auth-service/src/main/java/com/dyx/market/auth/config/AuthExceptionHandler.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/AuthService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/config/TokenRevocationConfig.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/RedisTokenRevocationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/InMemoryTokenRevocationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/util/JwtTokenUtils.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/DefaultCredentialGuard.java`
- `big-market-market-service/src/main/java/com/dyx/market/market/config/TokenAuthInterceptor.java`
- `big-market-market-service/src/main/java/com/dyx/market/market/config/OperationalAuthInterceptor.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/auth/service/AdminAccessService.java`
- `big-market-admin-service/src/main/java/com/dyx/market/admin/service/config/AdminAuthInterceptor.java`
- `big-market-admin-service/src/main/java/com/dyx/market/admin/service/config/WebMvcConfig.java`（仅拦截器；CORS 由 starter-web 提供）
- `big-market-admin-service/src/main/java/com/dyx/market/admin/config/AdminExceptionHandler.java`

## Raffle

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/application/CreditPayExchangeApplicationService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/application/ChatCreditApplicationService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/application/RaffleDrawApplicationService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/application/CalendarSignApplicationService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/application/RaffleActivityQueryApplicationService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/GlobalExceptionHandler.java`（作用域 `com.dyx.market.trigger.http`）
- `big-market-trigger/src/main/java/com/dyx/market/trigger/application/RaffleActivityFacade.java`（HTTP/RPC 共用编排）
- `big-market-trigger/src/main/java/com/dyx/market/trigger/application/RebateMessageApplicationService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/TriggerApiResponses.java`（委托 `ApiResponses`）
- `big-market-api/src/main/java/com/dyx/market/trigger/api/support/ApiResponses.java`（跨服务 RPC 响应工具）
- `big-market-trigger/src/main/java/com/dyx/market/trigger/support/DubboRpcAuthSupport.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleActivityServiceRPC.java`
- `big-market-market-service/src/main/java/com/dyx/market/market/config/WebMvcConfig.java`（Token/运营鉴权拦截器）
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake/AbstractRaffleActivityPartake.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/raffle/DefaultRaffleStrategy.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java`（门面）
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityQuerySupport.java`（活动/SKU/账户查询）
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityPartakeOrderSupport.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityQuotaOrderSupport.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityQuotaLedgerSupport.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivitySkuStockCacheSupport.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityStageRepositorySupport.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/StrategyRepository.java`（门面）
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/StrategyAwardCacheSupport.java`（奖品缓存/库存/概率表）
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/StrategyRuleTreeSupport.java`（规则树/权重）
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`（门面）
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardDispatchSupport.java` / `AwardCreditGrantSupport.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/config/MysqlMybatisConfiguration.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/config/ElasticsearchMybatisConfiguration.java`（`spring.elasticsearch.datasource.url` 存在时启用）

## Account And Credit

- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountCreditService.java`
- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountQuotaService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/application/AccountQuotaApplicationService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/application/AccountCreditApplicationService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountCreditServiceRPC.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java`
- `big-market-api/src/main/java/com/dyx/market/trigger/api/support/ApiResponses.java`（account RPC 共用）
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java`

## Rebate

- `big-market-domain/src/main/java/com/dyx/market/domain/rebate/service/BehaviorRebateService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`

## Strategy

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/armory/StrategyArmoryDispatch.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/rule/chain/factory/DefaultChainFactory.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service/rule/tree/factory/DefaultTreeFactory.java`

## Award Fulfillment

- `big-market-domain/src/main/java/com/dyx/market/domain/award/service/AwardService.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/WriteAdapterLocalConfig.java`

## Tasks, Outbox, And Operations（仅 message-job 运行时）

- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/CreditAdjustSuccessConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/ActivitySkuStockZeroConsumer.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateActivitySkuStockJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateAwardStockJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`（handlers `_DB1`/`_DB2`）
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RabbitMQDlqConfig.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/XxlJobConfig.java`
- `docs/data-and-outbox.md`（权威：Outbox / 幂等 / compose 覆盖）
- `docs/operations-checklist.md`
- `docs/xxl-job-handlers.md`

## Admin And Chatbot

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/ErpOperateController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/DCCController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/ErpOperateServiceRPC.java`
- `big-market-admin-service/src/main/java/com/dyx/market/admin/AdminConfigController.java`（含 `public/display`）
- `big-market-management/src/main/java/com/dyx/market/management/config/PlatformConfigService.java`
- `big-market-chatbot-service/src/main/java/com/dyx/market/chatbot/ChatbotController.java`

## Frontend (big-market-web)

- `big-market-web/index.html` — 用户主应用（抽奖 + Chatbot）
- `big-market-web/login.html` / `login.js` / `login-common.js` — 登录页
- `big-market-web/admin-login.html` / `admin-login.js` — 管理登录
- `big-market-web/admin.html` / `admin.js` — 管理端配置
- `big-market-web/app.js` — 活动 ID 解析、`loadDisplayConfig`、抽奖、Chatbot 门控、抽屉互斥、本地历史
- `big-market-web/api-client.js` — 网关 API 封装与 JWT 存储
- `big-market-web/config.js` — API 基址与渠道参数
- `big-market-web/styles.css` — 聊天居中、落地页滚动、桌面/Web 布局

## Shared Starters

- `big-market-starter-web` — `TraceIdFilter`、`CorsAutoConfiguration`、`RedissonSingleServerSupport`、线程池优雅关闭
- `big-market-starter-data` — `ThreadPoolAutoConfiguration` + `thread.pool.executor.config`（market/account/rebate/message-job 共用）
- `big-market-starter-dubbo` — 服务间 Dubbo 内部令牌 Filter（`app.internal-rpc`，默认 `enforce=false`）
- `big-market-starter-db-router` / `big-market-starter-dcc` / `big-market-starter-ratelimiter`
