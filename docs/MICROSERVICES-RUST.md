# Rust 架构（默认栈）

最后修订：2026-07-16。

本文档是 **Big Market Rust 轨（`big-market-rs/`）的权威架构入口**。  
默认本地演示、CI 与前端对接均以此为准。

历史 Java 说明（源码已删除）：[`MICROSERVICES.md`](./MICROSERVICES.md)。  
删除台账：[`rust-refactor/JAVA-DELETION-LEDGER.md`](../rust-refactor/JAVA-DELETION-LEDGER.md)。

## 设计立场：不按 Java 1:1 拆服务

Rust 轨采用 **模块化单体（modular monolith）+ 少量进程**，而不是把 10 个 Spring Boot 服务逐一枚举成 10 个 Rust 二进制。

| 原则 | 说明 |
| --- | --- |
| 边界在编译期 | `bm-domain` trait + 模块划分，而非 Dubbo RPC |
| 同步写路径集中 | HTTP 与领域编排落在 `bm-app` |
| 异步与补偿外置 | Outbox 消费、入账派发、Chat 补偿、库存刷写在 `bm-worker` |
| 默认少进程 | `bm-gateway` + `bm-app` + `bm-worker`（app 可内嵌 worker） |
| 数据层可共享 | 与 Java 学习库同一 MySQL 逻辑库；分片路由保留在 `DbRouter` |
| 有意不移植 | XXL 控制台、Nacos 中心、物理 DB-per-service、Dubbo 注册发现 |

映射表（能力级，非 1:1 进程）：[`rust-refactor/service-mapping.md`](../rust-refactor/service-mapping.md)。

## 进程拓扑

```text
big-market-web (:5173)
        │
        ▼
┌───────────────────┐
│  bm-gateway :8080 │  反向代理、限流、/health、/actuator/health
└─────────┬─────────┘
          │ BM_GW_APP_URL
          ▼
┌───────────────────┐
│  bm-app   :8083   │  JWT、抽奖、SKU、Chat、Admin/DCC/ERP、策略查询
│  (BM_EMBED_WORKER │  可选：内嵌 outbox 轮询（默认开启）
│   =1 时)          │
└─────────┬─────────┘
          │ 写 outbox 行 / 本地队列
          ▼
┌───────────────────┐
│  bm-worker :8085  │  独立部署时：与 app 共享 BM_BACKEND / BM_MYSQL_URL
│  (可选)           │  send_award → credit_award_task → 入账
└─────────┬─────────┘  rebate / chat reconcile / stock flush
          │
    ┌─────┴─────┬─────────────┐
    ▼           ▼             ▼
  MySQL      Redis(opt)   RabbitMQ(opt)
```

**禁止：** `bm-app` 注册与 `bm-worker` 相同的 MQ 消费者或 credit dispatch 循环（避免双消费/双入账）。与 Java「market 不扫 message-job listener」同构约束。

## Crate 分层

| Crate | 职责 | 依赖方向 |
| --- | --- | --- |
| `bm-types` | 错误码、金额、分片路由工具 | 无领域依赖 |
| `bm-domain` | 领域服务 + `trait` 端口（Credit/Quota/Award/…） | 仅 `bm-types` |
| `bm-infra` | MySQL(sqlx)、Redis、Rabbit、file/memory 适配器 | 实现 domain ports |
| `bm-api` | HTTP DTO / 契约（可选扩展） | types |
| `bm-app` | Axum 路由、鉴权、编排 | domain + infra |
| `bm-worker` | 定时 tick：outbox、补偿、库存 | domain + infra |
| `bm-gateway` | 边缘代理 | 无业务 DB |

```text
HTTP ─► bm-app ─► bm-domain ◄── bm-infra ──► MySQL / Redis / File
                      ▲
                 bm-worker (async tick only)
```

## 后端模式（`BM_BACKEND`）

| 值 | 用途 | 持久化 |
| --- | --- | --- |
| `file`（默认） | 无 Docker 演示；`BM_DATA_DIR/state.json` | 本地 JSON |
| `memory` | 单元测试 | 无 |
| `mysql` | 与 Java 同库对照；全端口走 sqlx | MySQL 分片 + `big_market` 目录表 |

`mysql` 模式下 **不需要** `state.json` 业务伴随文件；JWT 吊销可走 Redis（`BM_REDIS_URL`）或内存回退。

## 核心流程（Rust 入口）

### 抽奖 → 发奖 → 入账

1. `bm-app` `draw_by_token` → `RaffleService::draw`
2. `QuotaStore::consume_one`（幂等键 `draw_{user}_{activity}_{uuid}`）
3. `ParticipationStore::count_draws` → `StrategyStore::award_weights` → `pick_with_chain_lite`（可选 `BM_STRATEGY_CHAIN` 黑名单/权重桶；默认仅 `tree_lock_N` + 加权；demo `100401` 永不走 chain）
4. `StockStore::decr_stock`（mysql：扣 `strategy_award.award_count_surplus`；活动软库存 dirty → `flush_dirty` 写 `activity_soft_stock`）
5. `AwardStore::save_award_record` + `enqueue_send_award_message`
6. **Worker tick：** `consume_send_award` → `credit_award_task` pending → `dispatch_pending` → `CreditStore::apply_trade`（幂等 `award_order_id`）

