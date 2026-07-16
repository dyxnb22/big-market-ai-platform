# Rust 默认切流说明

Java 应用源码已移除。默认与唯一应用栈为 Rust。

## 怎么跑

```bash
./scripts/run-rust-stack.sh
./scripts/smoke-rust-api.sh
./scripts/acceptance-rust.sh
```

Gateway: `http://127.0.0.1:8080` → `bm-app :8083`（默认内嵌 outbox worker）。

前端：`./scripts/web-start.sh` → `http://127.0.0.1:8080/api/v1`。

## Docker

```bash
docker compose up --build -d
# 或兼容别名
docker compose -f docker-compose.rust.yml up --build -d
```

## MySQL

```bash
BM_BACKEND=mysql BM_MYSQL_URL=mysql://root:123456@127.0.0.1:13306/big_market ./scripts/run-rust-stack.sh
./scripts/smoke-rust-mysql.sh
```

完成标准见 `STATUS.md`、`docs/RUST-LEARNING-FREEZE.md`、`JAVA-DELETION-LEDGER.md`。
