# Big Market → Rust 重构（至替代 Java）

> **目标：** 用 Rust 重写并最终 **替代** 原 Java 微服务默认栈，降低内存与延迟，保留业务语义与幂等契约。  
> **状态：** 方案 + 路线图已定；实现代码尚未开始。  
> **当前权威可运行栈：** 仍是 Java 学习冻结基线，直到路线图 **M6/M7** 完成。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| **[ROADMAP.md](./ROADMAP.md)** | **主路线图：M0→M7，直到替代 Java 的完成定义** |
| **[tech-stack.md](./tech-stack.md)** | **已锁定技术栈（实现不得随意更换）** |
| [PLAN.md](./PLAN.md) | 原则、目标架构、幂等约束、成功标准 |
| [service-mapping.md](./service-mapping.md) | Java 服务 / 模块 → Rust crate 映射 |
| [memory-perf.md](./memory-perf.md) | 内存与性能优化与度量 |
| [phases.md](./phases.md) | 与里程碑对齐的执行清单 |

## 一句话结论

采用 **Axum + Tokio + SQLx + fred + lapin + tonic(可选)**，默认部署 **gateway + bm-app + bm-worker** 三进程；按路线图 M0→M7 做完验收与切流后，Rust 成为默认栈，Java 降为 legacy。

## 里程碑速览

```text
M0 骨架 → M1 鉴权网关 → M2 抽奖发奖闭环 → M3 积分/聊天/返利
 → M4 管理运维 → M5 全量验收+性能 → M6 默认切流 → M7 归档 Java ✅
```

完成定义见 [ROADMAP.md](./ROADMAP.md) 中 D1–D7。

## 与现有仓库的关系

```text
/workspace
├── big-market-*           # Java（M6 前仍是默认；M7 后 legacy）
├── docs/                  # 现有文档；M6+ 增加 Rust 权威入口
├── rust-refactor/         # ← 本目录：方案 / 路线图 / 技术栈
└── big-market-rs/         # 实现目录（M0 创建，尚未存在）
```
