# Big-Market 简历要点与项目必知手册

> 对应简历：**Big-Market 大营销抽奖平台｜后端开发工程师｜2024.10 – 2025.05**
>
> 本文一份搞定两件事：
> 1. **简历里写了什么** → 怎么结合代码讲清楚  
> 2. **这个项目还需要知道什么** → 面试/上手必会清单与文档索引  
>
> 基于当前 `main` 分支代码与 `docs/learning/` 学习材料整理。

---

## 一、简历原文（四点）与一句话定位

### 简历技术亮点（对应口述）

1. **架构设计**：基于 DDD，划分策略、活动、奖品、积分等领域，业务与基础设施解耦。  
2. **高性能算法**：基于 Redis 的 O(1) 空间换时间抽奖算法，结合多线程预装配，提升高并发抽奖响应。  
3. **规则引擎**：责任链 + 决策树双层规则引擎，编排黑名单、权重分段、次数锁定等规则。  
4. **高可用保障**：Outbox（Task 表 + 补偿 Job）保证 MQ 最终一致；Redis 预扣库存 + 异步批量回写 DB。

### 项目一句话

基于 DDD 的营销抽奖微服务平台：Redis 预装配概率表做 O(1) 抽奖，责任链 + 决策树编排规则，Outbox + Redis 库存扛高并发与最终一致性。

### 命名对照（简历 ↔ 代码）

| 简历用语 | 代码 Bounded Context |
|---------|----------------------|
| 策略 | `domain/strategy` |
| 活动 | `domain/activity` |
| 奖品 | `domain/award`（不是 prize） |
| 积分 | `domain/credit`（不是 points） |

---

## 二、简历四点详解（结合代码）

### 2.1 架构设计：DDD 四层 + 四个领域

**在说什么：** 按领域拆业务，domain 不依赖 Redis/MyBatis 实现，通过 Port/Repository 倒置依赖。

**四层：**

```text
trigger（HTTP / MQ / Job）
  → application（编排用例）
    → domain（业务规则、聚合）
      → infrastructure（MyBatis / Redis / MQ 实现）
```

| 层 | 模块 | 抽奖相关入口 |
|----|------|--------------|
| trigger | `big-market-trigger`、各 `*-service` | `RaffleActivityController`、`SendAwardConsumer`、`SendMessageTaskJob` |
| application | `domain/.../application` | `RaffleApplicationService` |
| domain | `big-market-domain` | `AbstractRaffleStrategy`、`AwardService`、`CreditAdjustService` |
| infrastructure | `big-market-infrastructure` | `*Repository`、`*Support`、`Local*Port` |

**四个领域职责：**

| 领域 | 包 | 职责 |
|------|-----|------|
| 策略 | `domain/strategy` | 概率表、责任链/规则树、奖品库存调度 |
| 活动 | `domain/activity` | 参与下单、总/月/日额度、活动 SKU |
| 奖品 | `domain/award` | 中奖记录、发奖 Outbox、分发 |
| 积分 | `domain/credit` | 积分账户、交易流水 |

**跨域靠 Port（防腐层）：**

| Port | 作用 |
|------|------|
| `IStrategyDecisionPort` | 活动域调策略抽奖 |
| `IAwardFulfillmentPort` | 活动域落中奖记录 |
| `IActivityAccountPort` | 远程额度扣减（开关控制） |

**一次抽奖编排（必背）：**

```text
RaffleApplicationService.executeDraw()
  ① createOrder          # 活动：额度 + 抽奖单
  ② performRaffle        # 策略：责任链 →（可选）规则树
  ③ saveUserAwardRecord  # 奖品：中奖记录 + Outbox → MQ
catch → 补偿活动额度
```

**注意：** `executeDraw` 里调用 `createOrder` 的那一行**表面没有扣额度**；扣减在 `createOrder` → `doSavePartakeOrder` → `saveCreatePartakeOrderAggregate` 的本地事务里（总/月/日 + 插单）。若已有 `state=create` 的未使用订单则**复用、不再扣额度**。

**口述模板：**  
> 按 DDD 拆策略/活动/奖品/积分。活动应用服务编排：先扣额度建单，再经 Port 调策略出奖，再落中奖记录。领域层只依赖接口，基础设施可替换。

**深读：** [13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md)、[04-module-or-service-boundaries.md](04-module-or-service-boundaries.md)

---

### 2.2 高性能算法：Redis O(1) 空间换时间

**在说什么：** 抽奖前把概率展开成查找表放进 Redis；抽奖时随机下标一次命中，O(1)。

**装配（armory）流程：** `AbstractStrategyAlgorithm.assembleLotteryStrategy`

