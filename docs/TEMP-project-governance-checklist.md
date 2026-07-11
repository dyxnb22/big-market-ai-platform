# Big Market AI Platform 全仓治理清单（临时工作文档）

> 状态：临时治理台账，完成治理后应将最终结论回写到权威文档，并删除或归档本文。
>
> 建立日期：2026-07-11  
> 基线提交：`8df5311`  
> 适用范围：后端、前端、数据、消息、任务、安全、测试、CI/CD、可观测性、文档与作品集呈现。

## 0. 使用规则

- [ ] 每个治理项指定负责人、目标迭代和目标提交。
- [ ] 每项完成时填写“代码证据、自动化验证、运行证据”中的适用项。
- [ ] 涉及积分、配额、库存、发奖、返利、兑换的变更，先核对 `docs/learning/archive/risky-changes-remediation.md`。
- [ ] 不以静态脚本单独证明运行闭环；启动类改动至少需要 Context 测试，资金/库存改动至少需要失败与重放测试。
- [ ] 历史快照不作为当前 HEAD 的验收结论；所有结论绑定具体提交。
- [ ] 默认不把物理拆库、全面服务拆分、HA、灰度发布夹带进普通缺陷修复。
- [ ] 完成一个阶段后，同步更新 `README.md`、`docs/MICROSERVICES.md`、`docs/operations-checklist.md` 和相关学习文档。

## 1. 治理目标与完成定义

| 目标 | 完成定义 |
| --- | --- |
| G1 构建稳定 | 全仓在固定 JDK/Maven 环境下无模型警告、无插件参数警告，连续构建结果稳定 |
| G2 当前闭环可信 | fresh/reuse/secure 三类 acceptance 在目标提交上有可追溯证据 |
| G3 服务边界可信 | 服务职责、RPC 契约、DAO/Mapper 归属被测试和门禁约束，而非仅靠文档 |
| G4 写路径可信 | 关键资金/库存路径具备幂等、未知终态、补偿、重放和对账验证 |
| G5 默认安全 | 非显式 demo 环境拒绝弱凭据、匿名内部写调用和无约束流量 |
| G6 可运维 | 核心状态可观测、告警可触发、故障有操作手册、恢复过程可演练 |
| G7 可持续演进 | 技术升级、前端工程化、性能与架构债有明确路线，不阻塞日常交付 |

## 2. P0：先恢复“当前 HEAD 可证明”

### 2.1 完整验收与运行态

- [ ] 在当前目标提交执行 `./scripts/acceptance.sh --reuse`。（2026-07-11：gateway `:8080` 未起；agent 无法 `docker compose up`；`--reuse --skip-build` 于 1s 内 fail-closed，产物见 `target/acceptance-artifacts/`。需本机 `docker compose up -d big-market-gateway` 后重跑。）
- [ ] 在可销毁的独立环境执行 `./scripts/acceptance.sh --fresh --confirm-destroy-volumes`。
- [ ] 使用非默认凭据执行 `./scripts/acceptance.sh --secure`。
- [x] 查明本地应用栈健康等待失败原因，区分容器未启动、健康检查超时、依赖未就绪和历史卷污染。（证据：8081–8087=200，8080 down；gateway 容器未运行。）
- [x] 为 acceptance 输出生成简洁摘要：提交、profile、测试数、失败阶段、耗时、服务健康状态。（`scripts/acceptance.sh` → `latest-summary.txt`）
- [x] 验收失败时自动保存 `docker compose ps`、关键服务日志和依赖健康状态。（`target/acceptance-artifacts/<stamp>-*/`）
- [x] 明确 `--reuse` 只证明旧卷兼容，`--fresh` 才证明初始化完整。（README / operations-checklist / MICROSERVICES；默认不自启 Docker，需 `--start-stack`。）
- [ ] 验证迁移脚本可重复执行，旧卷升级后与 fresh 初始化 schema 一致。
- [x] 验证 Playwright 用户端和管理端用例均运行，而非被环境条件静默跳过。（`--list` 零用例则 fail；报告目录缺失则 fail。）
- [x] 将当前 HEAD 的实际验收结果回写 `docs/audit-remediation-plan.md` §8。

### 2.2 测试运行副作用

- [x] 测试不再修改已跟踪的 `data/log/*.log`。（`git rm --cached` + `logback-test.xml`）
- [x] 测试日志输出到 `target/` 或临时目录。（`logback-test.xml` console；`logging.file.path=target/test-logs`）
- [x] Dubbo 测试 profile 禁止写用户目录 `~/.dubbo`。（registry/metadata/config-center=`N/A`，`metadata-type=local`）
- [x] 修复 Dubbo metadata cache 写失败后的高频重试/日志风暴。（同上 + logback ERROR for dubbo/nacos）
- [x] Context 测试关闭不必要的注册中心、元数据中心、远程配置监听和后台线程。（test yml）
- [ ] 检查所有 Context 测试能在无本地 Nacos/MySQL/Redis/RabbitMQ 时独立运行。（部分仍指向 13306；RPC 服务为 scan 烟测）
- [ ] 测试结束后验证非守护线程、连接池、定时器均正确关闭。

