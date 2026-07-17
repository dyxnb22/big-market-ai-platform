# 15 数据模型与表结构

本文覆盖 Big Market 所有关键表的结构、字段含义、分库分表规则和核心状态机。SQL 源文件在 `docs/dev-ops/mysql/sql/`。

---

## 数据库划分

| 数据库 | 用途 |
|--------|------|
| `big_market` | 配置类表（活动、策略、奖品、规则树等） |
| `big_market_01` | 用户数据分片 01（userId hash 路由） |
| `big_market_02` | 用户数据分片 02（userId hash 路由） |

**分库分表规则（`HashDBRouterStrategy`）：**
```java
int hash  = userId.hashCode() & Integer.MAX_VALUE;
int dbIdx = hash % 2 + 1;          // 1 → big_market_01，2 → big_market_02
int tbIdx = (hash / 2) % 4;        // 0~3 → _000 ~ _003
```
同一 userId 永远路由到同一库同一表，同库内可用本地事务，无需分布式事务。

---

## 一、big_market（配置库）

### 1.1 raffle_activity（活动表）

```
raffle_activity
├── activity_id      bigint   活动唯一标识
├── activity_name    varchar  活动名称
├── activity_desc    varchar  描述
├── begin_date_time  datetime 开始时间
├── end_date_time    datetime 结束时间
├── strategy_id      bigint   关联策略 ID
└── state            varchar  状态：create / open / close / restart
```

`state` 状态机：
```
create → open（运营上架）→ close（关闭）
                         ↕
                       restart
```
抽奖校验：必须为 `open` 且当前时间在 `begin_date_time ~ end_date_time` 之间。

---

### 1.2 raffle_activity_sku（活动 SKU 表）

```
raffle_activity_sku
├── sku                bigint   SKU ID（用户用积分兑换时的商品标识）
├── activity_id        bigint   关联活动
├── activity_count_id  bigint   关联次数配置
├── stock_count        int      总库存
├── stock_count_surplus int     剩余库存（MySQL 记录）
└── product_amount     decimal  积分售价
```

> Redis Key：`activity_sku_stock_count_{sku}` → 实时库存（DECR 操作）；  
> MySQL `stock_count_surplus` 由 `UpdateActivitySkuStockJob` 异步同步。

---

### 1.3 raffle_activity_count（次数配置表）

```
raffle_activity_count
├── activity_count_id  bigint  次数规格 ID
├── total_count        int     总抽奖次数上限
├── day_count          int     每日上限
└── month_count        int     每月上限
```

每个 SKU 对应一种次数规格，决定用户购买该 SKU 后获得多少抽奖次数。

---

### 1.4 raffle_activity_stage（活动展台）

```
raffle_activity_stage
├── channel      varchar  渠道（如 c01）
├── source       varchar  来源（如 s01）
├── activity_id  bigint   关联活动
└── state        varchar  create / active / expire
```

运营通过 ERP 接口将活动从 `create` 推进到 `active`，前端按渠道/来源查询上架活动。

---

### 1.5 strategy / strategy_award / strategy_rule（策略三表）

```
strategy
├── strategy_id   bigint   策略 ID
├── strategy_desc varchar  描述
└── rule_models   varchar  责任链规则列表（如 "rule_blacklist,rule_weight"）

strategy_award
├── strategy_id        bigint   策略 ID
├── award_id           int      奖品 ID
├── award_title        varchar  奖品名称
├── award_count        int      库存总量
├── award_count_surplus int     MySQL 库存剩余
├── award_rate         decimal  中奖概率（如 0.3000 = 30%）
├── rule_models        varchar  规则树 ID（如 "tree_lock_1"）
└── sort               int      展示排序

strategy_rule
├── strategy_id   int     策略 ID
├── award_id      int     奖品 ID（策略级规则时为 NULL）
├── rule_type     tinyint 1=策略规则，2=奖品规则
├── rule_model    varchar rule_blacklist / rule_weight / rule_lock / rule_luck_award
└── rule_value    varchar 规则参数（黑名单用户列表、积分权重段等）
```

---

### 1.6 rule_tree / rule_tree_node / rule_tree_node_line（规则树三表）

```
rule_tree
├── tree_id            varchar  树唯一 ID（如 "tree_lock_1"）
├── tree_name          varchar  树名称
└── tree_node_rule_key varchar  根节点 Key（从此节点开始遍历）

rule_tree_node
├── tree_id    varchar  所属树
├── rule_key   varchar  节点 Key（rule_lock / rule_stock / rule_luck_award）
└── rule_value varchar  节点参数（如 lock 节点的门槛次数 "1"）

rule_tree_node_line（边）
├── tree_id         varchar  所属树
├── rule_node_from  varchar  起始节点
├── rule_node_to    varchar  目标节点
├── rule_limit_type varchar  判断类型（EQUAL）
└── rule_limit_value varchar  判断值（ALLOW / TAKE_OVER）
```

