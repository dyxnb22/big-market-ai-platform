# 01 项目架构总览

## 项目定位

Big Market 是一个**大型营销活动平台**，核心功能是抽奖（Raffle）。
围绕抽奖，衍生出：活动管理、策略配置、积分账户、行为返利、奖品履约五大业务域。

这个项目的学习价值不在于"抽奖"本身，而在于：

- 如何用 DDD 把复杂业务分层、建模
- 如何用设计模式（责任链、决策树、模板方法）解耦可变业务规则
- 如何用 Outbox + MQ 在不引入分布式事务的前提下保证最终一致性
- 如何从单体到微服务做渐进式拆分

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 2.7.12 |
| ORM | MyBatis |
| 数据库 | MySQL 8.x（分库分表） |
| 缓存 | Redis（Redisson） |
| 消息队列 | RabbitMQ |
| 服务发现/配置 | Nacos |
| RPC | Dubbo 3.0.9 |
| 定时任务 | XXL-Job |
| 搜索 | Elasticsearch |
| 监控 | Prometheus + Grafana |
| 网关 | Spring Cloud Gateway |
| 容器 | Docker Compose |

---

## 模块全景图

```
big-market-ai-platform/
│
├── ── 核心库模块（JAR 依赖，不独立部署）──────────────────────────
│   ├── big-market-types          # 公共枚举、异常、响应码
│   ├── big-market-api            # 对外 DTO 和接口定义（供 Dubbo 服务复用）
│   ├── big-market-domain         # 领域层：五大领域的业务逻辑
│   ├── big-market-infrastructure # 基础设施：Repository 实现、DAO、Redis、MQ
│   ├── big-market-trigger        # 触发层：HTTP Controller、XXL-Job、MQ 消费者
│   ├── big-market-queries        # 查询层：读模型（ES 查询）
│   ├── big-market-auth-access    # 鉴权入口：JWT 校验
│   ├── big-market-admin          # 管理端配置 API
│   ├── big-market-management     # 本地持久化配置能力
│   └── big-market-chatbot        # 规则版 Chatbot 领域逻辑
│
├── ── Starter 框架模块（可复用中间件）──────────────────────────────
│   ├── big-market-starter-db-router    # DB 分片路由中间件
│   ├── big-market-starter-dcc          # 动态配置中心（特性开关）
│   └── big-market-starter-ratelimiter  # 限流中间件
│
├── ── Phase 1 微服务（独立可部署）─────────────────────────────────
│   ├── big-market-app                  # 原始单体（本地开发/兼容回退）
│   ├── big-market-gateway        :8080 # API 网关
│   ├── big-market-auth-service   :8081 # 登录 / JWT 签发
│   ├── big-market-admin-service  :8082 # 管理端
│   ├── big-market-market-service :8083 # 核心营销服务（活跃主力）
│   ├── big-market-chatbot-service:8084 # Chatbot 服务
│   ├── big-market-message-job-service:8085 # MQ 消费 + XXL-Job
│   ├── big-market-account-service:8086 # 账户服务（暗启动）
│   ├── big-market-fulfillment-service:8087 # 履约服务（暗启动）
│   ├── big-market-rebate-service :8088 # 返利服务（暗启动）
│   ├── big-market-strategy-service:8089 # 策略服务（暗启动）
│   └── big-market-activity-service:8090 # 活动服务（脚手架）
```

---

## 分层架构

项目采用经典的 **DDD 四层 + Ports & Adapters** 结构：

```
┌─────────────────────────────────────────────────────┐
│  触发层 (Trigger)                                     │
│  HTTP Controller  /  XXL-Job  /  RabbitMQ Consumer   │
└────────────────────────┬────────────────────────────┘
                         │ 调用
┌────────────────────────▼────────────────────────────┐
│  应用服务层 (Application Service)                      │
│  RaffleApplicationService（编排跨域用例）              │
└────────────────────────┬────────────────────────────┘
                         │ 调用
┌────────────────────────▼────────────────────────────┐
│  领域层 (Domain)                                      │
│  五大领域服务 + 聚合 + 实体 + 值对象 + 端口接口           │
└────────────────────────┬────────────────────────────┘
                         │ 实现端口
┌────────────────────────▼────────────────────────────┐
│  基础设施层 (Infrastructure)                           │
│  Repository 实现 / DAO / Redis / EventPublisher       │
└─────────────────────────────────────────────────────┘
```