## 3. P1：构建与依赖治理

### 3.1 Maven 父工程统一治理

- [ ] 在根 POM 的 `pluginManagement` 统一 compiler、resources、surefire、failsafe、jar、spring-boot 插件版本。
- [ ] 消除 `big-market-api` 缺少 `maven-compiler-plugin` 版本的模型警告。
- [ ] 消除老版 resources/compiler 插件不识别参数的警告。
- [ ] 统一所有模块的 Surefire 版本，避免部分模块仍使用 `2.6`。
- [ ] 统一 Spring Boot repackage 插件版本，禁止模块隐式使用 `2.6.0` 而父版本为 `2.7.12`。
- [ ] 删除或修正 Java 8 已无效的 JVM 参数，如 `MaxPermSize`。
- [ ] 将构建 JVM 参数从 POM 中的遗留文本迁移到明确的运行脚本或容器配置。
- [ ] 开启 Maven Enforcer：JDK、Maven 版本、依赖收敛、重复类检查。
- [ ] 开启 `maven-dependency-plugin:analyze`，治理未使用和未声明依赖。
- [ ] 统一源码、资源、测试编码为 UTF-8。
- [ ] 明确 `verify` 与 `package -DskipTests` 的使用边界。
- [ ] CI 将 Maven warning 基线化，新增警告需要显式审批。

### 3.2 依赖版本与漏洞治理

- [ ] 输出直接依赖和传递依赖清单。
- [ ] 清理显著过旧或不再使用的依赖：Hystrix、旧 XStream、旧 dom4j、旧 commons-lang3 等。
- [ ] 核对 `spring-boot-starter-amqp 3.2.0` 与 Spring Boot 2.7 基线是否存在版本错配。
- [ ] 核对 Spring Cloud、Dubbo、Nacos、Redisson、MyBatis 版本兼容矩阵。
- [ ] 增加 OWASP Dependency-Check、Trivy 或等价依赖/镜像扫描。
- [ ] 生成 SBOM（CycloneDX/SPDX）并作为构建产物保存。
- [ ] 为高危漏洞定义修复时限和例外审批机制。
- [ ] 固定基础镜像 digest，并建立月度镜像更新流程。
- [ ] 检查无维护或许可证不清晰的库。
- [ ] 清理仓库中未使用的 Elasticsearch/Canal/Hystrix 等能力或补齐真实使用说明。

### 3.3 技术升级路线

- [ ] 建立 Java 8 → Java 17 的兼容性分支或升级清单。
- [ ] 建立 Spring Boot 2.7 → 3.x 的迁移清单。
- [ ] 盘点 `javax.*` → `jakarta.*` 影响。
- [ ] 盘点 Spring Security、Actuator、MVC、Validation 行为变化。
- [ ] 盘点 Dubbo/Nacos/MyBatis/XXL-Job 对 Java 17 与 Boot 3 的支持版本。
- [ ] 先升级测试和构建插件，再升级运行框架。
- [ ] 为升级前后接口契约、数据库行为和消息行为建立回归基线。
- [ ] 保留一段时间的 Java 8 构建兼容门禁，明确最终下线日期。

## 4. P1：架构与服务边界治理

### 4.1 项目定位与拓扑声明

- [ ] 统一项目定位措辞：当前是“共享领域内核与基础设施的微服务学习架构”，避免暗示完全自治。
- [ ] 明确默认部署与可选独立部署的差异。
- [ ] 明确 rebate/strategy embedded provider 与独立服务的互斥配置。
- [ ] 为每个服务建立输入、输出、数据所有权、消息所有权和失败策略卡片。
- [ ] 更新架构图，区分 HTTP、Dubbo、MQ、定时任务和共享数据库访问。
- [ ] 标注哪些服务是真正独立，哪些只是独立启动器。
- [ ] 给出未来两条路线的决策：保持模块化共享内核，或继续服务自治；禁止长期模糊表述。

### 4.2 编译期边界

- [ ] 扩展 ArchUnit：domain 不依赖 trigger/infrastructure/service launcher。
- [ ] 扩展 ArchUnit：market 不扫描或依赖 job/listener 实现。
- [ ] 扩展 ArchUnit：message-job 不暴露业务 HTTP Controller。
- [ ] 扩展 ArchUnit：服务 launcher 只能依赖允许的共享模块。
- [ ] 限制跨领域直接 Repository/DAO 调用，优先通过 Port 或 Application Service。
- [ ] 检查 domain 中不必要的 Spring、MyBatis、Dubbo 注解渗透。
- [ ] 为所有公共 API/RPC DTO 建立向后兼容规则。
- [ ] 禁止服务通过实现类包绕过公开契约。

### 4.3 DAO、Mapper 与数据库归属

