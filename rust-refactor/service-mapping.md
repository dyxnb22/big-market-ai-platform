# Java → Rust 服务与模块映射

## 1. 可部署单元

| Java 服务 | 端口 | Rust 落点（Phase A） | Phase B（可选拆分） |
| --- | ---: | --- | --- |
| `big-market-gateway` | 8080 | `bm-gateway` | 同左 |
| `big-market-auth-service` | 8081 | `bm-app` 内 `auth` 模块 | `bm-auth` |
| `big-market-admin-service` | 8082 | 暂缓；或 `bm-app` 内 `admin` | `bm-admin` |
| `big-market-market-service` | 8083 | `bm-app` 内 `market` + embedded strategy/rebate | `bm-market` |
| `big-market-chatbot-service` | 8084 | `bm-app` 内 `chat`（可后置） | `bm-chat` |
| `big-market-message-job-service` | 8085 | `bm-worker` | 同左 |
| `big-market-account-service` | 8086 | `bm-app` 内 `account` | `bm-account` |
| `big-market-fulfillment-service` | 8087 | `bm-app` / `bm-worker` 本地履约 | `bm-fulfillment` |
| `big-market-rebate-service` | 8088 | embedded in `bm-app`（默认） | `bm-rebate` |
| `big-market-strategy-service` | 8089 | embedded in `bm-app`（默认） | `bm-strategy` |

前端 `big-market-web`：**不重写**，继续打 `http://127.0.0.1:8080/api/v1`。

## 2. 共享库映射

| Java 模块 | Rust crate | 备注 |
| --- | --- | --- |
| `big-market-types` | `bm-types` | 错误码、常量、值对象 |
| `big-market-api` | `bm-api` | DTO + OpenAPI/proto |
| `big-market-domain` | `bm-domain` | 无 IO 的领域逻辑优先纯函数/结构体 |
| `big-market-infrastructure` | `bm-infra` | SQLx、Redis、MQ、仓储实现 |
| `big-market-trigger` | 拆到 `bm-app`（http）与 `bm-worker`（listener/job） | **禁止** app 扫描 worker 的消费逻辑（对齐 Java 约束） |
| `big-market-starter-*` | feature flags / 小 crate | db-router、ratelimit、web 中间件 |

## 3. 关键类 → Rust 入口对照

| 流程 | Java 入口 | Rust 建议入口 |
| --- | --- | --- |
| 抽奖 | `RaffleActivityController` → `RaffleApplicationService` | `bm_app::http::raffle` → `bm_domain::activity::RaffleApp` |
| 策略 | `RaffleStrategyController` / strategy domain | `bm_domain::strategy` |
| 发奖消费 | `SendAwardConsumer`（仅 message-job） | `bm_worker::consumers::send_award` |
| 积分派发 | `DispatchCreditAwardTaskJob` | `bm_worker::jobs::dispatch_credit_award` |
| 账户入账 | `AccountCreditServiceRPC` | `bm_domain::credit` + `bm_infra::account_repo`；拆分后 tonic service |
| 返利 | `BehaviorRebateService` + `RebateMessageConsumer` | `bm_domain::rebate` + worker consumer |
| Chat | chatbot service + credit session | `bm_app::http::chat` + Redis idempotency |
| JWT | auth-service | `bm_app::http::auth` / `bm_auth` |

## 4. 扫描边界（继承 Java 铁律）

```text
bm-app     = HTTP / 同步领域 / 写 outbox 行
bm-worker  = RabbitMQ consumers + 定时派发 / 补偿
```

`bm-app` **不得**注册 `send_award` 消费者或 credit dispatch job，避免双消费与双入账。

## 5. 中间件复用

| 组件 | 策略 |
| --- | --- |
| MySQL | 复用 `docs/dev-ops` 初始化 SQL；Rust 连接同一逻辑库 |
| Redis | 同一实例；key 前缀可加 `rs:` 做灰度隔离（可选） |
| RabbitMQ | 同一 exchange；**灰度时换独立 queue**，避免与 Java consumer 抢消息 |
| XXL-Job | Phase A 用内置 scheduler；Phase B 再评估适配 |
| Nacos | Phase A 环境变量；Admin 配置同步后置 |
| Prometheus | `/metrics` 暴露；面板可新建 `rust-*` dashboard |

## 6. API 兼容层级

1. **L0 路径兼容**：`/api/v1/**` 路径与主字段名一致，前端零改或少改。  
2. **L1 错误码兼容**：对齐现有 `code/info/data` 包装（若 Java 使用该形态）。  
3. **L2 头兼容**：`Authorization`、trace id 透传。  

不以“字节级完全一致”为第一目标，以 **Playwright / smoke 脚本可切换网关上游** 为准。
