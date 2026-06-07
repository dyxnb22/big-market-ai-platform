#!/usr/bin/env bash
set -euo pipefail

API="${API:-http://127.0.0.1:8098/api/v1}"
BASE_URL="${API%/api/v1}"

echo "Actuator health"
curl -fsS "$BASE_URL/actuator/health" | sed 's/,/,\n/g'
echo

echo "Login"
LOGIN_RESPONSE="$(curl -fsS "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"xiaofuge","password":"demo"}')"
TOKEN="$(printf '%s' "$LOGIN_RESPONSE" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"

echo "Token length: ${#TOKEN}"
test -n "$TOKEN"
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
curl -fsS "$API/admin/config/list" | sed 's/,/,\n/g'

echo
echo "Chatbot"
curl -fsS "$API/chatbot/ask" \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$TOKEN\",\"activityId\":100301,\"message\":\"帮我查询活动100301还能抽几次\"}" | sed 's/,/,\n/g'
echo
