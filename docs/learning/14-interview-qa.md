# 14 面试高频问题 Q&A

本文覆盖面试官针对本项目最常问的问题，答案直接对应代码。

---

## 项目介绍类

### Q1：简单介绍一下你的项目

**参考回答：**

Big Market 是一个营销抽奖平台，采用 Spring Boot 2.7.12 + DDD 四层架构，拆分为 10 个微服务。核心功能包括：用户登录鉴权、活动抽奖、积分体系（签到/兑换/消费）、奖品异步发放、AI Chat 集成。

技术亮点：
- **抽奖策略**：责任链（前置规则过滤）+ 规则树（后置奖品过滤）两阶段设计
- **高并发**：Redis 原子操作做库存控制，分库分表（2库×4表）按 userId 路由
- **数据一致性**：Outbox 模式（task 表 + MQ + XXL-Job 补偿）保证消息可靠投递
- **服务间通信**：Dubbo RPC + Nacos 注册中心

---

### Q2：你的项目用了 DDD，能解释一下你的分层吗？

**参考回答：**

项目分四层：
1. **trigger 层**（`big-market-trigger`）：接收 HTTP 请求、MQ 消息、定时 Job，做参数校验和鉴权，不含业务逻辑。
2. **application 层**（`domain/.../application`）：编排领域服务，`RaffleApplicationService.executeDraw()` 串联了"参与活动→策略抽奖→写中奖记录"三步。
3. **domain 层**（`big-market-domain`）：核心业务规则，包含聚合根、实体、值对象、领域服务。通过 port 接口与外部交互，不依赖任何框架实现。
4. **infrastructure 层**（`big-market-infrastructure`）：实现 domain 定义的 port/repository 接口，包含 MyBatis、Redis、MQ 发布等。

关键设计：domain 层只依赖自己定义的接口，不 import infrastructure 包，基础设施可以替换而不改业务逻辑。

---

## 抽奖策略类

### Q3：抽奖策略是怎么实现的？

**参考回答：**

抽奖分两阶段：

**第一阶段（责任链）：** 决定抽哪个奖品 ID。
- 黑名单节点（`rule_blacklist`）：检查用户是否在黑名单，是则直接返回兜底奖品。
- 权重节点（`rule_weight`）：根据用户累计消耗积分，匹配对应的奖品子集，在子集内抽取。
- 默认节点（`rule_default`）：从预装配的 10000 格概率表中随机取位置，返回奖品 ID。

**第二阶段（规则树）：** 对抽出的奖品做"能不能给"的过滤。
- 次数锁节点（`rule_lock`）：用户今日抽奖次数未达解锁门槛，换成兜底奖品。
- 库存节点（`rule_stock`）：Redis DECR 扣减库存，库存不足换兜底奖品。
- 兜底节点（`rule_luck_award`）：返回配置的兜底奖品（通常是随机积分）。

两阶段的入口是 `AbstractRaffleStrategy.performRaffle()`，责任链走到 `rule_default` 节点时才进入规则树，黑名单和权重命中时直接跳过规则树返回。

---

### Q4：规则链和规则树的区别是什么？为什么要两层？

**参考回答：**

两者解决的问题不同：
- **责任链**：解决"抽什么"的问题，是前置规则，节点间是线性的，后一个节点只在前一个放行时才执行。
- **规则树**：解决"能不能给"的问题，是后置规则，节点间有条件分支（ALLOW/TAKE_OVER 决定走哪条边），能表达"满足 A 且满足 B"这样的复合条件。

分两层的原因：前置规则（黑名单、权重）和后置规则（次数锁、库存）的业务语义不同，放在一层处理会混淆"决策"和"验证"的职责，两层分开后扩展新规则时互不影响。

---

### Q5：概率表是怎么实现的？为什么要做预热？

**参考回答：**

`StrategyArmoryDispatch.assembleLotteryStrategy()` 在预热时：
1. 从数据库读取策略下所有奖品及概率（如一等奖 1%、二等奖 5%、三等奖 94%）。
2. 将概率乘以 10000 展开成整数权重，生成 10000 格数组（一等奖占 100 格，二等奖占 500 格…）。
3. Fisher-Yates 洗牌算法打乱顺序，存入 Redis（Key: `strategy_award_count_{strategyId}`）。

