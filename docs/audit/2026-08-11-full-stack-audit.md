# 2026-08-11 全栈审计与业务闭环改造

一次针对简历/学习定位的多维度审计，以及据此完成的修复与扩展。目标：不依赖 Docker
运行时，`mvn clean verify` 全绿，前后端业务闭环在代码与测试层面完整、可讲解。

## 审计结论总览

| 维度 | 结论 | 处置 |
| --- | --- | --- |
| 构建/测试基线 | **失败**：market/message-job/account 的 Context 测试在无本地 Nacos 时必挂 | 已修复（P0） |
| 后端业务闭环 | 抽奖→发奖→积分链路完整，但**用户侧查询闭环缺失**：无中奖记录、无积分流水查询 API | 已补齐 |
| 前端数据真实性 | 用户中心「最近抽奖」「积分流水」是 localStorage 假账本，与服务端事实脱钩 | 已改为服务端数据 |
| 领域一致性 | `award_state` 库值 `completed` 与枚举 `complete` 编码不一致（历史遗留） | 读路径用字符串读模型规避，不动写路径 |
| 钱路径安全 | 幂等键、outbox、补偿链完整；本次仅新增只读路径，不触碰写语义 | 无需变更 |
| 文档 | 与代码一致性良好；新增能力需登记 | 已同步 |

## P0 缺陷：Context 测试依赖活体 Nacos

`mvn clean verify` 在 market-service 失败：

    Failed to create nacos config service client. Reason: server status check failed.
    at org.apache.dubbo.config.deploy.DefaultApplicationDeployer.startConfigCenter

根因：Dubbo 默认把注册中心（`nacos://127.0.0.1:8848`）兼作 config-center /
metadata-center（`use-as-config-center` 默认 true），Context 测试因此要求本地有活体
Nacos。此前验证通过是因为 Docker 环境栈恰好在跑，属于**隐藏的环境耦合**。

修复：三个 Dubbo 服务的 `src/test/resources/application-test.yml` 增加

    dubbo.registry.use-as-config-center: false
    dubbo.registry.use-as-metadata-center: false

现在离线环境（无 MySQL/Redis/Nacos/RabbitMQ 容器）全量 `mvn clean verify` 通过。

## 业务闭环扩展：用户侧查询 API

此前前端用户中心的「最近抽奖」和「积分流水」完全由浏览器 localStorage 伪造：
随机积分到账金额靠轮询余额差值猜测，刷新浏览器换设备即丢失，也无法体现
`user_award_record` / `credit_award_task` / `user_credit_order` 的真实异步闭环。

### 新增 API（market-service，JWT 鉴权）

| 端点 | 数据源 | 说明 |
| --- | --- | --- |
| `POST /api/v1/raffle/activity/query_user_award_record_by_token` | `user_award_record`（market 本地 award 领域） | 中奖时间倒序，`awardState` 透传 create/completed/fail |
| `POST /api/v1/raffle/activity/query_user_credit_order_by_token` | `user_credit_order`（account 领域） | 交易时间倒序；本地 Profile 进程内读，Docker Profile 走 account RPC |

### 分层落点

- **读模型**：`UserAwardRecordLogEntity` / `CreditOrderLogEntity`（与写模型分离；
  `award_state` 与 `trade_name` 库值均为展示字符串，读路径不做枚举反解，避免
  `completed` vs `complete` 历史不一致抛异常）。
- **DAO**：`IUserAwardRecordDao.queryUserAwardRecordListByUserId`、
  `IUserCreditOrderDao.queryUserCreditOrderListByUserId`；Mapper XML 三份启动器副本
  （market / message-job / account）同步修改，`validate-mapper-ddl-gates.sh` 147 条
  statement 对比通过。分表路由经 `DBRouterTemplate` / `doRouter` 显式执行
  （`@DBRouter` 注解无 AOP 切面，仅为文档标记）。
- **领域**：`IAwardService.queryUserAwardRecords`、
  `ICreditAdjustService.queryUserCreditOrders` + 仓储接口/实现。