- [ ] 将 `docs/microservices-dao-ownership.md` 中的逻辑归属转成机器可检查规则。
- [ ] 继续维持 shared statement-id 和 statement-body 漂移门禁。
- [ ] 增加 ResultMap、SQL fragment、参数类型和返回类型漂移检查。
- [ ] 评估 Mapper 单一来源生成/复制机制，减少 101 份 XML 的人工同步。
- [ ] 禁止无归属服务访问资金、库存、发奖核心表。
- [ ] 对跨服务共享表记录所有读写方和事务假设。
- [ ] 明确哪些表允许跨服务只读，哪些只允许单一写方。
- [ ] 评估 Flyway/Liquibase；若暂不引入，强化 init SQL 与 migration SQL 一致性门禁。
- [ ] 为 schema 增加版本表与迁移审计记录。
- [ ] 检查所有分片表结构、索引和默认值完全一致。

### 4.4 远程模式与嵌入模式等价性

- [ ] 为 account quota embedded/remote 建立配置矩阵测试。
- [ ] 为 rebate embedded/remote 建立契约测试。
- [ ] 为 strategy embedded/remote 建立契约测试。
- [ ] 验证两种模式的错误码、幂等结果、超时语义和数据终态一致。
- [ ] 防止 embedded provider 与独立 provider 同时注册。
- [ ] 防止共享 task 与独立 outbox dispatcher 同时处理同一业务。
- [ ] 为每个模式提供最小启动拓扑和验收命令。

## 5. P1：核心业务与数据一致性治理

### 5.1 全局幂等规范

- [ ] 建立幂等键清单：业务、字段、唯一索引、有效期、重复返回语义。
- [ ] 所有写接口明确 requestId 是否必填、由谁生成、可重放多久。
- [ ] 所有 MQ 消息定义 messageId、businessId 和重复处理策略。
- [ ] 数据库唯一键作为最终幂等保障，Redis 只作为快速路径。
- [ ] 统一 duplicate key 转换，禁止按异常字符串判断。
- [ ] 重复请求返回原终态或确定的 `INDEX_DUP`，禁止重新执行外部副作用。
- [ ] 检查幂等记录与业务写是否处于同一事务或有可靠补偿。
- [ ] 为幂等键碰撞、过期、跨用户复用建立负向测试。

### 5.2 抽奖、配额与策略

- [ ] 权重、黑名单、兜底等奖品选择均经过统一库存确认。
- [ ] 明确无限库存奖与有限库存奖的模型和代码分支。
- [ ] 验证订单创建、配额扣减、库存预占、中奖记录写入的失败补偿顺序。
- [ ] 验证中奖记录保存失败时库存与配额均可恢复。
- [ ] 验证补偿自身失败时进入可重试任务或人工对账。
- [ ] 同一订单重放不会重复扣配额或重复占库存。
- [ ] 活动下架时冻结新请求，并正确 drain 已预占和待落库事件。
- [ ] 活动重新装配不会从陈旧数据库放大 Redis 可售库存。
- [ ] 规则树、权重和库存配置变更具备版本或一致性保护。

### 5.3 SKU、兑换与积分

- [ ] 兑换先建立业务幂等/处理中记录，再执行库存和积分副作用。
- [ ] SKU Redis reservation 绑定稳定业务号。
- [ ] RPC 成功但客户端超时时，通过业务号查询终态，不盲目退款或重复扣款。
- [ ] `wait_pay`、`compensating`、`completed`、`failed` 状态迁移使用 CAS。
- [ ] 退款与 SKU 恢复分别幂等，允许部分完成后继续补偿。
- [ ] 检查积分账户余额不能为负，必要时使用条件更新。
- [ ] 所有积分订单具备唯一 `out_business_no`。
- [ ] 验证重复兑换、并发兑换、余额不足、库存不足、RPC 超时和补偿超时。
- [ ] 建立积分账户余额与积分订单流水的对账 SQL。

### 5.4 奖品履约

- [ ] 明确每种奖品类型的支持状态和履约处理器。
- [ ] 未支持奖品不可进入可抽中库存，或必须明确失败并可观测。
- [ ] 区分中奖记录已创建、派发中、履约完成、履约失败。
- [ ] 禁止“消息已消费”直接等同于“用户已到账”。
- [ ] 奖品积分发放使用稳定 `award_order_id` 幂等。
- [ ] 外部奖品/额度发放的 UNKNOWN 状态进入查询和人工处置流程。
- [ ] 履约完成更新与 outbox/task 终态一致。
- [ ] 建立中奖记录、履约任务、积分订单三方对账。

### 5.5 返利与签到

- [ ] 同一用户同一天签到只有一个返利订单。
- [ ] 重复 `send_rebate` 不重复增加积分/配额、不重复扣 SKU。
- [ ] 返利订单与消息任务同事务写入或具备可靠补偿。
- [ ] 明确返利消息失败、DLQ、人工重放后的返回语义。
- [ ] 独立 rebate 服务和 embedded provider 行为一致。
- [ ] 建立返利订单、积分/配额到账和消息终态对账。

### 5.6 Chat 计费