抽奖时 `RandomUtil.nextInt(10000)` 取随机下标，O(1) 取到奖品 ID。

预热的原因：将概率计算前置，每次抽奖只做一次 Redis get，避免运行时重复计算；同时 Redis 命中率远高于数据库查询。

---

## 数据一致性类

### Q6：你是如何保证消息可靠投递的？

**参考回答：**

使用 Outbox 模式：

1. 业务数据写入（如 `user_award_record`）和 task 记录（含消息内容）在**同一本地事务**内完成，要么都成功要么都失败。
2. 事务提交后，`RabbitMQ EventPublisher` 异步发送消息。
3. 发送成功 → 更新 task.state = completed。
4. 发送失败 → task.state 保持 create，`SendMessageTaskJob` 定时扫描（每秒），补偿重发。

这样即使 MQ 短暂宕机，消息也不会丢失，只是延迟投递。消息消费者做幂等处理（唯一索引 + 捕获 DuplicateKey），保证重复消费无副作用。

**代码位置：** `AwardRepository.saveUserAwardRecord()`、`SendMessageTaskJob.java`

---

### Q7：如何保证积分扣减不会超扣（余额不足时）？

**参考回答：**

`CreditRepository.saveUserCreditTradeOrder()` 在更新积分账户时使用条件 SQL：

```sql
UPDATE user_credit_account
SET available_amount = available_amount + #{adjustAmount}
WHERE user_id = #{userId}
  AND available_amount + #{adjustAmount} >= 0
```

`adjustAmount` 对于扣减操作是负数，`available_amount + adjustAmount >= 0` 确保余额不会变负。如果积分不足，UPDATE 影响行数为 0，业务层抛出余额不足异常。配合 Redisson 分布式锁（以 userId 为 key）防止并发多次扣减。

---

### Q8：幂等是怎么实现的？

**参考回答：**

项目中所有写操作都有幂等设计，统一通过 `outBusinessNo` + 数据库唯一索引实现：

| 操作 | outBusinessNo 规则 | 唯一索引 |
|------|-------------------|---------|
| 日历签到 | `userId + yyyyMMdd` | `user_behavior_rebate_order(user_id, out_business_no)` |
| AI Chat 扣费 | `"chat_" + requestId` | `user_credit_order(out_business_no)` |
| AI Chat 退款 | `"chat_refund_" + requestId` | 同上 |
| 积分兑换 SKU | `userId + sku + date + millis` | `raffle_activity_order(out_business_no)` |

捕获到 `DuplicateKeyException`（错误码 `INDEX_DUP`）则认为已处理，直接返回成功。

---

## 高并发类

### Q9：分库分表是怎么实现的？路由规则是什么？

**参考回答：**

自研 `big-market-starter-db-router` 组件，基于 MyBatis 插件 + Spring AOP + ThreadLocal 实现。

路由算法（`HashDBRouterStrategy`）：
```java
int hash = userId.hashCode() & Integer.MAX_VALUE;  // 正整数 hash
int dbIdx = hash % dbCount + 1;    // 库号（1 或 2）
int tbIdx = (hash / dbCount) % tbCount;  // 表号（0~3）
```

2 库 × 4 表共 8 个分片，同一 userId 永远路由到同一分片，保证同一用户的所有操作在同一数据库内可以用本地事务，无需分布式事务。

Mapper 上标注 `@DBRouter(key = "userId")` 或 `@DBRouterStrategy(splitTable = true)` 触发路由。

---

### Q10：高并发抽奖场景下，如何防止库存超卖？

**参考回答：**

两级防护：
1. **Redis 原子操作（`DECR`）：** `RuleStockLogicTreeNode` 中调用 `strategyDispatch.subtractionAwardStock()`，Redis DECR 操作是单线程原子执行，返回值 >= 0 才认为扣减成功，不会出现超卖。
2. **异步同步 MySQL：** 扣减成功后写入 Redis 延迟队列，`UpdateAwardStockJob` 异步读队列批量更新 MySQL 的 `stock_count_surplus`，减少 MySQL 写压力。

