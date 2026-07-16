# Rust 重构总方案

最后修订：2026-07-16。基于当前 Java 微服务学习栈（gateway / auth / market / message-job / account 等）制定。

**主路线图（至替代 Java）：** [ROADMAP.md](./ROADMAP.md)  
**技术栈（已锁定）：** [tech-stack.md](./tech-stack.md)

## 1. 动机

| 痛点（当前 Java 栈） | Rust 预期收益 |
| --- | --- |
| 多 Spring Boot 进程各自堆 + Metaspace，本地需 ≥12GB Docker 内存 | 单二进制 / 少进程，RSS 可压到百 MB 级 |
| JVM 冷启动慢，compose 全量重建成本高 | 原生启动秒级，适合本地学习与 CI |
| 抽奖热路径对象分配多（DTO/领域实体装箱） | 零成本抽象、栈分配、复用缓冲，降低 GC 抖动 |
| Dubbo + 多 launcher 扫描边界易踩坑 | 进程内模块 + 显式 gRPC/HTTP，边界用类型系统表达 |

**终局目标：** 路线图 M7 完成后，Rust 成为默认可运行栈并替代 Java（见 ROADMAP 完成定义 D1–D7）。

**非目标（全程）：** 不做生产 HA / 物理库拆分；不重写前端框架（继续复用 `big-market-web`）；M6 前不拆除 Java 默认可运行路径。

## 2. 设计原则

1. **并行轨，不污染冻结基线** — Java 栈保持可验收；Rust 轨独立目录、独立 compose profile。
2. **语义兼容优先于结构同构** — HTTP 契约、幂等键（`out_business_no`、`award_order_id`、`chat:request:{userId}:{requestId}` 等）与 Outbox 状态机必须对齐 `docs/data-and-outbox.md`。
3. **先垂直切片，后水平拆服务** — 先打通「登录 → 抽奖 → 发奖 outbox → 入账」，再拆独立进程。
4. **内存与延迟可度量** — 每阶段报告 RSS、启动时间、抽奖 P50/P99、MQ 消费延迟；无数字不宣称“优化成功”。
5. **Money path 保守** — 积分/配额/库存写路径保持幂等与 UNKNOWN/补偿语义，禁止“为了快”去掉幂等键。

## 3. 目标架构（Phase A：学习友好）

```text
big-market-web :5173  (不变)
        │
   bm-gateway-rs :8080     # Axum 路由 + JWT 透传 + 简单熔断
        │
   bm-app-rs :8083         # 单体应用进程（默认）
        ├── auth           # JWT 签发 / 校验 / Redis 吊销
        ├── market         # 活动 / 抽奖 / SKU
        ├── strategy       # 策略抽奖（原 embedded）
        ├── rebate         # 返利（原 embedded）
        ├── chatbot        # 扣费会话（可选同进程）
        ├── account        # 积分 / 配额
        └── fulfillment    # 本地履约（默认）

   bm-worker-rs :8085      # MQ 消费 + 定时派发（替代 message-job + XXL 子集）
        ├── SendAward consumer
        ├── credit_award_task dispatcher
        └── 库存刷新 / 补偿 job

中间件复用现有：MySQL / Redis / RabbitMQ /（可选）Nacos、Prometheus
```

**为何默认单体 + 独立 worker：**

- 砍掉 8～10 个 JVM 的固定开销，直接兑现内存目标。
- 保留 **同步写路径** 与 **异步消费** 的进程隔离（避免抽奖线程被慢消费拖死）。
- 日后拆 `bm-account-rs` / `bm-auth-rs` 时，crate 边界已经存在，只需换 transport。

Phase B（可选）：按 crate 拆独立进程，gRPC 替代进程内调用；端口对齐现有 8081–8089 便于对照学习。

## 4. Cargo Workspace 建议结构

```text
big-market-rs/
├── Cargo.toml                 # workspace
├── crates/
│   ├── bm-types/              # 错误码、Money、ID 类型、幂等键工具
│   ├── bm-domain/             # 纯领域：activity / strategy / award / credit / rebate / chat
│   ├── bm-infra/              # SQLx / Redis / RabbitMQ / 分库路由
│   ├── bm-api/                # OpenAPI / proto / DTO
│   ├── bm-gateway/            # bin: gateway
│   ├── bm-app/                # bin: 默认同进程 HTTP API
│   └── bm-worker/             # bin: MQ + scheduler
├── proto/                     # 若采用 gRPC
└── docker/                    # 可选独立 compose overlay
```

领域模块保持与 Java 包大致同名，降低对照成本：

- `domain::activity` ← `com.dyx.market.domain.activity`
- `domain::strategy` ← `...strategy`
- `domain::award` ← `...award`
- `domain::credit` ← `...credit`
- `domain::rebate` ← `...rebate`
- `domain::chat` ← `...chat`