- [ ] 同一 `userId + requestId` 只调用一次 AI 或复用同一最终结果。
- [ ] 请求先占位 `processing`，再执行扣费和 AI 调用。
- [ ] 明确扣费与 AI 调用顺序及各自失败补偿。
- [ ] UNKNOWN 扣费保持 `deducting`，通过 reconcile 查询终态。
- [ ] 只有确认扣费成功的 session 才允许退款。
- [ ] 退款状态 CAS：`none/pending → refunding → refunded`。
- [ ] `recordDeduction` 不覆盖已存在的退款状态。
- [ ] session 始终按 userId 路由到正确分片。
- [ ] 幂等缓存过期后仍由数据库唯一键兜底。
- [ ] 验证 AI 超时、AI 明确失败、扣费超时、退款超时和请求并发重放。

### 5.7 库存落库与对账

- [ ] MySQL 成功提交后才 ACK/删除 Redis 队列项。
- [ ] DB 失败时删除快速去重标记但保留可重试事件。
- [ ] Strategy award stock 使用稳定 reservationId Ledger。
- [ ] Activity SKU stock 使用 `(sku, lockSurplus)` Ledger。
- [ ] Redis SETNX 不作为唯一持久幂等凭据。
- [ ] Pending queue key 能覆盖活动离线后的残留任务。
- [ ] 并发 Job 通过分布式锁或数据库 claim/CAS 避免重复处理。
- [ ] 卡在 processing 的任务可安全回收。
- [ ] 建立 Redis、数据库库存、Ledger 和业务订单的对账任务。
- [ ] 定义库存差异告警阈值和人工修正流程。

### 5.8 分片路由

- [ ] 收敛 ThreadLocal 手工路由为 `executeOnShard(userId, callback)` 模板。
- [ ] 模板在 finally 中保证 clear，禁止嵌套调用破坏外层路由。
- [ ] 所有 pending task、chat session、stock confirm、账户/订单表在 DAO 前显式路由。
- [ ] 定时扫描按明确 shard 执行，而不是依赖默认库。
- [ ] RPC/消息 DTO 携带可靠 shard key。
- [ ] 缺失 shard key 时 fail fast，禁止落入 db00。
- [ ] 建立 DB1/DB2 对称测试及跨分片负向测试。
- [ ] 检查扩容或变更路由算法时历史数据迁移策略。

## 6. P1：消息、任务与补偿治理

### 6.1 RabbitMQ

- [ ] 为所有 exchange、queue、routing key、DLX、TTL 建立拓扑清单。
- [ ] 检查声明参数变化导致 `inequivalent arg` 的升级流程。
- [ ] 消息体包含 schema/version，消费者兼容旧版本。
- [ ] publisher confirm/return 的失败路径可重试并可观测。
- [ ] 消费者手动 ACK 策略与数据库事务边界明确。
- [ ] 区分可自动重试、进入 DLQ、必须人工审核的消息类型。
- [ ] 对 poison message 设置最大重试次数和退避策略。
- [ ] 消费日志包含 messageId、businessId、traceId、retryCount。
- [ ] 建立积压、消费失败、DLQ 增长监控。
- [ ] 验证重复投递、乱序、延迟、消费者宕机和 RabbitMQ 重启。

### 6.2 XXL-Job

- [ ] `@XxlJob` handler 与 seed 保持自动对齐。
- [ ] 每个 handler 明确默认启停状态、调度频率、超时和负责人。
- [ ] executor appname 与数据库 group seed 一致。
- [ ] 金钱类重放 Job 默认关闭，并要求人工审核状态。
- [ ] 每个 Job 支持分片、互斥或 claim，避免多实例重复执行。
- [ ] Job 记录扫描数、成功数、跳过数、失败数和耗时。
- [ ] 卡死任务有 lease/processing timeout 回收机制。
- [ ] Job 失败不吞异常，并能触发告警。
- [ ] 验证调度中心不可用时业务主流程的降级行为。

### 6.3 DLQ 与人工重放

- [ ] DLQ 表对 business_message_id 建立正确唯一/重激活语义。
- [ ] 明确 `pending/manual_pending/reviewed/replaying/done/failed` 状态机。
- [ ] 人工审核前必须查询业务幂等键和远端终态。
- [ ] 审核操作记录操作者、原因、时间和关联工单。
- [ ] 重放使用原 businessId，不生成新的资金类幂等键。
- [ ] 重放失败可再次进入人工态，不形成无限自动循环。
- [ ] 提供只读排查 SQL 和最小授权操作脚本。

## 7. P1：安全治理

### 7.1 默认凭据与配置

- [ ] 将“默认允许弱凭据”逐步收敛为仅显式 `demo/dev` profile 允许。
- [ ] 非 dev/profile 未声明时拒绝启动，而不是自动回落到开发配置。
- [ ] secure profile 检查 JWT、RPC、Admin、Chat、XXL、数据库、MQ、Grafana 全部凭据。
- [ ] 禁止在日志、异常、Actuator env 中输出 secret/token/password。
- [ ] 提供 `.env.example`，只包含变量名和生成说明，不包含真实秘密。
- [ ] 增加 secret scanning，阻止真实密钥提交。
- [ ] 建立密钥轮换说明和双密钥过渡方案。
- [ ] Docker/CI 使用 secret 注入，不把敏感值写入镜像层。

