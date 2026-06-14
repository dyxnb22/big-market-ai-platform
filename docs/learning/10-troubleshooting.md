# 10 常见问题与排查

## 排查思路：从外到内

```
Gateway 日志 → 目标 Service 日志 → Domain 日志 → 数据库/Redis 状态
```

每个请求都有 `traceId`（由 `TraceIdGlobalFilter` 注入），日志中用 `traceId` 跨服务串联。

---

## 场景 1：登录失败

**现象：** `/api/v1/auth/login` 返回错误或 401。

**排查路径：**

1. 检查请求参数：`userId` 和 `password` 是否正确。
2. 检查配置：`big-market-auth-service/src/main/resources/application.yml` 中 `app.auth.dev-users` 是否包含该用户。
3. 检查 `AuthAccessController.login()` 日志，确认是"用户不存在"还是"密码错误"。
4. 检查 `DefaultCredentialGuard`：非 dev 环境下若使用默认密码会被拒绝。

---

## 场景 2：抽奖返回"活动不存在"或"活动未开启"

**现象：** `draw_by_token` 返回 `ACTIVITY_STATE_ERROR` 或 `ACTIVITY_DATE_ERROR`。

**排查路径：**

1. 确认 `activityId` 正确。
2. 查 `raffle_activity` 表：`state` 是否为 `open`，`begin_date_time` / `end_date_time` 是否覆盖当前时间。
3. 确认已调用 `armory` 接口完成活动预热（Redis 缓存中是否有活动数据）。
4. 代码：`AbstractRaffleActivityPartake.createOrder()` 第 50、55 行的校验。

---

## 场景 3：抽奖返回"超出日/月/总次数限制"

**现象：** 返回 `ACTIVITY_DATE_ERROR` 类相关额度错误。

**排查路径：**

1. 查 `raffle_activity_account`（总账户）、`raffle_activity_account_day`、`raffle_activity_account_month` 三张表的 `surplus` 字段。
2. 如果 `surplus = 0`，说明额度已用完。
3. 检查 `AbstractRaffleActivityPartake.doFilterAccount()` 的账户查询逻辑。

---

## 场景 4：抽奖成功但奖品一直未发放

**现象：** `user_award_record` 中 `award_state = create`，长时间未变为 `complete`。

**排查路径（outbox 链路）：**

1. 查 `task` 表：找到对应 `orderId` 的 task 行，检查 `state` 是否为 `create`（MQ 未发出）还是 `completed`（已发出）。
2. 若 task state = `create`：检查 RabbitMQ 是否正常，`SendMessageTaskJob` 是否在运行。
3. 若 task state = `completed`：检查 `SendAwardConsumer` 消费日志，确认消费者是否正常处理。
4. 查 MQ 死信队列（DLQ）：`big-market-message-job-service` 中的 `RabbitMQDlqConfig` 配置了 DLQ，死信消息在此积压。

---

## 场景 5：积分签到后积分未到账

**现象：** `calendarSignRebateByToken` 返回成功，但用户积分未增加。

**排查路径：**

1. 查 `user_behavior_rebate_order` 表：确认签到订单已创建。
2. 查 `task` 表（rebate task）：`state` 是否为 `completed`。
3. 检查 `RebateMessageConsumer` 消费日志：`send_rebate` topic 是否被消费。
4. 查 `user_credit_account` 表：确认积分账户是否已创建（首次签到积分账户可能异步创建，`calendarSignRebate()` 中捕获异常返回 0）。

---

## 场景 6：积分兑换 SKU 失败（积分不足）

**现象：** `credit_pay_exchange_sku_by_token` 返回失败。

**排查路径：**

1. 查 `user_credit_account.available_amount`：当前积分余额。
2. 查 `raffle_activity_sku` 表：该 SKU 对应的 `product_amount`（积分价格）。
3. 检查 `RaffleActivityController.creditPayExchangeSku()` 中的库存恢复逻辑：积分扣减失败后是否触发了 `restoreActivitySkuStock()`（Redis `INCR` 恢复）。

---

## 场景 7：Gateway 返回 `code=0007`（服务熔断）

**现象：** 所有请求返回 `{"code":"0007","info":"服务熔断，系统繁忙"}`。

**排查路径：**

1. 检查下游服务是否正常启动（`docker ps` 或 healthcheck）。
2. 查 Gateway 日志：`TraceIdGlobalFilter` 和路由配置中的 circuit breaker 状态。
3. 代码：`big-market-gateway/src/main/java/com/dyx/market/gateway/fallback/FallbackController.java`。
4. 等待熔断窗口恢复（默认配置），或重启下游服务。

---

## 场景 8：MQ 消息积压 / 消费者不消费

**排查路径：**

1. 登录 RabbitMQ 管理界面（默认 `http://127.0.0.1:15672`）查看队列积压数量。
2. 检查消费者 prefetch 配置（`application.yml` 中 `listener.simple.prefetch: 1`）是否过低。
3. 检查 `SendAwardConsumer`、`RebateMessageConsumer`、`CreditAdjustSuccessConsumer` 的异常日志，消费失败会触发重试，最终进 DLQ。
4. 查 `RabbitMQDlqConfig`：DLQ 中积压的消息需手动处理后再重新入队。

---

## 常用验证命令

```bash
# 构建验证
mvn clean package -DskipTests

# 代码结构和配置一致性验证（无需 Docker）
./scripts/validate-microservices-runtime-safety.sh

# 服务健康检查（需要 Docker 环境）
./scripts/validate-microservices-stack.sh

# API 接口冒烟测试（需要完整环境）
./scripts/smoke-api.sh
```
