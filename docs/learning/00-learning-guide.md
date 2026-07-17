# 00 学习路径总览

> 文档索引与分阶段顺序见 [README.md](README.md)。就绪边界见 [`../LEARNING-FREEZE.md`](../LEARNING-FREEZE.md)；服务拓扑见 [`../MICROSERVICES.md`](../MICROSERVICES.md)。

## 十步学习路径

1. **整体架构**：[`../MICROSERVICES.md`](../MICROSERVICES.md) + [03-architecture-overview.md](03-architecture-overview.md)。
2. **请求链路**：网关 → auth / market / admin / chatbot；对照 [01-url-request-flows.md](01-url-request-flows.md) 与 `big-market-web`。
3. **领域模型**：`big-market-domain/.../domain` 下 activity、strategy、award、credit、rebate、auth、task。
4. **服务边界**：`pom.xml` + [04-module-or-service-boundaries.md](04-module-or-service-boundaries.md)（注意 market / message-job 扫描拆分）。
5. **数据与任务**：先 [15-data-model.md](15-data-model.md)，再 [`../data-and-outbox.md`](../data-and-outbox.md)。
6. **MQ / XXL-Job**：源码在 `big-market-trigger` 的 `listener`/`job`，**运行时只在 message-job**；Job 配置见 `big-market-message-job-service`；跳转表 [09-code-map.md](09-code-map.md)。
7. **幂等与一致性**：SQL 唯一业务键 + repository 幂等；权威说明在 `data-and-outbox.md`。
8. **降级与回滚**：[07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md)。
9. **监控与排查**：[`../operations-checklist.md`](../operations-checklist.md)、[10-troubleshooting.md](10-troubleshooting.md)。
10. **代码地图**：[09-code-map.md](09-code-map.md)。

运营查询（ERP）涉及 Canal/ES 时补 [17-canal-es-sync.md](17-canal-es-sync.md)。

## 建议先打开的代码入口

- `big-market-web/app.js`
- `big-market-trigger/.../http/RaffleActivityController.java`
- `big-market-domain/.../activity/application/RaffleApplicationService.java`
- `big-market-message-job-service/.../config/DispatchCreditAwardTaskJob.java`
- `big-market-admin-service/.../AdminConfigController.java`（含 `public/display`）

## 本地完成标准

默认学习路径是否完成，以 `acceptance.sh` 动态证据为准（勿把“代码存在”当成“运行已证实”）：

| 领域 | 验收项 |
| --- | --- |
| 构建 | `mvn clean package -DskipTests` 成功 |
| 网关 | `/api/v1/auth/**`、`/admin/**`、`/chatbot/**`、`/raffle/**` 均经 gateway |
| 鉴权 | 登录、校验、注销、JWT 过期与撤销可解释 |
| 抽奖 | 兑换扣积分、策略决策、中奖记录、MQ/outbox、账户入账均核对 |
| 积分 | 签到、兑换、Chatbot 扣退、奖品积分发放可解释 |
| 返利 | 返利订单、task、MQ 消费、幂等可读 |
| 任务 | task 重试、库存 Job、credit-award outbox 派发可解释 |
| 前端 | 登录、抽奖、签到、兑换、Chatbot；展示配置与开关生效 |
| 监控 | Actuator、Prometheus、Grafana、traceId、日志齐全 |
| 回滚 | 网关降级、额度回滚、积分退款、任务重试有文档支撑 |

```bash
mvn -B verify -DfailIfNoTests=false
./scripts/acceptance.sh --reuse
./scripts/smoke-security.sh
```

`validate-microservices-runtime-safety.sh` 只校验静态护栏，不能替代 Context、XXL 注册、业务表终态和浏览器测试。当前最终拓扑是 8080-8086 七个应用服务；本工作树的 fresh / secure 尚未重新验证，见 `LEARNING-FREEZE.md`。
