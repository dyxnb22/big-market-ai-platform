# 分阶段执行清单（对齐 ROADMAP M0–M7）

> 与 [ROADMAP.md](./ROADMAP.md) 一一对应。技术栈以 [tech-stack.md](./tech-stack.md) 为准。  
> 当前仅文档；代码从 M0 起在 `big-market-rs/` 落地。

---

## M0 — 工程骨架

- [ ] 创建 `big-market-rs/` workspace（edition 2021，MSRV ≥1.78）
- [ ] crates：`bm-types` / `bm-domain` / `bm-infra` / `bm-api` / `bm-gateway` / `bm-app` / `bm-worker`
- [ ] 引入：tokio、axum、tower-http、tracing、figment、thiserror
- [ ] `docker-compose.rust.yml`（复用 dev-ops 中间件）
- [ ] CI：fmt、clippy `-D warnings`、test、cargo-deny
- [ ] `/health`、`/metrics` 空实现；Release profile（thin LTO）

**退出：** app + worker 可起并报健康。

---

## M1 — 鉴权 + 网关

- [ ] JWT 登录 / 校验 / logout + **fred** Redis denylist
- [ ] `bm-gateway`：路由、`Authorization`、trace id、超时
- [ ] 兼容 demo 用户；前端可登录
- [ ] 安全负向：吊销后拒绝、伪造 token 拒绝

**退出：** 对齐 `smoke-security` 鉴权相关项。

---

## M2 — 抽奖发奖入账闭环

- [ ] 策略读 + 抽奖（确定性 → 权重随机）
- [ ] 活动参与、配额事务、`DbRouter` 分片
- [ ] 写中奖记录 + outbox/task（**sqlx**）
- [ ] worker：**lapin** 消费 `SendAward`（独立 queue）
- [ ] `credit_award_task` + **tokio-cron-scheduler** 派发入账

**退出：** `smoke-raffle-award-e2e` 语义 PASS。

---

## M3 — SKU / Chat / 返利 / 库存

- [ ] SKU 兑换幂等（`{userId}_{sku}_{requestId}`）
- [ ] Chat 扣费、退款状态机、reconcile job
- [ ] 行为返利 + `send_rebate` 消费
- [ ] 库存 Redis + 刷库 job
- [ ] DLQ / UNKNOWN 重试说明

**退出：** Chat 退款与补偿 PASS；无双花。

---

## M4 — Admin / DCC / Secure / 指标

- [ ] Admin 配置 API（ENV/Toml；Nacos 仅可选 feature）
- [ ] DCC 动态配置
- [ ] Secure overlay 对齐
- [ ] Prometheus 业务指标 + Grafana `rust-*` 面板
- [ ] Gateway 限流可配置

**退出：** 安全 smoke 全过；指标可刮取。

---

## M5 — 全量验收 + 性能

- [ ] `scripts/acceptance-rust.sh`（契约、E2E、Playwright×2）
- [ ] `rust-refactor/bench/RESULTS.md`（RSS / 启动 / P99）
- [ ] 达标或书面豁免（附 profile）

**退出：** acceptance-rust PASS + 性能门禁满足 ROADMAP D5。

---

## M6 — 切流：Rust 为默认

- [ ] 默认 `docker-compose.yml` / profile 指向 Rust
- [ ] `acceptance.sh` 默认 Rust；Java → `legacy` / `acceptance-java.sh`
- [ ] README 声明默认栈 = Rust
- [ ] 回滚：`legacy-java` 仍可一键启动
- [ ] 连续两轮 acceptance PASS

**退出：** 新用户按文档启动即为 Rust（ROADMAP D1）。

---

## M7 — 归档 Java，替代完成

- [ ] Java 标为 legacy；CI 默认只跑 Cargo
- [ ] 文档权威入口切到 Rust
- [ ] 写 `rust-refactor/STATUS.md`（日期、SHA、验收命令、bench）
- [ ] 勾选 ROADMAP **D1–D7**

**退出：** 路线图关闭；Rust 正式替代 Java 默认栈。

---

## 全程禁区

- M6 前不删除 Java 默认路径  
- `bm-app` 不消费 MQ / 不跑 credit dispatch  
- 不改 money path 幂等键语义  
- 不把前端改成 React  
- 不采用 [tech-stack.md](./tech-stack.md) §11 已拒绝的技术  

## PR 切片

1. `rust-workspace-skeleton` → M0  
2. `rust-auth-and-gateway` → M1  
3. `rust-raffle-outbox-account` → M2  
4. `rust-sku-chat-rebate-stock` → M3  
5. `rust-admin-dcc-secure-metrics` → M4  
6. `rust-acceptance-and-bench` → M5  
7. `rust-default-compose-cutover` → M6  
8. `rust-archive-java-baseline` → M7  