---

## 微服务类

### Q11：你的微服务是怎么拆分的？拆分原则是什么？

**参考回答：**

按业务领域和数据所有权拆分，共 10 个服务：

| 服务 | 职责 | 拆分原因 |
|------|------|---------|
| gateway | 路由、熔断 | 统一入口，独立扩缩容 |
| auth-service | JWT 鉴权 | 安全职责独立 |
| market-service | 抽奖核心 | 最核心业务域 |
| account-service | 积分/额度 | 数据所有权独立，避免多服务写同一账户表 |
| fulfillment-service | 发奖 | 对接外部（OpenAI 等），独立隔离 |
| rebate-service | 返利 | 独立的返利业务域 |
| strategy-service | 策略读 | 读密集型，可独立扩容 |
| chatbot-service | AI Chat | 外部 API 依赖独立隔离 |
| message-job-service | MQ/Job | 异步任务独立，不影响同步链路 |
| admin-service | 管理配置 | 管理面与用户面隔离 |

---

### Q12：Dubbo 服务如何注册和发现的？

**参考回答：**

使用 Nacos 作为注册中心。服务提供方启动时，Dubbo 自动向 Nacos 注册服务（接口名 + 版本 + 地址）。消费方通过 `@DubboReference(version = "1.0")` 声明依赖，运行时从 Nacos 拉取提供方地址列表，按负载均衡策略（默认随机）选择一个调用。

本项目中所有 `@DubboReference` 设置了 `check = false`，允许提供方未启动时消费方也能正常启动（适合本地开发分步启动）。

---

## 技术选型类

### Q13：你用了 RabbitMQ，为什么不用 Kafka？

**参考回答：**

本项目的 MQ 场景是**可靠的事件通知**（奖品发放、返利到账、积分调整），消息量不大（与用户抽奖行为量级相关），对顺序性要求不严格，更关注消息确认和死信处理。RabbitMQ 的 ACK 机制、死信队列（DLQ）、延迟队列开箱即用，运维成本低。Kafka 更适合日志流、大吞吐量的流处理场景。

---

### Q14：Redisson 在项目里是怎么用的？

**参考回答：**

三个用途：
1. **分布式锁：** `CreditRepository` 中用 `RLock` 防止同一用户并发积分操作（key 为 `userId`）。
2. **Redis 操作封装：** `RedissonService` 提供 `getValue`/`setValue`/`addToMap`/`getFromMap` 等封装，统一管理 Redis 操作。
3. **计数器：** 活动 SKU 库存、策略奖品概率表均通过 Redisson 管理的 Redis 结构存储。

---

### Q15：XXL-Job 在项目里是怎么用的？

**参考回答：**

四个 Job：
1. **`SendMessageTaskJob`：** 扫描 `task` 表中 state=create 的记录，补偿重发 MQ（outbox 兜底）。
2. **`UpdateAwardStockJob`：** 消费 Redis 延迟队列，将奖品库存扣减同步到 MySQL。
3. **`UpdateActivitySkuStockJob`：** 同步 SKU 库存到 MySQL（类似 UpdateAwardStockJob）。
4. **`DispatchCreditAwardTaskJob`**（在 `big-market-message-job-service`）：处理积分发放任务，扫描待处理的 credit award task 行。

---

## 安全类

### Q16：你的鉴权是怎么实现的？

**参考回答：**

三种鉴权机制：

1. **用户 JWT 鉴权：** `TokenAuthInterceptor` 拦截所有 `*_by_token` 接口，从 `Authorization` 头解析 JWT，验签并提取 `userId` 写入 request attribute。控制器从 attribute 取 `userId`，不信任请求体中的 userId，防止身份伪造。

2. **管理员鉴权：** ERP 和 DCC 接口支持两种方式：`X-Admin-Token` 静态 token 比对（配置在 yml 中），或 JWT 中的 `openId` 在 `app.admin.user-ids` 白名单内。