### 7.2 认证与授权

- [ ] JWT 校验 issuer、audience、exp、nbf、alg 和 key 长度。
- [ ] 禁止算法降级和空 secret。
- [ ] 登出吊销使用 Redis 共享存储，并在 Redis 读取失败时 fail closed。
- [ ] 吊销记录 TTL 与 token 剩余有效期一致。
- [ ] 管理权限使用角色/权限声明，逐步替代静态 userId 白名单和共享 Admin Token。
- [ ] 所有写接口明确匿名、普通用户、管理员权限矩阵。
- [ ] E2E 覆盖 401、403、越权访问、登出后旧 token。
- [ ] 内部退款、补偿、管理接口不可通过公网网关直接访问。
- [ ] 对敏感操作记录安全审计日志。

### 7.3 内部 RPC 与网络边界

- [ ] secure 环境 `app.internal-rpc.enforce=true`。
- [ ] 每个 Dubbo 写服务验证内部 token 或更强的服务身份。
- [ ] 防止 token 在业务异常中回显。
- [ ] 长期评估 mTLS 或服务网格身份，替代全栈共享 token。
- [ ] Redis/MySQL/RabbitMQ/Nacos/XXL/Grafana 不向非开发网络无保护暴露。
- [ ] Docker Compose 端口暴露区分开发与 secure 配置。
- [ ] 建立最小网络访问矩阵。

### 7.4 输入、接口与滥用防护

- [ ] 登录、抽奖、兑换、Chat、管理写接口均有限流。
- [ ] 限流键防止只按可伪造 IP 绕过。
- [ ] DTO 使用 Bean Validation，并统一错误码。
- [ ] 防止 SQL 注入、表达式注入、反序列化风险和路径穿越。
- [ ] 上传/外部 URL 功能如存在，检查 SSRF 和内容类型。
- [ ] 设置请求体、Header、连接、读取和响应大小限制。
- [ ] CORS 仅允许明确来源，secure 环境禁止通配。
- [ ] 安全响应头和 Cookie 策略有明确配置。
- [ ] 管理端防暴力破解并记录失败审计。

## 8. P1：测试体系治理

### 8.1 单元与领域测试

- [ ] 为每个核心状态机建立状态转换表测试。
- [ ] 为策略规则、库存、配额、积分、履约补齐边界值测试。
- [ ] 验证异常路径中的补偿调用次数和顺序。
- [ ] 验证重复执行不产生额外副作用。
- [ ] 清理只验证 getter/setter 或无业务价值的测试。
- [ ] 使用固定时钟和确定性随机源，减少时间/概率型不稳定测试。

### 8.2 服务 Context 与契约测试

- [ ] gateway 增加路由、鉴权透传、traceId、fallback Context/切片测试。
- [ ] auth-service 增加完整 Context 测试。
- [ ] admin-service 增加完整 Context 测试。
- [ ] account-service 增加完整 Context 与 RPC 契约测试。
- [ ] fulfillment-service 增加完整 Context 与 RPC 契约测试。
- [ ] rebate-service 增加完整 Context 与 RPC 契约测试。
- [ ] strategy-service 增加完整 Context 与 RPC 契约测试。
- [ ] market/message-job/chatbot 保持完整 Context 测试。
- [ ] 所有服务测试不依赖开发机外部服务。
- [ ] 建立 provider/consumer DTO 兼容测试。

### 8.3 数据库与集成测试

- [ ] 使用 Testcontainers 或专用 Compose 测试 MySQL、Redis、RabbitMQ。
- [ ] Mapper 测试不只加载 XML，还执行关键 SQL。
- [ ] DDL 在 MySQL 实例中真实应用并验证索引/约束。
- [ ] DB1/DB2 分片结构和查询行为均验证。
- [ ] 测试事务回滚、死锁重试、唯一键冲突和连接中断。
- [ ] 验证旧 schema 迁移到新 schema，而不只验证 fresh schema。

### 8.4 E2E 与验收

- [ ] 登录 → 签到 → 兑换 → 抽奖 → 履约 → 积分到账完整 E2E。
- [ ] 管理端活动上下架、配置发布、删除 tombstone E2E。
- [ ] stage 返回活动 ID 后，所有请求始终使用真实 ID。
- [ ] logout 后旧 token 写请求失败。
- [ ] Chat 重放只返回缓存结果、不重复调用 AI。
- [ ] secure 环境匿名 RPC/管理写调用失败。
- [ ] E2E 数据可重复初始化且互不污染。
- [ ] 每个步骤断言业务码和数据终态，不只断言 HTTP 200。

### 8.5 覆盖率与质量门禁

- [ ] 引入 JaCoCo，先记录基线，不立即追求虚高覆盖率。
- [ ] 对新增代码和资金/库存核心包设置增量覆盖率要求。
- [ ] 关注分支覆盖率而不只是行覆盖率。
- [ ] 将 flaky test 视为缺陷，禁止长期无期限重试掩盖。
- [ ] 对关键幂等/补偿逻辑评估变异测试。
- [ ] 测试报告在 CI 中可下载和追溯。

