# 全仓审批整改计划（参考）

> 来源：2026-07-10 Codex 全仓审批报告（结论：不通过）。  
> 用途：后续修 bug / 补闭环时按阶段执行，避免跳步或重复劳动。  
> 原则：**先能启动与演示，再修一致性与安全默认值，最后做架构债。**

相关文档：

- `README.md` / `docs/MICROSERVICES.md` — 部署与拓扑声明（修完后需同步措辞）
- `docs/data-and-outbox.md` — outbox / 幂等
- `docs/operations-checklist.md` — 运维检查项
- `docs/learning/archive/risky-changes-remediation.md` — 资金类改动风险约束
- `AGENTS.md` / `.cursor/rules/` / `.cursor/skills/` — Agent 常驻规则与任务技能（改代码时优先遵循）

---

## 0. 总目标与完成定义

| 目标档 | 完成定义 |
| --- | --- |
| **A. 可启动** | `market-service`、`message-job-service` Context 能起来；gateway 不再因 market 不健康卡住 |
| **B. 可演示闭环** | fresh Docker 下：登录 → 签到/兑换 → 抽奖 → 发奖/积分到账 → 库存落库，前端用真实 stage 活动 |
| **C. 准生产基线** | 关键写路径幂等正确、补偿语义正确、默认安全边界成立、门禁能拦住启动级回归 |

**当前状态（2026-07-10 复核修复后）：**

- **A. 可启动** — 代码已修：BM-001/002/003；`MarketServiceSpringBootContextTest` 与 `MessageJobServiceApplicationContextTest` 为全量 `@SpringBootTest`；message-job `MessageJobReadAdapterConfig` + `IAccountReadAdapter` @Primary。
- **B. 可演示闭环** — `./scripts/acceptance.sh --reuse` 全绿（2026-07-11）：`test-http-contracts`（401/403/400）、smoke 21/21、smoke-api、Chat 退款 E2E、Playwright 18/18×2；旧卷统一 `./scripts/apply-stack-migrations.sh`；演示前 `./scripts/ensure-demo-activity-online.sh`（stage `c01/s01`→100401）。
- **C. 准生产基线** — 部分：BM-015 `secure` + 关键写路径终态（库存 ledger / chat deduct_state / remote continuation FSM / `acceptance.sh`）已落地；BM-016/017 按两周工程债计划推进（指标门禁 + ArchUnit/Mapper 漂移，不做物理拆库）。

**执行约定：**

1. 每阶段结束必须有可勾选验收项；未验收不进入下一阶段。
2. 改资金/库存/发奖语义时，对照 `risky-changes-remediation.md`，保留幂等键与回滚路径。
3. 文档与代码冲突时，以代码为准，并回写文档。
4. 优先补「能发现本阶段问题」的测试，再扩功能。

---

## 1. 问题清单索引（BM → 阶段）

| ID | 摘要 | 严重性 | 阶段 |
| --- | --- | ---: | --- |
| BM-001 | market 漏扫 `trigger.application/support/adapter` | P0 | 1 |
| BM-002 | message-job 重复 Mapper + 漏扫 application | P0 | 1 |
| BM-003 | XXL appname/group 不一致，handler 未 seed | P0 | 1 |
| BM-004 | 积分发奖 dispatcher 未调度；OpenAI 奖品断裂 | P1 | 2 |
| BM-005 | 权重/黑名单接管绕过库存树 | P1 | 2 |
| BM-006 | SKU 返利先扣库存后幂等 | P1 | 2 |
| BM-007 | 远程写补偿语义错误 + pending 路由错库 | P1 | 2 |
| BM-008 | 库存落库 poll 后 DB 失败丢事件；confirm 路由 clear | P1 | 2 |
| BM-009 | remote quota 模式非等价（余额恒 0 / 未知终态） | P1 | 2 |
| BM-010 | Chatbot session 路由 + requestId 重放免费调 AI | P1 | 2 |
| BM-011 | DCC 默认关闭；namespace 点号解析；delete 无 tombstone | P1 | 3 |
| BM-012 | 前端强制回退 activityId=100301 | P1 | 3 |
| BM-013 | 上下架双表非原子 | P1 | 3 |
| BM-014 | 前端 logout 不调吊销接口 | P1 | 3 |
| BM-015 | 默认栈安全边界不足（RPC/Redis/凭据/限流） | P1 | 3 |
| BM-016 | Prometheus/告警缺口 | P2 | 4 |
| BM-017 | 边界仅文档、Mapper 多处复制 | P2 | 4 |

