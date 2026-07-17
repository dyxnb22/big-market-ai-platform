# 13 DDD 架构与设计模式

## 一、DDD 四层架构

本项目采用 DDD（领域驱动设计）分层架构（chatbot 等应用编排可落在服务模块内），理解分层是读懂主路径的钥匙。

```text
┌─────────────────────────────────────────────────────┐
│  trigger 层（触发器层）                              │
│  big-market-trigger / 各微服务 controller           │
│  职责：接收外部请求（HTTP/MQ/Job），转发给应用层     │
│  不含业务逻辑，只做参数校验、鉴权、结果转换         │
├─────────────────────────────────────────────────────┤
│  application 层（应用层）                           │
│  big-market-domain/.../application/                 │
│  职责：编排领域服务，串联一次完整的业务流程          │
│  代表：RaffleApplicationService.executeDraw()       │
│        CreditPayExchangeApplicationService          │
├─────────────────────────────────────────────────────┤
│  domain 层（领域层）— 核心                          │
│  big-market-domain/                                 │
│  职责：业务规则、状态机、聚合、实体、值对象          │
│  通过 port 接口调用外部能力（不依赖具体实现）        │
├─────────────────────────────────────────────────────┤
│  infrastructure 层（基础设施层）                    │
│  big-market-infrastructure/                         │
│  职责：实现 domain 的 port/repository 接口             │
│  包括 MyBatis DAO、Redis、MQ 发布、ES 查询           │
└─────────────────────────────────────────────────────┘
```



### 四层的具体对应关系


| 层              | 模块                                                                                   | 典型类                                                                                                                                                                                                                                                                                                                                              |
| -------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| trigger        | `big-market-trigger`                                                                 | `RaffleActivityController`、`SendAwardConsumer`、`GlobalExceptionHandler`、`DubboRpcAuthSupport`                                                                                                                                                                                                                                                    |
| application    | `big-market-domain/.../application` + `big-market-trigger/.../application` + chatbot | `RaffleDrawApplicationService`、`RaffleStrategyQueryApplicationService`、`ErpOperateApplicationService`、`ChatbotApplicationService` 等                                                                                                                                                                                                              |
| domain         | `big-market-domain`                                                                  | `AbstractRaffleActivityPartake`、`DefaultRaffleStrategy`、`BehaviorRebateService`、`AdminAccessService`                                                                                                                                                                                                                                             |
| infrastructure | `big-market-infrastructure`                                                          | `ActivityRepository`、`ActivityQuerySupport`、`ActivityPartakeOrderSupport`、`ActivityQuotaOrderSupport`、`ActivityQuotaLedgerSupport`、`StrategyRepository`、`StrategyAwardCacheSupport`、`StrategyRuleTreeSupport`、`AwardRepository`、`AwardDispatchSupport`、`AwardCreditGrantSupport`、`MysqlMybatisConfiguration`、`ElasticsearchMybatisConfiguration` |


---



## 二、Domain 层内部结构（以 activity 域为例）

每个业务域（activity、strategy、award、credit、rebate）在 domain 层内部结构一致：

```text
domain/activity/
├── adapter/
│   ├── port/          ← 领域需要调用外部能力时，定义的接口（防腐层）
│   │   ├── IActivityAccountPort.java    ← 额度扣减/回滚
│   │   ├── IStrategyDecisionPort.java   ← 调用策略抽奖
│   │   └── IAwardFulfillmentPort.java   ← 调用发奖
│   └── repository/    ← 仓储接口（数据读写抽象）
│       └── IActivityRepository.java
├── model/
│   ├── aggregate/     ← 聚合根（跨多表的事务单元）
│   │   └── CreatePartakeOrderAggregate.java
│   ├── entity/        ← 实体（有唯一标识的业务对象）
│   │   └── ActivityEntity.java、UserRaffleOrderEntity.java
│   └── valobj/        ← 值对象（无唯一标识，描述状态/类型）
│       └── ActivityStateVO.java、OrderTradeTypeVO.java
├── service/           ← 领域服务（核心业务逻辑）
│   └── partake/
│       └── AbstractRaffleActivityPartake.java
└── application/       ← 应用服务（编排多个领域服务）
    └── RaffleApplicationService.java
```



### 聚合根的作用

`CreatePartakeOrderAggregate` 是典型聚合根：一次参与抽奖涉及
`activity_account`（总、日、月额度）和 `user_raffle_order` 多张表，
必须在同一事务中操作，通过聚合根确保一致性。

```java
// ActivityPartakeOrderSupport.saveCreatePartakeOrderAggregate()
// 在一个本地事务内完成：扣减总/日/月额度 + 插入抽奖单
// ActivityRepository 仅作门面委托，便于按职责继续拆分
```



### Port 接口的作用（防腐层）

Domain 层不依赖任何具体的 RPC 框架或数据库框架，
通过 Port 接口隔离外部依赖：