**关键原则**：领域层只依赖接口（端口），不依赖具体实现。基础设施层实现端口，可以是 Local 适配器，也可以换成 Remote（Dubbo）适配器，领域层代码不动。

---

## 五大领域职责速查

| 领域 | 包名 | 核心职责 |
|------|------|---------|
| **Activity** | `domain.activity` | 活动参与、配额管理（总/月/日）、SKU 库存 |
| **Strategy** | `domain.strategy` | 抽奖策略、概率算法、规则过滤（责任链 + 决策树） |
| **Rebate** | `domain.rebate` | 行为返利（签到、参与活动触发积分/SKU 奖励） |
| **Credit** | `domain.credit` | 积分账户（增减、查询、积分兑换） |
| **Award** | `domain.award` | 奖品记录、异步履约分发 |

---

## 核心服务依赖关系（运行时）

```
用户
  │ HTTP
  ▼
big-market-gateway
  │
  ▼
big-market-market-service
  │  ├─ Dubbo RPC（暗启动，flag=false 时走本地）
  │  │      ├─ account-service
  │  │      ├─ fulfillment-service
  │  │      ├─ rebate-service
  │  │      └─ strategy-service
  │  │
  │  └─ RabbitMQ（事件驱动）
  │         │
  │         ▼
  │  big-market-message-job-service
  │         ├─ SendAwardConsumer（发奖）
  │         ├─ RebateMessageConsumer（返利）
  │         └─ CreditAdjustSuccessConsumer（积分）
  │
  └─ XXL-Job（定时任务）
         ├─ SendMessageTaskJob（Outbox 轮询发 MQ）
         ├─ UpdateAwardStockJob（Redis库存同步DB）
         └─ UpdateActivitySkuStockJob
```

---

## 数据库表全览

数据库采用分库分表（`big_market_01` / `big_market_02`，表后缀 `_000` ~ `_003`）。

| 表 | 所属领域 | 说明 |
|----|---------|------|
| `strategy` | Strategy | 策略主表（strategyId, ruleModels） |
| `strategy_award` | Strategy | 策略奖品（奖品ID、概率、库存） |
| `strategy_rule` | Strategy | 规则配置（黑名单、权重规则值） |
| `rule_tree / rule_tree_node / rule_tree_node_line` | Strategy | 决策树结构 |
| `raffle_activity` | Activity | 活动主表 |
| `raffle_activity_count` | Activity | 活动配额配置（总/月/日上限） |
| `raffle_activity_sku` | Activity | 活动 SKU（库存） |
| `raffle_activity_account` | Activity | 用户活动总账户（剩余配额） |
| `raffle_activity_account_month` | Activity | 用户月度配额账户 |
| `raffle_activity_account_day` | Activity | 用户日度配额账户 |
| `user_raffle_order` | Activity | 用户抽奖订单（create/used/cancel/failed） |
| `raffle_activity_order` | Activity | SKU 兑换订单 |
| `raffle_quota_decrement_ledger` | Activity | 配额扣减幂等账本 |
| `award` | Award | 奖品主表（awardKey 决定发奖类型） |
| `user_award_record` | Award | 用户中奖记录（create → used） |
| `daily_behavior_rebate` | Rebate | 每日行为返利配置 |
| `user_behavior_rebate_order` | Rebate | 用户返利订单（uq_biz_id 幂等） |
| `user_credit_account` | Credit | 用户积分账户 |
| `user_credit_order` | Credit | 积分交易流水 |
| `credit_award_task` | Credit | 积分发奖异步任务（outbox） |
| `task` | Task | 通用 Outbox 任务表 |