---

## 2. 阶段计划

### 阶段 1 — 解除启动阻塞（目标档 A）

**目标：** fresh 构建下两个关键服务可启动，补偿 Job 可被调度。

#### 1.1 修复 market 组件扫描（BM-001）

- **改动点**
  - `big-market-market-service/.../MarketServiceApplication.java`：精确增加  
    `com.dyx.market.trigger.application`、`trigger.support`、`trigger.adapter`（或等价 `@Import`）。
  - 确认与现有 `market.config` 中 remote adapter / `@ConditionalOn*` 不冲突（本地 adapter 仅在缺省时生效）。
- **验收**
  - [ ] `market-service` `@SpringBootTest` Context 加载成功
  - [ ] 关键 Controller（如 `RaffleActivityController`）所需 Facade/ApplicationService 均有 Bean
  - [ ] 不误扫 `trigger.job` / `trigger.listener`（仍归 message-job）

#### 1.2 修复 message-job 启动（BM-002）

- **改动点**
  - 删除  
    `big-market-message-job-service/.../raffle_activity_stage_mapper.xml`  
    中重复的 `updateStageActivity2ExpireById`（保留带 `update_time` 的版本，并与其他服务 mapper 对齐）。
  - `MessageJobServiceApplication` 增加扫描  
    `com.dyx.market.trigger.application`（或 `@Import` 所需 ApplicationService）。
  - 确认 `ChatRefundReconcileJob`、`RebateMessageConsumer` 依赖可注入。
- **验收**
  - [ ] message-job `@SpringBootTest` Context 加载成功
  - [ ] 对该服务全部 Mapper XML 构建 `SqlSessionFactory` 无重复 statement

#### 1.3 对齐 XXL-Job 调度（BM-003）

- **改动点**
  - 统一 executor `appname` 与 `docs/dev-ops/mysql/sql/xxl_job.sql` 中 `xxl_job_group.app_name`  
    （建议以 message-job 的 `big-market-message-job` 为准，同步改 SQL；或反过来统一，二选一写进注释）。
  - 为全部必须运行的 `@XxlJob` handler 补 seed，且关键任务 `trigger_status=1`，至少包括：
    - `updateAwardStockJob` / `UpdateActivitySkuStockJob`
    - `SendMessageTaskJob_DB1/DB2`
    - `DispatchCreditAwardTaskJob_DB1/DB2`
    - `StrategyAwardStockConfirmJob_DB1/DB2`
    - `CreditPayDeliveryReconcileJob_DB1/DB2`
    - `RemoteWriteReconcileJob` / `DlqReplayJob` / `ChatRefundReconcileJob`（按产品需要启用）
  - 可选：脚本或单测「反射收集 `@XxlJob` ↔ SQL seed」做门禁。
- **验收**
  - [ ] appname 与 group 一致
  - [ ] 代码 handler 集合 ⊆ 已启用 seed（或明确标注「默认关闭」的 handler）
  - [ ] 本地 XXL Admin 可见执行器在线且任务可触发

#### 1.4 阶段 1 门禁

```bash
# 优先补并跑：
# - MarketServiceApplication / MessageJobServiceApplication 的 @SpringBootTest
# - Mapper SqlSessionFactory 加载测试（至少 message-job）
./scripts/validate-microservices-stack.sh
./scripts/smoke-test-microservices.sh
```

- [ ] market / message-job / gateway health 通过
- [ ] 增强或新增校验：Context 可启动、Mapper 无重复、XXL handler 对齐（现有 `runtime-safety` 假绿，不可单独作为门禁）

**阶段 1 完成标志：** 目标档 A。

---

### 阶段 2 — 核心业务闭环与一致性（目标档 B 的后端部分）

**目标：** 抽奖 / 库存 / 发奖 / 返利 / 兑换 / Chat 计费在默认栈下语义正确。

#### 2.1 发奖类型与完成态（BM-004）