```java
// 领域层定义接口
public interface IStrategyDecisionPort {
    RaffleAwardEntity performRaffle(RaffleFactorEntity raffleFactorEntity);
}

// infrastructure 层实现（本地调用策略领域；@ConditionalOnMissingBean）
public class LocalStrategyDecisionPort implements IStrategyDecisionPort { ... }
```

当前抽奖决策默认进程内 `LocalStrategyDecisionPort`。account 的积分写入仍保留
明确的 local / remote adapter 边界，策略读请求固定由 market-local adapter 处理。

---



## 三、设计模式



### 模式 1：模板方法（Template Method）

**位置：** `AbstractRaffleActivityPartake`、`AbstractRaffleStrategy`

**问题：** 参与活动的流程（校验活动状态 → 查未使用订单 → 扣额度 → 保存订单）对所有子类相同，但"额度过滤"和"订单构建"的细节不同。

**实现：** 父类定义算法骨架，子类只需实现变化的步骤。

```java
// AbstractRaffleActivityPartake.java
public UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity) {
    // 1. 校验活动状态（固定步骤）
    // 2. 查未被使用的订单（固定步骤）
    // 3. 【抽象】额度账户过滤
    CreatePartakeOrderAggregate aggregate = this.doFilterAccount(userId, activityId, currentDate);
    // 4. 【抽象】构建订单
    UserRaffleOrderEntity order = this.buildUserRaffleOrder(userId, activityId, currentDate);
    // 5. 保存聚合（可Override）
    doSavePartakeOrder(aggregate);
}

protected abstract CreatePartakeOrderAggregate doFilterAccount(...);
protected abstract UserRaffleOrderEntity buildUserRaffleOrder(...);
```

**面试回答要点：** 父类定义了"参与抽奖"的算法骨架，子类 `RaffleActivityPartakeService` 实现了具体的额度检查逻辑（查总/日/月账户）。

---



### 模式 2：责任链（Chain of Responsibility）

**位置：** `big-market-domain/.../strategy/service/rule/chain/`

**问题：** 抽奖前需要按顺序过滤多个规则（黑名单 → 权重 → 默认），每个规则可以"接管"或"放行"。

**实现：** 每个规则节点持有 `next` 引用，形成链表。

```java
// DefaultChainFactory.buildChain()
// 从数据库配置的规则顺序构建责任链，例如：
// rule_blacklist → rule_weight → rule_default

ILogicChain logicChain = applicationContext.getBean(ruleModels[0], ILogicChain.class);
ILogicChain current = logicChain;
for (int i = 1; i < ruleModels.length; i++) {
    current = current.appendNext(applicationContext.getBean(ruleModels[i], ILogicChain.class));
}
current.appendNext(applicationContext.getBean("rule_default", ILogicChain.class));
```

**三个节点的职责：**

- `BlackListLogicChain`（rule_blacklist）：用户在黑名单中，直接返回兜底奖品，不再往下传
- `RuleWeightLogicChain`（rule_weight）：按用户积分匹配权重范围，命中则接管，否则放行
- `DefaultLogicChain`（rule_default）：按概率表随机抽奖，责任链的终点

**注意：** 每个链节点用 `@Scope(SCOPE_PROTOTYPE)` 修饰，确保每次 `getBean` 拿到新实例，避免链表 next 引用互相污染。

---



### 模式 3：组合模式 + 规则树（Composite / Decision Tree）

**位置：** `big-market-domain/.../strategy/service/rule/tree/`

**问题：** 责任链抽出奖品 ID 后，还需要对奖品做进一步过滤（是否解锁、库存是否充足），这些规则之间有条件分支，不是线性链表能表达的。

**实现：** 规则树从数据库（`rule_tree`、`rule_tree_node`、`rule_tree_line`）加载，`DecisionTreeEngine` 按节点边的条件遍历。

```java
// DecisionTreeEngine.process()
String nextNode = ruleTreeVO.getTreeRootRuleNode();  // 从根节点开始
while (null != nextNode) {
    ILogicTreeNode logicTreeNode = logicTreeNodeGroup.get(ruleTreeNode.getRuleKey());
    TreeActionEntity result = logicTreeNode.logic(userId, strategyId, awardId, ruleValue, endDateTime);
    // 根据返回的 ALLOW / TAKE_OVER 决定走哪条边到下一节点
    nextNode = nextNode(result.getRuleLogicCheckType().getCode(), ruleTreeNode.getTreeNodeLineVOList());
}
```

**三个树节点的职责：**

- `RuleLockLogicTreeNode`（rule_lock）：检查用户今日抽奖次数是否达到解锁门槛，未达到则 TAKE_OVER（拦截，走向兜底奖品）
- `RuleStockLogicTreeNode`（rule_stock）：Redis 扣减奖品库存，扣减成功则 TAKE_OVER（返回该奖品），库存不足则 ALLOW（放行，走向下一节点）
- `RuleLuckAwardLogicTreeNode`（rule_luck_award）：兜底节点，返回配置的兜底奖品

**与责任链的区别（面试高频）：**


