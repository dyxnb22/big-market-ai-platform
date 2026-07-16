# Rust 轨 — 剩余阶段计划（M0–M7 之后）

最后修订：2026-07-16。

## 背景

| 轨道 | 状态 | 说明 |
| --- | --- | --- |
| **M0–M7** | ✅ 基线完成 | 见 [STATUS.md](./STATUS.md)、[ROADMAP.md](./ROADMAP.md) D1–D7 |
| **架构 Phase A** | ✅ | 模块化单体为权威默认（[`docs/MICROSERVICES-RUST.md`](../docs/MICROSERVICES-RUST.md)） |
| **架构 Phase B** | ✅ | `tree_lock_N` 参与过滤 + 统一 `WorkerScheduler` / `JOB_CATALOG` |

**M0–M7 已解决「能替代默认演示」**；下面剩余阶段解决「深度对齐学习价值 / 运维稳健 / 诚实冻结」，**不是**再走一遍 M0–M7。

**明确不做：** 按 Java 1:1 拆 8～10 个 Rust 进程、移植 XXL 控制台、Nacos 中心、物理 DB-per-service（除非有观测证据，见 MICROSERVICES-RUST「何时才拆更多进程」）。

---

## 总览（剩余 4 个 Phase）

```text
Phase C  策略深度（规则树 lite）     ✅
   ↓
Phase D  异步与任务稳健（Rabbit/DLQ/防双消费） ✅
   ↓
Phase E  MySQL 硬化（空库、刷库、对账） ✅
   ↓
Phase F  演示冻结收尾 ✅ → docs/RUST-LEARNING-FREEZE.md
```

| Phase | 目标 | 状态 | 退出门禁 |
| --- | --- | --- | --- |
| **C** | 策略/规则树学习路径对齐 | ✅ | `award_lock_view` + smoke `isAwardUnlock`; `BM_STRATEGY_CHAIN` lite |
| **D** | MQ/worker 可验证、无双入账 | ✅ | `smoke-rust-rabbit.sh`; Rabbit URL disables embed |
| **E** | mysql 空库可重复验收 | ✅ | `flush_dirty` → `activity_soft_stock`; mysql smoke note |
| **F** | 文档冻结 + 可选增强 | ✅ | `docs/RUST-LEARNING-FREEZE.md`（OpenAI 仍 out of scope） |

PR 切片建议：`phase-c-strategy-lite` → `phase-d-async-hardening` → `phase-e-mysql-hardening` → `phase-f-freeze`。

---

## Phase C — 策略深度（规则树 lite）

**为什么现在做：** Phase B 只做了 `tree_lock_N` 过滤；Java 学习文档的两阶段（责任链 + 规则树）仍是面试/对照高频点，需要 **可解释的 lite 版**，而不是声称「完整引擎」。

### 范围（做）

1. **奖品列表 lock 态**：`query_raffle_award_list` 返回每个奖品是否因 `tree_lock_N` 对当前用户锁定（前端转盘/列表可展示）。
2. **责任链 lite（可选插件，默认关闭或仅非 demo 活动）**
   - `rule_blacklist`：配置用户 → 固定奖（若种子数据存在则读表；否则 ENV/platform_config）
   - `rule_weight`：按累计抽奖次数切权重桶（对齐学习文档「分段权重」语义，简化实现）
3. **单测**：多奖品 + `tree_lock_2` / `tree_lock_3` 的 filter + list 可见性。
4. **文档**：`MICROSERVICES-RUST.md` 诚实写清「lite vs Java 完整树」。

### 非范围（不做）

- 完整 `rule_tree` / `rule_tree_node` / `rule_tree_node_line` 图引擎
- 动态热更新规则图（Nacos）

### 退出

```bash
cargo test -p bm-domain strategy::
./scripts/acceptance-rust.sh
# 若有奖品 list lock 字段：smoke 断言至少覆盖 activity 100401 不破坏确定性
```

### 风险

- 改变 `100401` 确定性单奖路径 → **禁止**；demo 活动保持单权重 `tree_luck_award`。

---

## Phase D — 异步与任务稳健

**为什么：** Rabbit 桥已存在，但缺独立 smoke；`BM_EMBED_WORKER` 与独立 `bm-worker` / Rabbit 同时开时有双消费风险（架构文档已禁止，代码侧要可验证）。

