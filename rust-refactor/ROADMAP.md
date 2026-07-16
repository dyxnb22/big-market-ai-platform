# Big Market Rust 重构路线图（至替代 Java）

最后修订：2026-07-16。

本路线图从零写到 **Rust 成为默认可运行栈、可替代原 Java 微服务**。  
技术栈已锁定，见 [tech-stack.md](./tech-stack.md)。阶段细则见 [phases.md](./phases.md)。

```text
M0 骨架 → M1 鉴权网关 → M2 抽奖发奖闭环 → M3 积分/聊天/返利
 → M4 管理与运维对齐 → M5 验收与性能门禁 → M6 切流替代 Java
 → M7 归档 Java 默认路径 → ✅ 完成
```

---

## 完成定义（Definition of Done）

同时满足以下条件，才算「Rust 可替代原本的 Java」：

| # | 条件 |
| --- | --- |
| D1 | 默认 `docker compose up`（或文档指定的默认 profile）启动的是 **Rust 服务**，不再依赖 Spring Boot 进程 |
| D2 | `./scripts/acceptance.sh --reuse`（或等价默认验收）在 **Rust 栈**上通过：HTTP 契约、真实抽奖→outbox→入账、Chat 补偿、安全负向、Playwright |
| D3 | 前端 `big-market-web` **零改或仅改配置**即可对 Rust 网关 `:8080` 工作 |
| D4 | 幂等与 Outbox 语义对齐 `docs/data-and-outbox.md`；无双入账 |
| D5 | 应用侧 RSS ≤ Java 旧栈 **20%**；抽奖 P99 ≤ Java **50%**（同机同数据，数字写入 bench 报告） |
| D6 | 文档入口改为 Rust（`README` / `MICROSERVICES` 等价文档）；Java 降为 `legacy` profile 或 `docs/archive` |
| D7 | CI 默认跑 `cargo test` + Rust acceptance；Java 构建改为 optional / scheduled |

未满足 D1–D7 前，Java 学习冻结基线仍是权威可运行栈。

---

## 里程碑总览

| 里程碑 | 名称 | 目标 | 替代进度 |
| --- | --- | --- | --- |
| **M0** | 工程骨架 | Cargo workspace、健康检查、compose overlay、CI | 0% |
| **M1** | 鉴权 + 网关 | JWT 登录/吊销；网关路由到 Rust | ~10% |
| **M2** | 抽奖发奖闭环 | 登录→兑换/配额→抽奖→MQ→入账 | ~40% |
| **M3** | Money + 周边业务 | SKU、Chat 扣退、返利、库存 job | ~65% |
| **M4** | 管理 / 配置 / 运维 | Admin、DCC、安全 overlay、指标面板 | ~80% |
| **M5** | 全量验收门禁 | `acceptance-rust` 对齐 Java 证据集 + 性能对照 | ~90% |
| **M6** | 切流替代 | 默认 compose/脚本切到 Rust；双跑对比通过 | ~98% |
| **M7** | 收尾归档 | Java 移出默认路径；文档与仓库声明 Rust 为权威 | **100%** |

---

## M0 — 工程骨架

**产出**

- 仓库根目录创建 `big-market-rs/`（Cargo workspace）
- crates：`bm-types` / `bm-domain` / `bm-infra` / `bm-api` / `bm-gateway` / `bm-app` / `bm-worker`
- `docker-compose.rust.yml`：复用 `docs/dev-ops` 的 MySQL/Redis/RabbitMQ
- GitHub Actions：`cargo fmt`、`clippy -D warnings`、`cargo test`
- `/health` + Prometheus `/metrics` 空实现

**退出：** 两进程（app + worker）可起；RSS 有基线记录。

**锁定技术：** 见 tech-stack；本阶段引入 Tokio、Axum、tracing、figment。

---

## M1 — 鉴权与网关

**产出**

- `POST/GET` 登录、校验、logout；Redis denylist
- `bm-gateway`：`/api/v1/**` 反代到 `bm-app`；Trace ID；基础超时/拒绝
- 与 demo 用户兼容；前端可登录 Rust

**退出：** 安全负向：吊销 JWT 被拒；伪造 token 被拒。

**并行策略：** 网关可按 path 把 `/api/v1/auth/**` 指到 Rust，其余仍 Java（灰度练习）。

---

## M2 — 抽奖 → 发奖 → 入账（P0 闭环）

**产出**

- 策略读取 + 抽奖（先确定性策略，再权重随机）
- 活动参与、配额扣减、分片路由（`big_market_01/02`）
- 写 `user_award_record` + outbox/task
- `bm-worker`：`SendAward` 消费 → `credit_award_task` → account credit
- **独立 RabbitMQ queue**，避免与 Java consumer 抢消息

**退出：** 语义对齐 `smoke-raffle-award-e2e`（扣积分 → 固定奖 → outbox `dispatched` → 账户流水）。

**说明：** 达成本里程碑后，Rust 已具备「主业务故事」演示能力，但仍不能替代全栈。

---

## M3 — Money path 与周边业务

**产出**

| 能力 | 要求 |
| --- | --- |
| SKU 兑换 | `requestId` → `out_business_no={userId}_{sku}_{requestId}` |
| Chat 扣费/退款 | Redis idempotency + refund 状态机 + reconcile job |
| 行为返利 | 签到返利订单 + `send_rebate` 消费 |
| 库存 | Redis 计数 + 异步刷库 job（对齐原 Update*StockJob） |
| 履约 | 默认本地积分履约；预留 fulfillment 扩展点 |

