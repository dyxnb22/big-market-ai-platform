# 11 关键设计决策与理由

面试官最常问的不是"你实现了什么"，而是"你为什么这样设计"。本文记录项目中的关键架构决策和背后的理由。

---

## 决策 1：为什么用 Dubbo 而不是 HTTP 做服务间调用？

**选择：** account-service、fulfillment-service、rebate-service、strategy-service 均通过 Dubbo RPC 暴露接口。

**理由：**

1. **接口契约强绑定：** Dubbo 以 Java 接口为契约（`big-market-api` 模块），编译期就能发现接口不匹配，而 HTTP/JSON 只在运行时才报错。
2. **负载均衡和注册中心内置：** Dubbo 与 Nacos 集成，自动服务发现和负载均衡，无需手写服务地址管理。
3. **本项目特点：** 服务均为 Java，内网调用，Dubbo 的序列化效率优于 JSON HTTP。

**代价：** 强耦合于 Java 生态，非 Java 服务无法直接调用 Dubbo 接口。

---

## 决策 2：为什么用 Outbox 模式（task 表 + 异步 MQ），而不是直接发消息？

**选择：** `AwardRepository.saveUserAwardRecord()` 在同一事务内写 `user_award_record` 和 `task`，事务提交后再发 MQ。

**理由：** 分布式系统中数据库写入和 MQ 发送是两个独立操作，无法用单一事务保证原子性：

- 先写库再发 MQ：库已写但 MQ 宕机 → **消息永久丢失**，奖品不发
- 先发 MQ 再写库：消息已发但库失败 → **重复发奖**

Outbox 模式的关键：task 表和业务数据**在同一数据库同一事务**，本地事务保证两者同时成功或失败。MQ 发送失败只影响 task 状态，不影响业务数据，由 `SendMessageTaskJob`（message-job）定时重试补偿。

默认 Docker 学习栈还有**积分二级 Outbox**（`credit_award_task` + `DispatchCreditAwardTaskJob`）。细则只维护一份：[`docs/data-and-outbox.md`](../data-and-outbox.md)。

**代价：** 增加了 task 表和补偿 Job，系统复杂度上升；消息不是严格实时（有毫秒级到秒级延迟）。

---

## 决策 3：为什么用 Redis 做奖品库存扣减，而不是直接更新 MySQL？

**选择：** `RuleStockLogicTreeNode` 调用 `strategyDispatch.subtractionAwardStock()`，底层是 Redis DECR。

**理由：** 高并发场景下（如秒杀活动），大量请求同时扣减 MySQL 库存会产生严重的行锁竞争，导致超时和性能下降。Redis 的 DECR 是原子操作，单线程执行，天然避免超卖，且 QPS 远高于 MySQL。

**配合机制：** Redis 扣减成功后，将 `(strategyId, awardId)` 放入延迟队列，`UpdateAwardStockJob` 异步读取队列，批量更新 MySQL 的 `stock_count_surplus` 字段。MySQL 是最终持久化记录，Redis 是实时控制。

---

## 决策 4：为什么要用分库分表（2库×4表），而不是单库？

**选择：** `big-market-starter-db-router` 按 `userId` 哈希路由到 db01/db02 两个库，每库 4 张分表。

**理由：** 用户抽奖记录、额度账户、积分账户等都以 `userId` 为核心查询条件。单库在用户量增大时会遇到：

- 单表行数过多，B+ 树索引层高，查询变慢
- 单库并发写入形成瓶颈

分库分表后，同一用户的所有数据路由到同一库同一表，保证**同用户操作在同一数据库内可以用本地事务**，不引入分布式事务。

**路由算法（`HashDBRouterStrategy`）：**

```java
int hash = routeKey.hashCode() & Integer.MAX_VALUE;  // 取正整数
int dbIdx = hash % dbCount + 1;                       // 库号：1 或 2
int tbIdx = (hash / dbCount) % tbCount;               // 表号：0~3
```

**限制：** 路由键确定后不可改变（否则历史数据找不到）；跨用户聚合查询（如"活动总参与人数"）需要走 ES 或另建统计表。

---

## 决策 5：为什么 rebate-service 和 strategy-service 默认 embedded 在 market-service 内？

**选择：** `RaffleStrategyServiceRPC`、`RebateServiceRPC`、`RaffleActivityServiceRPC`、`ErpOperateServiceRPC` 在 `big-market-trigger` 中，`@ConditionalOnProperty(matchIfMissing = true)` 表示默认激活，嵌入 market-service 进程内运行。HTTP Controller **不再**直接标注 `@DubboService`，RPC 与 HTTP 分离注册。

**理由：**

1. **学习环境简化：** 不需要启动 10 个进程，减少本地资源消耗。
2. **渐进式演进：** 业务验证阶段先在单进程内调通，稳定后再剥离为独立服务，降低风险。
3. **Dubbo 的透明性：** 无论是 embedded 还是独立服务，调用方（market-service 的 Consumer）代码完全一致，切换只改配置。

**切换方法：** 设置 `strategy.embedded-rpc-provider.enabled=false` + `strategy.service.remote-read.enabled=true`，再启动独立的 `big-market-strategy-service`，market-service 就会通过 Nacos 发现并 RPC 调用它。

---

## 决策 6：为什么幂等键用 `outBusinessNo` 而不是数据库自增 ID？

**选择：** 所有写操作都携带 `outBusinessNo`（如签到：`yyyyMMdd`，Chat 扣费：`chat_{requestId}`），数据库在该字段上建唯一索引。

**理由：** 自增 ID 是数据库生成的，客户端在重试时不知道上次是否已插入成功。`outBusinessNo` 由业务方生成，具有业务含义（天级别、请求级别），重试时可以携带相同的 key，数据库唯一索引保证插入幂等，捕获到 `INDEX_DUP` 异常则说明已处理，直接返回成功。

---

## 决策 7：为什么抽奖额度回滚使用 CAS 状态机而不是直接回滚 SQL？

**选择：** `RaffleApplicationService.executeDraw()` 异常时调用 `activityRepository.compensatePartakeQuota()`，内部用条件 UPDATE（`WHERE order_state = 'create'`）。

**理由：** 在高并发场景下，同一个 `orderId` 可能被多个线程同时尝试回滚（网络重试、Job 扫描），直接 `UPDATE quota SET surplus = surplus + 1` 会导致重复加回。CAS 写法确保只有当订单处于 `create` 态时才执行回滚，幂等安全。

---

## 决策 8：为什么 Dubbo 无 token 重载要显式拒绝？

**选择：** `RaffleActivityServiceRPC`、`ErpOperateServiceRPC` 对 `draw(request)`、`armory`、`creditPayExchangeSku(request)` 等无鉴权参数的重载调用 `DubboRpcAuthSupport.rejectInternalRpc()`；ERP 带 token 重载则 `requireAdmin()` 后再委托 Controller。

**理由：** HTTP 路径有 `TokenAuthInterceptor` / `OperationalAuthInterceptor` 保护，但 Dubbo 直连会绕过 Servlet 过滤器。若保留无 token 重载并直接调 Controller，内网任意消费者可冒充用户抽奖或运营操作。

**代价：** 旧版仅通过 Dubbo 无 token 调用的集成需要改为 HTTP 网关或携带 token 的 RPC 重载。