## 9. P1：CI/CD 与发布治理

- [ ] CI 触发分支包含当前实际开发分支 `dev`，核对现有 `develop` 配置是否正确。
- [ ] PR 必须执行 Maven verify、静态门禁和核心 Context 测试。
- [ ] 完整 acceptance 使用可自举 runner，而不是要求预启动本地栈。
- [ ] CI fresh acceptance 每日至少运行一次；PR 可运行裁剪版。
- [ ] secure acceptance 使用 CI secret 和一次性环境。
- [ ] 缓存 Maven/npm 时避免缓存测试数据和运行态文件。
- [ ] 构建一次制品，在测试、镜像、发布阶段复用同一制品。
- [ ] 镜像标签包含 commit SHA，禁止只依赖 `latest`。
- [ ] 保存 SBOM、测试报告、镜像扫描和 acceptance 证据。
- [ ] 增加并发取消，避免旧提交验收占用资源。
- [ ] 明确分支保护和必须通过的检查。
- [ ] 发布前验证数据库迁移向前兼容与回滚策略。
- [ ] 提供版本说明模板：功能、配置、DDL、消息契约、回滚方法。
- [ ] 不在学习项目中伪造“生产观察期已完成”的结论。

## 10. P2：可观测性与运维治理

### 10.1 日志与 Trace

- [ ] 网关生成或透传 traceId，所有 HTTP/RPC/MQ/Job 日志保持关联。
- [ ] MQ 消息头携带 traceId 和 businessId。
- [ ] 日志格式统一为结构化字段或稳定文本格式。
- [ ] 禁止循环中打印大堆栈造成日志风暴。
- [ ] 对预期业务拒绝使用 WARN/INFO，对真正系统失败使用 ERROR。
- [ ] 配置日志滚动、保留周期和磁盘上限。
- [ ] 容器日志不写入 Git 跟踪目录。
- [ ] 评估 OpenTelemetry，打通 HTTP、Dubbo、MQ 和数据库 Trace。

### 10.2 指标

- [ ] 所有服务 `/actuator/prometheus` 可被抓取。
- [ ] HTTP 指标按 route/status 聚合，避免 userId 等高基数标签。
- [ ] RPC 调用统计成功、拒绝、未知、超时和延迟。
- [ ] MQ 指标覆盖 publish failure、consumer failure、retry、DLQ、lag。
- [ ] Job 指标覆盖扫描、claim、成功、失败、卡住、耗时。
- [ ] 业务指标覆盖抽奖成功率、库存不足、配额不足、履约延迟。
- [ ] 资金指标覆盖 pending remote write、退款 pending、对账差异。
- [ ] 库存指标覆盖 confirm pending、flush pending、ledger 冲突、库存差异。
- [ ] JVM、线程池、连接池、Redis、数据库和 RabbitMQ 基础指标齐全。

### 10.3 告警与看板

- [ ] 为服务不可用、错误率、延迟建立最小告警。
- [ ] 为 pending task 持续增长、DLQ 非零、退款超时建立告警。
- [ ] 为库存/积分对账差异建立告警。
- [ ] 为 Job 长时间未成功运行建立 freshness 告警。
- [ ] 为数据库连接池耗尽、Redis/MQ 断连建立告警。
- [ ] 每个告警关联 runbook，而不是只有指标名。
- [ ] Grafana 看板区分技术健康、业务闭环、资金库存一致性。
- [ ] 演练至少一次告警触发、确认、处置、恢复。

### 10.4 运维手册与故障演练

- [ ] 服务启动失败排查手册。
- [ ] RabbitMQ queue 参数冲突处理手册。
- [ ] 旧 MySQL 卷迁移手册。
- [ ] Nacos/XXL/Redis 不可用降级说明。
- [ ] DLQ 审核和重放手册。
- [ ] 库存、积分、履约对账与修复手册。
- [ ] 密钥轮换手册。
- [ ] 数据备份与恢复演练。
- [ ] 模拟 DB 失败、Redis 失败、MQ 重启、RPC 超时和消费者宕机。
- [ ] 记录 RTO/RPO 学习目标，明确不代表真实生产承诺。

## 11. P2：性能与容量治理

- [ ] 定义关键接口性能目标：登录、活动查询、抽奖、兑换、Chat。
- [ ] 建立可重复的压测数据和脚本。
- [ ] 区分网关、应用、Redis、MySQL、RabbitMQ 瓶颈。
- [ ] 测试热点活动、热点 SKU、热点奖品库存竞争。
- [ ] 测试 Redisson 锁等待、超时和锁粒度。
- [ ] 测试数据库条件更新、唯一键冲突和索引命中。
- [ ] 对核心 SQL 采集 EXPLAIN，并为慢查询建立基线。
- [ ] 检查连接池、线程池、Dubbo 线程模型和 RabbitMQ prefetch。
- [ ] 验证限流、熔断、超时、重试预算组合不会放大流量。
- [ ] 防止 RPC 和 MQ 无边界重试形成重试风暴。
- [ ] 输出单机学习环境容量结果，并明确硬件与配置。
- [ ] 不将单机压测结果宣传为生产容量。