- **改动点**
  - 确保 `DispatchCreditAwardTaskJob` 被调度且能把 `credit_award_task` 推到入账完成。
  - 明确默认演示支持的奖品类型；`openai_model` / `openai_use_count`：补实现，或从初始化数据移除/标记不可用。
  - 积分奖完成态：避免「记录已 completed 但积分未到账」；区分 `dispatching` / `completed`（或等价状态机）。
- **验收**
  - [ ] 演示奖品中奖后，积分/履约终态可在 DB 核对
  - [ ] 不支持的奖品类型有明确失败与可观测日志，不静默假完成

#### 2.2 策略接管仍过库存（BM-005）

- **改动点**
  - `AbstractRaffleStrategy.performRaffle`：chain 只决定候选奖；非无限奖必须进入库存确认（规则树或统一 reservation）。
  - 显式标注「无需库存」的虚拟/兜底奖。
- **验收**
  - [ ] 单测：权重/黑名单 takeover 仍执行库存 reservation
  - [ ] 有限库存奖在 Redis 耗尽后不可继续发出

#### 2.3 SKU 返利/兑换幂等前置（BM-006）

- **改动点**
  - 先建业务幂等/处理中记录，再 DECR / 预占；库存 reservation 绑定 `bizId`。
  - `RebateMessageApplicationService`：INDEX_DUP 时不得留下额外库存副作用。
- **验收**
  - [ ] 同一 `send_rebate` 重投：额度不重复、SKU Redis 库存不额外减少

#### 2.4 远程写补偿与分片路由（BM-007、BM-008 部分、BM-010 路由）

- **改动点**
  - `AccountRemoteCreditWriteAdapter` / reconcile：区分「明确失败」与「未知终态」；支付类失败禁止盲目后扣。
  - `PendingRemoteWriteSupport.enqueue`、Chat session、stock confirm 扫描：按 `userId`/`dbIdx` 设路由，避免写 db00 而表只在 01/02。
  - `StrategyAwardStockConfirmJob`：port 内部 `clear()` 不得破坏后续同批查询的路由（改为显式 `executeOnShard` 模板更佳）。
- **验收**
  - [ ] pending / chat_session / confirm_task 写入正确分片
  - [ ] account 超时场景有终态查询或人工可对账路径，不会「前端失败 + 稍后又扣款发货」

#### 2.5 库存落库可靠性（BM-008）

- **改动点**
  - `UpdateAwardStockJob` / `UpdateActivitySkuStockJob`：DB 成功后再 ACK/删除队列项；失败 requeue 或保留。
  - 下架流程：先 drain/冻结队列，再关活动；或扫描条件覆盖「仍有队列残留」的活动。
- **验收**
  - [ ] 模拟 DB 失败后事件不丢，重试可收敛
  - [ ] 重新 armory 不会从陈旧 DB 放大可售库存（或有对账告警）

#### 2.6 remote quota 模式（BM-009）

- **改动点**
  - 积分余额走正式 account read RPC，禁止恒返回 0。
  - decrement 返回 `SUCCESS / REJECTED / UNKNOWN`；UNKNOWN 按业务号查终态。
- **验收**
  - [ ] 开启 remote-quota 后兑换路径可用且超时可对账
  - [ ] 配置矩阵测试：embedded vs remote 行为表

#### 2.7 Chatbot 计费状态机（BM-010）

- **改动点**
  - 同 `requestId`：先占位 `processing`，缓存最终答/失败；重放返回同一结果，不重复调 AI。
  - session 与 userId 同分片；`recordDeduction` 不得错误重置 refund 状态。
- **验收**
  - [ ] 同 requestId 重放：只一次 AI 调用（或第二次直接返回缓存）
  - [ ] AI 失败可走退款 reconcile

#### 2.8 阶段 2 门禁

```bash
./scripts/smoke-api.sh   # 需逐步改为断言业务码，而非只打印
# 建议新增 Testcontainers 或本地脚本核对：
# 签到首次/重投、抽奖+发奖、兑换、库存 DB 失败重试
```

- [ ] 后端主路径 DB/Redis/MQ 终态可核对
- [ ] 至少补齐报告建议的最小测试集中与本阶段相关的 4–6 项

**阶段 2 完成标志：** 后端达到目标档 B（不含前端/配置体验打磨）。

---

