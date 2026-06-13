# 学习文档导航

Big Market 抽奖平台 — 面试 / 学习项目文档中心

---

## 推荐学习路径

```
第一阶段：读懂项目   →  01-architecture.md  +  02-domain-design.md
第二阶段：读懂业务   →  03-business-flows.md
第三阶段：读懂设计   →  04-design-patterns.md
第四阶段：读懂拆分   →  05-microservices.md
横向补充：基础设施   →  06-infrastructure.md
```

| 文档 | 内容概述 | 建议阅读时长 |
|------|---------|------------|
| [01 项目架构总览](01-architecture.md) | 模块划分、技术栈、分层结构 | 30 min |
| [02 领域模型设计](02-domain-design.md) | 五大领域、实体/聚合/值对象、端口与适配器 | 60 min |
| [03 核心业务流程](03-business-flows.md) | 抽奖、发奖、签到返利、积分、配额补偿等完整流程 | 60 min |
| [04 设计模式解析](04-design-patterns.md) | 模板方法、责任链、决策树、Outbox、特性开关等 12 个模式 | 45 min |
| [05 微服务拆分历程](05-microservices.md) | 8 个 Phase 的拆分过程、端口适配器、特性开关、最终状态 | 30 min |
| [06 基础设施组件](06-infrastructure.md) | DB 分片路由、Redis 缓存策略、MQ、分布式锁、幂等 | 45 min |

---

## 面试高频问题索引

| 问题方向 | 对应文档章节 |
|---------|------------|
| 抽奖概率算法是怎么实现的？O(1) 是怎么做到的？ | [04 § 概率算法](04-design-patterns.md#概率算法-o1-vs-ologn) |
| 规则引擎怎么设计的？责任链和决策树分别解决什么？ | [04 § 责任链](04-design-patterns.md#责任链) + [04 § 决策树](04-design-patterns.md#决策树) |
| 分布式事务怎么保证一致性？Outbox 是什么？ | [04 § Outbox 模式](04-design-patterns.md#outbox-模式) + [03 § 发奖流程](03-business-flows.md#流程二发奖履约) |
| 用户配额（总次数 / 月 / 日）是怎么扣减的？并发怎么保证？ | [03 § 配额扣减](03-business-flows.md#流程一参与活动与抽奖) |
| 库存超卖怎么防止？ | [06 § Redis 库存](06-infrastructure.md#redis-缓存策略) |
| DB 分库分表怎么路由的？ | [06 § DB 分片路由](06-infrastructure.md#db-分片路由中间件) |
| 为什么要做微服务拆分？拆分边界怎么定的？ | [05 § 拆分原则](05-microservices.md#拆分原则) |
| 服务之间怎么通信的？同步和异步分别用在哪？ | [05 § 服务通信](05-microservices.md#服务间通信) |
| 签到返利的幂等是怎么保证的？ | [03 § 签到返利流程](03-business-flows.md#流程三签到返利) |
| 项目整体用了哪些设计模式？ | [04 § 总览](04-design-patterns.md#设计模式总览) |