遍历逻辑：从 `tree_node_rule_key` 出发，节点 logic() 返回 ALLOW 或 TAKE_OVER，按边条件跳到下一节点，直到叶子节点。

---

### 1.7 award（奖品表）

```
award
├── award_id     int     奖品内部 ID
├── award_key    varchar 发奖标识（user_credit_random / openai_use_count / openai_model）
├── award_config varchar 发奖参数（如积分范围 "1,100"，OpenAI 次数 "5"）
└── award_desc   varchar 描述
```

`award_key` 决定发奖策略，`award_config` 是参数：
- `user_credit_random`：随机积分，范围由 config 指定
- `openai_use_count`：增加 OpenAI 使用次数，次数由 config 指定
- `openai_model`：解锁 OpenAI 模型，模型名由 config 指定
- `user_credit_blacklist`：黑名单兜底积分

---

### 1.8 daily_behavior_rebate（返利配置表）

```
daily_behavior_rebate
├── behavior_type  varchar  sign（签到）/ openai_pay（支付）
├── rebate_desc    varchar  描述
├── rebate_type    varchar  sku（增加抽奖次数）/ integral（增加积分）
├── rebate_config  varchar  sku 值或积分值
└── state          varchar  open / close
```

---

## 二、big_market_01 / big_market_02（用户数据分片）

以下各表在两个数据库中都有，且每库有 4 张分表（_000 ~ _003）。

---

### 2.1 raffle_activity_account（活动总账户）

```
raffle_activity_account
├── user_id               varchar  用户 ID（分片 key）
├── activity_id           bigint   活动 ID
├── total_count           int      总次数上限
├── total_count_surplus   int      总次数剩余
├── day_count             int      日次数上限（每次参与初始化）
├── day_count_surplus     int      日次数剩余
├── month_count           int      月次数上限
└── month_count_surplus   int      月次数剩余
```

唯一索引：`(user_id, activity_id)`。一次参与抽奖扣减这里的 surplus。

---

### 2.2 raffle_activity_account_day / raffle_activity_account_month

```
raffle_activity_account_day
├── user_id          varchar  用户 ID
├── activity_id      bigint   活动 ID
├── day              varchar  日期（yyyy-mm-dd）
├── day_count        int      当日上限
└── day_count_surplus int     当日剩余

raffle_activity_account_month
├── user_id           varchar  用户 ID
├── activity_id       bigint   活动 ID
├── month             varchar  月份（yyyy-mm）
├── month_count       int      当月上限
└── month_count_surplus int    当月剩余
```

每日/每月首次参与时 INSERT，后续 UPDATE surplus - 1。唯一索引保证不重复创建。

---

### 2.3 raffle_activity_order_000 ~ _003（SKU 购买订单，分表）

```
raffle_activity_order
├── user_id         varchar   用户 ID
├── sku             bigint    购买的 SKU
├── activity_id     bigint    活动 ID
├── order_id        varchar   订单 ID（幂等）
├── total_count     int       本次获得的总次数
├── day_count       int       本次获得的日次数
├── month_count     int       本次获得的月次数
├── pay_amount      decimal   支付积分（0 表示返利免费）
├── state           varchar   complete（唯一状态）
└── out_business_no varchar   业务防重 ID（唯一索引）
```

---

### 2.4 user_raffle_order_000 ~ _003（抽奖单，分表）

```
user_raffle_order
├── user_id       varchar  用户 ID
├── activity_id   bigint   活动 ID
├── strategy_id   bigint   策略 ID
├── order_id      varchar  订单 ID（唯一索引，幂等 key）
├── order_time    datetime 下单时间
└── order_state   varchar  create / used / cancel
```

**状态机：**
```
[create] ──抽奖成功──→ [used]
    └──异常补偿──→ [cancel]（目前实际观察到失败路径标记，cancel 作为保留态）
```

---

### 2.5 user_award_record_000 ~ _003（中奖记录，分表）

```
user_award_record
├── user_id      varchar  用户 ID
├── activity_id  bigint   活动 ID
├── strategy_id  bigint   策略 ID
├── order_id     varchar  抽奖订单 ID（唯一索引）
├── award_id     int      奖品 ID
├── award_title  varchar  奖品名称
├── award_time   datetime 中奖时间
└── award_state  varchar  create / completed
```

**状态机：**
```
[create] ──MQ 消费发奖成功──→ [completed]
```
MQ 失败时 award_state 停留在 create，由 `SendMessageTaskJob` 补偿重发。

---

### 2.6 task（Outbox 任务表）

