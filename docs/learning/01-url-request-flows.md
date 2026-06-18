# 01 URL 请求流程

## URL 总览

| URL | Method | 入口 | 鉴权 | 主业务 |
| --- | --- | --- | --- | --- |
| `/api/v1/auth/login` | POST | `AuthAccessController.login` | 账号密码 | 生成 JWT |
| `/api/v1/auth/verify` | GET | `AuthAccessController.verify` | Authorization | 校验 JWT |
| `/api/v1/auth/logout` | POST | `AuthAccessController.logout` | Authorization | 撤销 jti |
| `/api/v1/raffle/activity/query_stage_activity_id` | GET | `RaffleActivityController.queryStageActivityId` | 未见拦截 | 渠道/来源查上架活动 |
| `/api/v1/raffle/activity/armory` | GET | `RaffleActivityController.armory` | 未见拦截 | 活动和策略预热 |
| `/api/v1/raffle/activity/draw_by_token` | POST | `RaffleActivityController.draw` | TokenAuthInterceptor | 抽奖 |
| `/api/v1/raffle/activity/calendar_sign_rebate_by_token` | POST | `RaffleActivityController.calendarSignRebateByToken` | TokenAuthInterceptor | 签到返利 |
| `/api/v1/raffle/activity/is_calendar_sign_rebate_by_token` | POST | `RaffleActivityController.isCalendarSignRebateByToken` | TokenAuthInterceptor | 查今日是否签到 |
| `/api/v1/raffle/activity/query_user_activity_account_by_token` | POST | `RaffleActivityController.queryUserActivityAccount` | TokenAuthInterceptor | 查抽奖额度 |
| `/api/v1/raffle/activity/query_sku_product_list_by_activity_id` | POST | `RaffleActivityController.querySkuProductListByActivityId` | 未见拦截 | 查可兑换 SKU |
| `/api/v1/raffle/activity/query_user_credit_account_by_token` | POST | `RaffleActivityController.queryUserCreditAccountByToken` | TokenAuthInterceptor | 查积分 |
| `/api/v1/raffle/activity/credit_pay_exchange_sku_by_token` | POST | `RaffleActivityController.creditPayExchangeSku` | TokenAuthInterceptor | 积分兑换抽奖次数 |
| `/api/v1/raffle/activity/chat_credit_deduct_by_token` | POST | `RaffleActivityController.chatCreditDeductByToken` | TokenAuthInterceptor | AI Chat 扣积分 |
| `/api/v1/raffle/activity/chat_credit_refund_by_token` | POST | `RaffleActivityController.chatCreditRefundByToken` | TokenAuthInterceptor | AI Chat 退积分 |
| `/api/v1/raffle/strategy/strategy_armory` | GET | `RaffleStrategyController.strategyArmory` | 未见拦截 | 策略装配 |
| `/api/v1/raffle/strategy/query_raffle_award_list_by_token` | POST | `RaffleStrategyController.queryRaffleAwardListByToken` | TokenAuthInterceptor | 查奖品和解锁状态 |
| `/api/v1/raffle/erp/*` | GET/POST | `ErpOperateController` | `X-Admin-Token` 或管理员 JWT | 运营查询/上架 |
| `/api/v1/raffle/dcc/update_config` | GET | `DCCController.updateConfig` | `X-Admin-Token` 或管理员 JWT | 动态配置 |
| `/api/v1/admin/config/*` | GET/POST | `AdminConfigController` | AdminAuthInterceptor | 平台配置 |
| `/api/v1/chatbot/ask` | POST | `ChatbotController.ask` | 有扣费时需要 token | AI Chat |

## 网关请求流

```mermaid
flowchart TD
    Client["Client / big-market-web"] --> Gateway["big-market-gateway:8080"]
    Gateway --> Trace["TraceIdGlobalFilter"]
    Trace --> Route{"Path route"}
    Route -->|/auth/**| Auth["auth-service:8081"]
    Route -->|/admin/**| Admin["admin-service:8082"]
    Route -->|/chatbot/**| Chatbot["chatbot-service:8084"]
    Route -->|/api/**| Market["market-service:8083"]
    Auth --> Resp["Response"]
    Admin --> Resp
    Chatbot --> Resp
    Market --> Resp
    Route -->|downstream failure| Fallback["/fallback/{service} code=0007"]
```

