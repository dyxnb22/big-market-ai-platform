# 2026-07-11 学习冻结独立审计报告

## 1. 审计结论

结论：**有条件冻结**。

审计没有沿用历史 BM/phase 的完成结论，而是以目标工作树的代码、配置、Docker 初始化 SQL、运行日志和实际数据库结果重新取证。默认本地拓扑在复用卷上已形成可学习的闭环；审计期间发现的默认发奖断链、演示奖品不确定、验收漏测和 XXL 注册假健康均已做最小修复并重跑通过。由于 fresh 空卷和完整 secure overlay 未执行，结论不能提升为无条件“可冻结学习”，更不能称为生产就绪。

## 2. 范围与方法

覆盖根 Maven reactor、10 个服务启动模块、共享 domain/infrastructure/api/types/starters、静态前端、101 份 Mapper XML、Docker/SQL、RabbitMQ/XXL、CI、测试、脚本和主要学习文档。证据优先级为：运行结果与数据库状态 > 代码/配置/初始化 SQL > 当前权威文档 > 历史修复记录。

采用三层验证：

1. 静态：模块/依赖/组件扫描、Mapper statement、DDL、compose、Prometheus、凭据与配置开关。
2. 启动：Maven verify、基础设施与 8080–8087 健康、Nacos/Dubbo/XXL 执行器注册。
3. 业务：登录鉴权、真实积分兑换和抽奖、MQ/outbox/XXL 到账户入账、Chatbot 退款补偿、浏览器交互与安全负向请求。

## 3. 系统事实

- 默认入口是 web `:5173` 和 gateway `:8080`；应用服务为 auth/admin/market/chatbot/message-job/account/fulfillment `:8081–8087`。
- rebate `:8088`、strategy `:8089` 默认不在 compose；market 内嵌 provider。
- 默认 `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true`，且共享 task 积分奖派发关闭，因此 `credit_award_task` 的 XXL dispatcher 必须运行。
- 默认 `ACCOUNT_FULFILLMENT_REMOTE_AWARD_ENABLED=false`。默认抽奖积分奖由 message-job 的本地奖品领域与 account RPC 闭环，不应描述成必经 fulfillment RPC。
- 服务边界是逻辑边界：共享 domain/infrastructure、共享物理 MySQL 和启动器内 Mapper 副本仍存在。

## 4. 分级发现

### P0

未发现修复后仍阻断默认栈启动或导致必然数据破坏的 P0。此判断只覆盖已验证的默认复用环境。

### P1-01 默认积分奖 outbox 无调度器

- 严重度：P1，已修复。
- 证据：compose 开启 `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true`，同时关闭共享 task 积分派发；SQL 中 XXL job 5/6 原为 stopped。修复前实际抽奖后 `user_award_record=completed`、`send_award task=completed`，但 `credit_award_task=pending` 且账户无流水。
- 影响：页面和中奖记录看似完成，积分实际不到账，形成假闭环。
- 修复：fresh SQL 和迁移 SQL 均启用 job 5/6；运行时安全门禁检查 compose 开关与 seed；验收等待执行器注册并核对 outbox 与账户流水。
- 建议：保留 `award_state` 的“持久化接管”语义说明，后续若产品需要最终态，应另行设计状态机而非在冻结阶段重构。

### P1-02 演示策略可能抽到本地不支持奖品

- 严重度：P1，已修复。
- 证据：活动 100401 使用策略 10007；原数据包含 `openai_use_count` 和 `openai_model`，仓库没有对应 quota 调整 HTTP 闭环，`AwardService` 对 `openai_model` 明确报错。
- 影响：同一演示步骤随机成功或失败，不能稳定学习主路径。
- 修复：冻结演示数据将策略 10007 固定为本地可兑现的奖品 101（5 积分）；fresh 与旧卷迁移一致，并失效相关派生 Redis 策略缓存。
- 建议：外部模型/次数奖品保留为扩展练习，不纳入冻结验收。

### P1-03 旧验收未验证业务闭环

- 严重度：P1，已修复。
- 证据：原 `smoke-api.sh` 只做预热、账户查询和 Chatbot；前端测试只验证抽奖按钮状态，没有核对中奖、outbox 和积分流水。
- 影响：服务健康与 UI 绿色不能发现钱路径断链。
- 修复：新增 `smoke-raffle-award-e2e.sh`，使用唯一 requestId 兑换、抽奖、定位分片记录、触发 dispatcher，并核对扣 5/奖 101/outbox dispatched/账户入账 5。
- 建议：钱路径变更必须保留该门禁及业务幂等键。