```
task
├── user_id     varchar  用户 ID（分片 key）
├── topic       varchar  MQ topic（send_award / send_rebate / credit_adjust_success）
├── message_id  varchar  消息 ID（唯一索引）
├── message     varchar  消息体 JSON
└── state       varchar  create / completed / fail
```

**状态机：**
```
[create] ──发送 MQ 成功──→ [completed]
    └──多次重试仍失败──→ [fail]（进 DLQ）
```

`SendMessageTaskJob`（message-job）扫描 state=`create` 的 task，补偿重发。更新成功后库中一般为 `completed`（mapper 硬编码；领域枚举名可能写作 `complete`——查库以列值为准）。

---

### 2.6.1 credit_award_task（积分发奖二级 Outbox）

所有环境都使用该积分发奖 Outbox；权威说明见 [`docs/data-and-outbox.md`](../data-and-outbox.md)。

```
credit_award_task
├── user_id / award_order_id   幂等与对账键
├── state                      pending → dispatched / failed（以代码与 DDL 为准）
├── retry_count                派发重试
└── （分片库 big_market_01/02）
```

`DispatchCreditAwardTaskJob_DB1/DB2` 扫描 pending → 调 account RPC → `dispatched`。
**勿**仅用 `user_award_record.award_state=completed` 证明积分已入账。

---

### 2.7 user_behavior_rebate_order_000 ~ _003（返利订单，分表）

```
user_behavior_rebate_order
├── user_id        varchar  用户 ID
├── order_id       varchar  订单 ID
├── behavior_type  varchar  sign / openai_pay
├── rebate_type    varchar  sku / integral
├── rebate_config  varchar  sku 值或积分值
├── out_business_no varchar  外部业务 ID（如 yyyyMMdd）
└── biz_id         varchar  内部唯一 ID（out_business_no + rebate_type，唯一索引）
```

幂等依赖 `biz_id` 唯一索引；签到返利 `out_business_no = yyyyMMdd`，重复签到命中 DuplicateKey 则返回已签到。

---

### 2.8 user_credit_account（积分账户）

```
user_credit_account
├── user_id          varchar  用户 ID（唯一索引）
├── total_amount     decimal  历史总积分
├── available_amount decimal  当前可用积分
└── account_status   varchar  open / close（冻结）
```

扣减 SQL（防超扣）：
```sql
UPDATE user_credit_account
SET available_amount = available_amount + #{adjustAmount}
WHERE user_id = #{userId}
  AND available_amount + #{adjustAmount} >= 0
```

---

### 2.9 user_credit_order_000 ~ _003（积分流水，分表）

```
user_credit_order
├── user_id         varchar  用户 ID
├── order_id        varchar  订单 ID（唯一索引）
├── trade_name      varchar  交易名称（行为返利 / 兑换抽奖 / Chat 扣费）
├── trade_type      varchar  forward（加积分）/ reverse（扣积分）
├── trade_amount    decimal  交易金额（扣减为负数）
└── out_business_no varchar  业务防重 ID（唯一索引）
```

---

## 三、表关系总览

```
raffle_activity ──strategy_id──→ strategy ──strategy_id──→ strategy_award
      │                                                           │
      ├──activity_id──→ raffle_activity_sku ──activity_count_id──→ raffle_activity_count
      │
      └──activity_id──→ raffle_activity_account（用户额度总账户）
                              ├──→ raffle_activity_account_day
                              └──→ raffle_activity_account_month

strategy_award.rule_models ──tree_id──→ rule_tree
                                              └──→ rule_tree_node
                                              └──→ rule_tree_node_line

用户参与一次抽奖写入：
  user_raffle_order（抽奖单）
  user_award_record（中奖记录）
  task（MQ Outbox）
  ──MQ──→ SendAwardConsumer（message-job）
           ├── 积分奖 + Docker outbox → credit_award_task → account RPC
           └── 其它奖品类型 → 对应履约路径
```

---

## 四、关键状态机汇总

| 表 | 字段 | 状态值 | 含义 |
|----|------|--------|------|
| `raffle_activity` | `state` | create / open / close / restart | 活动生命周期 |
| `raffle_activity_stage` | `state` | create / active / expire | 展台上架状态 |
| `user_raffle_order` | `order_state` | create / used / cancel | 抽奖单使用状态 |
| `user_award_record` | `award_state` | create / completed | 发奖完成态（库值；勿等同账户入账） |
| `task` | `state` | create / completed / fail | MQ Outbox 状态 |
| `credit_award_task` | `state` | pending / dispatched / failed | 积分二级 Outbox |
| `user_credit_account` | `account_status` | open / close | 积分账户冻结 |
| `user_credit_order` | `trade_type` | forward / reverse | 积分增加/扣减 |
