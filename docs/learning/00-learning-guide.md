# 00 学习路径总览

## 项目定位

Big Market 是一个已完成的本地微服务学习项目，模拟营销抽奖平台。涵盖登录鉴权、网关路由、活动额度、积分、抽奖策略、奖品发放、返利、MQ 补偿、XXL-Job 任务和本地可观测性。

用户端前端为 `big-market-web`：原生 HTML/CSS/JS（非 React），通过网关调用后端 API；活动 ID 由 `query_stage_activity_id` 动态解析，展示文案与 Chatbot 开关由 `GET /api/v1/admin/config/public/display` 拉取。

建议先读这些文件：

- `docs/MICROSERVICES.md`
- `docs/learning/16-local-setup.md`（本地启动）
- `docker-compose.yml`
- `docs/dev-ops/docker-compose-environment.yml`
- `big-market-gateway/src/main/resources/application.yml`
- `big-market-web/app.js`（用户端入口与 API 编排）
- `big-market-trigger/src/main/java/com/dyx/market/trigger/http/RaffleActivityController.java`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/application/RaffleApplicationService.java`
- `big-market-admin-service/src/main/java/com/dyx/market/admin/AdminConfigController.java`（含公开展示配置 API）
- `big-market-message-job-service/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java`

## 十步学习路径

1. **整体架构**：读 `docs/MICROSERVICES.md` 和 `docs/learning/03-architecture-overview.md`。
2. **请求链路**：从网关路由追踪到 auth、market、admin、chatbot 服务；对照 `01-url-request-flows.md` 与 `big-market-web` 页面。
3. **领域模型**：阅读 `big-market-domain/src/main/java/com/dyx/market/domain` 下的 activity、strategy、award、credit、rebate、auth、task 包。
4. **服务边界**：对照 `pom.xml` 中的服务启动模块与共享库，参考 `04-module-or-service-boundaries.md`。
5. **数据与任务**：先读 `docs/learning/15-data-model.md`，再读 `docs/data-and-outbox.md` 及各服务的 MyBatis mapper。
6. **MQ / XXL-Job**：读 `big-market-trigger` 下的 RabbitMQ 监听器，以及 `big-market-message-job-service` 下的 Job 处理器。
7. **幂等与一致性**：检查 SQL 唯一业务键和 repository 中的幂等处理。
8. **降级与回滚**：检查网关 fallback、额度回滚、积分退款、任务重试和 DLQ 日志；详见 `07-failure-degradation-and-resilience.md`。
9. **监控与排查**：读 `docs/operations-checklist.md` 和 `10-troubleshooting.md`。
10. **代码地图**：用 `09-code-map.md` 作为跳转表。

运营查询（ERP）涉及 Canal/ES 同步时，补充阅读 `17-canal-es-sync.md`。

## 本地完成标准

学习环境视为完成，需满足：

| 领域 | 验收项 |
| --- | --- |
| 构建 | `mvn clean package -DskipTests` 成功 |
| 网关 | `/api/v1/auth/**`、`/admin/**`、`/chatbot/**`、`/raffle/**` 均经 gateway 路由 |
| 鉴权 | 登录、校验、注销、JWT 过期与撤销路径可解释 |
| 抽奖 | 抽奖链路覆盖额度扣减、策略决策、中奖记录、MQ 任务 |
| 积分 | 签到、兑换、Chatbot 扣退、奖品积分发放路径可解释 |
| 返利 | 返利订单、task、MQ 消费、幂等读取可解释 |
| 任务 | task 重试、库存 Job、credit-award outbox 派发可解释 |
| 前端 | `big-market-web` 可登录、抽奖、签到、兑换、Chatbot；展示配置与 Chatbot 开关生效 |
| 监控 | Actuator、Prometheus、Grafana、traceId、日志齐全 |
| 回滚 | 网关降级、额度回滚、积分退款、任务重试有文档支撑 |
| 文档 | `docs/MICROSERVICES.md`、`docs/learning/*` 与代码路径一致 |

验证命令：

```bash
mvn clean package -DskipTests
./scripts/validate-microservices-runtime-safety.sh
./scripts/validate-microservices-stack.sh
./scripts/smoke-api.sh
```

`validate-microservices-runtime-safety.sh` 无需 Docker 即可校验架构护栏。

本项目是学习作品集，不包含真实生产灰度观察期。归档的英文维护文档见 `archive/`。