### P1-04 XXL Admin 健康不等于执行器可调度

- 严重度：P1，已修复。
- 证据：全量重建时 XXL Admin 曾 OOM；重启后 message-job 缓存过失败注册，Admin HTTP 健康但 group `address_list` 为空，Chat refund reconcile 不能触发。
- 影响：定时补偿静默停摆。
- 修复：验收和补偿 smoke 现在等待 `big-market-message-job` 的非空注册地址。
- 建议：学习排障时同时检查 Admin、执行器注册、handler seed 和业务表状态。

### P1-05 本地资源下限与前端依赖文档失真

- 严重度：P1（可复现性），文档/脚本已修复。
- 证据：Docker 可用 7.807 GiB 时全栈重建导致 `xxl-job-admin` exit 137；Playwright 首轮曾因浏览器 executable 未安装而 17 条瞬时失败；原文档只建议 8 GB 且未列 Node/browser 前置。
- 影响：学习者按文档操作仍无法完成验收。
- 修复：建议至少 12 GB；补 Node/npm 与 `playwright install chromium`；仓库已有本地 browser cache 时验收自动使用；零测试计数适配当前 Playwright 输出。
- 建议：在 12 GB+ 的干净机器执行一次 fresh 验收以关闭条件。

### P2 已接受债务

| 发现 | 证据与影响 | 建议 |
| --- | --- | --- |
| 物理数据与代码边界弱 | 默认共享 MySQL；101 份 Mapper XML，常用 mapper 有 4–8 份副本；漂移门禁只覆盖部分 statement | 冻结期不拆库；新改动同步副本并扩门禁，长期生成单一来源 |
| 测试强度与隔离不均 | market/message-job/domain/infrastructure 覆盖较强；account/fulfillment/rebate/strategy 多为扫描/切片。部分 Context 测试会发现并访问正在运行的 Nacos/Dubbo 实例，重跑时出现 metadata 与测试库缺表告警，虽最终通过但隔离性不足 | 按事故风险补失败注入与独立服务 Context；测试 profile 隔离外部注册中心/数据库，不追求机械覆盖率 |
| 完成态语义偏早 | 积分奖写入 outbox 后中奖记录即可 `completed` | 文档规定 task/账户流水为最终核验；若改状态机需迁移与兼容方案 |
| 可选部署未动态验证 | 8088/8089 独立模式、remote fulfillment、外部 OpenAI 奖品不在默认验收 | 作为扩展实验单列，不混入默认学习完成标准 |
| 安全仅为本地基线 | 默认账号、宽松 RPC/限流关闭；JWT 在 localStorage；只有默认栈负向 smoke | 隔离本地使用；公开部署前必须 secure fresh、CSP/XSS 评审与密钥管理 |
| 依赖年代与供应链 | Java 8 / Boot 2.7.12 等为旧基线；本轮未做全量 CVE/SBOM；`openai-code-review.yml` 下载执行未固定校验和的第三方 JAR | CI 下载物固定版本+SHA/签名；单列依赖升级，不在冻结期大迁移 |
| 启动慢且资源重 | market 启动约 97 秒，完整应用健康约 127–132 秒；基础设施包含 ES/Canal/Grafana 等 | 文档给足超时/内存；日常学习按主题裁剪，最终验收再开全栈 |
| CI 动态验收非默认 | 标准 verify 以 Maven/静态门禁为主；Docker acceptance 需显式触发，且 CI 配置可能跳前端 | 合并关键钱路径前启用完整 acceptance 或保存人工证据 |

### P3 后续优化

- Mapper/DDL 单一来源生成、按服务物理拆库、Java 17/Boot 3、完整 SBOM/CVE、容量/HA/灾备、真实灰度与告警值班均超出学习冻结范围。
- 可将验收产物结构化为 JUnit/JSON，并给业务表轮询增加统一诊断输出。
- 可补独立 rebate/strategy 与 secure overlay 的可选 CI matrix，但不应拖累默认学习路径。

## 5. 本次变更

