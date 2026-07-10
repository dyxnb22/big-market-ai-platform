# 微服务架构

最后修订：2026-07-10。

本仓库是 Big Market 抽奖平台的完整微服务学习与作品集项目。系统以本地学习的最终架构形态呈现：网关路由、可独立部署的 Spring Boot 服务、Dubbo/Nacos 服务契约、RabbitMQ 消息处理、XXL-Job 定时任务、MySQL 持久化、Redis 缓存，以及 Prometheus/Grafana 可观测性。

**就绪说明：** 2026-07-10 审批整改（BM-001～015）代码已合入。服务默认仍可本地启动，但「可演示完整闭环」以 fresh Docker + smoke/Playwright 验收为准，见 `docs/audit-remediation-plan.md` §8。生产向演示可叠加 `secure` profile（`application-secure.yml`：`internal-rpc.enforce`、`gateway.rate-limiter`）。

本文档是当前架构的 authoritative entry point（权威入口文档）。较早的实现说明仅作为历史归档材料保留在 `docs/archive/` 下。

## 服务列表

| 服务 | 端口 | 当前状态 | 职责 |
| --- | ---: | --- | --- |
| `big-market-gateway` | 8080 | 稳定，已启用 | API 网关、路由断言、Trace ID 透传、Resilience4j 降级响应 |
| `big-market-auth-service` | 8081 | 稳定，已启用 | 登录、JWT 签发、Token 校验、登出吊销 |
| `big-market-admin-service` | 8082 | 稳定，已启用 | 管理端配置 API 与 Nacos 配置同步 |
| `big-market-market-service` | 8083 | 已修复启动扫描（BM-001） | 核心抽奖 HTTP API、活动操作、ERP/DCC 端点、本地领域编排 |
| `big-market-chatbot-service` | 8084 | 已启用 | 聊天机器人 API、平台配置消费、积分扣费/退款集成（BM-010 requestId FSM） |
| `big-market-message-job-service` | 8085 | 已修复 Mapper+扫描（BM-002）；XXL `big-market-message-job`（BM-003） | RabbitMQ 消费者、XXL-Job 处理器、任务重试、Outbox 派发 |
| `big-market-account-service` | 8086 | 稳定，已归属 | 积分账户、积分交易、活动配额、配额账本 RPC 契约 |
| `big-market-fulfillment-service` | 8087 | 稳定，已归属 | 奖品履约 RPC、中奖记录完成、奖品积分 Outbox 集成 |
| `big-market-rebate-service` | 8088 | 稳定，已归属 | 行为返利创建/查询 RPC 契约与返利任务归属 |
| `big-market-strategy-service` | 8089 | 稳定，已归属 | 策略读取 RPC、奖品列表读取、规则权重读取、账户参与记录读取 |

`big-market-domain`、`big-market-infrastructure`、`big-market-api`、`big-market-types` 以及各 starter 模块等共享模块，是各服务启动器所依赖的库。

## 核心流程

### 抽奖（Raffle）

`big-market-gateway` 将 `/api/v1/raffle/**` 路由至 `big-market-market-service`。`RaffleActivityController.draw_by_token` 校验 JWT 用户上下文，`RaffleApplicationService.executeDraw` 创建或复用参与订单，活动领域消费配额，策略领域选出奖品，奖品领域写入 `user_award_record` 并创建消息任务。

代码路径：

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/service/partake`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/service`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/AwardRepository.java`

### 账户配额（Account Quota）

活动配额由账户相关的 Port 与 Repository 持有。本地学习环境的默认做法是将配额扣减与订单创建在事务上尽量贴近抽奖路径；`big-market-account-service` 则对外暴露配额扣减、回滚与账户查询的服务契约。

代码路径：

- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountQuotaService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountQuotaServiceRPC.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port/IActivityAccountPort.java`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/ActivityRepository.java`

### 积分（Credit）

积分余额与积分交易订单支撑签到奖励、SKU 兑换、聊天机器人扣费/退款，以及奖品积分发放。幂等性通过 `out_business_no`、`award_order_id` 及任务消息 ID 等业务编号保证。

代码路径：

- `big-market-api/src/main/java/com/dyx/market/trigger/api/IAccountCreditService.java`
- `big-market-account-service/src/main/java/com/dyx/market/account/provider/AccountCreditServiceRPC.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/credit`
- `big-market-infrastructure/src/main/java/com/dyx/market/infrastructure/adapter/repository/CreditRepository.java`

### 奖品履约（Award Fulfillment）

抽奖路径写入中奖记录并发布 `send_award` 事件。message-job 服务消费该事件后调用奖品领域发放积分或外部配额，再将中奖记录标记为完成。

代码路径：

- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/award/service/AwardService.java`
- `big-market-fulfillment-service/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java`

### 返利（Rebate）

签到会创建行为返利订单与任务，发布 `send_rebate` 事件，消费者据此发放积分或活动配额。返利归属由 `big-market-rebate-service` 的 RPC 契约及本地任务/Outbox Port 表示。

代码路径：

- `big-market-domain/src/main/java/com/dyx/market/domain/rebate/service/BehaviorRebateService.java`
- `big-market-rebate-service/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`

### 策略读取（Strategy Reads）

策略读取对外提供奖品列表、规则权重与账户参与信号。Market HTTP 控制器通过 `IStrategyReadAdapter` 调用；策略服务提供对应的 RPC 实现。

代码路径：

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IStrategyReadAdapter.java`
- `big-market-strategy-service/src/main/java/com/dyx/market/strategy/provider/StrategyReadServiceRPC.java`

### 消息与任务（Messages And Jobs）

RabbitMQ Topic 承载奖品、返利、积分调整与库存归零等事件。XXL-Job 处理器重试任务行，并异步刷新库存计数器。

代码路径：

- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateActivitySkuStockJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateAwardStockJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RabbitMQDlqConfig.java`

## 数据与归属

服务边界矩阵见 `docs/microservices-dao-ownership.md`。DDL 与 Outbox 学习说明见 `docs/data-and-outbox.md` 与 `docs/sql/`。SQL 文件为学习环境下的 DDL 参考，本仓库不会自动执行。

## 本地完成标准

在本学习环境中，当满足以下条件时，可认为架构已完整：

- `mvn clean package -DskipTests` 构建成功。
- 在本地 Docker 环境中 `./scripts/validate-microservices-stack.sh` 校验通过。
- `./scripts/validate-microservices-runtime-safety.sh` 作为最终架构护栏校验通过。
- 核心流程可从 Controller 到领域服务、Repository、MQ/XXL-Job，以及回滚/幂等处理完整说明。
- `docs/learning/*`、本架构文档与代码/配置注释讲述同一套最终态故事。

## 生产环境声明

本项目为学习环境，不包含真实的生产灰度或观察期。

## 文档索引

- `docs/learning/README.md` — 最终态学习指南
- `docs/production-readiness-learning.md` — 学习版就绪性说明
- `docs/operations-checklist.md` — 本地运维检查清单
- `docs/data-and-outbox.md` — 数据、Outbox、幂等与重复处理
- `docs/microservices-dao-ownership.md` — 表与 DAO 归属矩阵
- `docs/old-path-cleanup-inventory.md` — 旧路径清理说明
- `docs/archive/microservices-historical-readiness-notes.md` — 归档历史说明，非当前状态
- `docs/archive/evidence-template-archive.md` — 归档证据模板，非当前状态
