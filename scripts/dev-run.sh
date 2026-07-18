#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
# shellcheck source=lib/java17-precheck.sh
source "$ROOT_DIR/scripts/lib/java17-precheck.sh"
require_java_17

echo "[1/3] Start middleware containers"
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d mysql redis rabbitmq nacos xxl-job-admin elasticsearch

echo "[2/3] Build all modules"
mvn -DskipTests package

echo "[3/3] Start microservices stack via docker-compose"
echo "  Gateway:  http://127.0.0.1:8080"
echo "  Web:      http://127.0.0.1:5173 (run scripts/web-start.sh separately)"
docker compose up -d --build