**退出：** Chat 立即退款与补偿均通过；重复请求零双花。

---

## M4 — 管理、配置、安全与可观测

**产出**

- Admin 配置 API（平台参数）；配置以文件/ENV 为源，**Nacos 同步为可选 feature**（`nacos-sync`）
- DCC 动态配置读取（进程内 + 可选远程）
- Secure overlay：非默认 JWT、内部 token、演示凭据，对齐 `docker-compose.secure.yml` 意图
- Grafana：`rust-*` dashboard（JVM 面板不复用）
- 限流：gateway Tower rate-limit（替代 starter-ratelimiter 默认关闭策略可配置）

**退出：** `smoke-security` 全项在 Rust 通过；`/metrics` 可刮取关键业务指标。

---

## M5 — 全量验收与性能门禁

**产出**

- `scripts/acceptance-rust.sh`：复刻 Java acceptance 证据集  
  - HTTP contracts、microservices smoke 等价、API smoke  
  - raffle-award E2E、chat refund E2E、security、Playwright×2  
- `rust-refactor/bench/RESULTS.md`：RSS / 启动 / P99 对照表
- Context 级集成测试：关键领域 + worker 状态机

**退出：**

- acceptance-rust **PASS**（`--reuse`；并至少一次文档化的 `--fresh` 尝试或明确跳过原因）
- 性能达到 PLAN 成功标准，或书面豁免（瓶颈在 DB 时需附 profile）

---

## M6 — 切流：Rust 成为默认栈

**产出**

- 根目录 `docker-compose.yml` 默认服务改为 Rust 二进制（或 `compose` profile `default=rust`）
- `scripts/acceptance.sh` 默认指向 Rust；Java 改为 `acceptance-java.sh` / profile `legacy-java`
- 网关只路由 Rust；去掉 Java 上游依赖
- README / 学习文档增加「默认栈 = Rust」说明；原 LEARNING-FREEZE 标注为历史 Java 基线

**切流检查清单（必须全勾）**

- [ ] M5 退出标准全部满足  
- [ ] 连续两轮 acceptance-rust PASS  
- [ ] Playwright 18 条×2 PASS  
- [ ] 无已知 P0 资金路径缺陷  
- [ ] 回滚方案就绪：`profile legacy-java` 仍可一键拉起旧栈  

**退出：** 新同学按 README 启动，得到的是 Rust，而不是 Java。

---

## M7 — 归档 Java，宣布替代完成

**产出**

- Java 模块保留在仓库但标记 `legacy/` 或文档声明「仅供对照，不默认构建」
- CI：`mvn` 改为 `workflow_dispatch` / nightly；PR 默认只跑 Cargo
- `docs/MICROSERVICES.md` 增加 Rust 权威入口，或新增 `docs/MICROSERVICES-RUST.md` 并在 README 置顶
- 本目录 `rust-refactor/STATUS.md` 写明：**替代完成日期 + 提交 SHA + 验收命令 + bench 摘要**
- （可选）删除已无用的 Java launcher Dockerfile 引用，避免误导

**退出：** D1–D7 全部打勾 → 路线图关闭。

---

## 依赖关系（简图）

```text
M0
 └─ M1
     └─ M2 ──────────────┐
         └─ M3 ──────────┤
             └─ M4 ──────┤
                 └─ M5 ←─┘  （M2–M4 功能并入同一验收）
                     └─ M6
                         └─ M7 ✅
```

M2 是业务关键路径，阻塞 M5/M6。  
M3/M4 可部分并行（例如 Chat 与 Admin），但 **M5 前必须齐活**。

---

## 每阶段统一门禁

无论哪个里程碑合并主线前：

1. `cargo clippy -D warnings` 与 `cargo test` 通过  
2. Money path 变更走幂等键审查（对照 `docs/data-and-outbox.md`）  
3. 不删除、不削弱 Java `acceptance.sh`，直到 M6 显式切流  
4. `bm-app` 不注册 MQ consumer / credit dispatch（消费只在 `bm-worker`）

---

## 建议实现 PR 序列（对应里程碑）

| PR 主题 | 里程碑 |
| --- | --- |
| `rust-workspace-skeleton` | M0 |
| `rust-auth-and-gateway` | M1 |
| `rust-raffle-outbox-account` | M2 |
| `rust-sku-chat-rebate-stock` | M3 |
| `rust-admin-dcc-secure-metrics` | M4 |
| `rust-acceptance-and-bench` | M5 |
| `rust-default-compose-cutover` | M6 |
| `rust-archive-java-baseline` | M7 |

---

## 明确不做（全程）

- 不为“更像微服务”做物理 DB-per-service  
- 不把前端改成 React（保持 `big-market-web`）  
- 不在切流前宣称“已替代 Java”  
- 不为微优化引入大量 `unsafe`  
- 不在 M6 前移除 Java 默认可运行路径  

---

## 当前状态

| 项 | 状态 |
| --- | --- |
| 方案与路线图文档 | ✅ |
| `big-market-rs/` 代码 | ✅ M0–M7 + frontend API parity |
| 默认演示路径 | ✅ Rust（`acceptance-rust.sh` / README） |
| Java | ✅ 保留为 legacy 对照（未删模块） |
| 可选后续 | OpenAI chatbot、完整 XXL-Job、Java rule-tree lock 引擎 |
