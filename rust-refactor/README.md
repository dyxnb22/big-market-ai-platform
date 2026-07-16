# Big Market → Rust 重构（至替代 Java）

> **目标：** 用 Rust 重写并最终 **替代** 原 Java 微服务默认演示路径，降低内存与延迟，保留业务语义与幂等契约。  
> **状态：** M0–M7 已在本分支落地（memory 后端 + HTTP 闭环）。详见 [STATUS.md](./STATUS.md)。  
> **默认本地命令：** `./scripts/acceptance-rust.sh`

## 文档索引

| 文档 | 内容 |
| --- | --- |
| **[STATUS.md](./STATUS.md)** | **当前完成状态与验收命令** |
| **[JAVA-DELETION-LEDGER.md](./JAVA-DELETION-LEDGER.md)** | **Java 安全删除台账（逐文件对照）** |
| **[NEXT-PHASES.md](./NEXT-PHASES.md)** | **M0–M7 之后深度阶段 C–F（已完成）** |
| **[docs/RUST-LEARNING-FREEZE.md](../docs/RUST-LEARNING-FREEZE.md)** | **Rust 轨冻结边界** |
| **[ROADMAP.md](./ROADMAP.md)** | 主路线图 M0→M7 + 完成定义 |
| **[CUTOVER.md](./CUTOVER.md)** | 默认切流与 Java legacy 回滚 |
| **[docs/MICROSERVICES-RUST.md](../docs/MICROSERVICES-RUST.md)** | **Rust 权威架构（模块化单体，非 1:1 Java）** |
| **[tech-stack.md](./tech-stack.md)** | 已锁定技术栈 |
| [PLAN.md](./PLAN.md) | 原则、架构、幂等约束 |
| [service-mapping.md](./service-mapping.md) | Java → Rust 映射 |
| [memory-perf.md](./memory-perf.md) | 内存与性能 |
| [phases.md](./phases.md) | 执行清单 |

## 实现位置

```text
big-market-rs/          # Cargo workspace（权威实现）
scripts/run-rust-stack.sh
scripts/smoke-rust-api.sh
scripts/acceptance-rust.sh
docker-compose.rust.yml
```
