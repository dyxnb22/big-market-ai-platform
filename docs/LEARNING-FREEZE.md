# Big Market 学习冻结基线

最后更新：2026-07-19。

## 结论

**有条件冻结（栈已升级）。** 当前修复工作树在 **Java 17 + Spring Boot 3.5.16 + Spring Cloud 2025.0.3** 上完成 clean Maven 构建、Context、Mapper/DDL、Compose 配置校验、核心 Docker smoke，以及抽奖发奖 / chat refund E2E；本轮 `./scripts/acceptance.sh --reuse --start-stack` 已通过全部门禁（含共享演示账号使用单 worker 执行的 Playwright 18 个用例连续两轮）。fresh 空卷和完整 secure overlay 尚未执行，因此不能写成“全环境已验证”或“生产就绪”。

本仓库冻结的是一套可复现、可讲解的本地微服务学习样本，不是生产发布基线。
栈升级决策见 docs/adr/2026-07-18-stack-upgrade.md；历史证据见 docs/audit/2026-07-17-learning-freeze-audit.md。

## 当前最终拓扑（核心 Docker smoke + 钱路径 E2E 已通过）

    big-market-web :5173
           |
    gateway :8080
           |-- auth :8081
           |-- admin :8082
           |-- market :8083 -- local rebate/strategy capabilities
           +-- chatbot :8084

    message-job :8085 -- RabbitMQ consumers + XXL executor
    account :8086     -- credit/quota RPC

中间件默认镜像（docs/dev-ops/docker-compose-environment.yml）：
MySQL **8.4.5**、Redis **7.4.9**、RabbitMQ **4.3.2**、Nacos **v3.2.3-slim**、
XXL-Job Admin **2.5.0**（Apple Silicon 用 `kuschzzp/xxl-job-aarch64:2.5.0`）。
docker-compose.yml 是最终应用入口，仅包含 8080-8086。策略与返利固定在 market 内部执行，积分奖固定由
message-job 本地 outbox 派发到 account。

默认 compose 中积分奖走本地 outbox：SendAwardConsumer 写
credit_award_task，DispatchCreditAwardTaskJob 派发到账户服务。
user_award_record.award_state=completed 表示发奖已被持久化接管，不单独证明
账户已入账；最终结果应同时检查 outbox 与账户流水。

## 当前证据

| 项目 | 2026-07-19（upgrade/java17-boot3）结果 |
| --- | --- |
| Maven | 19 个 reactor 模块 `mvn clean verify` 通过（Boot 3.5.16） |
| Mapper/DDL 门禁 | 通过 |
| 运行时安全门禁 | 通过 |
| Compose 配置 | 默认配置通过 |
| 核心 Docker smoke | 健康检查与路由 smoke 通过（偶发 chatbot AI 配置残留时 ask 可能非 0000） |
| 抽奖发奖 E2E | `smoke-raffle-award-e2e.sh` PASSED |
| Chat refund E2E | `smoke-chat-refund-e2e.sh` PASSED |
| 完整 acceptance | `./scripts/acceptance.sh --reuse --start-stack` 通过；Playwright 18/18，单 worker 连续两轮 |

静态门禁不能替代 fresh、secure 和业务状态验收。

## 最短启动与验收

    docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
    docker compose up --build -d
    npm install
    npx playwright install chromium
    ./scripts/acceptance.sh --reuse --start-stack
    ./scripts/smoke-security.sh

acceptance.sh 默认不会替你启动容器；需要脚本启动时加 --start-stack。
全新卷验收会删除数据，必须显式确认：

    ./scripts/acceptance.sh --fresh --confirm-destroy-volumes --start-stack

secure overlay 需要非默认 JWT、内部 RPC、管理、XXL、MySQL 和 RabbitMQ 凭据：

    ./scripts/acceptance.sh --secure --start-stack

## 学习顺序

1. 先读 docs/MICROSERVICES.md，画出默认拓扑和同步/异步边界。
2. 跟踪登录：gateway → auth → JWT → Redis 吊销。
3. 跟踪抽奖：RaffleActivityController → RaffleApplicationService →
   activity/strategy/award repository。
4. 跟踪发奖：SendAwardConsumer → AwardService → credit_award_task →
   DispatchCreditAwardTaskJob → account RPC。
5. 跟踪失败路径：docs/data-and-outbox.md 中的幂等键、UNKNOWN、补偿与 DLQ。
6. 按 docs/learning/00-learning-guide.md 和 docs/learning/16-local-setup.md
   动手验证。

## 冻结后约束

- 不为“更像微服务”而拆物理库、重写共享 kernel 或再次大迁移框架。
- 不改变积分、额度、发奖、返利、SKU/奖品库存的业务幂等键。
- 不让 market 扫描 trigger.job / trigger.listener；消息消费与 XXL 归 message-job。
- 不把静态 validator、健康端点或 award_state=completed 单独当作业务闭环证据。
- 不把默认开发凭据、secure 配置文件存在、一次本地通过描述成生产安全。
- 冻结后只接受明确缺陷、依赖安全修复或学习文档事实错误；大规模重构另立目标。

## 已知边界

- 最终 7 服务拓扑已执行完整 reuse acceptance 与钱路径 E2E；fresh 空卷、完整 secure overlay 仍需显式授权再跑。
- 默认拓扑仍共享物理 MySQL，DAO 归属主要靠文档和部分 ArchUnit 规则；
  Mapper XML 有多份启动器副本。
- account 的失败注入覆盖仍可加强；已有最小 `@SpringBootTest` Context 门禁。
- 栈基线为 Java 17 / Spring Boot 3.5；CI 默认生成 CycloneDX SBOM，但本轮未声称完成全量 CVE 审计。
- 前端 JWT 仅放在 sessionStorage；聊天/抽奖历史仍在 localStorage，默认凭据、宽松 RPC 和关闭限流仅适用于本地隔离环境。
- 没有生产灰度、容量、HA、灾备或真实外部奖品履约证明。
- Dubbo Hessian 仍保留窄范围 `--add-opens`（见 Dockerfile.service / Surefire）；Nacos 3.x 学习栈关闭 namespace compatible mode；平台 DataId 以 empty 为 SDK 写入 SoT，admin 通过 JDBC fail-closed 确认并镜像 `public` twin。Nacos publish 是配置提交点，Redis fan-out 只作可重试通知；通知暂挂会返回 `notificationPending`，Nacos listener/启动读取负责最终收敛。

本轮报告问题、代码落点、迁移与验证证据见 `docs/audit/2026-07-19-remediation.md`。
