# 06 基础设施组件

## DB 分片路由中间件

**模块**：`big-market-starter-db-router`

### 为什么需要分库分表

单表 user_raffle_order 在亿级用户场景下会达到数千万行，单表查询慢、写入瓶颈、锁竞争严重。

### 路由策略

```
分库：big_market_01 / big_market_02
分表：表名后缀 _000 / _001 / _002 / _003

路由键（shardingKey）：通常是 userId 或 activityId
路由算法：
  tableIndex = hash(shardingKey) % (dbCount * tableCount)
  dbIndex    = tableIndex / tableCount    → 决定连哪个库
  tbIndex    = tableIndex % tableCount    → 决定查哪张表

例：userId="abc123"，hash % 8 = 5
  dbIndex = 5/4 = 1 → big_market_02
  tbIndex = 5%4 = 1 → user_raffle_order_001
```

### 实现机制

```java
// 1. 注解标记（方法或类级别）
@DBRouter(key = "userId")  // 指定路由键
public UserRaffleOrder queryRaffleOrder(String userId, ...) { ... }

// 2. AOP 拦截
@Aspect
public class DBRouterJoinPoint {
    @Around("@annotation(dbRouter)")
    public Object doRouter(ProceedingJoinPoint jp, DBRouter dbRouter) {
        String routerKey = getRouterKey(jp, dbRouter.key());
        int routerKey = routerKey.hashCode() & (dbCount * tableCount - 1);
        DBContextHolder.setDBKey(routerKey / tableCount);
        DBContextHolder.setTBKey(routerKey % tableCount);
        return jp.proceed();
    }
}

// 3. 动态数据源（DynamicRoutingDataSource）
// Spring 的 AbstractRoutingDataSource，根据 ThreadLocal 中的 key 选择数据源

// 4. 动态表名（MyBatis 插件 DynamicTableNamePlugin）
// 拦截 SQL，把 user_raffle_order 替换为 user_raffle_order_001
```

### 哪些表分片，哪些不分片

```
分片（按 userId 路由）：
  user_raffle_order          → 用户抽奖订单
  user_credit_account        → 用户积分账户
  user_credit_order          → 积分流水
  user_behavior_rebate_order → 返利订单
  user_award_record          → 中奖记录
  raffle_activity_account    → 活动账户
  task                       → Outbox 任务

不分片（配置类，数量少）：
  strategy, strategy_award, strategy_rule
  raffle_activity, raffle_activity_count
  daily_behavior_rebate, award
```

---

## Redis 缓存策略

**客户端**：Redisson（封装在 `IRedisService` 中）

### 缓存分类与 Key 格式

#### 1. 策略概率表（核心热点）

```
Key: strategy:rate_table:{strategyId}          Hash
Value: {index -> awardId}（O(1)算法平铺表）

Key: strategy:rate_range:{strategyId}          String
Value: rateRange 总数（用于取模计算 index）

Key: strategy:award:count:{strategyId}:{awardId}  String
Value: 奖品库存数量（Integer）
```

**生命周期**：活动有效期内永久有效（手动删除或服务重启时重新装配）。

#### 2. 活动 SKU 库存

```
Key: activity:sku:stock:{sku}   String
Value: 剩余库存数（Integer）

扣减：Redis DECR → 原子操作，无竞争
同步：UpdateActivitySkuStockJob 定期同步到 DB
```

#### 3. 奖品延迟同步队列

```
Key: strategy:award:stock:queue:{strategyId}:{awardId}  Delayed Queue
Value: 消费记录（用于驱动 DB 更新）
```

#### 4. 分布式锁

```
Key: activity:account:lock:{userId}:{activityId}       → 参与活动锁
Key: user:credit:account:lock:{userId}:{outBusinessNo} → 积分操作锁
Key: activity:account:update:lock:{userId}:{outNo}     → 订单更新锁
Key: send:message:task:lock:{db}                       → Outbox 任务发送锁
```

#### 5. 序列化种子

```
Key: strategy:seed:{strategyId}  String (自增)
每次抽奖时 INCR，对 rateRange 取模得到 index
```

### 缓存一致性

```
库存数据（奖品/SKU）：
  读：优先 Redis
  写：先写 Redis（原子扣减），再异步同步 DB
  风险：Redis 宕机或重启 → 库存数据丢失
  保障：服务重启时 assembleStrategy 会重新从 DB 加载库存到 Redis
  
概率表数据：
  读：只读 Redis
  写：活动上线时一次性写入
  风险：概率表错误 → 所有抽奖结果错误
  保障：策略装配接口有手动触发入口，可重新装配
```