1. 查策略奖品列表  
2. 奖品库存写入 Redis  
3. 装配默认概率表（`key = strategyId`）  
4. 若有权重规则，再为每个积分段装配子集表（`key = strategyId_4000` 等）

**算法选择：** `StrategyArmoryDispatch`，阈值 `ALGORITHM_THRESHOLD_VALUE = 10000`

- `rateRange ≤ 10000` → `O1Algorithm`（展开 + shuffle + Hash 查表）  
- `rateRange > 10000` → `OLogNAlgorithm`（累计区间；大表可用多线程分段查找）

**O(1) 抽奖：** `secureRandom.nextInt(rateRange)` → Redis Hash GET → `awardId`

**「多线程预装配」怎么说才准：**

| 说法 | 是否准确 |
|------|----------|
| 活动上线前预装配概率表到 Redis | ✅ |
| 抽奖路径 O(1) 读表 | ✅ |
| 概率表构建本身多线程并行填充 | ⚠️ 代码主要是单线程装配 |
| O(log n) 大分段查找、库存回写 Job 用线程池 | ✅ |

**口述建议：**  
> 装配阶段把概率表预热到 Redis，抽奖 O(1) 查表；范围过大切 O(log n)。多线程用在大范围查找和库存异步回写，保证高并发 RT。

**深读：** [12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md)

**关键代码：**

- `.../armory/StrategyArmoryDispatch.java`
- `.../armory/algorithm/impl/O1Algorithm.java`
- `.../armory/AbstractStrategyAlgorithm.java`
- `infrastructure/.../StrategyAwardCacheSupport.java`

---

### 2.3 规则引擎：责任链 + 决策树

**在说什么：** 双层规则——抽前分流「抽什么」，抽后过滤「能不能给」。

**入口：** `AbstractRaffleStrategy.performRaffle`

```text
阶段一：责任链 → awardId + logicModel
  若 logicModel != rule_default → 直接返回（跳过规则树）
阶段二：规则树 → 次数锁 / 库存 / 兜底
```

**责任链（典型顺序）：** `rule_blacklist → rule_weight → rule_default`

| 节点 | 类 | 行为 |
|------|-----|------|
| 黑名单 | `BlackListLogicChain` | 命中 → 固定兜底奖，接管 |
| 权重 | `RuleWeightLogicChain` | 按累计消耗积分匹配分段 → 子集表抽奖 |
| 默认 | `DefaultLogicChain` | 主概率表随机抽奖 |

链节点 `@Scope(PROTOTYPE)`，避免不同策略共享 `next` 指针。

**决策树（典型）：**

```text
        [rule_lock 次数锁]
         /              \
    已解锁               未解锁 → [幸运奖]
       ↓
  [rule_stock 库存]
   /          \
扣减成功      无库存 → [幸运奖]
```

| 节点 | 类 | 行为 |
|------|-----|------|
| 次数锁 | `RuleLockLogicTreeNode` | 今日次数未达门槛 → 兜底 |
| 库存 | `RuleStockLogicTreeNode` | Redis DECR；失败走兜底 |
| 兜底 | `RuleLuckAwardLogicTreeNode` | 配置的幸运奖（常为积分） |

**口述模板：**  
> 责任链做抽前分流（黑名单、权重）；只有默认随机才进决策树做次数锁和库存。规则可配置、可加节点，主流程不用大改。

**深读：** [12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md)、[13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md)

---

### 2.4 高可用：Outbox + Redis 预扣库存

**在说什么：** 消息可靠投递 + 库存高并发，两条机制互补。

#### Outbox（Task 表 + 补偿 Job）

同本地事务：

1. 写 `user_award_record`  
2. 写 `task`（`state=create`，带消息体）  
3. 抽奖单 `create → used`（CAS）

提交后再发 RabbitMQ：成功标完成，失败留给 `SendMessageTaskJob` 扫描重试。

**关键代码：** `AwardService.saveUserAwardRecord`、`AwardDispatchSupport`、`SendMessageTaskJob`

#### Redis 预扣 + 异步回写

| 级别 | 存储 | 时机 |
|------|------|------|
| 热数据 | Redis 原子 DECR | 抽奖实时（规则树库存节点） |
| 冷数据 | MySQL `stock_count_surplus` | `UpdateAwardStockJob` 异步回写 |

正确性以 Redis 为准防超卖；MySQL 最终一致。

**口述模板：**  
> 中奖记录和发奖消息同事务落 Outbox，Job 补偿保证最终投递；库存 Redis 预扣，Job 异步回写 DB，热点不打 MySQL。

**深读：** [06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md)、[../data-and-outbox.md](../data-and-outbox.md)、[11-key-design-decisions.md](11-key-design-decisions.md)（决策 2、3）

