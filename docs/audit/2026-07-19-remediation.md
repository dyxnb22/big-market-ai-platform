# 2026-07-19 报告问题修复与文档整理

本文是本轮报告的当前闭环记录。它描述修复工作树，不改写
`docs/audit/2026-07-17-learning-freeze-audit.md` 等历史证据；历史文档继续保留
原始时间、SHA 和当时的结论。

## 修复总览

| 报告项 | 修复结果 | 主要落点 |
| --- | --- | --- |
| P1-01 库存并发 | 账本 `reserved → applied` 由 CAS 授权实际扣减；库存刷新等待所有异步任务完成后再释放锁；补充 CAS 丢失回归测试 | `StrategyAwardCacheSupport`、`StrategyAwardCacheSupportTest`、`UpdateAwardStockJob` |
| P1-02 旧任务 Outbox | 增加重试次数、下次执行时间、错误摘要和 `manual_pending` 终态；查询只取到期行并使用有界退避 | 三份 `task_mapper.xml`、`Task`/`TaskRepository`、`SendMessageTaskJob` |
| P1-03 Chat refund | refund/deduct 重试均使用到期时间和最大次数；耗尽后进入 `manual_pending`，保留有限错误摘要 | 三份 `chat_credit_session_mapper.xml`、两个 reconcile job |
| P1-04 Nacos/Redis 配置 | Nacos publish 是提交点；Redis fan-out 失败返回 `notificationPending` 并后台重试；content hash 提供乐观并发控制；新配置会淘汰旧通知重试 | `PlatformConfigService`、`PlatformConfigChangeNotifier`、管理 DTO |
| P1-05 不安全 OpenAI 工作流 | 移除下载未校验 JAR、注入密钥的旧工作流，改为官方 Actions、JDK 17、只读权限和仓库内验证 | `.github/workflows/openai-code-review.yml` |
| P1-06 旧 release tag | 不移动、不覆盖 `v0.1.0-interview-freeze`；增加 tag 祖先关系和服务拓扑检查 | `scripts/validate-release-lineage.sh`、`.github/workflows/release-lineage.yml` |
| P2-01 XXL seed | `DlqReplayJob` 默认停止，注释与 handler catalog 对齐 | `z-xxl-job-extra-handlers.sql`、`docs/xxl-job-handlers.md` |
| P2-02 SQL 漂移 | 对齐 reconcile 参考 SQL 与 Docker SQL；新增幂等 schema migration ledger 和旧卷迁移脚本 | `docs/sql/migrations/`、`z-bounded-retry-states.sql`、`apply-stack-migrations.sh` |
| P2-03 Maven/Java 基线 | 默认 Enforcer 要求 Java 17/Maven 3.9；移除可绕过的 optional 误配置 | 根 `pom.xml`、模块边界门禁 |
| P2-04 HTTP 状态码 | AppException/校验异常不再统一返回 HTTP 200；保留业务 response code，同时映射真实 HTTP 状态 | 各服务 `*ExceptionHandler` |
| P2-05 依赖 | 清理旧 commons-lang3/fastjson 误用，统一到当前 Spring Boot/BOM 依赖线 | 各模块 `pom.xml` |
| P2-06 前端安全 | JWT 改为 sessionStorage；CDN 版本固定并启用 SRI；Nginx 增加 CSP、nosniff、frame/referrer/permissions 策略 | `big-market-web/api-client.js`、`index.html`、`nginx.conf` |
| P2-07 Chat SSRF/输入 | 远程地址仅允许 HTTPS、精确 host allowlist、无 userinfo/query/fragment；message/requestId 增加 DTO 与服务层上限 | `ChatbotApplicationService`、`ChatbotAskRequestDTO` |
| P2-08 可观测性 | 增加旧 task backlog/age/manual 与 Chat manual gauges；消息日志只记录标识和 payload 长度 | `BusinessMetricsPublisher`、MQ listeners |
| P2-09 模块边界 | market 不再扫描 `trigger.job`/`trigger.listener`；trigger 的 XXL/MQ handler 依赖改为 optional，由 message-job 显式引入；market 仅保留基础设施发布所需的 AMQP 能力；增加脚本门禁 | `validate-module-boundaries.sh`、三个 launcher `pom.xml` |
| P2-10 运行时边界 | 默认 reuse 栈已重新构建并验收；fresh 空卷和 secure overlay 仍明确标记为未执行 | `LEARNING-FREEZE.md`、`operations-checklist.md` |
| P2-11 DLQ 紧密重回队 | DLQ 持久化失败重回队增加有界退避，避免错误风暴 | `RabbitMQDlqConfig` |
| P2-12 CI 覆盖 | 默认 verify 产出 CycloneDX SBOM；手动 acceptance job 安装 Playwright 并执行双跑 | `.github/workflows/build-verify.yml` |
| P2-13 镜像漂移 | 应用/基础镜像改为固定可审计 tag/digest，Compose 不再使用 `latest` | `docker-compose.yml`、`Dockerfile.service`、Web Dockerfile |
| P3-01 文档/证据 | 根 README、冻结基线、架构、Outbox、运维和 DAO 矩阵已更新；历史审计保留并明确历史属性 | 本目录及相关 `docs/*.md` |
| P3-02 可维护性 | 不进行高风险大拆；把本轮状态机、迁移顺序、人工终态、指标和边界约定集中到当前文档，后续重构继续限定在 P1 状态机/事务边界 | `docs/data-and-outbox.md`、`docs/operations-checklist.md`、本文件 |

