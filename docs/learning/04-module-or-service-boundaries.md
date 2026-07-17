# 04 模块与服务边界

## 服务模块

| 模块 | 边界 |
| --- | --- |
| `big-market-gateway` | 网关路由、trace 透传、响应 fallback |
| `big-market-auth-service` | 登录、token 校验、注销撤销 |
| `big-market-admin-service` | 平台配置 API（含公开只读 `public/display`） |
| `big-market-market-service` | Raffle/activity HTTP API 与本地编排 |
| `big-market-chatbot-service` | Chatbot API 与积分扣退集成 |
| `big-market-message-job-service` | MQ 消费者、XXL-Job 处理器、重试派发 |
| `big-market-account-service` | 积分与额度 RPC provider |

## 前端模块

| 模块 | 边界 |
| --- | --- |
| `big-market-web` | 用户端与管理端静态页面（原生 HTML/CSS/JS，非 React）；`app.js` 用户抽奖/Chatbot；`admin.js` 管理配置；API 经网关，桌面/Web 优先 |

## 共享库

- `big-market-trigger`：HTTP/RPC、应用编排、RabbitMQ 监听器、XXL-Job 处理器的**源码模块**（非独立进程）。
  - **market-service** 扫描：`trigger.http`、`trigger.rpc`、`trigger.application`、`trigger.support`、`trigger.adapter`
  - **message-job-service** 扫描：`trigger.job`、`trigger.listener`（及消费所需 application）；**不得**由 market 扫描 job/listener
- `big-market-domain`：activity、strategy、award、credit、rebate、auth、task 等领域模型/服务/端口。
- `big-market-infrastructure`：MyBatis DAO、repository 适配器（含 `MysqlMybatisConfiguration` 共享 MySQL 配置）、Redis、ES、MQ 发布与本地端口实现。
- `big-market-api`：Dubbo API 契约与 DTO（含共用 `ApiResponses`）。
- `big-market-types`：通用响应码、异常、注解与常量。
- `big-market-management`：平台配置辅助，供 `admin-service` 与 `chatbot-service` 使用（`PlatformConfigService`）。
- Starter 模块：DB router、DCC、rate limiter、**web**（TraceId、**CorsAutoConfiguration**、Redis 连接封装）、**data**（共享线程池）。

## 当前边界

- 返利与策略保留为 market 内部边界；积分奖本地派发保留在 message-job。

## 边界规则

服务 API 集中在 `big-market-api`。领域端口隔离跨域调用。Repository 适配器将 MyBatis DAO 隐藏在领域接口之后。本学习项目有意复用共享库，在保持服务归属清晰的同时让本地作品集栈更紧凑。

关键文件：

- `pom.xml`
- `docs/microservices-dao-ownership.md`
- `big-market-domain/src/main/java/com/dyx/market/domain/activity/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/award/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/credit/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/rebate/adapter/port`
- `big-market-domain/src/main/java/com/dyx/market/domain/strategy/adapter/port`
- `big-market-web/app.js`
- `big-market-admin-service/src/main/java/com/dyx/market/admin/service/config/WebMvcConfig.java`（`public/display` 排除管理员鉴权）
