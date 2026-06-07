# Big Market AI Platform Rebuild Roadmap

## 当前完成状态

已经完成：

- 原项目已复制到 `/Users/diaoyuxuan/big-market-ai-platform`，原始复习项目不受影响。
- 删除了旧的零散学习文档，只保留产品架构、重建路线和 dev-ops 资料。
- Java 包名从原作者命名空间迁移到 `com.dyx.market`。
- `querys` 已统一修正为 `queries`。
- 外部 DB Router starter 已替换为项目内 `big-market-starter-db-router`。
- DCC 动态配置抽到 `big-market-starter-dcc`。
- 限流 AOP 抽到 `big-market-starter-ratelimiter`。
- 新增登录、管理配置、Chatbot、配置持久化、轻量前端和启动脚本。
- `mvn -DskipTests compile` 已验证通过。

## 从 0 重做顺序

### Week 1: 工程骨架和可启动链路

目标：先跑通 `启动项目 -> 初始化策略 -> 调用抽奖接口 -> 返回 awardId`。

- 建 Maven 多模块。
- 建 `types/api/app/domain/infrastructure/trigger`。
- 接 MySQL、MyBatis、Redis。
- 做最小抽奖策略预热和抽奖接口。

### Week 2: 策略领域

目标：讲清楚 Redis O(1) 抽奖。

- 策略、奖品、概率表建模。
- 概率表预热到 Redis。
- 运行时随机下标查表。
- 对比 O(1) 和 O(logN) 的适用场景。

### Week 3: 活动领域

目标：讲清楚活动账户、SKU、库存扣减。

- 活动、活动次数包、用户活动账户。
- SKU 积分兑换。
- Redis 库存预扣减。
- 延迟队列 + XXL-Job 异步落库。

### Week 4: 规则引擎

目标：讲清楚责任链 + 决策树。

- 抽奖前规则：黑名单、权重、默认规则。
- 抽奖后规则：库存、次数锁、兜底奖。
- 把规则配置化，避免 if/else 堆业务。

### Week 5: 消息最终一致性

目标：讲清楚本地 task 表 + MQ + Job 补偿。

- MySQL `task` 表保存待发送消息。
- MQ 发送成功后更新状态。
- Job 扫描失败/超时任务重试。
- 明确 Redis 延迟队列、RabbitMQ 事件、本地 outbox 的边界。

### Week 6: 认证、管理端、Chatbot

目标：把项目变成可演示产品。

- 登录和 token 校验。
- 管理端配置。
- Chatbot 工具调用。
- 轻量前端联调。
- 未来替换为 LLM function calling。

### Week 7: 测试和部署

目标：让项目经得起面试追问。

- 单测：概率表、规则链、规则树、token、配置服务。
- 集成测试：登录、签到、查次数、抽奖、Chatbot。
- Docker Compose 一键中间件。
- 冒烟脚本覆盖核心接口。

## 面试讲法

这个项目可以总结成：

> 我在一个大营销抽奖平台上做了产品化补全。底层保留 DDD 领域拆分，抽奖核心用 Redis 概率表实现 O(1)，规则用责任链和决策树拆开，库存用 Redis 预扣减加 XXL-Job 异步落库，消息用本地 task 表、MQ 和补偿 Job 保证最终一致性。在此基础上，我又补了登录、管理配置、Chatbot 入口、可运行脚本和轻量前端。

## 还可以继续补强

- 把 `PlatformConfigService` 从本地 properties 升级为 MySQL `sys_config`。
- 增加 admin RBAC、审计日志和操作记录。
- Chatbot 接入 OpenAI/DeepSeek function calling。
- 把管理端从静态 HTML 升级为 React/Vite。
- 给 Redis 库存、task outbox、规则树补集成测试。