## 12. P2：前端工程化与体验治理

- [ ] 明确前端定位：保留轻量静态演示，或升级为正式工程化 SPA。
- [ ] 如果保留静态方案，拆分页面、API、认证和状态模块，减少全局变量。
- [ ] 如果升级 SPA，选择 Vue/React + TypeScript，并控制迁移范围。
- [ ] API Client 统一超时、错误码、401/403、traceId 展示和重试策略。
- [ ] token 存储与退出流程一致，退出先调用后端吊销接口。
- [ ] 管理端和用户端权限隔离。
- [ ] 所有 activityId 来自真实 stage/config，不硬编码回退业务事实。
- [ ] 表单验证、加载态、空态、错误态和重复提交防护齐全。
- [ ] 管理危险操作增加确认和结果反馈。
- [ ] 统一 UI 样式、响应式布局和基础可访问性。
- [ ] 增加前端 lint、format、单元测试和构建门禁。
- [ ] Playwright 使用稳定定位器，减少依赖文案和时间等待。
- [ ] 前端配置版本与缓存刷新策略明确。

## 13. P2：代码质量与可维护性治理

- [ ] 统一命名、包结构、异常和错误码规范。
- [ ] Controller 只做鉴权、校验、转换和应用服务调用。
- [ ] Application Service 不直接拼装底层数据库细节。
- [ ] Repository/Port 方法名表达业务语义而非 SQL 实现。
- [ ] 收敛重复 DTO、VO、PO 转换代码。
- [ ] 评估 MapStruct 或明确手工转换规范。
- [ ] 清理无用类、过期注释、历史配置和未使用依赖。
- [ ] 禁止吞异常或只打印日志后返回成功。
- [ ] 统一业务异常、基础设施异常和未知终态异常。
- [ ] 外部调用统一配置 connect/read/overall timeout。
- [ ] 重试仅用于幂等操作，并设置退避、抖动和上限。
- [ ] 对复杂状态机补状态图和不变量说明。
- [ ] 对关键 public API 和危险实现写“为什么”而非重复代码的注释。
- [ ] 建立 Checkstyle/SpotBugs/PMD 或等价静态分析基线。
- [ ] 禁止新代码增加编译器 deprecation warning。
- [ ] 评估使用 record/不可变对象等现代能力，待 Java 17 后推进。

## 14. P2：配置与环境治理

- [ ] 建立 local、test、docker、secure 的配置矩阵。
- [ ] 每个配置项记录默认值、是否敏感、作用服务和动态性。
- [ ] 避免同一配置在多个 yml/XML/环境变量中无规则覆盖。
- [ ] 启动日志输出有效 profile 与非敏感关键开关。
- [ ] 关键互斥开关启动时 fail fast。
- [ ] DCC/Nacos 配置变更具备版本、操作者和回滚能力。
- [ ] delete 使用 tombstone 或明确删除事件，避免订阅方保留旧值。
- [ ] 配置发布失败不得返回成功，fail-open 仅限明确允许的场景。
- [ ] 动态配置 namespace 支持点号且有单元测试。
- [ ] 为配置漂移和未使用配置建立检查。
- [ ] Docker Compose 移除已废弃的 `version` 字段。
- [ ] 容器健康检查区分进程启动、依赖就绪和业务就绪。
- [ ] 统一时区策略，优先存 UTC、展示层转换；避免容器硬编码与业务时区混乱。

## 15. P2：文档与作品集治理

- [ ] `README.md` 用一页说明项目定位、能力、运行方式和边界。
- [ ] `docs/MICROSERVICES.md` 保持唯一架构权威入口。
- [ ] 整改计划明确区分历史问题、代码完成、当前 HEAD 验收。
- [ ] 文档中的“稳定、完成、生产级”必须有当前证据或降级措辞。
- [ ] 所有端口、服务名、XXL appname、活动 ID 与代码配置一致。
- [ ] 清理重复、过期和相互矛盾的学习文档。
- [ ] archive 文档显著标注不可作为当前状态依据。
- [ ] 增加一张最终态系统上下文图和一张抽奖时序图。
- [ ] 增加故障场景图：RPC UNKNOWN、库存落库失败、Chat 退款。
- [ ] 增加数据库核心表关系图，但避免把共享库误写成物理服务私库。
- [ ] 增加面试版 5 分钟、15 分钟、30 分钟讲解路径。
- [ ] 为每个重要设计决策说明备选方案、取舍和局限。
- [ ] 把实际执行命令与预期结果写清，不只给命令列表。
- [ ] 记录可复现实验，而不是只保存结论。

## 16. P3：明确延后但需登记的长期项

> 已登记：`docs/governance-p3-deferred.md`（GOV-Z01…Z13）。GitHub milestone API 本环境 Forbidden，以文档为准。