---

### 2.5 面试 2 分钟串讲（贴简历）

> 营销抽奖平台后端。DDD 拆策略/活动/奖品/积分，活动服务编排，Port 解耦基础设施。  
> 装配阶段把概率表预热到 Redis，抽奖 O(1)；规则用责任链做黑名单/权重，决策树做次数锁和库存。  
> 中奖与发奖消息走 Outbox + Job 补偿；库存 Redis 预扣、异步回写 DB。

---

## 三、项目还需要知道和了解的内容

简历四点是「亮点」；下面是**完整项目画像**——面试深挖或本地上手都会问到。

### 3.1 必会：业务全貌

| 能力 | 你要能讲清 |
|------|------------|
| 用户路径 | 登录 → 查活动/额度 → 签到或积分兑换次数 → 抽奖 → 异步发奖 |
| 运营路径 | 活动装配/策略预热、上架、平台配置、ERP 查询 |
| 系统任务 | MQ 消费、XXL-Job 补偿、库存回写 |

**深读：** [02-business-flows-and-diagrams.md](02-business-flows-and-diagrams.md)、[01-url-request-flows.md](01-url-request-flows.md)

### 3.2 必会：微服务与运行时

约 **10 个服务启动器** + 共享库（domain / infrastructure / api / starters）。

| 服务 | 职责（口述级） |
|------|----------------|
| gateway | 路由、限流、熔断 |
| auth-service | JWT 登录/校验/注销 |
| market-service | 抽奖核心编排 |
| account-service | 积分/额度账户 |
| fulfillment-service | 发奖落地 |
| rebate-service | 签到返利（默认可 embedded） |
| strategy-service | 策略读（默认可 embedded） |
| chatbot-service | AI Chat |
| admin-service | 运营配置 |
| message-job-service | Outbox/任务派发 |

**注意：** rebate / strategy 默认可 **embedded** 在 market 进程内，改配置可切独立 Dubbo 进程。

**深读：** [03-architecture-overview.md](03-architecture-overview.md)、[../MICROSERVICES.md](../MICROSERVICES.md)

### 3.3 必会：技术栈关键词

Java 8、Spring Boot 2.7、Spring Cloud Gateway、Dubbo、Nacos、MyBatis、MySQL、Redis/Redisson、RabbitMQ、XXL-Job、ES、JWT、分库分表（`big-market-starter-db-router`，2 库 × 4 表按 userId）。

**深读：** [08-technical-stack.md](08-technical-stack.md)

### 3.4 必会：七个「为什么这样设计」

面试官常问理由，不只要实现：

1. 服务间为什么用 **Dubbo** 而不是纯 HTTP？  
2. 为什么用 **Outbox** 而不是直接发 MQ？  
3. 库存为什么 **Redis DECR** 而不是直接改 MySQL？  
4. 为什么要 **分库分表**？同用户如何保证本地事务？  
5. 为什么 rebate/strategy 默认 **embedded**？  
6. 抽奖规则为什么拆成 **责任链 + 规则树**？  
7. （延伸）额度失败如何补偿？订单复用为何不重复扣额度？

**深读：** [11-key-design-decisions.md](11-key-design-decisions.md)

### 3.5 必会：数据与一致性

| 主题 | 要点 |
|------|------|
| 核心表 | 活动/策略/奖品、额度账户、抽奖单、中奖记录、`task`、积分账户/流水 |
| Outbox | 业务行 + task 同事务；Job 补偿发送 |
| 幂等 | `outBusinessNo` + 唯一索引 + 捕获 DuplicateKey |
| 额度 | 总/月/日；预检查 + DB `WHERE surplus > 0` 原子扣减 |
| 库存 | Redis 为准；MySQL 异步投影 |

**深读：** [15-data-model.md](15-data-model.md)、[../data-and-outbox.md](../data-and-outbox.md)、[06-high-concurrency-scenarios.md](06-high-concurrency-scenarios.md)

### 3.6 必会：鉴权与网关

- 用户 JWT：登录、校验、注销、撤销（Redis）  
- 网关路由 `/api/v1/auth/**`、`/raffle/**`、`/admin/**`、`/chatbot/**`  
- 管理端与公开展示配置  

**深读：** [05-authentication-and-authorization.md](05-authentication-and-authorization.md)

### 3.7 应了解：降级、回滚、排障

- 抽奖失败：额度补偿（本地 CAS / 远程 rollback）  
- 网关熔断 fallback  
- 积分扣退、任务重试、DLQ 日志  
- 本地启动与冒烟  

