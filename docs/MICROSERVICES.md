# 微服务架构

最后修订：2026-07-17。

本仓库是 Big Market 抽奖平台的完整微服务学习与作品集项目。系统以本地学习的最终架构形态呈现：网关路由、可独立部署的 Spring Boot 服务、Dubbo/Nacos 服务契约、RabbitMQ 消息处理、XXL-Job 定时任务、MySQL 持久化、Redis 缓存，以及 Prometheus/Grafana 可观测性。

**就绪说明：** 当前 7 服务默认拓扑已通过 clean Maven、静态安全、Mapper/DDL、Compose 配置校验和核心 Docker smoke（7 个应用健康、Web 200、Smoke 20/20）；完整 acceptance、fresh 空卷和 secure overlay 尚未运行，结论为“有条件冻结”。当前证据与限制见 `docs/LEARNING-FREEZE.md`。

本文档是当前架构的 authoritative entry point（权威入口文档）。补充学习材料位于 `docs/learning/`。

## 服务列表

| 服务 | 端口 | 部署定位 / 证据 | 职责 |
| --- | ---: | --- | --- |
| `big-market-gateway` | 8080 | 默认栈；当前配置验证 | API 网关、路由断言、Trace ID 透传、Resilience4j 降级响应 |
| `big-market-auth-service` | 8081 | 默认栈；当前配置验证 | 登录、JWT 签发、Token 校验、登出吊销 |
| `big-market-admin-service` | 8082 | 默认栈；当前配置验证 | 管理端配置 API 与 Nacos 配置同步 |
| `big-market-market-service` | 8083 | 默认栈；当前配置验证 | 核心抽奖 HTTP API、活动操作、ERP 端点、Nacos runtime switches、本地领域编排 |
| `big-market-chatbot-service` | 8084 | 默认栈；当前配置验证 | 聊天机器人 API、平台配置消费、积分扣费/退款集成 |
| `big-market-message-job-service` | 8085 | 默认栈；当前配置验证 | RabbitMQ 消费者、XXL-Job 处理器、任务重试、Outbox 派发 |
| `big-market-account-service` | 8086 | 默认栈；当前配置验证 | 积分账户、积分交易、活动配额、配额账本 RPC 契约 |

> **部署默认：** `docker-compose.yml` 是最终且唯一的应用部署入口，共 7 个服务（8080-8086）。返利与策略固定由 `big-market-market-service` 内部实现，积分奖固定由 `big-market-message-job-service` 的本地 outbox 派发到 account。

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

抽奖路径写入中奖记录并发布 `send_award` 事件。message-job 的 `SendAwardConsumer` 消费事件后调用本地奖品领域；积分奖写入 `credit_award_task`，由 `DispatchCreditAwardTaskJob`（handlers `_DB1`/`_DB2`）派发到 account RPC。

`user_award_record.award_state=completed` 表示发奖动作已被持久化接管；对积分奖，最终闭环必须继续核对 `credit_award_task=dispatched` 和账户积分流水，不能只看中奖记录。

代码路径：

- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/SendAwardConsumer.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/award/service/AwardService.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/WriteAdapterLocalConfig.java`

### 返利（Rebate）

签到会创建行为返利订单与任务，发布 `send_rebate` 事件，消费者据此发放积分或活动配额。返利创建与读取固定在 market 内部完成，消息消费仍由 message-job 负责。

代码路径：

- `big-market-domain/src/main/java/com/dyx/market/domain/rebate/service/BehaviorRebateService.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateOrderAdapter.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalRebateReadAdapter.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/listener/RebateMessageConsumer.java`

### 策略读取（Strategy Reads）

策略读取对外提供奖品列表、规则权重与账户参与信号。Market HTTP 控制器通过 `IStrategyReadAdapter` 调用，最终由 market 内部策略实现响应。

代码路径：

- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleStrategyController.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/IStrategyReadAdapter.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/adapter/LocalStrategyReadAdapter.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/rpc/RaffleStrategyServiceRPC.java`

### 消息与任务（Messages And Jobs）

RabbitMQ Topic 承载奖品、返利、积分调整与库存归零等事件。XXL-Job 处理器重试任务行，并异步刷新库存计数器。

代码路径：

- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/SendMessageTaskJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateActivitySkuStockJob.java`
- `big-market-trigger/src/main/java/com/dyx/market/trigger/job/UpdateAwardStockJob.java`
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/RabbitMQDlqConfig.java`

## 数据与归属

服务边界矩阵见 `docs/microservices-dao-ownership.md`（**逻辑约定**，非编译器强制；边界靠 ports/adapters + ArchUnit 门禁）。DDL 与 Outbox 学习说明见 `docs/data-and-outbox.md` 与 `docs/sql/`。`docs/sql/` 下的 SQL 是需手动执行的学习参考；`docs/dev-ops/mysql/sql/` 会在 fresh MySQL 卷初始化时由 Docker 自动执行。旧卷不会重跑 init SQL，需执行 `./scripts/apply-stack-migrations.sh`。

- Admin 平台配置：当 `nacos.config.sync.enabled=true` 时，保存以 Nacos publish 成功为准（可用 `nacos.config.sync.fail-open=true` 降级为仅写本地）。

## 本地完成标准

在本学习环境中，只有明确记录验证模式后才能声称完成：

- `mvn -B verify -DfailIfNoTests=false` 成功。
- 验收：`./scripts/acceptance.sh`（默认 `--reuse`，**不**自动 `compose up`；包含真实抽奖发奖入账、Chat 补偿和 Playwright 双跑）。
- `--reuse` 只证明旧卷兼容；`--fresh` 才证明初始化完整。失败产物在 `target/acceptance-artifacts/`。
- `./scripts/validate-microservices-stack.sh`（默认不自启 Docker，加 `--start-stack` 才起栈）与强化后的 `validate-microservices-runtime-safety.sh`（后者 alone 仍不足以替代 Context/smoke）。
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
