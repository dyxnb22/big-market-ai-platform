# Big Market 学习冻结基线

最后核验：2026-07-11；基线：本学习冻结提交（自 `8d51601` 起的验收修复与文档）。

## 结论

**有条件冻结。** 默认本地学习拓扑已在复用数据卷上完成构建、服务健康、HTTP 契约、真实抽奖发奖入账、聊天扣费退款/补偿、安全负向检查和两轮浏览器测试。尚未在本次审计中销毁数据卷执行 fresh 初始化，也未用非默认凭据启动完整 secure overlay，因此不能写成“全环境已验证”或“生产就绪”。

本仓库冻结的是一套可复现、可讲解的本地微服务学习样本，不是生产发布基线。详细证据与遗留项见 [`audit/2026-07-11-learning-freeze-audit.md`](audit/2026-07-11-learning-freeze-audit.md)。

## 已验证拓扑

```text
big-market-web :5173
       |
gateway :8080
       |-- auth :8081
       |-- admin :8082
       |-- market :8083 -- embedded rebate/strategy providers
       `-- chatbot :8084

message-job :8085 -- RabbitMQ consumers + XXL executor
account :8086     -- credit/quota RPC

optional dedicated providers:
fulfillment :8087 -- optional remote award RPC
rebate      :8088 -- optional dedicated rebate provider
strategy    :8089 -- optional dedicated strategy provider
```

MySQL、Redis、RabbitMQ、Nacos、XXL-Job Admin、Prometheus/Grafana 等由 `docs/dev-ops/docker-compose-environment.yml` 提供。`docker-compose.yml` 默认仅包含 8080-8086。`fulfillment-service:8087`、`rebate-service:8088`、`strategy-service:8089` 为可选独立部署；默认积分奖不走 remote fulfillment，rebate/strategy 默认由 market 内嵌对应 Dubbo provider。

默认 compose 中积分奖走本地 outbox：`SendAwardConsumer`（message-job）写 `credit_award_task`，XXL `DispatchCreditAwardTaskJob` handlers（任务 5/6）派发到账户服务。`user_award_record.award_state=completed` 表示发奖已被持久化接管，不单独证明账户已入账；最终结果应同时检查 outbox 与账户流水。

## 前置条件

- JDK 8、Maven 3.6+、Docker Desktop / Compose v2。
- 建议 Docker 可用内存 **至少 12 GB**。本次 7.8 GiB 配额在全量重建时出现过 `xxl-job-admin` OOM（exit 137）。
- Node.js/npm；首次浏览器验收执行 `npm install` 与 `npx playwright install chromium`。
- 本地默认账号和中间件口令仅用于隔离的学习环境，不得暴露到公网。

## 最短启动与验收

```bash
# 基础设施与应用
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d

# 首次准备浏览器测试
npm install
npx playwright install chromium

# 已存在数据卷：非破坏性完整验收
./scripts/acceptance.sh --reuse

# 单独复核默认安全负向路径
./scripts/smoke-security.sh
```

`acceptance.sh` 默认不会替你启动容器；需要脚本启动时加 `--start-stack`。全新卷验收会删除数据，必须显式确认：

```bash
./scripts/acceptance.sh --fresh --confirm-destroy-volumes --start-stack
```

secure overlay 需要先提供非默认 `JWT_SECRET`、内部 RPC/管理/XXL 令牌、Grafana 与演示用户凭据，再执行：

```bash
./scripts/acceptance.sh --secure --start-stack
```

## 当前证据

| 项目 | 2026-07-11 结果 |
| --- | --- |
| `mvn -B verify -DfailIfNoTests=false` | 23 个 reactor 模块通过 |
| Mapper/DDL、运行时安全、Prometheus 配置 | 通过；运行时安全门禁 100/100 |
| `acceptance.sh --reuse --skip-build` | 通过，80 秒 |
| 后端契约与 smoke | HTTP 契约全过；microservices smoke 全过；API smoke 全过 |
| 抽奖发奖 | 兑换扣 5 积分 → 策略 10007 固定奖品 101 → outbox 派发 → 账户入账 5，通过 |
| Chatbot 补偿 | 立即退款与 `ChatRefundReconcileJob` 补偿均通过 |
| Playwright | 18 条测试连续两轮通过 |
| 默认安全负向 smoke | 注销吊销 JWT、伪造内部 token、未授权 admin 均被拒绝 |

失败取证默认写入 `target/acceptance-artifacts/`。复用验收只能证明当前旧卷可迁移并可运行，不能替代 fresh 验收。

## 拓扑调整说明（2026-07-17）

- 本工作树已将默认应用拓扑收敛为 7 服务（8080-8086）。
- `fulfillment/rebate/strategy` 仍保留源码、RPC 契约与独立启动能力，但不纳入默认 compose 与默认验收健康前提。
- 2026-07-11 审计文档中的历史证据保持不变；若需宣称本工作树“默认拓扑已验证”，应按当前配置重新执行验收并留存新证据。

## 学习顺序

1. 先读 [`MICROSERVICES.md`](MICROSERVICES.md)，画出默认拓扑和同步/异步边界。
2. 跟踪登录：gateway → auth → JWT → Redis 吊销。
3. 跟踪抽奖：`RaffleActivityController` → `RaffleApplicationService` → activity/strategy/award repository。
4. 跟踪发奖：`SendAwardConsumer` → `AwardService` → `credit_award_task` → `DispatchCreditAwardTaskJob` → account RPC。
5. 跟踪失败路径：[`data-and-outbox.md`](data-and-outbox.md) 中的幂等键、UNKNOWN、补偿与 DLQ。
6. 按 [`learning/00-learning-guide.md`](learning/00-learning-guide.md) 和 [`learning/16-local-setup.md`](learning/16-local-setup.md) 动手验证。

## 冻结后约束

- 不为“更像微服务”而拆物理库、重写共享 kernel 或迁移框架。
- 不改变积分、额度、发奖、返利、SKU/奖品库存的业务幂等键。
- 不让 market 扫描 `trigger.job` / `trigger.listener`；消息消费与 XXL 归 message-job。
- 不把静态 validator、健康端点或 `award_state=completed` 单独当作业务闭环证据。
- 不把默认开发凭据、secure 配置文件存在、一次本地通过描述成生产安全。
- 不以历史 BM 编号、归档文档或旧提交的 PASS 代替目标工作树的重跑证据。
- 冻结后只接受明确缺陷、依赖安全修复或学习文档事实错误；大规模重构另立目标。

## 已知边界

- fresh 空卷、完整 secure overlay、8087/8088/8089 独立部署未在本次审计动态验证。
- 默认拓扑仍共享物理 MySQL，DAO 归属主要靠文档和部分 ArchUnit 规则；Mapper XML 有多份启动器副本。
- account/fulfillment/rebate/strategy 的完整 Spring Context 与失败注入覆盖弱于 market/message-job。
- Java 8 / Spring Boot 2.7 属于学习基线；未完成本轮全量 CVE/SBOM 审计。
- 前端 JWT 存在 `localStorage`，默认凭据、宽松 RPC 和关闭限流仅适用于本地隔离环境。
- 没有生产灰度、容量、HA、灾备或真实外部 OpenAI 奖品履约证明。
