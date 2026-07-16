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

见 `STATUS.md` 与 `ROADMAP.md` D1–D7。默认演示与 CI 路径为 Rust file 栈；`BM_BACKEND=mysql` 可走完整 sqlx 存储（CI `mysql-smoke` job）。

```bash
BM_BACKEND=mysql BM_MYSQL_URL=mysql://root:123456@127.0.0.1:13306/big_market ./scripts/run-rust-stack.sh
./scripts/smoke-rust-mysql.sh
# 可选：Java 对照（需 Java 栈在 JAVA_API_BASE）
JAVA_API_BASE=http://127.0.0.1:8098/api/v1 ./scripts/acceptance-dual-stack.sh
```
