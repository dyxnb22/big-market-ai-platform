# Big Market 当前学习冻结审计报告

更新时间：2026-07-17

## 1. 审计结论

结论：**最终 7 服务拓扑已完成代码与配置收敛，核心 Docker smoke 已通过；完整运行验收仍待补跑。**

当前仓库已经物理收敛为 gateway、auth、admin、market、chatbot、message-job、
account 七个应用服务。market 内部提供策略与返利能力，message-job 通过本地
outbox 派发积分奖，account 提供积分和额度 RPC。

清理后的代码、配置、Compose、SQL、Agent 约束和学习文档已统一到该拓扑。
当前静态门禁、Maven 构建和核心 Docker smoke 通过；完整 acceptance、fresh 空卷、
secure overlay 和生产能力仍未验证，因此不能把本次结果描述为生产就绪。

## 2. 范围与架构事实

审计范围包括根 Maven reactor、七个应用服务、共享 domain/infrastructure/api/
types/starters、静态前端、Mapper XML、Docker/SQL、RabbitMQ/XXL、CI、测试、
脚本和学习文档。

最终入口：

- 前端：big-market-web，端口 5173
- gateway：8080
- auth：8081
- admin：8082
- market：8083
- chatbot：8084
- message-job：8085
- account：8086

边界约束：

- market 不扫描 trigger.job / trigger.listener。
- message-job 负责消息消费、补偿任务和 XXL-Job，不暴露业务 HTTP/RPC 入口。
- 策略与返利通过 market-local port/adapter 执行。
- 积分奖由 message-job 本地 outbox 接管，再调用 account RPC 入账。
- 资金、额度、库存、发奖和返利路径继续使用既有幂等键、UNKNOWN 和对账语义。

## 3. 当前验证结果

| 检查 | 结果 |
| --- | --- |
| mvn -B clean verify -DfailIfNoTests=false | PASS，19 个 reactor 模块 |
| scripts/validate-microservices-runtime-safety.sh | PASS，98/98 |
| scripts/validate-mapper-ddl-gates.sh | PASS，18/18 |
| 默认 Compose 配置 | PASS |
| secure Compose 配置 | PASS（非默认占位凭据校验） |
| 路径、契约和 Provider 入口扫描 | PASS，无残留 |
| git diff --check、JSON、Shell 语法 | PASS |
| 核心 Docker smoke | PASS，7 个应用健康、Web HTTP 200、20/20 |

## 4. 尚未验证

- Docker 启动后的完整 ./scripts/acceptance.sh --reuse
- fresh 空卷初始化与迁移一致性
- 完整 secure overlay 的运行验收
- 生产 HA、容量、灰度、灾备和外部奖品履约

本地动态验收需要先启动基础设施和应用栈：

    docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch
    docker compose up --build -d
    ./scripts/acceptance.sh --reuse

## 5. 冻结约束

- 不重新拆出独立业务 Provider，不引入额外切换模式。
- 不让 market 扫描消息 Job/listener。
- 不改变积分、额度、库存、发奖、返利和 SKU 的幂等键。
- 不以静态 validator、健康端点或 award_state=completed 单独证明业务闭环。
- 任何业务路径改动都必须同步更新 docs/data-and-outbox.md、相关测试和
  当前架构文档。

## 6. 当前风险

- 默认拓扑仍共享物理 MySQL，Mapper XML 存在启动器副本。
- account 的失败注入与完整 Context 覆盖仍需加强。
- Java 8 / Spring Boot 2.7 是学习基线，未完成全量 CVE/SBOM 审计。
- 默认凭据、localStorage JWT、宽松本地 RPC 和关闭限流只适用于隔离学习环境。

## 7. 审计依据

- README.md
- AGENTS.md
- docs/MICROSERVICES.md
- docs/LEARNING-FREEZE.md
- docs/data-and-outbox.md
- docs/operations-checklist.md
- docs/microservices-dao-ownership.md