### 阶段 3 — 演示体验、配置与安全默认（目标档 B 收尾 + C 起步）

#### 3.1 前端活动与退出（BM-012、BM-014）

- **改动点**
  - `big-market-web/app.js`：stage 返回什么就用什么；演示数据不足改初始化 SQL，不改写业务事实。
  - logout：先 `POST /auth/logout`，再 `clearAuth()`；admin 同理。
- **验收**
  - [ ] stage=100401 时所有业务请求带 100401
  - [ ] 退出后旧 JWT 调写接口失败

#### 3.2 Admin / DCC（BM-011）

- **改动点**
  - 默认演示栈打开 DCC 或文档明确「默认关闭及开启步骤」。
  - 修复 `PlatformConfigService` 点号 namespace 解析（如 `activity.100301.title.value`）。
  - delete 发 tombstone；publish 失败不得报成功；refresh 语义明确。
- **验收**
  - [ ] 保存 → 订阅方生效 → 重启后可恢复（或明确仅 Nacos 源）
  - [ ] 删除后旧值不再残留

#### 3.3 上下架原子性（BM-013）

- **改动点**
  - `ErpOperateApplicationService`：同库事务 + CAS 前置状态；预热失败不发布 active。
- **验收**
  - [ ] 不会出现 stage active 而 activity close（或相反）的中间态残留

#### 3.4 安全默认值（BM-015）— 作品集演示可「可开关」，准生产必须默认安全

- **改动点（按优先级）**
  1. 内部 RPC `enforce` 默认 true（或 compose 生产 profile true）
  2. 网关写接口 / 登录限流默认开启（学习 profile 可关）
  3. Redis 不无密码暴露到宿主；文档警告
  4. 移除或强制覆盖默认 `admin/admin`、可预测 JWT secret（缺失则启动失败）
- **验收**
  - [ ] 「学习 profile」与「安全 profile」配置矩阵写清
  - [ ] 安全 profile 下匿名 Dubbo 写调用失败

#### 3.5 阶段 3 门禁

```bash
./scripts/web-start.sh
# Playwright：stage 活动 ID、logout 吊销
npm test
./scripts/smoke-test-microservices.sh
```

- [ ] 前端主路径可演示，且与后端 stage 一致
- [ ] README「可演示闭环」措辞与真实能力一致后再恢复宣传

**阶段 3 完成标志：** 目标档 B 完整达成。

---

### 阶段 4 — 可观测性与架构债（目标档 C 深化）

可与阶段 3 并行，但不阻塞演示。

| 项 | 内容 | 对应 |
| --- | --- | --- |
| 监控 | 全服务暴露并抓取 prometheus；补 pending/DLQ Gauge + 最小 Grafana/告警 | BM-016（最小可用） |
| 门禁 | 修 `validate-microservices-runtime-safety.sh` 假绿；加 Context/Mapper/XXL 对齐检查 | 报告门禁批评 |
| 边界 | ArchUnit 规则 + Mapper statement-id 漂移门禁；物理单一来源延后 | BM-017（门禁子集） |
| 路由模板 | `executeOnShard(userId, callback)` 收敛 ThreadLocal 手工路由 | BM-007/008/010 |
| 拆分 | market 继续拆服务、真拆库 — **明确延后** | 架构评估 |

---

## 3. 建议迭代顺序（1–2 周）

| 天 | 内容 | 产出 |
| ---: | --- | --- |
| 1–2 | 阶段 1 全部 | 两服务可启动 + XXL 对齐 + Context/Mapper 测试 |
| 3–4 | BM-004、BM-003 余量、库存 confirm/DLQ Job 真跑 | 发奖与补偿可触发 |
| 5 | 端到端：登录/签到/兑换/抽奖/发奖，核对 DB·Redis·MQ | 演示脚本或 checklist |
| 6–8 | BM-005/006/007/008/010 | 一致性 P1 收敛 |
| 9–10 | BM-012/014/011/013 + 安全默认（BM-015 子集） | 作品集可重复演示 |
| 11–14 | BM-009 配置矩阵、监控、门禁增强、文档回写 | 准生产基线起步 |

若时间只够一周：**只做阶段 1 + BM-004 + BM-012 + 一条端到端核对**，即可显著改善「审批不通过」的核心原因。

---