- **RPC 契约**：`IAccountCreditService.queryUserCreditOrders(userId, limit)`，
  account Provider + market `IAccountReadAdapter` 三个实现
  （Local / Remote / MessageJobLocal）全部对齐。
- **HTTP/Dubbo 入口**：`RaffleActivityController` 与 `RaffleActivityServiceRPC`
  （RPC 侧 userId 直连入口按既有约定 `rejectInternalRpc`）。

### 前端（big-market-web）

- `app.js`：删除 localStorage 假账本（`DRAW_HISTORY_KEY` / `CREDIT_LEDGER_KEY` /
  `pushDrawHistory` / `pushCreditLedger` 等），`renderHistories()` 改为调用上述两个
  API；抽奖/签到/兑换/AI 扣费后，若用户中心打开则自动刷新。
- 中奖记录展示发奖状态徽标（发放中/已到账/发放失败），直接可视化
  「中奖落库 → MQ 发奖 → outbox 派发 → 账户入账」的最终一致性过程。
- 积分流水按 `tradeType` 显示 +/-（forward 绿 / reverse 红），金额取绝对值。
- 聊天历史仍留在 localStorage（会话内容非服务端事实，属可接受的演示取舍）。

## 新增测试

| 测试 | 覆盖 |
| --- | --- |
| `RaffleActivityQueryHistoryTest`（market） | 读模型→DTO 映射、空 userId 校验、读适配器委托 |
| `AccountCreditQueryOrdersTest`（account） | RPC DTO 映射、userId/limit 参数校验 |
| `MarketDaoMapperStatementTest` 扩展 | 新 DAO 方法必须有对应 Mapper statement |

既有门禁继续生效：`MessageJobMapperXmlLoadTest`（XML 解析）、account Context 测试
（非 lazy，启动即解析全部 Mapper）、`validate-mapper-ddl-gates.sh`、
`validate-module-boundaries.sh`。

## 验证证据（本工作树，离线）

    mvn -B clean verify -DfailIfNoTests=false   # BUILD SUCCESS，19 模块，0 失败
    ./scripts/validate-mapper-ddl-gates.sh      # 23 passed, 0 failed
    ./scripts/validate-module-boundaries.sh     # OK
    node --check big-market-web/*.js            # 全部通过

**边界声明**：以上为编译 + 单测 + Context + 静态门禁证据；新端点的运行时业务闭环
（真实 DB 数据、网关路由、Playwright）仍需按 `docs/LEARNING-FREEZE.md` 流程跑
`./scripts/acceptance.sh --reuse` 后才可声称 runtime-verified。

## 第二轮优化（2026-08-12）

1. **复合索引**：`user_award_record_00X` 的 `idx_user_id` → `idx_user_id_award_time
   (user_id, award_time)`；`user_credit_order_00X` 的 `idx_user_id` →
   `idx_user_id_create_time (user_id, create_time)`。fresh DDL（`big_market_01/02.sql`
   共 16 张分表）与老卷迁移（`z-history-query-indexes.sql`，幂等，含 schema_migration
   台账，接入 `apply-stack-migrations.sh`）同步修改，历史查询避免 filesort。
2. **验收覆盖**：`smoke-api.sh` 新增两个查询端点的业务码断言；Playwright
   `business-flows.spec.js` 新增用户中心服务端历史用例（断言 API `0000` +
   面板非错误态渲染），在下次 `acceptance.sh --reuse` 运行时生效。
3. **学习文档补齐**：01（端点表）、02（发奖机制的用户可见性）、03（前端要点）、
   09（代码地图：读模型/读适配器）、15（数据模型：索引与读模型）、19（面试 Q7：
   查询闭环）与 MICROSERVICES / dao-ownership / LEARNING-FREEZE 全部对齐本轮改动。

## 遗留事项（有意不做）

- 聊天历史服务端化：chatbot 侧仅有计费会话表，无消息内容表；扩展属新业务域。
- `award_state` 枚举编码统一（`complete` → `completed`）：牵动写路径与存量数据，
  违反冻结约束「不改钱路径幂等/状态机」，保持读侧兼容即可。
- Mapper XML 物理单源化：BM-017 子集，冻结文档已登记为 deferred。
