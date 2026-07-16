# Big Market → Rust 重构方案（并行探索轨）

> **性质：** 独立于 Java 学习冻结基线的探索目录。不改动现有 Spring 服务、不破坏 `docs/LEARNING-FREEZE.md` 已验证拓扑。  
> **目标：** 用 Rust 重写核心路径，降低常驻内存与延迟，保留业务语义与幂等契约。  
> **状态：** 方案阶段（仅文档，无运行时代码）。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [PLAN.md](./PLAN.md) | 总方案：原则、目标架构、技术选型、分期、验收 |
| [service-mapping.md](./service-mapping.md) | Java 服务 / 领域包 → Rust crate 映射 |
| [memory-perf.md](./memory-perf.md) | 内存与性能优化专项（相对 JVM 的收益点与度量） |
| [phases.md](./phases.md) | 分阶段落地清单与每阶段交付物 |

## 一句话结论

先做 **Workspace 单体进程 + 进程内领域模块**（HTTP/gRPC 面与 Java API 兼容），用 Outbox + RabbitMQ 保住异步闭环；再按需拆进程。优先迁移 **auth → market 抽奖 → message-job 发奖 → account**，用压测证明 RSS / P99 收益后再谈全面替换。

## 与现有仓库的关系

```text
/workspace
├── big-market-*          # Java 冻结基线（权威可运行栈）
├── docs/                 # Java 架构与学习文档
├── rust-refactor/        # ← 本目录：Rust 重构方案（本轨）
└── （后续）big-market-rs/ # 建议实现落地目录，尚未创建
```

实现代码建议落在仓库根目录 `big-market-rs/`（Cargo workspace），与本方案目录分离，避免和 Java 模块混扫。