## 4. 测试与门禁补齐清单

报告建议的最小集，按阶段挂靠：

| # | 测试 | 阶段 |
| ---: | --- | ---: |
| 1 | market / message-job `@SpringBootTest` | 1 |
| 2 | 全 Mapper `SqlSessionFactory` 加载 | 1 |
| 3 | `@XxlJob` ↔ `xxl_job_info` 对比 | 1 |
| 4 | 签到首次/重投、抽奖+发奖、兑换（容器或本地集成） | 2 |
| 5 | 权重/黑名单仍走库存 reservation | 2 |
| 6 | RPC 成功但客户端超时的终态查询 | 2 |
| 7 | Chat requestId 首次/重放/失败退款 | 2 |
| 8 | 前端 stage=100401 请求断言 | 3 |
| 9 | logout 后旧 token 失效 | 3 |

现有脚本使用注意：

- `validate-microservices-runtime-safety.sh` — 当前假绿，增强前不当唯一门禁
- `smoke-test-microservices.sh` — 基础冒烟；fallback 断言 HTTP 503 + body `0007`（2026-07-10 修）
- `smoke-api.sh` — 多处只打印响应，应逐步加业务码断言

---

## 5. 文档回写清单（阶段完成时做）

- [ ] `README.md`：删除或改写「completed local stack / 稳定可演示」等与事实不符的表述，直到阶段 3 验收通过
- [ ] `docs/MICROSERVICES.md` / `docs/operations-checklist.md`：XXL appname、必开 Job、DCC 默认开关
- [ ] `docs/data-and-outbox.md`：发奖完成态、库存确认、remote write 语义
- [ ] `docs/learning/02-business-flows-and-diagrams.md`：前端 activityId 真实行为
- [ ] `docs/microservices-dao-ownership.md`：若仍共享全量 infrastructure，弱化「resolved」措辞

---

## 6. 明确延后（不要在闭环修复中夹带）

- 将 market 拆成更多自治服务
- 按服务物理拆库
- 领域模型去 Spring 注解的深度纯化
- 多节点 HA / 灰度 / 真实 SLO
- 前端 localStorage 历史迁服务端账本
- 真实 DeepSeek/OpenAI 联调与 CVE/SBOM（单独安全迭代）

---

## 7. 单次 PR / 单次会话建议切分

后续修改请尽量按下列切片开 PR 或开 Agent 会话，便于审查与回滚：

1. **boot-scan-fixers** — BM-001 + BM-002 + Context/Mapper 测试  
2. **xxl-job-wiring** — BM-003  
3. **award-dispatch-types** — BM-004  
4. **strategy-stock-takeover** — BM-005  
5. **sku-rebate-idempotency** — BM-006  
6. **shard-routing-pending-stock-chat** — BM-007/008/010 路由与落库 ACK  
7. **remote-quota-saga** — BM-009  
8. **chat-requestid-fsm** — BM-010 状态机  
9. **web-stage-logout-dcc-erp** — BM-011/012/013/014  
10. **secure-defaults-observability** — BM-015/016 + 门禁脚本  

每片合并前：跑该片相关测试 + 阶段门禁中已具备的脚本。

---

## 8. 进度跟踪（勾选）

| 阶段 | 状态 | 完成日期 | 备注 |
| --- | --- | --- | --- |
| 1 启动阻塞 | 基本完成（代码+Context 测试） | 2026-07-10 | BM-001/002/003；market/message-job `@SpringBootTest`；CI `build-verify.yml` |
| 2 核心闭环 | 已验（acceptance 全绿） | 2026-07-11 | `acceptance.sh`：HTTP 契约、smoke 21/21、chat-refund E2E、Playwright 18/18×2；`apply-stack-migrations.sh`；admin.js stage=100401；`0008`→403 |
| 3 演示与安全 | 已完成（代码+验收） | 2026-07-11 | BM-011～015；Nacos 保存元数据；secure profile 可选 |
| 4 观测与架构债 | 门禁完成（本轮切片） | 2026-07-11 | BM-016 指标含 refund/stock confirm pending + `validate-prometheus-config.sh` CI；BM-017 ArchUnit+mapper 漂移；runtime-safety + HTTP 契约脚本 |

更新本表时同步更新「当前状态」一节的目标档结论。