## 5. 技术选型（已锁定）

完整条款见 [tech-stack.md](./tech-stack.md)。摘要：

| 能力 | 锁定 |
| --- | --- |
| HTTP / 中间件 | **axum** + **tower** / **tower-http** |
| 运行时 | **tokio** |
| DB | **sqlx**（MySQL，不用 SeaORM/Diesel） |
| Redis | **fred** |
| MQ | **lapin** |
| 进程间 RPC（可选拆分） | **tonic** + **prost** |
| JWT | **jsonwebtoken** + Redis denylist |
| 配置 | **figment**（Env + Toml）；Nacos 非默认 |
| 可观测 | **tracing** + **metrics** + Prometheus exporter |
| 定时任务 | **tokio-cron-scheduler**（不复刻 XXL Admin UI） |
| 前端 | **big-market-web** 不变 |

拒绝清单（Actix 默认、Kafka 替换、Dubbo 兼容层等）见 tech-stack §11。

## 6. 核心链路迁移顺序

与 Java 学习路径对齐，保证每步可演示：

1. **Auth** — login / verify / logout + Redis 吊销  
2. **Strategy 读 + 固定奖抽奖** — 先打通 `strategy 10007 → award 101` 类确定性路径  
3. **Activity 参与 + 配额扣减** — 事务边界与分片路由  
4. **Award 写记录 + send_award 出站** — Outbox / task 行  
5. **Worker：SendAward → credit_award_task → Account credit** — 完整闭环  
6. **SKU 兑换 / Chat 扣费退款** — money path 加固  
7. **Rebate / Admin / DCC** — 次优先  

完整路线图见 [ROADMAP.md](./ROADMAP.md)；执行清单见 [phases.md](./phases.md)；服务映射见 [service-mapping.md](./service-mapping.md)。

## 7. 数据与幂等（硬约束）

从 Java 栈继承，不得“简化掉”：

| 场景 | 键 / 状态 |
| --- | --- |
| 积分订单 | `out_business_no` |
| 奖品积分发放 | `award_order_id` + `credit_award_task` 状态机 → `dispatched` |
| Chat 扣费 | Redis `chat:request:{userId}:{requestId}`；`out_business_no = chat_{userId}_{requestId}` |
| Chat 退款 | `chat_refund_{userId}_{requestId}`；`refund_state: none\|pending → refunding → refunded` |
| SKU 兑换 | `out_business_no = {userId}_{sku}_{requestId}` |
| 中奖记录 | `user_award_record.award_state=completed` **不等于** 已入账；入账以 outbox + 流水为准 |

分库：保留 `big_market_01` / `big_market_02` 路由语义（`userId` hash），用显式 `DbRouter` 而不是隐式 AOP。

## 8. 与 Java 共存 → 替代策略

| 阶段 | 做法 |
| --- | --- |
| M0–M5 共存 | 同一中间件；Rust 用 `compose` profile `rust`；独立 MQ queue |
| 灰度 | 网关可按 path 切流（如先 `/api/v1/auth/**`） |
| M5 验收 | `scripts/acceptance-rust.sh` 对齐 Java 证据集 |
| M6 切流 | 默认 compose + `acceptance.sh` 指向 Rust；Java → `legacy` |
| M7 归档 | Java 非默认 CI；文档权威入口改为 Rust |

## 9. 成功标准（方案级）

相对默认 Java compose 复用栈（同等业务负载脚本）：

| 指标 | 目标（建议基线，落地后校准） |
| --- | --- |
| 应用侧 RSS 合计 | ≤ Java 栈的 **20%** |
| 冷启动到 ready | ≤ **5s** / 进程（不含中间件） |
| 抽奖 HTTP P99 | ≤ Java 的 **50%**（同机同数据） |
| 闭环正确性 | `smoke-raffle-award-e2e` 语义等价通过 |
| 回归 | 关键幂等：重复请求不双入账 |

内存/性能手段详见 [memory-perf.md](./memory-perf.md)。

## 10. 风险摘要

- **业务复杂度在领域不在语言** — 策略规则、库存、补偿状态机仍是主成本。  
- **XXL-Job Admin UI** — 学习演示可先用内置 scheduler；若需保留 Admin，再做 HTTP 回调适配。  
- **MyBatis XML 多副本** — Rust 侧单一 `queries/` 目录，避免再复制一份。  
- **团队技能** — 异步 + 生命周期 + 事务边界有学习曲线；用垂直切片控制爆炸半径。

## 11. 下一步（实现尚未开始）

1. 按 [ROADMAP.md](./ROADMAP.md) 启动 **M0**：创建 `big-market-rs/` 与 CI。  
2. 技术栈严格遵循 [tech-stack.md](./tech-stack.md)。  
3. 依次推进 M1→M7，直至 D1–D7 完成、Rust 替代 Java 默认栈。