## 登录流程

- URL: `/api/v1/auth/login`
- Entry: `AuthAccessController.login`
- Domain: `AuthService.createToken`
- Request: `LoginRequestDTO`
- Response: `LoginResponseDTO`
- 数据写入: Not found in current code. 登录只校验配置中的 `app.auth.dev-users` 并生成 JWT。
- 风险: `dev-users` 是配置式账号源，不是正式用户体系。

```mermaid
sequenceDiagram
    participant C as Client
    participant A as AuthAccessController
    participant S as AuthService
    C->>A: POST /auth/login userId/password
    A->>A: parse app.auth.dev-users
    A->>S: createToken(userId)
    S-->>A: JWT(openId,jti,exp)
    A-->>C: LoginResponseDTO
```

## 注销流程

- URL: `/api/v1/auth/logout`
- Entry: `AuthAccessController.logout`
- Domain: `ITokenRevocationService.revoke(jti, expiresAt)`
- Auth header: 支持 `Bearer <jwt>`（`JwtTokenUtils` 统一解析）
- 数据写入: Redis key `jwt:revoked:{jti}`（Docker 栈）或进程内 Map（本地默认）
- 失败行为: Redis 写入失败时 `logout` 返回错误，不返回成功；Redis 模式但无 `RedissonClient` 时服务启动 fail-fast

```mermaid
sequenceDiagram
    participant C as Client
    participant A as AuthAccessController
    participant R as ITokenRevocationService
    participant Redis as Redis
    participant M as market-service AuthService
    C->>A: POST /auth/logout Authorization Bearer jwt
    A->>A: extractJti + expiresAt
    A->>R: revoke(jti, expiresAt)
    R->>Redis: SET jwt:revoked:{jti} TTL
    A-->>C: SUCCESS
    Note over C,M: 后续请求
    C->>M: POST draw_by_token Authorization
    M->>R: isRevoked(jti) via checkToken
    R-->>M: true
    M-->>C: TOKEN_ERROR
```

## 抽奖流程

- URL: `/api/v1/raffle/activity/draw_by_token`
- Entry: `RaffleActivityController.draw(String token, ActivityDrawRequestDTO)`
- Application: `RaffleApplicationService.executeDraw`
- Domain: `IRaffleActivityPartakeService.createOrder`、`IStrategyDecisionPort.performRaffle`、`IAwardFulfillmentPort.saveUserAwardRecord`
- Repository: `ActivityRepository.saveCreatePartakeOrderAggregate`、`AwardRepository.saveUserAwardRecord`
- Storage/MQ: MySQL、Redis、RabbitMQ `send_award`
- Auth: `TokenAuthInterceptor` 校验 JWT，将 `userId` 放入 request attribute。

```mermaid
flowchart TD
    A["用户点击抽奖"] --> B["Gateway /market route"]
    B --> C["TokenAuthInterceptor 校验 Authorization"]
    C --> D["RaffleActivityController.draw_by_token"]
    D --> E["RaffleApplicationService.executeDraw"]
    E --> F["PartakeService 创建/复用抽奖单并扣额度"]
    F --> G["StrategyDecisionPort 执行策略"]
    G --> H["AwardFulfillmentPort 保存中奖记录"]
    H --> I["AwardRepository 写 user_award_record + task"]
    I --> J["RabbitMQ send_award"]
    J --> K["返回 awardId/awardTitle/awardIndex"]
```

```mermaid
sequenceDiagram
    participant C as Client
    participant I as TokenAuthInterceptor
    participant R as RaffleActivityController
    participant A as RaffleApplicationService
    participant P as PartakeService
    participant S as StrategyDecisionPort
    participant W as AwardFulfillmentPort
    participant DB as MySQL/Redis
    participant MQ as RabbitMQ
    C->>I: POST draw_by_token Authorization
    I->>R: userId attribute
    R->>A: executeDraw(userId, activityId)
    A->>P: createOrder
    P->>DB: 扣额度/写抽奖单
    A->>S: performRaffle
    S->>DB: 查策略/扣奖品库存
    A->>W: saveUserAwardRecord
    W->>DB: 写中奖记录/任务
    W->>MQ: publish send_award
    A-->>R: award result
    R-->>C: Response
```