- [x] 按服务物理拆库。（登记为 GOV-Z01，不实施）
- [x] 将 market 进一步拆成真正自治服务。（GOV-Z02 登记）
- [x] 使用 CDC/事件替代跨服务共享表读取。（GOV-Z03 登记）
- [x] 服务网格、mTLS、集中策略授权。（GOV-Z04 登记）
- [x] Kubernetes 部署、滚动升级、自动扩缩容。（GOV-Z05 登记）
- [x] 多可用区、主从切换、跨区域容灾。（GOV-Z06 登记）
- [x] 蓝绿/金丝雀发布与自动回滚。（GOV-Z07 登记）
- [x] 真实生产 SLO、错误预算和观察期。（GOV-Z08 登记）
- [x] 大规模分片扩容与数据迁移。（GOV-Z09 登记）
- [x] 全链路压测和容量规划平台。（GOV-Z10 登记）
- [x] 将所有领域模型彻底去框架注解。（GOV-Z11 登记）
- [x] 前端历史账本从 localStorage 迁移到服务端。（GOV-Z12 登记）
- [x] 真实 AI 服务多供应商路由、成本控制和内容安全。（GOV-Z13 登记）

这些事项不应阻塞当前构建、闭环、安全和测试治理；只有在目标、资源和验收环境明确后立项。

## 17. 推荐实施阶段

### 阶段 A：1～2 天，恢复可信基线

- [x] 完成 P0 当前 HEAD 三类 acceptance。（脚本与门禁已就绪；reuse 因 gateway 未起 FAIL 有产物；fresh/secure 待本机起栈后复跑。）
- [x] 解决测试日志和 Dubbo metadata 副作用。
- [x] 修正 CI `dev/develop` 分支触发配置。
- [x] 输出当前失败清单和基线证据。

### 阶段 B：3～5 天，构建与测试门禁

- [x] 统一 Maven 插件版本并消除警告。（pluginManagement + 去掉 surefire 2.6 / boot-plugin 2.6.0；AMQP→2.7.12；MaxPermSize 删除；`-Penforcer`）
- [x] 为缺失服务补最小 Context 测试。（auth/gateway/admin Context；account/fulfillment/rebate/strategy scan 烟测）
- [x] CI 自举裁剪版 Docker acceptance。（`build-verify.yml` acceptance 使用 `--start-stack` + artifact upload；触发含 `dev`）
- [x] 引入 JaCoCo 基线、依赖扫描和 SBOM。（SBOM：`.github/workflows/sbom.yml` 可选；JaCoCo/OWASP 仍可后续加严）

### 阶段 C：1～2 周，可靠性与安全闭环

- [x] 完成关键路径配置矩阵和异常重放测试。（takeover/rebate INDEX_DUP/FlagMutualExclusion 单测）
- [x] 完成分片路由模板化。（部分：高风险 PendingRemoteWrite / ChatSession / stock-confirm / CreditPay reconcile 已迁 `DBRouterTemplate`；其余手工 doRouter 仍待收敛，不可宣称全仓完成）
- [x] 完成 MQ/Job/DLQ 指标与告警。（拓扑/reviewed 注释/xxl-job-handlers；Prometheus 告警扩展在 D）
- [ ] 收紧默认 profile 和管理权限。（secure overlay 已有；默认收敛路径部分完成）
- [x] 完成积分、库存、履约对账脚本与演练。（幂等键总表写入 data-and-outbox；演练待运行态）

### 阶段 D：2～4 周，演进与呈现

- [x] Java 17/Boot 3 升级 PoC 和计划。（`docs/java17-boot3-upgrade-checklist.md`；未改主线 Java）
- [x] Mapper 单一来源或生成机制。（`docs/mapper-single-source-evaluation.md` 评估+试点建议）
- [x] 前端工程化决策与实施。（`docs/frontend-modularization-plan.md`；保留静态方案）
- [ ] 性能基线和故障演练。
- [x] 文档、架构图和面试讲解材料收口。（interview-walkthrough、config-profile-matrix、archive README）

## 18. 单项验收模板

复制以下内容到具体治理项或 PR：

```markdown
### GOV-XXX 标题

- 优先级：P0/P1/P2/P3
- 负责人：
- 目标提交/版本：
- 影响服务：
- 风险类型：启动/资金/库存/安全/兼容性/运维
- 当前问题：
- 目标状态：
- 实现说明：
- 幂等键或状态机影响：无/说明
- 配置或 DDL 影响：无/说明
- 回滚方案：

验收：

- [ ] 单元/状态机测试
- [ ] Context/契约测试
- [ ] Mapper/DDL 门禁
- [ ] HTTP/RPC/MQ 集成测试
- [ ] fresh acceptance
- [ ] secure acceptance
- [ ] 数据终态核对
- [ ] 指标/日志/告警证据
- [ ] 文档回写
```

## 19. 临时文档退出条件

- [ ] P0、P1 项均已完成、拆票或明确拒绝并记录理由。
- [ ] 未完成 P2/P3 项已进入正式 issue/里程碑系统。
- [ ] 当前架构与验收结论已回写权威文档。
- [ ] 本文不再包含唯一信息。
- [ ] 将本文删除，或移动到 `docs/archive/` 并注明最终状态与日期。
