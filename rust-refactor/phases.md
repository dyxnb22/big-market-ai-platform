# 分阶段落地清单

> 本文件是执行清单。当前仓库仅交付方案文档；实现从 Phase 0 另开任务。

## Phase 0 — 工程骨架（无业务）

**交付**

- [ ] 创建 `big-market-rs/` Cargo workspace  
- [ ] `bm-types` / `bm-domain` / `bm-infra` / `bm-api` / `bm-app` / `bm-worker` / `bm-gateway` 空 crate  
- [ ] `docker-compose.rust.yml` overlay（复用 `docs/dev-ops` 中间件）  
- [ ] CI：`cargo fmt`、`clippy -D warnings`、`cargo test`  
- [ ] README：如何 `cargo run -p bm-app` 与健康检查  

**退出标准：** `GET /actuator/health` 或 `/health` 返回 200；RSS 有记录。

## Phase 1 — Auth

**交付**

- [ ] 登录签发 JWT、校验、logout 写 Redis 吊销  
- [ ] 网关透传 / 拒绝吊销 token  
- [ ] 与现有 demo 用户表兼容或提供 seed  

**退出标准：** 对齐 `smoke-security` 中「注销后拒绝」语义；前端可对 Rust auth 登录。

## Phase 2 — 只读策略 + 确定性抽奖

**交付**

- [ ] 读取策略、奖品、权重  
- [ ] 固定策略（如 10007 → 101）可抽通  
- [ ] 尚可不接完整配额  

**退出标准：** 单测覆盖抽奖纯函数；HTTP 返回奖品 ID 稳定。

## Phase 3 — 活动参与 + 配额 + 写中奖记录

**交付**

- [ ] 参与订单创建/复用  
- [ ] 配额扣减事务 + 分片路由  
- [ ] 写 `user_award_record` + outbox/task 行  

**退出标准：** DB 状态可人工核对；重复参与幂等。

## Phase 4 — Worker 发奖闭环（P0 业务）

**交付**

- [ ] `SendAward` 消费（独立 queue，避免与 Java 抢）  
- [ ] `credit_award_task` 写入与状态迁移  
- [ ] 定时/拉取派发到 account credit  
- [ ] 账户流水可查  

**退出标准：** 语义对齐 `smoke-raffle-award-e2e`（兑换扣分 → 抽奖 → outbox → 入账）。

## Phase 5 — Money path 加固

**交付**

- [ ] SKU 兑换 `requestId` 幂等  
- [ ] Chat 扣费 / 退款 / reconcile job  
- [ ] DLQ 与 UNKNOWN 重试策略文档化  

**退出标准：** 重复请求零双花；退款状态机单测完备。

## Phase 6 — 性能对照与文档

**交付**

- [ ] bench 表格：RSS、启动、P99  
- [ ] 更新本目录结论区（实测数字替换预估值）  
- [ ] （可选）网关按路径切流 runbook  

**退出标准：** 达到 [PLAN.md](./PLAN.md) §9 成功标准或书面说明未达标原因。

## Phase 7 — 可选拆分

**交付**

- [ ] account / auth 独立进程 + gRPC  
- [ ] 端口对齐 8081/8086 便于对照  
- [ ] rebate/strategy 独立开关（默认仍 embedded）  

**退出标准：** 拆分后闭环回归仍通过；内存仍优于 Java 多进程基线。

## 明确不在初期范围

- 物理 DB-per-service  
- 完整复刻 XXL-Job Admin 全量任务种子  
- 前端 React 化  
- 生产级 mTLS / 多活  

## 建议的 PR 切片（实现阶段）

1. `rust-workspace-skeleton`  
2. `rust-auth-jwt`  
3. `rust-raffle-deterministic`  
4. `rust-award-outbox-worker`  
5. `rust-money-path-chat-sku`  
6. `rust-bench-and-docs`  

与 Java 轨 PR 互不阻塞；合并前不得删除或削弱现有 `acceptance.sh` Java 路径。
