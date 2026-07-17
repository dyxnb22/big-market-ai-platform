# Big Market 学习冻结基线

最后更新：2026-07-17。

## 结论

**有条件冻结。** 当前最终拓扑已完成 clean Maven 构建、静态安全门禁、
Mapper/DDL 门禁、Compose 配置校验和核心 Docker smoke。完整 acceptance、
fresh 空卷和完整 secure overlay 尚未验证，因此不能写成“全环境已验证”
或“生产就绪”。

本仓库冻结的是一套可复现、可讲解的本地微服务学习样本，不是生产发布基线。
详细证据与限制见 docs/audit/2026-07-17-learning-freeze-audit.md。

## 当前最终拓扑（核心 Docker smoke 已通过）

    big-market-web :5173
           |
    gateway :8080
           |-- auth :8081
           |-- admin :8082
           |-- market :8083 -- local rebate/strategy capabilities
           +-- chatbot :8084

    message-job :8085 -- RabbitMQ consumers + XXL executor
    account :8086     -- credit/quota RPC

MySQL、Redis、RabbitMQ、Nacos、XXL-Job Admin、Prometheus/Grafana 等由
docs/dev-ops/docker-compose-environment.yml 提供。docker-compose.yml 是最终
应用入口，仅包含 8080-8086。策略与返利固定在 market 内部执行，积分奖固定由
message-job 本地 outbox 派发到 account。

默认 compose 中积分奖走本地 outbox：SendAwardConsumer 写
credit_award_task，DispatchCreditAwardTaskJob 派发到账户服务。
user_award_record.award_state=completed 表示发奖已被持久化接管，不单独证明
账户已入账；最终结果应同时检查 outbox 与账户流水。

## 当前证据

| 项目 | 2026-07-17 结果 |
| --- | --- |
| Maven | 19 个 reactor 模块通过 |
| Mapper/DDL 门禁 | 18/18 通过 |
| 运行时安全门禁 | 98/98 通过 |
| Compose 配置 | 默认与 secure 配置均通过 |
| 旧路径扫描 | 无残留 |
| JSON、Shell 语法和 git diff 检查 | 通过 |
| 核心 Docker smoke | 20/20 通过；7 个应用健康、Web 200 |
| 完整 acceptance | 尚未运行 |

静态门禁不能替代 fresh、secure 和业务状态验收。

## 最短启动与验收

    docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
    docker compose up --build -d
    npm install
    npx playwright install chromium
    ./scripts/acceptance.sh --reuse
    ./scripts/smoke-security.sh

acceptance.sh 默认不会替你启动容器；需要脚本启动时加 --start-stack。
全新卷验收会删除数据，必须显式确认：

    ./scripts/acceptance.sh --fresh --confirm-destroy-volumes --start-stack

secure overlay 需要非默认 JWT、内部 RPC、管理和 XXL 凭据：

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

- 不为“更像微服务”而拆物理库、重写共享 kernel 或迁移框架。
- 不改变积分、额度、发奖、返利、SKU/奖品库存的业务幂等键。
- 不让 market 扫描 trigger.job / trigger.listener；消息消费与 XXL 归 message-job。
- 不把静态 validator、健康端点或 award_state=completed 单独当作业务闭环证据。
- 不把默认开发凭据、secure 配置文件存在、一次本地通过描述成生产安全。
- 冻结后只接受明确缺陷、依赖安全修复或学习文档事实错误；大规模重构另立目标。

## 已知边界

- 最终 7 服务拓扑已执行核心 Docker smoke，尚未执行 fresh 空卷、完整 secure overlay 和全量 acceptance。
- 默认拓扑仍共享物理 MySQL，DAO 归属主要靠文档和部分 ArchUnit 规则；
  Mapper XML 有多份启动器副本。
- account 的失败注入与完整 Spring Context 覆盖仍需加强。
- Java 8 / Spring Boot 2.7 属于学习基线；未完成本轮全量 CVE/SBOM 审计。
- 前端 JWT 存在 localStorage，默认凭据、宽松 RPC 和关闭限流仅适用于本地隔离环境。
- 没有生产灰度、容量、HA、灾备或真实外部奖品履约证明。