| 维度   | 责任链                            | 规则树                                               |
| ---- | ------------------------------ | ------------------------------------------------- |
| 结构   | 线性链表                           | 有向无环图（树形）                                         |
| 时机   | 抽奖**前**：决定抽哪个奖品                | 抽奖**后**：对已抽出的奖品做过滤/替换                             |
| 分支   | 接管即终止，否则顺序传递                   | 根据 ALLOW/TAKE_OVER 走不同的树边                         |
| 配置来源 | `strategy` 表的 `rule_models` 字段 | `rule_tree`/`rule_tree_node`/`rule_tree_line` 三张表 |


---



### 模式 4：策略模式（Strategy）

**位置：** `big-market-domain/.../activity/service/quota/policy/`

**问题：** SKU 订单有两种交易类型（积分兑换、返利免费），创建订单的核心逻辑不同。

**实现：** 定义 `ITradePolicy` 接口，两种实现由工厂根据 `OrderTradeTypeVO` 分发。

```java
// ITradePolicy
public interface ITradePolicy {
    void trade(CreateQuotaOrderAggregate createQuotaOrderAggregate);
}

// 积分支付
public class CreditPayTradePolicy implements ITradePolicy { ... }

// 返利免费（无需扣积分）
public class RebateNoPayTradePolicy implements ITradePolicy { ... }
```

---



### 模式 5：适配器模式（Adapter）

**位置：** `big-market-market-service/src/main/java/.../market/config/`，`big-market-trigger/src/main/java/.../trigger/adapter/`

**问题：** 同一个业务操作（如"扣减积分"），在本地开发时直接调用 domain 层，在服务化部署时通过 Dubbo RPC 调用 account-service。需要在不修改调用方代码的情况下切换实现。

**实现：** 定义统一接口，本地和远程各提供一个实现，通过 `@ConditionalOnProperty` 按配置激活。

```java
// 统一接口
public interface IAccountCreditWriteAdapter {
    String createOrder(TradeEntity tradeEntity);
}

// 本地实现（直接调用 CreditService）
@ConditionalOnProperty(name = "account.service.remote-credit-write.enabled",
    havingValue = "false", matchIfMissing = true)
public class LocalAccountCreditWriteAdapter implements IAccountCreditWriteAdapter { ... }

// 远程实现（调用 account-service Dubbo RPC）
@ConditionalOnProperty(name = "account.service.remote-credit-write.enabled", havingValue = "true")
public class AccountRemoteCreditWriteAdapter implements IAccountCreditWriteAdapter { ... }
```

**项目中共有 5 组适配器：**

- `IAccountReadAdapter`：账户读取（本地域/远程 account-service）
- `IAccountCreditWriteAdapter`：积分写入
- `IAccountQuotaWriteAdapter`：额度写入
- `IRebateOrderAdapter`：返利订单创建
- `IRebateReadAdapter`：返利签到查询

**Dubbo Provider 瘦身：** RPC 类仅做日志与 `com.dyx.market.trigger.api.support.ApiResponses` 包装；业务与校验下沉至 domain 的 `application` 包。HTTP 与内部 RPC 共用 `RaffleActivityFacade`。

---



### 模式 6：Outbox（发件箱）模式

**位置：** `AwardRepository`、`CreditRepository`、`BehaviorRebateRepository`，以及各 task 表

**问题：** 写数据库和发 MQ 消息是两个操作，如果先写库再发消息，库已写但 MQ 宕机则消息丢失；如果先发消息再写库，消息已发但库写失败则数据不一致。

**实现：** 将 MQ 消息先存入同库的 `task` 表（与业务数据同一本地事务），事务提交后再异步发送 MQ。发送成功后更新 task 状态为 completed；发送失败则 task 保持 create 状态，由 `SendMessageTaskJob`（**message-job**）定时扫描重发。

```text
┌──────────────────────────────────────────┐
│  本地事务（同一数据库）                   │
│  ① 写业务表（user_award_record）          │
│  ② 写 task 表（状态=create，存消息内容）  │
└──────────────────┬───────────────────────┘
                   │ 事务提交后
                   ▼
          ③ 异步发送 RabbitMQ
          ④ 成功 → task.state = completed
             失败 → task 保留，等 Job 重扫
```

**默认 Docker 二级 Outbox（积分奖）：** compose 将 `ACCOUNT_AWARD_CREDIT_OUTBOX_ENABLED=true`（覆盖 yml 缺省 `false`）。`SendAwardConsumer` 写 `credit_award_task`，再由 `DispatchCreditAwardTaskJob` 调 account RPC。细节以 [`docs/data-and-outbox.md`](../data-and-outbox.md) 为准；`award_state=completed` ≠ 账户已入账。

**代码位置：**

- `AwardRepository.saveUserAwardRecord()`：写 `user_award_record` + `task`，事务提交后调用 `eventPublisher` 发 MQ
- `SendMessageTaskJob.java`：扫描 status=create 的 task，补偿重发（message-job）
- `DispatchCreditAwardTaskJob.java`：积分奖二级 outbox 派发（message-job）
