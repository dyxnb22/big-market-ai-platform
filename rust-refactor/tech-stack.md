# 技术栈锁定（Big Market Rust）

最后修订：2026-07-16。  
本文件为 **最终选型**；实现阶段不得随意更换主干框架。若必须变更，先改本文再写代码。

## 1. 语言与工程

| 项 | 锁定 | 版本策略 |
| --- | --- | --- |
| 语言 | **Rust** | stable；MSRV **1.78+**（实现时写死在 `Cargo.toml`） |
| Edition | **2021** | workspace 统一 |
| 构建 | **Cargo workspace** | 根目录 `big-market-rs/` |
| 质量门禁 | `rustfmt` + **clippy `-D warnings`** + `cargo test` | CI 必过 |
| 依赖审计 | **cargo-deny**（licenses + advisories） | M0 引入 |
| 发布二进制 | `release`：`lto = "thin"`，`codegen-units = 1` | 体积/性能平衡 |

## 2. 运行时与 HTTP

| 能力 | 锁定 crate | 备注 |
| --- | --- | --- |
| 异步运行时 | **tokio**（`rt-multi-thread`, `macros`, `signal`, `time`） | 全栈唯一 runtime |
| HTTP 服务 | **axum** | 应用与网关 |
| 中间件 | **tower** / **tower-http** | timeout、trace、cors、compression、限流 |
| 路由网关 | **axum** 反向代理（`reqwest` 或 `hyper` 上游） | 不引入 Envoy/Nginx 作为学习默认（compose 里前端仍可用现有 nginx） |

## 3. 数据、缓存、消息

| 能力 | 锁定 | 备注 |
| --- | --- | --- |
| MySQL 访问 | **sqlx**（`runtime-tokio`, `mysql`, `migrate`） | 编译期 SQL 检查；**不用** Diesel/SeaORM 作默认 |
| 连接池 | sqlx pool | 每进程小池，配置化 max |
| 分库路由 | 自研 `bm_infra::DbRouter` | 对齐 `userId` → `big_market_01/02` |
| Redis | **fred** | 多路复用；JWT 吊销、库存、chat idempotency |
| RabbitMQ | **lapin** + **tokio-executor-trait** | Topic / 独立 queue；DLQ 显式声明 |
| 迁移 | sqlx migrate **或** 复用现有 Docker init SQL | 默认复用 `docs/dev-ops/mysql/sql`；Rust 侧只加增量 migrate |

## 4. RPC、序列化、API 契约

| 能力 | 锁定 | 备注 |
| --- | --- | --- |
| 进程内调用 | Rust `async trait` | M0–M5 默认；零序列化 |
| 进程间 RPC | **tonic** + **prost** | M6 后可选拆服务时启用 |
| JSON | **serde** + **serde_json** | HTTP DTO |
| 内部二进制（可选） | prost | 仅 gRPC |
| OpenAPI | **utoipa** + **utoipa-swagger-ui**（可选 feature） | 文档；不阻塞闭环 |
| HTTP 客户端 | **reqwest**（`rustls-tls`） | 网关上游、健康探测 |

## 5. 安全与配置

| 能力 | 锁定 | 备注 |
| --- | --- | --- |
| JWT | **jsonwebtoken** | HS256 对齐现有学习配置；算法可配置 |
| 密码哈希（若需） | **argon2** | 仅当脱离明文 demo 时 |
| 内部服务 token | 常量时间比较（`subtle`） | 对齐 secure overlay |
| 配置 | **figment**（`Env` + `Toml`） | 12-factor；密钥只来自环境变量 |
| Nacos | **不做默认依赖**；可选 feature `nacos-sync` 后期再加 | 避免拖慢替代主线 |

## 6. 任务调度与后台

| 能力 | 锁定 | 备注 |
| --- | --- | --- |
| 定时任务 | **tokio-cron-scheduler** | 替代 XXL 执行器侧；覆盖 credit dispatch、库存刷、chat reconcile |
| XXL-Job Admin | **不复刻 UI** | 需要时用简单 `/admin/jobs` 只读列表；学习演示足够 |
| MQ 消费监督 | worker 内 supervisor + 重试/DLQ | 对齐 message-job |

## 7. 可观测性与日志

| 能力 | 锁定 | 备注 |
| --- | --- | --- |
| 日志/追踪 | **tracing** + **tracing-subscriber** | JSON 或 pretty（ENV 切换） |
| 指标 | **metrics** + **metrics-exporter-prometheus** | `/metrics` |
| 分布式追踪 | **opentelemetry** + OTLP（可选 feature） | 默认可关，降低本地复杂度 |
| 错误类型 | **thiserror**（库）+ **anyhow**（bin 边界） | 禁止请求路径裸 `unwrap` |

## 8. 测试

| 能力 | 锁定 | 备注 |
| --- | --- | --- |
| 单测 | `cargo test` | domain 纯逻辑优先 |
| HTTP 测 | **axum-test** 或 tower `oneshot` | |
| 集成 | testcontainers **或** 复用 compose 中间件 | M2+ |
| 契约/E2E | 移植/复用 `scripts/*` + Playwright | 前端不改框架 |
| Mock | **wiremock**（HTTP） | |

## 9. 容器与本地编排

| 能力 | 锁定 | 备注 |
| --- | --- | --- |
| 基础镜像构建 | `rust:bookworm` multi-stage | |
| 运行镜像 | **gcr.io/distroless/cc** 或 `debian:bookworm-slim` | 优先小镜像 |
| 编排 | 现有 Docker Compose | `docker-compose.rust.yml` → M6 并入默认 |
| 前端 | **big-market-web** 不变 | 静态 HTML/JS |

## 10. 默认可部署拓扑（替代完成后）

```text
bm-gateway   :8080
bm-app       :8083   # auth+market+account+chat+rebate+strategy+admin（feature 可裁剪）
bm-worker    :8085   # MQ + cron jobs

中间件：MySQL / Redis / RabbitMQ / Prometheus / Grafana
（Nacos、XXL-Admin：非默认）
```

端口刻意贴近原 Java，便于对照；进程数从 ~10 个 JVM 降为 **3 个** 原生进程。

## 11. 明确拒绝的技术（除非改本文件）

| 不采用 | 原因 |
| --- | --- |
| Actix-web 作默认 | 与 Axum/Tower 生态重叠；统一 Axum |
| SeaORM / Diesel 作默认 | 隐式与热路径控制弱于 SQLx |
| Kafka 替换 RabbitMQ | 无必要；保持与现网中间件一致 |
| Dubbo 协议兼容层 | 成本高；用 tonic/进程内 trait |
| 全量复刻 XXL-Job Admin | 非替代关键路径 |
| 前端 React/Next 重写 | 超出「替代后端」范围 |
| Kubernetes 作为本地默认 | 学习栈保持 Compose |

## 12. crate 职责（与选型对应）

| Crate | 依赖焦点 |
| --- | --- |
| `bm-types` | serde、thiserror |
| `bm-domain` | 几乎无 IO；可依赖 `bm-types` |
| `bm-infra` | sqlx、fred、lapin、DbRouter |
| `bm-api` | DTO、utoipa（可选）、prost（可选） |
| `bm-gateway` | axum、tower、reqwest、jsonwebtoken（校验） |
| `bm-app` | axum、domain、infra |
| `bm-worker` | lapin、tokio-cron-scheduler、domain、infra |