## 数据迁移顺序

新卷由 Docker SQL 初始化；旧卷不会自动重放 init SQL，统一执行：

```bash
./scripts/apply-stack-migrations.sh
```

脚本按顺序应用 reconcile DDL、XXL seed、`V20260719__bounded_retry_states`
幂等迁移、Nacos twin 对齐和学习演示数据，然后检查 task/chat 字段、
`schema_migration` ledger、XXL handler 与关键缓存。迁移脚本不使用当前 MySQL
不支持的 `ADD COLUMN IF NOT EXISTS`，重复执行安全。

## 当前验证证据

本轮在 `main` 基线 `40dc23e` 的未提交修复工作树上执行：

- `mvn -B clean verify -DfailIfNoTests=false`：19 个 reactor 模块通过。
- Mapper/DDL 门禁：23 项通过，`compared=145`、`exceptions=8`、0 error。
- Runtime safety：99 项通过，0 failure；module boundary 门禁通过。
- `docker compose config`、XML mapper parse、脚本 `bash -n` 通过。
- `./scripts/acceptance.sh --reuse --start-stack`：7 服务健康、HTTP contract、20/20 microservice smoke、XXL executor 注册、raffle-award E2E、chat-refund E2E，以及共享演示账号单 worker 下 Playwright 18/18 连续两轮全部通过。

`v0.1.0-interview-freeze` 未被移动；`validate-release-lineage.sh` 会将它识别为
非 Java 主线祖先并拒绝作为当前发布输入。新发布应从明确的 Java 主线提交创建新 tag。

仍未声称完成：`--fresh --confirm-destroy-volumes` 的空卷验证、完整
`--secure` overlay 验证、生产 HA/容量/真实外部奖品履约，以及全量 CVE 审计。
CI 已默认生成 CycloneDX SBOM，但 SBOM 生成不等于完成漏洞审计。

## 文档使用顺序

1. `docs/LEARNING-FREEZE.md`：当前结论与未验证边界。
2. `docs/MICROSERVICES.md`：固定七服务拓扑和同步/异步边界。
3. `docs/data-and-outbox.md`：幂等键、状态机、补偿与 DLQ。
4. `docs/operations-checklist.md`：启动、迁移、指标和人工处置。
5. `docs/microservices-dao-ownership.md`：逻辑 DAO 归属及已登记例外。
6. `docs/learning/`：学习路径；`docs/archive/` 与旧 audit 仅作历史材料。