3. **JWT 注销：** `logout` 提取 JWT 的 `jti`，写入共享的 `ITokenRevocationService`：
   - **本地 `mvn spring-boot:run`（默认）：** 各进程使用 `InMemoryTokenRevocationService`，注销**不跨服务**同步。
   - **Docker 栈（`TOKEN_REVOCATION_REDIS_ENABLED=true`）：** auth / market / admin 共用 Redis 黑名单（`RedisTokenRevocationService`），`AuthService.checkToken()` 在各服务统一校验 `jti`。
   - **安全策略：** Redis 模式开启但拿不到 `RedissonClient` 时 **fail-fast 启动失败**（不会静默退回内存黑名单）；Redis 写入注销失败时 `logout` **返回错误**（不假装成功）；Redis 读取失败时 **fail-closed**（拒绝 token）。
   - `JwtTokenUtils` 支持 `Authorization: Bearer <jwt>` 格式。

---

### Q17：接口有限流吗？是怎么实现的？

**参考回答：**

有，通过 `big-market-starter-ratelimiter` 自研限流组件实现，基于 Guava `RateLimiter`。

在 Controller 方法上标注（业务代码中已有类似结构）：
- `key`：以 userId 为维度限流（每个用户独立计数）
- `permitsPerSecond`：每秒允许的请求数
- `blacklistCount`：超过该次数被限流后，将用户加入黑名单 24 小时
- `fallbackMethod`：触发限流时调用的降级方法

---

### Q18：DCC 动态配置是怎么实现的？

**参考回答：**

DCC（Dynamic Configuration Center）使用 ZooKeeper 实现。`DCCController.updateConfig()` 向 ZooKeeper 的 `/big-market-dcc/config/{key}` 节点写入新值。应用启动时，`@DCCValue("degradeSwitch:close")` 注解的字段会监听对应 ZK 节点，节点值变更时自动更新字段值（反射注入），无需重启服务。

`degradeSwitch` 是典型应用：紧急情况下通过 DCC 将其设为 `open`，`RaffleActivityController.draw()` 会立即返回熔断响应，关闭所有抽奖请求。

---

## 项目深度类

### Q19：聚合根的作用是什么？举个例子。

**参考回答：**

聚合根是 DDD 中保证数据一致性的边界。以 `CreatePartakeOrderAggregate` 为例：

用户参与一次抽奖需要：
- 扣减 `raffle_activity_account`（总账户 surplus - 1）
- 扣减 `raffle_activity_account_month`（月账户）
- 扣减 `raffle_activity_account_day`（日账户）
- 插入 `user_raffle_order`（抽奖单）

这四步必须在同一事务内完成，任何一步失败都要全部回滚。聚合根 `CreatePartakeOrderAggregate` 将这四个对象封装成一个整体，`ActivityRepository.saveCreatePartakeOrderAggregate()` 接收整体后在单个 `@Transactional` 方法内完成所有操作，外部调用方无需关心事务细节。

---

### Q20：如果让你改进这个项目，你会从哪里入手？

**参考回答（展示技术深度）：**

1. **用户体系：** 当前 `app.auth.dev-users` 是配置文件中的固定账号，生产应接入真实用户数据库或 OAuth2。
2. **分布式追踪：** 已有 `traceId`，但只靠日志。可接入 Zipkin/Skywalking，实现跨服务的可视化链路追踪。
3. **策略配置热更新：** 当前奖品概率表修改后需重新调用 `armory` 接口刷新缓存，可引入配置变更事件自动重装配。
4. **OpenAI 发奖的熔断：** `OpenAIAccountAdjustQuotaAward` 依赖外部 API，目前只有任务补偿兜底，可加 Resilience4j 断路器，快速失败后进 task 重试。
5. **ES 数据同步：** 当前通过 Canal 监听 MySQL binlog 同步到 ES（`docs/dev-ops/canal-adapter` 配置），可监控 Canal 消费延迟避免查询到过期数据。
