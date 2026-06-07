#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "[1/3] Start middleware containers"
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch

echo "[2/3] Build application"
mvn -DskipTests package

echo "[3/3] Run application on http://127.0.0.1:8098"
java -jar big-market-app/target/big-market-app.jar
