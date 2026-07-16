# 微服务架构（Java — 历史说明）

> **Java 源码已从本仓库删除。** 默认可运行栈仅为 Rust，见 [`MICROSERVICES-RUST.md`](./MICROSERVICES-RUST.md)。  
> 本文保留为 **学习对照**（流程/端口/历史组件名），不再对应可编译的 Spring 工程。  
> 删除台账：[`rust-refactor/JAVA-DELETION-LEDGER.md`](../rust-refactor/JAVA-DELETION-LEDGER.md)。

最后修订：2026-07-16（源码移除）。

历史学习栈曾包含：网关、auth/admin/market/chatbot/message-job/account/fulfillment、Dubbo/Nacos、RabbitMQ、XXL-Job、MySQL、Redis。冻结证据见 `docs/LEARNING-FREEZE.md`（针对删除前的 Java 验收）。

## 历史服务列表（已无源码）

| 服务 | 端口 | 现状 |
| --- | ---: | --- |
| gateway / auth / admin / market / chatbot / message-job / account / fulfillment | 8080–8087 | **已删除**；能力由 `bm-gateway` / `bm-app` / `bm-worker` 承接 |
| rebate / strategy 独立进程 | 8088–8089 | Batch 1 即已删除（原为 optional） |

共享模块 `domain` / `infrastructure` / `api` / `types` / starters：**已删除**。

## 核心流程（概念）

抽奖 → 发奖 outbox → 入账、SKU、Chat、签到等语义仍以 [`data-and-outbox.md`](./data-and-outbox.md) 为准；**实现入口**改为：

- Rust HTTP：`bm-app`
- Rust worker：`bm-worker` / embed scheduler
- 文档：[`MICROSERVICES-RUST.md`](./MICROSERVICES-RUST.md)

## 中间件

`docs/dev-ops/docker-compose-environment.yml` 中的 MySQL / Redis / RabbitMQ /（可选）XXL Admin、Nacos **基础设施**可继续用于 Rust `BM_BACKEND=mysql` 等；应用进程不再是 Spring。
