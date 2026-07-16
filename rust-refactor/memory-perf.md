# 内存与性能优化专项

面向「相对当前多 JVM 微服务栈」的可验证优化，而不是空泛的“Rust 更快”。

## 1. 主要浪费来源（现状）

| 来源 | 影响 |
| --- | --- |
| 每服务一个 Spring Boot 进程 | 堆、Metaspace、线程、连接池成倍叠加 |
| 冷启动与健康检查链 | gateway 等依赖拉长 ready 时间 |
| 热路径装箱 / 短命 DTO | GC 与分配速率上升 |
| 同步路径上的远程 RPC 跳数 | 抽奖链路上序列化与网络往返 |
| ORM/会话缓存误用风险 | 连接与一级缓存占用 |

## 2. Rust 侧结构性手段（高收益）

### 2.1 进程合并

- 默认 **2 进程**：`bm-app` + `bm-worker`（外加轻量 `bm-gateway` 可选同进程 feature）。  
- 预期：应用 RSS 从「数 GB 量级多 JVM」降到「数百 MB 内」（需实测校准）。  
- 用 feature flag 控制 chatbot/admin 是否编进同一二进制，避免无用代码常驻。

### 2.2 连接池与线程预算

| 资源 | 建议 |
| --- | --- |
| MySQL | 每进程小池（如 5–10），全局连接数可控 |
| Redis | 单多路复用客户端 |
| Tokio | 默认 worker threads = CPU；阻塞 SQL 走 `spawn_blocking` 仅在必要时 |
| MQ | 预取（prefetch）可配置，防止 worker 内存被消息撑大 |

### 2.3 分配与序列化

- 热路径 DTO 用 `serde` + 借用（`Cow`/`&str`）减少 `String` 拷贝。  
- 响应缓冲复用（`BytesMut`）。  
- 策略抽奖计算结果用值类型，避免 `Box<dyn ...>` 除非规则插件需要。  
- JSON 仅在边界；进程内调用传结构体引用。

### 2.4 缓存策略

- 策略配置、奖品列表：进程内 `ArcSwap` / `moka` 本地缓存 + Redis 二级。  
- 库存：Redis 计数 + 异步落库（对齐现有 job 思路），避免每请求打 DB。  
- JWT 校验：本地 JWKS/密钥缓存；吊销查 Redis（短超时）。

### 2.5 异步边界

- 抽奖同步路径只做：鉴权 → 配额 → 抽奖 → 写记录 + outbox。  
- 入账、外部履约一律 worker，缩短请求持有连接与事务时间。

## 3. 编码层面清单

- [ ] `clippy` + `cargo deny` 作为 CI 门禁  
- [ ] 禁止在请求路径 `unwrap` 后静默丢错误导致重试风暴  
- [ ] 大查询禁止 `SELECT *` 拉全行到内存；分页/流式  
- [ ] 日志默认 `INFO`；热点用 `tracing` 采样，避免字符串格式化占 CPU  
- [ ] Release：`lto = thin`、`codegen-units = 1` 权衡编译时间与体积  
- [ ] 镜像：`distroless` 或 `scratch` + 静态/准静态链接，减小攻击面与镜像层

## 4. 基准与对比方法

### 4.1 固定场景

复用现有脚本语义：

- 登录 → 兑换/扣积分 → 策略固定奖 → outbox → 入账  
- 重复 `requestId` 幂等  
- Chat 扣费 + 退款补偿  

### 4.2 采集指标

| 指标 | 工具 |
| --- | --- |
| RSS / 峰值内存 | `ps`、`docker stats`、`/proc/<pid>/status` |
| 启动 ready | 健康检查时间戳 |
| HTTP 延迟 | `oha` / `wrk` / 脚本内建计时 |
| MQ 积压与消费延迟 | RabbitMQ metrics + 自定义 histogram |
| 分配（深挖） | `dhat`、`heaptrack`（可选） |

### 4.3 对比协议

1. 同一机器、同一中间件容器、同一数据集。  
2. 先跑 Java `acceptance`/`smoke-raffle-award-e2e` 记基线。  
3. 再跑 Rust 等价脚本，输出表格进 `rust-refactor/bench/`（落地后创建）。  
4. 只有 **正确性通过 + 指标达标** 才合并“性能优化”结论。

## 5. 预期收益量级（经验估计，待实测）

| 项目 | 保守预期 |
| --- | --- |
| 应用 RSS | 降至 Java 多进程合计的 10%～25% |
| 冷启动 | 单个二进制 < 5s ready |
| 抽奖 P99 | 同机下降 30%～60%（若原瓶颈在 JVM/RPC；若瓶颈在 MySQL 则收益有限） |

若 profiling 显示瓶颈在 MySQL 锁/慢 SQL，优先修 SQL 与索引，而不是继续“换语言”。

## 6. 不优先的优化

- 过早 SIMD / 手写汇编  
- 无证据的全异步批量聚合导致一致性变难  
- 为省内存关闭幂等存储或缩短吊销 TTL 到不安全水平  
- 用 `unsafe` 换微秒级收益
