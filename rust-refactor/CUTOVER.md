# Rust 默认切流说明（M6）

## 默认怎么跑（Rust）

```bash
./scripts/run-rust-stack.sh
./scripts/smoke-rust-api.sh
./scripts/acceptance-rust.sh
```

Gateway: `http://127.0.0.1:8080` → `bm-app :8083`（内嵌 outbox worker）。

前端仍可用 `./scripts/web-start.sh`，将 API 指到 `http://127.0.0.1:8080/api/v1`。

## Java legacy（回滚 / 对照）

原 Spring 微服务栈保留在仓库中，**不再作为 Rust 轨默认启动路径**：

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d
docker compose up --build -d
./scripts/acceptance.sh --reuse
```

## Compose profile

```bash
# 需要 Docker 时构建 Rust 镜像
docker compose -f docker-compose.rust.yml --profile rust up --build -d
```

## 完成标准对照

见 `STATUS.md` 与 `ROADMAP.md` D1–D7。当前默认演示与 CI 友好路径为 Rust memory 栈；与 Java 共享 MySQL 的生产级双跑需后续启用 `mysql`/`redis` features。
