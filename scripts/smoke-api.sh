#!/usr/bin/env bash
set -euo pipefail

API="${API:-http://127.0.0.1:8080/api/v1}"
BASE_URL="${API%/api/v1}"

echo "Actuator health"
curl -fsS "$BASE_URL/actuator/health" | sed 's/,/,\n/g'
echo

echo "Login"
LOGIN_RESPONSE="$(curl -fsS "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}')"
TOKEN="$(printf '%s' "$LOGIN_RESPONSE" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
ADMIN_LOGIN_RESPONSE="$(curl -fsS "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"admin","password":"admin"}')"
ADMIN_TOKEN="$(printf '%s' "$ADMIN_LOGIN_RESPONSE" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"

echo "Token length: ${#TOKEN}"
test -n "$TOKEN"
echo "Admin token length: ${#ADMIN_TOKEN}"
test -n "$ADMIN_TOKEN"
echo

echo "Verify token"
curl -fsS "$API/auth/verify" -H "Authorization: $TOKEN" | sed 's/,/,\n/g'
echo

echo "Activity armory"
curl -fsS "$API/raffle/activity/armory?activityId=100301" | sed 's/,/,\n/g'
echo

echo "User activity account"
curl -fsS "$API/raffle/activity/query_user_activity_account_by_token" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"activityId":100301}' | sed 's/,/,\n/g'
echo

echo "Admin config list"
curl -fsS "$API/admin/config/list" \
  -H "Authorization: $ADMIN_TOKEN" | sed 's/,/,\n/g'

echo
echo "Chatbot"
curl -fsS "$API/chatbot/ask" \
  -H "Authorization: $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"requestId\":\"smoke-$(date +%s)\",\"activityId\":100301,\"message\":\"帮我查询活动100301还能抽几次\"}" | sed 's/,/,\n/g'
echo