**深读：** [07-failure-degradation-and-resilience.md](07-failure-degradation-and-resilience.md)、[10-troubleshooting.md](10-troubleshooting.md)、[16-local-setup.md](16-local-setup.md)

### 3.8 加分了解：运营查询与 ES

Canal → ES 同步，支撑 ERP/运营查询（非抽奖主路径，但体现数据同步能力）。

**深读：** [17-canal-es-sync.md](17-canal-es-sync.md)

### 3.9 代码走读清单（约 30～45 分钟）

按这个顺序跟一遍，简历四点都能落到类名：

1. `RaffleApplicationService.executeDraw`  
2. `AbstractRaffleActivityPartake.createOrder` → `ActivityPartakeOrderSupport`  
3. `LocalStrategyDecisionPort` → `AbstractRaffleStrategy.performRaffle`  
4. `DefaultChainFactory` + `BlackList` / `Weight` / `Default` LogicChain  
5. `DecisionTreeEngine` + `RuleLock` / `RuleStock` / `RuleLuckAward`  
6. `O1Algorithm` + `StrategyArmoryDispatch`  
7. `AwardService.saveUserAwardRecord` → Outbox 写路径  
8. `SendMessageTaskJob`、`UpdateAwardStockJob`、`SendAwardConsumer`  

**地图：** [09-code-map.md](09-code-map.md)

---

## 四、常见追问速查（贴简历 + 易踩坑）

| 问题 | 短答 |
|------|------|
| createOrder 那行有没有扣额度？ | 编排类没有；新建订单时在保存聚合的事务里扣；复用 create 订单不扣。 |
| 黑名单为什么不走规则树？ | 链已接管给出最终奖；只有默认随机才需要抽后过滤。 |
| 多线程预装配？ | 预装配是主路径；多线程在 O(log n) 查找和库存 Job。 |
| Outbox 和事务消息？ | 本地消息表 + 补偿 Job，不是 Broker 事务消息。 |
| 库存 Redis 和 MySQL 不一致？ | 以 Redis 防超卖；Job 追平 MySQL。 |
| 抽奖失败回滚奖品库存吗？ | 默认补偿的是**活动额度**；奖品 Redis DECR 一般不在该路径回滚。 |
| 链节点为何 Prototype？ | 避免单例共享 next，串改不同策略的链。 |
| 为何分库按 userId？ | 同用户数据集中，可用本地事务，避免分布式事务。 |

**更多题库：** [14-interview-qa.md](14-interview-qa.md)

---

## 五、推荐学习路径（按目标）

### 目标 A：只为面试讲简历（1～2 天）

1. 本文 **§二**（四点）+ **§二.5** 串讲  
2. [12-raffle-strategy-algorithm.md](12-raffle-strategy-algorithm.md)  
3. [13-ddd-and-design-patterns.md](13-ddd-and-design-patterns.md)  
4. [11-key-design-decisions.md](11-key-design-decisions.md)  
5. [14-interview-qa.md](14-interview-qa.md)  
6. 走读 **§三.9** 清单

### 目标 B：真正吃透项目（按 learning 索引）

见 [README.md](README.md) 四阶段：启动 → 架构 → 业务 → 实现 → 面试。

总览：[00-learning-guide.md](00-learning-guide.md)

---

## 六、关键代码入口（收藏）

| 主题 | 路径 |
|------|------|
| 抽奖编排 | `big-market-domain/.../activity/application/RaffleApplicationService.java` |
| 参与/额度 | `.../activity/service/partake/AbstractRaffleActivityPartake.java` |
| 额度落库 | `big-market-infrastructure/.../ActivityPartakeOrderSupport.java` |
| 双层规则 | `.../strategy/service/AbstractRaffleStrategy.java` |
| O(1) 算法 | `.../armory/algorithm/impl/O1Algorithm.java` |
| 算法选择 | `.../armory/StrategyArmoryDispatch.java` |
| Outbox 构建 | `.../award/service/AwardService.java` |
| MQ 补偿 | `big-market-trigger/.../job/SendMessageTaskJob.java` |
| 库存回写 | `big-market-trigger/.../job/UpdateAwardStockJob.java` |
| 用户前端 | `big-market-web/app.js` |

---

## 七、文档关系

| 文档 | 用途 |
|------|------|
| **本文** | 简历四点 + 项目必知总册（从这里开始） |
| [14-interview-qa.md](14-interview-qa.md) | 高频问答全文 |
| [11-key-design-decisions.md](11-key-design-decisions.md) | 「为什么」决策 |
| [12](12-raffle-strategy-algorithm.md) / [13](13-ddd-and-design-patterns.md) | 算法与 DDD 深挖 |
| [README.md](README.md) | 完整学习索引 |