奖品列表：`query_raffle_award_list_by_token` 返回 `awardRuleLockCount` / `isAwardUnlock` / `waitUnLockCount`（对齐 Java DTO）。

代码：`bm-domain/src/raffle.rs`、`bm-domain/src/strategy.rs`、`bm-domain/src/worker.rs`（`JOB_CATALOG` + `WorkerScheduler`；`bm-worker` 暴露 `GET /actuator/jobs`）。

**Embed 互斥：** 设置 `BM_RABBIT_URL` 时 `bm-app` 默认关闭内嵌 worker（除非 `BM_EMBED_WORKER_FORCE=1`），由 `bm-worker` 消费 MQ/本地 outbox。

### SKU 兑换

`credit_pay_exchange_sku` → 积分扣减（`out_business_no={userId}_{sku}_{requestId}`）→ `QuotaStore::add_quota`。

### Chat 扣费 / 退款

`ChatBillingService`：Redis/内存 idempotent 缓存 + `chat_credit_session`（mysql）+ worker `reconcile_pending`。

### 签到返利

`RebateService::calendar_sign` → 积分入账 + 本地 rebate outbox（可选 Rabbit `bm.send_rebate`）。

## 与 Java 能力对照（诚实边界）

| 能力 | Rust 默认 | Java learning |
| --- | --- | --- |
| HTTP `/api/v1/**` | ✅ `bm-app` | ✅ 多服务经 gateway |
| 幂等 / outbox 语义 | ✅ 对齐 `data-and-outbox.md` | ✅ |
| MySQL 分片表 | ✅ `BM_BACKEND=mysql` | ✅ |
| Rule-tree 策略引擎 | lite：权重 + `tree_lock_N` + 可选 chain（非完整 Java 规则树） | 完整 |
| XXL-Job | `WorkerScheduler` 轮询 tick + `JOB_CATALOG`（无控制台） | 全量 handler |
| OpenAI Chatbot | 本地 echo | 可选外部 API |
| Nacos Admin | `platform_config` + ENV | Nacos sync |
| Dubbo RPC | 同进程 trait 调用 | 跨进程 |

## 配置（常用环境变量）

见 [`big-market-rs/README.md`](../big-market-rs/README.md)。安全演示：`BM_SECURE=1` + [`scripts/run-rust-secure.sh`](../scripts/run-rust-secure.sh)。

## 本地验证

```bash
./scripts/run-rust-stack.sh
./scripts/acceptance-rust.sh
./scripts/acceptance-rust.sh --e2e      # Playwright 17×2
./scripts/acceptance-rust.sh --mysql    # 需 :13306 MySQL
./scripts/acceptance-rust.sh --rabbit   # 需 :5672 Rabbit（无则 SKIP）
./scripts/acceptance-dual-stack.sh      # 可选 JAVA_API_BASE 契约对比
```

状态与限制：[`rust-refactor/STATUS.md`](../rust-refactor/STATUS.md)。  
切流说明：[`rust-refactor/CUTOVER.md`](../rust-refactor/CUTOVER.md)。  
剩余深度阶段（C–F，已完成）：[`rust-refactor/NEXT-PHASES.md`](../rust-refactor/NEXT-PHASES.md)。  
Rust 冻结边界：[`RUST-LEARNING-FREEZE.md`](./RUST-LEARNING-FREEZE.md)。

## 何时才拆更多进程

仅在**有观测证据**时考虑，例如：

- worker CPU 与 HTTP 长期争抢 → 关闭 `BM_EMBED_WORKER`，独立 `bm-worker`
- MQ 消费需独立扩缩 → 多 worker 实例 + 独立 queue（`BM_RABBIT_URL`）
- 合规要求物理隔离 → 再评估独立 chat/支付进程（学习项目通常不需要）

在此之前，增加 Rust 二进制数量只会提高运维成本，不会带来与 Java 微服务教科书对等的收益。

## 文档索引

| 文档 | 用途 |
| --- | --- |
| 本文档 | Rust 权威架构 |
| [`MICROSERVICES.md`](./MICROSERVICES.md) | Java legacy 架构 |
| [`data-and-outbox.md`](./data-and-outbox.md) | 幂等与 outbox |
| [`microservices-dao-ownership.md`](./microservices-dao-ownership.md) | 表归属（逻辑；Rust 同库） |
| [`LEARNING-FREEZE.md`](./LEARNING-FREEZE.md) | Java 栈冻结证据（历史） |
