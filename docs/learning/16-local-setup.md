# 16 本地环境启动指南

本文描述如何从零启动完整的 Big Market 本地开发栈。

---

## 前置条件

| 工具 | 版本要求 |
| ------ | --------- |
| Docker Desktop | 4.x+，建议分配内存 ≥ 8 GB |
| JDK | 8（`java -version` 确认） |
| Maven | 3.6+（`mvn -version` 确认） |

---

## 第一步：启动基础设施

从**项目根目录**执行（与根 `README.md` 一致）：

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
```

这个命令启动以下容器：

| 容器 | 端口 | 用途 | 账密 |
| ------ | ------ | ------ | ------ |
| mysql | 13306 | 业务数据库（自动初始化 DDL） | root / 123456 |
| phpmyadmin | 8899 | MySQL Web 管理 | root / 123456 |
| redis | 16379 | 缓存 + 分布式锁 | 无密码 |
| redis-admin | 18081 | Redis Web 管理 | admin / admin |
| rabbitmq | 5672 / 15672 | 消息队列 | admin / admin |
| zookeeper | 2181 | DCC 动态配置 | 无密码 |
| nacos | 8848 | 注册中心 + 配置中心 | nacos / nacos |
| canal-server | 11111 | MySQL binlog 监听 | — |
| canal-adapter | 18082 | binlog → ES 同步 | — |
| elasticsearch | 9200 | 运营查询（ES） | 无密码 |
| kibana | 5601 | ES Web 管理 | 无密码 |
| xxl-job-admin | 9090 | 定时任务管理 | admin / 123456 |
| prometheus | 9091 | 指标采集 | — |
| grafana | 4000 | 监控大盘 | — |

> **等待提示：** MySQL、Nacos、RabbitMQ 有 healthcheck，容器 Started 后仍需等待约 30 秒才真正就绪。可用 `docker compose -f docs/dev-ops/docker-compose-environment.yml ps` 确认 STATUS 为 healthy。

---

## 第二步：验证基础设施

```bash
# 确认所有容器状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 验证 MySQL（应能查到 big_market 库）
mysql -h 127.0.0.1 -P 13306 -u root -p123456 -e "show databases;"

# 验证 Redis
redis-cli -p 16379 ping   # 返回 PONG

# 验证 RabbitMQ（浏览器访问）
open http://127.0.0.1:15672   # admin/admin

# 验证 Nacos
open http://127.0.0.1:8848/nacos   # nacos/nacos
```

---

## 第三步：构建代码

```bash
# 从项目根目录执行
mvn clean package -DskipTests

# 如果构建失败，查看第一个报错的模块
mvn clean package -DskipTests 2>&1 | grep -E "BUILD|ERROR" | head -20
```

---

## 第四步：启动应用服务

### 方式一：用 Docker Compose 启动全部服务（推荐学习用）

```bash
# 从项目根目录（需先完成第三步 mvn package）
docker compose up --build -d
```

查看各服务端口：

| 服务 | 端口 | 说明 |
| ------ | ------ | ------ |
| big-market-gateway | 8080 | API 网关（所有请求入口） |
| big-market-auth-service | 8081 | 登录鉴权 |
| big-market-admin-service | 8082 | 管理配置 |
| big-market-market-service | 8083 | 核心抽奖业务 |
| big-market-chatbot-service | 8084 | AI Chat |
| big-market-message-job-service | 8085 | MQ 消费 + XXL-Job |
| big-market-account-service | 8086 | 积分/额度 RPC |
| big-market-fulfillment-service | 8087 | 发奖 RPC |
| big-market-rebate-service | 8088 | 返利 RPC（默认 embedded，可不启动） |
| big-market-strategy-service | 8089 | 策略 RPC（默认 embedded，可不启动） |

> **注意：** `rebate-service` 和 `strategy-service` 默认以 embedded 模式内嵌在 `market-service` 中运行，不需要单独启动对应容器。
>
> **Token 注销：** Docker 栈为 `auth-service`、`admin-service`、`market-service` 设置了
> `TOKEN_REVOCATION_REDIS_ENABLED=true`，logout 写入 Redis 黑名单后三服务均可校验。
> 若用方式二单独 `mvn spring-boot:run` 且未开启 Redis 注销，各进程使用内存黑名单，
> **logout 不会跨服务生效**。

### 方式二：只启动核心服务（节省资源）

最小化启动顺序（先后顺序很重要）：

```bash
# 1. gateway（依赖所有下游服务）
cd big-market-gateway && mvn spring-boot:run &

