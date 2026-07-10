#!/usr/bin/env bash
# Shared health polling for stack / acceptance scripts.
# Usage: source this file, then call wait_for_http_up <url> [timeout_sec] [label]

wait_for_http_up() {
  local url="$1"
  local timeout_sec="${2:-120}"
  local label="${3:-$url}"
  local elapsed=0
  echo "  Waiting for $label (timeout ${timeout_sec}s)..."
  while [ "$elapsed" -lt "$timeout_sec" ]; do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "  UP  $label (${elapsed}s)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  echo "  TIMEOUT  $label after ${timeout_sec}s" >&2
  return 1
}

wait_for_actuator_up() {
  local host="$1"
  local port="$2"
  local name="$3"
  local timeout_sec="${4:-180}"
  wait_for_http_up "http://${host}:${port}/actuator/health" "$timeout_sec" "$name"
}

wait_for_stack_healthy() {
  local host="${1:-localhost}"
  local timeout_sec="${2:-180}"
  wait_for_actuator_up "$host" 8080 "gateway" "$timeout_sec"
  wait_for_actuator_up "$host" 8081 "auth" "$timeout_sec"
  wait_for_actuator_up "$host" 8082 "admin" "$timeout_sec"
  wait_for_actuator_up "$host" 8083 "market" "$timeout_sec"
  wait_for_actuator_up "$host" 8084 "chatbot" "$timeout_sec"
  wait_for_actuator_up "$host" 8085 "message-job" "$timeout_sec"
  wait_for_actuator_up "$host" 8086 "account" "$timeout_sec"
  wait_for_actuator_up "$host" 8087 "fulfillment" "$timeout_sec"
}

resolve_stage_activity_id() {
  local gw="$1"
  local channel="${2:-c01}"
  local source="${3:-s01}"
  local id
  id=$(curl -sf "${gw}/api/v1/raffle/activity/query_stage_activity_id?channel=${channel}&source=${source}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data') or '')" 2>/dev/null || true)
  if [ -z "$id" ]; then
    echo "Failed to resolve stage activityId for channel=${channel} source=${source}" >&2
    return 1
  fi
  printf '%s' "$id"
}

assert_json_code() {
  local label="$1"
  local expected="$2"
  local body="$3"
  local actual
  actual=$(printf '%s' "$body" | python3 -c "import sys,json; print(json.load(sys.stdin).get('code',''))" 2>/dev/null || echo "")
  if [ "$actual" = "$expected" ]; then
    echo "  PASS  $label (code=$expected)"
    return 0
  fi
  echo "  FAIL  $label (expected code=$expected, got: ${body:0:160})" >&2
  return 1
}

assert_http_and_code() {
  local label="$1"
  local expected_http="$2"
  local expected_code="$3"
  local body_file="$4"
  local actual_http="${5:-}"
  local actual_code
  actual_code=$(python3 -c "import json; print(json.load(open('$body_file')).get('code',''))" 2>/dev/null || echo "")
  if [ -n "$actual_http" ] && [ "$actual_http" != "$expected_http" ]; then
    echo "  FAIL  $label (expected http=$expected_http, got http=$actual_http code=$actual_code)" >&2
    return 1
  fi
  if [ "$actual_code" = "$expected_code" ]; then
    if [ -n "$actual_http" ]; then
      echo "  PASS  $label (http=$expected_http code=$expected_code)"
    else
      echo "  PASS  $label (code=$expected_code)"
    fi
    return 0
  fi
  echo "  FAIL  $label (expected code=$expected_code, got http=${actual_http:-?} code=$actual_code)" >&2
  return 1
}

wait_for_xxl_admin() {
  local host="${1:-localhost}"
  local timeout_sec="${2:-120}"
  wait_for_http_up "http://${host}:9090/xxl-job-admin" "$timeout_sec" "xxl-job-admin"
}
