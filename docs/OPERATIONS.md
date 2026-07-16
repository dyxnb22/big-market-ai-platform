# Operations

## Local (file backend)

```bash
./scripts/run-stack.sh          # gateway :8080 + app :8083
./scripts/acceptance.sh         # test + clippy + API smoke
./scripts/web-start.sh          # UI :5173
./scripts/bench.sh              # optional RSS snapshot
```

## Secure demo

```bash
./scripts/run-secure.sh
./scripts/acceptance.sh --secure
```

## MySQL

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql
BM_BACKEND=mysql BM_MYSQL_URL=mysql://root:123456@127.0.0.1:13306/big_market \
  ./scripts/run-stack.sh
./scripts/acceptance.sh --mysql
```

Init SQL lives under `docs/dev-ops/mysql/sql/` (including `z-reconcile-tables.sql` for ledgers / `activity_soft_stock` / `platform_config`).

## Rabbit

```bash
# with Rabbit reachable on :5672
./scripts/acceptance.sh --rabbit
```

Sets `BM_EMBED_WORKER=0` and starts `bm-worker` for the smoke path.

## Docker app stack

```bash
docker compose up --build -d
# secure overlay:
# docker compose -f docker-compose.yml -f docker-compose.secure.yml up -d
```

## Env (common)

| Variable | Meaning |
| --- | --- |
| `BM_BACKEND` | `file` \| `memory` \| `mysql` |
| `BM_DATA_DIR` | file snapshot directory |
| `BM_MYSQL_URL` | mysql URL |
| `BM_REDIS_URL` | JWT revoke (optional) |
| `BM_RABBIT_URL` | MQ bridge (optional) |
| `BM_EMBED_WORKER` | `1` default; forced off when Rabbit URL set |
| `BM_JWT_SECRET` / `BM_INTERNAL_TOKEN` | auth / internal calls |
| `BM_STRATEGY_CHAIN` | enable blacklist/weight lite |

## Acceptance flags

```bash
./scripts/acceptance.sh --e2e --secure --mysql --rabbit
```