# 2. auth-service
cd big-market-auth-service && mvn spring-boot:run &

# 3. market-service（包含 embedded rebate 和 strategy）
cd big-market-market-service && mvn spring-boot:run &

# 4. message-job-service（MQ 消费 + XXL-Job）
cd big-market-message-job-service && mvn spring-boot:run &
```

---

## 第五步：活动预热（必须操作）

启动后，抽奖前必须先调用 armory 接口预热缓存，否则抽奖会因策略数据不在 Redis 而失败。

```bash
# 预热活动（activityId=100301 是测试活动；需管理员凭证）
curl -H "X-Admin-Token: admin-dev-token" \
  "http://127.0.0.1:8080/api/v1/raffle/activity/armory?activityId=100301"

# 或使用管理员 JWT（先登录 admin/admin）
ADMIN_TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"admin","password":"admin"}' | jq -r '.data.token')
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://127.0.0.1:8080/api/v1/raffle/activity/armory?activityId=100301"

# 预热策略（strategyId=100006）
curl -H "X-Admin-Token: admin-dev-token" \
  "http://127.0.0.1:8080/api/v1/raffle/strategy/strategy_armory?strategyId=100006"
```

---

## 第六步：冒烟测试

```bash
# 结构和配置一致性验证（无需 Docker）
./scripts/validate-microservices-runtime-safety.sh

# 服务健康检查（需要容器运行）
./scripts/validate-microservices-stack.sh

# API 接口冒烟测试（需要完整环境 + 预热）
./scripts/smoke-api.sh
```

可选：验证 logout 跨服务生效（Docker 栈 + Redis 注销已开启）：

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}' | jq -r '.data.token')
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/logout -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/verify -H "Authorization: Bearer $TOKEN"
# 第二次应返回 TOKEN_ERROR
```

---

## 第七步：启动前端（可选）

```bash
./scripts/web-start.sh
open http://127.0.0.1:5173/login.html
```

前端 API 默认请求 `http://127.0.0.1:8080/api/v1`（经 gateway）。

---

## 常见启动问题

### 问题 1：Nacos 启动报错 "wait for mysql"

MySQL healthcheck 未通过。等待 30 秒后再看，或检查 MySQL 日志：

```bash
docker logs mysql --tail 30
```

### 问题 2：应用报 "No provider available" (Dubbo)

Nacos 未就绪或 market-service 的 embedded provider 配置未生效。检查：

```bash
# 确认 rebate.embedded-rpc-provider.enabled=true（默认已开启）
grep "embedded-rpc-provider" big-market-market-service/src/main/resources/application.yml
```

### 问题 3：抽奖返回 "活动未开启" 或策略数据为空

未执行预热，或 armory 未带管理员凭证。重新执行第五步的 armory 调用（需 `X-Admin-Token` 或管理员 JWT）。若返回 `0008`，见 `10-troubleshooting.md` 场景 1.3。

### 问题 4：XXL-Job 任务不执行

访问 <http://127.0.0.1:9090/xxl-job-admin>（admin/123456），在"执行器管理"确认 `big-market` 执行器已注册并在线。

---

## 管理界面速查

| 界面 | 地址 | 账密 |
| ------ | ------ | ------ |
| phpMyAdmin（MySQL） | <http://127.0.0.1:8899> | root / 123456 |
| Redis Commander | <http://127.0.0.1:18081> | admin / admin |
| RabbitMQ Management | <http://127.0.0.1:15672> | admin / admin |
| Nacos Console | <http://127.0.0.1:8848/nacos> | nacos / nacos |
| XXL-Job Admin | <http://127.0.0.1:9090/xxl-job-admin> | admin / 123456 |
| Kibana（ES） | <http://127.0.0.1:5601> | 无密码 |
| Prometheus | <http://127.0.0.1:9091> | 无密码 |
| Grafana | <http://127.0.0.1:4000> | — |
