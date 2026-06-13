# 09 代码地图

## 认证登录

- 先读：`big-market-auth-service/src/main/java/com/dyx/market/auth/AuthAccessController.java`
- 再读：`big-market-domain/src/main/java/com/dyx/market/domain/auth/service/AuthService.java`
- 关联：`TokenAuthInterceptor`、`AdminAuthInterceptor`、`DefaultCredentialGuard`
- 学到：JWT 生成/校验、jti 注销、管理员鉴权、默认凭据防护。

## 网关

- 先读：`big-market-gateway/src/main/resources/application.yml`
- 再读：`TraceIdGlobalFilter`、`FallbackController`
- 学到：Path 路由、CircuitBreaker fallback、trace id 透传。

## 抽奖主链路

- 先读：`RaffleActivityController.draw_by_token`
- 再读：`RaffleApplicationService.executeDraw`
- 继续读：`AbstractRaffleActivityPartake.createOrder`、`DefaultRaffleStrategy`、`AwardRepository.saveUserAwardRecord`
- 学到：登录用户参与活动、扣额度、抽策略、写中奖记录、发送发奖消息。

## 活动额度和 SKU 兑换

- 先读：`RaffleActivityController.creditPayExchangeSku`
- 再读：`RaffleActivityAccountQuotaService`、`AbstractRaffleActivityAccountQuota`、`ActivityRepository.doSaveCreditPayOrder`、`CreditRepository.saveUserCreditTradeOrder`
- 学到：SKU 库存、积分扣减、订单完成、MQ 补偿。

## 签到返利

- 先读：`RaffleActivityController.calendarSignRebate`
- 再读：`BehaviorRebateService`、`BehaviorRebateRepository.saveUserRebateRecord`
- 再读 MQ：`RebateMessageConsumer`
- 学到：每日幂等、返利配置、积分/SKU 入账。

## 策略配置和抽奖算法

- 先读：`RaffleStrategyController`
- 再读：`StrategyArmoryDispatch`、`DefaultChainFactory`、`DefaultTreeFactory`
- 再读：`O1Algorithm`、`OLogNAlgorithm`
- 学到：策略装配、责任链、规则树、概率表。

## 发奖

- 先读：`AwardService`
- 再读：`AwardRepository.saveUserAwardRecord`、`saveGiveOutPrizesAggregate`
- 再读：`SendAwardConsumer`
- 学到：中奖记录、任务表、消息、发奖状态。

## 任务和补偿

- 先读：`SendMessageTaskJob`
- 再读：`UpdateActivitySkuStockJob`、`UpdateAwardStockJob`、`DispatchCreditAwardTaskJob`
- 学到：XXL-Job、Redisson 分布式锁、任务表重试、最终一致。

## 管理和配置

- 先读：`AdminConfigController`
- 再读：`PlatformConfigService`、`NacosConfigSyncService`
- 再读：`DCCController`、`DccValueBeanPostProcessor`
- 学到：平台配置、Nacos 同步、Zookeeper/DCC 动态值。

## AI Chat

- 先读：`ChatbotController.ask`
- 再读：`RestTemplateConfig`、`PlatformConfigService`
- 关联：market 的 `chat_credit_deduct_by_token` 和 `chat_credit_refund_by_token`
- 学到：调用前扣积分，AI 失败后退还积分，本地 fallback。

