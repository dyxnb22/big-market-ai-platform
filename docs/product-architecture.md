# Big Market AI Platform Architecture

## 目标定位

这个目录是原始 `big-market` 的产品化学习版，不影响原项目复习。它不是把项目硬说成 Chatbot，而是在大营销抽奖平台之上补一层自然语言入口、登录认证、管理配置和前端联调页面。

## 当前模块

| 模块 | 职责 |
| --- | --- |
| `big-market-app` | Spring Boot 启动应用，聚合各业务模块 |
| `big-market-trigger` | HTTP、MQ、XXL-Job 等触发入口 |
| `big-market-domain` | 策略、活动、奖品、积分、返利、任务、认证等 DDD 领域模型 |
| `big-market-infrastructure` | MySQL、Redis、RabbitMQ、ES、外部网关适配 |
| `big-market-api` | 对外 DTO、Response、接口契约 |
| `big-market-queries` | 查询侧模型和仓储接口 |
| `big-market-auth-access` | 登录、JWT token 发放和校验 |
| `big-market-management` | 平台配置服务，本地持久化到 `data/platform-config.properties` |
| `big-market-admin` | 管理端配置接口 |
| `big-market-chatbot` | 规则版 Chatbot，调用受控业务工具 |
| `big-market-starter-db-router` | 内置分库分表路由 |
| `big-market-starter-dcc` | 内置 Zookeeper DCC 动态配置 |
| `big-market-starter-ratelimiter` | 内置 Guava + AOP 限流 |
| `big-market-web` | 轻量联调前端 |

## 关键业务链路

1. 用户调用 `auth/login` 获取 token。
2. 前端或 Chatbot 带 token 调用抽奖、签到、积分、SKU 查询接口。
3. 抽奖入口进入 `RaffleActivityController`，先做 token/参数/降级/限流校验。
4. 领域层完成活动账户扣减、策略抽奖、规则链和规则树判断。
5. Redis 负责高频库存预扣减和延迟队列，XXL-Job 负责异步落库补偿。
6. RabbitMQ + MySQL `task` 表承担业务消息最终一致性。

## Chatbot 边界

Chatbot 只做自然语言入口和工具编排，不绕过领域规则。当前支持：

- 签到：`calendarSignRebateByToken`
- 查积分：`queryUserCreditAccountByToken`
- 查活动次数：`queryUserActivityAccount`
- 查 SKU：`querySkuProductListByActivityId`
- 抽奖：`draw`

管理端可以通过配置关闭 Chatbot：

```text
namespace=chatbot
configKey=enabled
configValue=false
```

## 后续微服务拆分建议

现在仍是模块化单体，更适合学习和本地运行。真正拆微服务时建议按这个顺序：

1. `gateway-service`
2. `auth-service`
3. `admin-service`
4. `chatbot-service`
5. `marketing-service`
6. `raffle-service`
7. `account-service`
8. `fulfillment-service`
9. `rebate-service`
10. `message-job-service`

面试时可以说：当前是模块化单体，边界已经按服务拆好了；如果上生产，先拆网关、认证、管理、Chatbot 和核心 marketing 服务，库存、消息补偿、奖品履约按流量和团队边界继续拆。