- 对齐 fresh/旧卷演示数据：策略 10007 固定发 5 积分奖 101。
- 启用积分奖 outbox dispatcher 的 XXL seeds，并在迁移脚本验证。
- 新增真实抽奖发奖 E2E；验收加入 XXL 执行器注册、主路径与两轮前端测试门禁。
- 强化 runtime safety 对 compose 有效配置和 job seed 的联动检查。
- 修正 Playwright 列表计数和本地浏览器 cache 发现。
- 回写架构、启动、运维、outbox、学习路径、README、AGENTS 与冻结文档。

未改变 Java 业务实现和任何现有幂等键；未提交 commit。

## 6. 动态验证证据

环境：Amazon Corretto 1.8.0_462、Maven 3.9.9、Docker 28.5.2、Compose 2.40.3、macOS arm64；Docker 内存约 7.8 GiB。

| 命令/门禁 | 结果 |
| --- | --- |
| `mvn -B verify -DfailIfNoTests=false` | PASS，23 个 reactor 模块；末轮 80 秒。日志含外部 Nacos/Dubbo metadata 与测试库缺表告警，未造成测试失败，已列为隔离债务 |
| `validate-mapper-ddl-gates.sh` | PASS，18/18 |
| `validate-microservices-runtime-safety.sh` | PASS，100/100（修复后） |
| `validate-prometheus-config.sh` | PASS，promtool config/rules |
| 两份 `docker compose ... config -q` | PASS |
| 全量应用 `docker compose up --build -d` | 8080–8087 最终健康；曾发生 XXL OOM，已记录为资源风险 |
| `acceptance.sh --reuse --skip-build` | PASS，80 秒 |
| `smoke-raffle-award-e2e.sh` | PASS，扣 5、奖 101、outbox dispatched、账户加 5 |
| `smoke-chat-refund-e2e.sh` | PASS，立即退款和 pending reconcile |
| Playwright | PASS，18/18，连续两轮 |
| `smoke-security.sh`（默认栈） | PASS，4 项负向边界 |

本轮未执行：`--fresh --confirm-destroy-volumes`（会删除现有卷）、完整 `--secure` overlay、8088/8089 独立模式、真实外部模型奖品、生产容量/HA/灰度。以上不得从复用验收推断为通过。

## 7. 评分

| 维度 | 5 分制 | 说明 |
| --- | ---: | --- |
| 可启动与可复现 | 4 | 复用栈全绿；fresh 未跑且 8 GB OOM |
| 核心业务闭环 | 4 | 默认真实抽奖/入账与 Chat 补偿已证实；扩展奖品未证实 |
| 架构与边界 | 3 | 责任可解释，但共享库/DB/Mapper 复制明显 |
| 文档一致性 | 4 | 权威入口已回写；历史材料仍需按历史理解 |
| 测试与门禁 | 4 | Maven、业务 E2E、浏览器双跑；服务覆盖不均 |
| 运维与可观测 | 3 | 健康、指标、日志、XXL 注册有门禁；无生产演练 |
| 安全 | 3 | 默认负向边界与 secure 配置存在；secure fresh/CVE 未证实 |
| 学习价值 | 5 | 同步、异步、幂等、补偿、分片和可观测路径完整可讲 |

综合：**30/40，有条件冻结**。

解除条件：在至少 12 GB Docker 内存、可销毁数据卷的隔离环境中执行一次完整 fresh acceptance；如对外展示 secure 能力，再单独执行 secure acceptance 并保存证据。生产就绪不属于解除条件，因为本仓库未以生产发布为目标。

## 8. 一周学习计划

| 日 | 主题 | 产出 |
| --- | --- | --- |
| 1 | 启动与服务拓扑 | 画默认 8080–8087、基础设施、embedded/optional 边界图 |
| 2 | 登录与网关 | 跟一次 login/verify/logout，解释 Redis 吊销与 gateway fallback |
| 3 | 抽奖领域 | 从 Controller 跟到配额、策略、库存与中奖记录，画同步时序 |
| 4 | 发奖异步链 | 跟 `send_award`、task、credit outbox、XXL、account，记录幂等键 |
| 5 | 失败与补偿 | 跑 Chat refund、阅读 pending remote write/DLQ/stock confirm |
| 6 | 前端与运维 | 跑 Playwright、看 traceId/Prometheus/XXL 注册，制造一个可恢复故障 |
| 7 | 总复盘 | 从零口述架构和一条钱路径，运行 acceptance，列出“已证实/未证实” |