### 范围（做）

1. **`scripts/smoke-rust-rabbit.sh`**：起 app（`BM_EMBED_WORKER=0`）+ worker + Rabbit → 抽奖 → 入账闭环。
2. **互斥守卫**：当 `BM_RABBIT_URL` 已连接时，app 内嵌 scheduler **强制跳过** outbox 本地 consume（或启动时 warn+拒绝 embed consume）。
3. **JOB_CATALOG 对齐**：核对 Java XXL 学习子集；缺的关键 job（若有）补进 `WorkerScheduler::tick` 或明确标为 out-of-scope。
4. **失败可见性**：dispatch failed / rebate ingest 错误打 metrics counter（已有 Prometheus 则可复用）。

### 非范围

- XXL-Job Admin UI / 动态注册
- 多 worker 抢占锁（学习环境单 worker 即可）

### 退出

```bash
# 需 Rabbit 可达
./scripts/smoke-rust-rabbit.sh
./scripts/acceptance-rust.sh   # 默认 file 路径不被破坏
```

---

## Phase E — MySQL 硬化

**为什么：** `BM_BACKEND=mysql` 已全端口接入，但「空卷首次启动 / 库存刷回 / 对账」仍是最容易假绿的地方。

### 范围（做）

1. **空库/重置路径**：文档 + 脚本步骤（复用 `docs/dev-ops` init SQL + `z-reconcile-tables.sql`）；CI `mysql-smoke` 尽量接近干净库。
2. **库存 dirty → MySQL 回写**：worker `stock_flush` 不只 `clear_dirty`，对 mysql backend 写回 `award_count_surplus`（或现有表字段）。
3. **对账抽样**：脚本或 job 核对「completed 中奖记录 ≠ 已入账」提示（对齐 `data-and-outbox.md` 诚实边界）。
4. **`acceptance-rust.sh --mysql`** 稳定 PASS（含抽奖→入账）。

### 非范围

- 物理分库拆实例
- 与 Java 同时写同一分片表的双写灰度（可选日后，非本阶段）

### 退出

```bash
./scripts/acceptance-rust.sh --mysql
./scripts/smoke-rust-mysql.sh
```

---

## Phase F — 演示冻结收尾

**为什么：** 给 Rust 轨一份类似 `LEARNING-FREEZE.md` 的边界声明，避免后续「默认再拆微服务 / 再补 OpenAI」扯皮。

### 范围（做）

1. 新增 **`docs/RUST-LEARNING-FREEZE.md`**：已验证命令、未验证项、与 Java freeze 对照。
2. **Bench 补齐**：同机抽奖 P50/P99（Rust file / mysql）；Java 对照若环境不足则书面豁免。
3. **可选 OpenAI chatbot**（feature flag，默认关；失败回退 echo）— 仅当需要演示「外部模型」时做。
4. 更新 `STATUS.md`：勾选 C–F；标明仓库进入「Rust learning freeze」。

### 非范围

- 删除 Java 源码（保持 legacy 对照）
- 生产 HA / 容量认证

### 退出

- Freeze 文档合并；`acceptance-rust --e2e` 再跑一轮；STATUS 与 MICROSERVICES-RUST 无矛盾宣称。

---

## 执行顺序与并行

| 建议 | 说明 |
| --- | --- |
| **先 C 后 E** | 策略 list/lock 行为稳定后再硬化 mysql 对照 |
| **D 可与 C 并行** | 不同子系统（strategy vs worker/MQ） |
| **F 最后** | 冻结文档依赖 C–E 证据 |

若只能做一个 Phase：**做 C**（学习对照价值最高，且改动面可控）。

---

## 与旧文档命名对照

| 名称 | 含义 |
| --- | --- |
| M0–M7 | 主路线图：从骨架到替代 Java 默认路径（已完成） |
| PLAN「Phase A/B」 | A=模块化单体（已采纳）；B=可选拆进程（**仍不默认做**） |
| 本文 Phase B | 已完成的 lock + WorkerScheduler（勿与 PLAN Phase B 混淆） |
| 本文 Phase C–F | **剩余工作**（本文件权威） |

更新清单勾选时改本文件与 [STATUS.md](./STATUS.md)，不要再发明第三套 Phase 字母表。