---

## RabbitMQ 消息设计

### 消息格式

所有消息都封装在 `EventMessage<T>` 中：

```json
{
  "id": "uuid",
  "timestamp": "2026-06-13T10:00:00",
  "data": {
    // 具体消息体，泛型 T
  }
}
```

### 主题清单

| Topic | 发布方 | 消费方 | 消息体 |
|-------|--------|--------|--------|
| `send_award` | Outbox Job | SendAwardConsumer | SendAwardMessage (userId, orderId, awardId, awardConfig) |
| `send_rebate` | Outbox Job | RebateMessageConsumer | RebateMessage (userId, rebateType, rebateConfig, bizId) |
| `credit_adjust_success` | Outbox Job | CreditAdjustSuccessConsumer | CreditAdjustSuccessMessage |
| `activity_sku_stock_zero` | 库存扣减时 | ActivitySkuStockZeroConsumer | ActivitySkuStockZeroMessage (activityId, sku) |

### 消费端幂等

所有消费者处理重复消息的标准代码：

```java
try {
    distributeAward(distributeAwardEntity);
} catch (DuplicateKeyException e) {
    log.warn("幂等拦截，消息已处理 orderId:{}", message.getOrderId());
    // 不 ack 失败，视为成功
}
```

### 死信队列（DLX）

消费失败的消息进入死信队列，不影响主队列，供人工介入排查。

---

## XXL-Job 定时任务

| Job 方法 | 触发频率 | 功能 |
|---------|---------|------|
| `SendMessageTaskJob.exec_db01()` | 5 秒 | 扫描 db01 shard 的 pending task，发 MQ |
| `SendMessageTaskJob.exec_db02()` | 5 秒 | 扫描 db02 shard 的 pending task，发 MQ |
| `UpdateAwardStockJob` | 按需 | 消费奖品库存延迟队列，批量更新 DB |
| `UpdateActivitySkuStockJob` | 按需 | 消费 SKU 库存延迟队列，批量更新 DB |

**每个 Job 方法对应一个 DB shard**：因为 task 表分库，每个 shard 的 Job 独立持锁、独立扫描，互不干扰，可并行提升吞吐。

---

## 动态配置中心（DCC）

**模块**：`big-market-starter-dcc`

### 使用方式

```java
@DCCValue("strategy.service.remote-read.enabled:false")
private boolean remoteReadEnabled;
// 冒号后面是默认值
// Nacos 中修改该配置 → Spring 上下文自动刷新
```

### 配合特性开关

```
代码路径：
  if (remoteReadEnabled) {
      return strategyRemoteReadPort.queryXxx();  // Dubbo 远程
  }
  return localStrategyReadAdapter.queryXxx();    // 本地
```

---

## Elasticsearch 集成

**模块**：`big-market-queries`（读模型）

```
写入：Canal Adapter 同步 MySQL binlog → ES
读取：IElasticSearchUserRaffleOrderDao.queryXxx()
用途：用户中奖记录的快速全文检索、聚合统计
```

---

## 认证与鉴权

**模块**：`big-market-auth-service` + `big-market-auth-access`

```
登录：POST /api/v1/login
  → 校验用户名/密码
  → 签发 JWT（userId 写入 payload）
  → 返回 token

请求鉴权（Gateway 拦截）：
  → 从 Authorization Header 提取 Bearer token
  → JWT 签名验证
  → 解析出 userId，注入下游请求 Header

Controller 取 userId：
  @RequestHeader("userId") String userId
  → 不信任前端传参，从 token 解析
```

---

## Prometheus + Grafana 监控

**采集点**：Micrometer + `@PrometheusConfiguration`

常用指标：

```
raffle_request_total           → 抽奖请求总量
raffle_request_duration_seconds → 抽奖响应时间 P99
award_dispatch_success_total   → 发奖成功数
task_pending_count             → Outbox 待发任务数（告警：积压过高）
redis_stock_remaining          → 奖品库存剩余（告警：接近 0）
```

**TraceId 追踪**：

```java
// TraceIdFilter（每个微服务都有）
@Component
public class TraceIdFilter implements Filter {
    public void doFilter(ServletRequest req, ...) {
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        // 透传给下游
        ((HttpServletResponse) resp).setHeader("X-Trace-Id", traceId);
        chain.doFilter(req, resp);
    }
}
```

日志格式中包含 traceId，可以在 Grafana Loki 中跨服务追踪一次完整请求链路。