## 签到返利流程

- URL: `/api/v1/raffle/activity/calendar_sign_rebate_by_token`
- Entry: `RaffleActivityController.calendarSignRebateByToken`
- Domain: `BehaviorRebateService.createOrder`
- Repository: `BehaviorRebateRepository.saveUserRebateRecord`
- MQ Consumer: `RebateMessageConsumer`
- 幂等: 先查 `rebateReadAdapter.isCalendarSignRebate`，并通过唯一索引冲突返回已签到。

```mermaid
flowchart TD
    A["用户签到"] --> B["JWT 校验得到 userId"]
    B --> C["生成 outBusinessNo=yyyyMMdd"]
    C --> D{"今日订单是否存在"}
    D -->|存在| E["返回已签到 + 当前积分"]
    D -->|不存在| F["创建返利订单和 task"]
    F --> G["发布 send_rebate"]
    G --> H["RebateMessageConsumer"]
    H --> I{"rebateType"}
    I -->|integral| J["CreditRepository 增积分"]
    I -->|sku| K["ActivityRepository 增额度"]
    J --> L["返回签到成功"]
    K --> L
```

## 积分兑换 SKU 流程

- URL: `/api/v1/raffle/activity/credit_pay_exchange_sku_by_token`
- Entry: `RaffleActivityController.creditPayExchangeSku`
- Domain/Adapter: `IAccountQuotaWriteAdapter.createOrder`、`IAccountCreditWriteAdapter.createOrder`、`IAccountQuotaWriteAdapter.updateOrder`
- Repository: `ActivityRepository.doSaveCreditPayOrder`、`CreditRepository.saveUserCreditTradeOrder`
- 重要机制: SKU 库存扣减、积分扣减、扣减失败恢复 SKU 库存、发货失败由 MQ 补偿。

```mermaid
flowchart TD
    A["用户用积分兑换抽奖次数"] --> B["JWT userId 覆盖请求 userId"]
    B --> C["生成 outBusinessNo"]
    C --> D["创建 SKU 订单/扣库存"]
    D --> E["创建积分扣减订单"]
    E -->|成功| F["同步完成额度发货 updateOrder"]
    E -->|失败且非重复| G["restoreActivitySkuStock"]
    F --> H["返回兑换成功"]
    G --> I["返回业务错误"]
```

## AI Chat 流程

- URL: `/api/v1/chatbot/ask`
- Entry: `ChatbotController.ask`
- External: DeepSeek HTTP API when `provider=deepseek` and apiKey exists；否则本地 fallback。
- Internal HTTP: 调用 market 的 `chat_credit_deduct_by_token` 和 `chat_credit_refund_by_token`。

```mermaid
sequenceDiagram
    participant C as Client
    participant Bot as ChatbotController
    participant M as Market credit API
    participant AI as DeepSeek/localFallback
    C->>Bot: POST /chatbot/ask
    Bot->>Bot: 读取平台配置 enabled/cost/provider
    Bot->>M: query credit / deduct credit
    M-->>Bot: balance
    Bot->>AI: ask message
    alt AI success
        AI-->>Bot: answer
        Bot-->>C: success + balance
    else AI failed after deduction
        Bot->>M: refund credit
        Bot-->>C: failed + refunded
    end
```

## ERP / DCC / Admin 配置流

- ERP: `ErpOperateController` 使用 `X-Admin-Token` 或管理员 JWT。
- DCC: `DCCController` 使用 `X-Admin-Token` 或管理员 JWT，写 Zookeeper `/big-market-dcc/config/{key}`。
- Admin Config: `AdminConfigController` 由 `AdminAuthInterceptor` 拦截，读写 `PlatformConfigService`。

```mermaid
flowchart TD
    A["管理员请求"] --> B{"鉴权方式"}
    B -->|X-Admin-Token| C["静态 token 比对"]
    B -->|Authorization JWT| D["AuthService.checkToken + adminUserIds"]
    C --> E["ERP/DCC/Admin 业务"]
    D --> E
    E --> F["ES 查询 / 活动上架 / ZK DCC / 平台配置"]
```

