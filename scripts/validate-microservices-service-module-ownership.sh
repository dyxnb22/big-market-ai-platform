#!/usr/bin/env bash
# Repo-only service module ownership regression validator.
#
# This script checks that dark-launch service modules do not accidentally grow
# runtime surfaces or direct DAO couplings outside their current ownership.

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0
FAIL=0

pass() { echo "[PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $*"; FAIL=$((FAIL + 1)); }

echo ""
echo "========================================================================"
echo "  Microservices Service Module Ownership Validator"
echo "========================================================================"

count_matches() {
  local path="$1"
  local pattern="$2"
  if [[ ! -e "$path" ]]; then
    echo 0
    return
  fi
  grep -RInE "$pattern" "$path" --include='*.java' --include='*.xml' --include='*.yml' --include='*.yaml' 2>/dev/null \
    | grep -v '/target/' \
    | wc -l | tr -d ' '
}

assert_zero() {
  local label="$1" path="$2" pattern="$3"
  local count
  count=$(count_matches "$path" "$pattern")
  if [[ "$count" -eq 0 ]]; then
    pass "$label"
  else
    fail "$label ($count match(es))"
    grep -RInE "$pattern" "$path" --include='*.java' --include='*.xml' --include='*.yml' --include='*.yaml' 2>/dev/null \
      | grep -v '/target/' \
      | sed 's#^#       #'
  fi
}

assert_file_exists() {
  local label="$1" file="$2"
  [[ -f "$file" ]] && pass "$label" || fail "$label — missing: $file"
}

ACTIVITY="$REPO_ROOT/big-market-activity-service"
STRATEGY="$REPO_ROOT/big-market-strategy-service"
REBATE="$REPO_ROOT/big-market-rebate-service"
FULFILLMENT="$REPO_ROOT/big-market-fulfillment-service"
MESSAGE_JOB="$REPO_ROOT/big-market-message-job-service"

echo ""
echo "── 1. activity-service remains dark-launch only ──"
assert_file_exists "activity-service launcher exists" "$ACTIVITY/src/main/java/com/dyx/market/activity/ActivityServiceApplication.java"
ACTIVITY_MAPPER_COUNT=$(find "$ACTIVITY/src/main/resources" -path '*/mybatis/mapper/*' -type f -name '*.xml' 2>/dev/null | wc -l | tr -d ' ')
if [[ "$ACTIVITY_MAPPER_COUNT" -eq 0 ]]; then
  pass "activity-service has no mapper XML"
else
  fail "activity-service has mapper XML files ($ACTIVITY_MAPPER_COUNT)"
  find "$ACTIVITY/src/main/resources" -path '*/mybatis/mapper/*' -type f -name '*.xml' 2>/dev/null | sed 's#^#       #'
fi
assert_zero "activity-service has no Dubbo provider" "$ACTIVITY/src/main/java" '@DubboService'
assert_zero "activity-service has no REST controller" "$ACTIVITY/src/main/java" '@RestController'
assert_zero "activity-service has no MQ listener" "$ACTIVITY/src/main/java" '@RabbitListener'
assert_zero "activity-service has no XXL job" "$ACTIVITY/src/main/java" '@XxlJob'
assert_zero "activity-service has no mapper scan/mapper annotations" "$ACTIVITY/src/main/java" '@MapperScan|@Mapper'

echo ""
echo "── 2. strategy-service DAO ownership does not expand outside strategy ──"
assert_file_exists "strategy-service launcher exists" "$STRATEGY/src/main/java/com/dyx/market/strategy/StrategyServiceApplication.java"
assert_zero "strategy-service does not import non-strategy infrastructure DAOs" "$STRATEGY/src/main/java" 'com\.dyx\.market\.infrastructure\.dao\.(IRaffle|IUser|ICredit|IAward|IDaily|ITask)'
BAD_STRATEGY_MAPPERS=$(find "$STRATEGY/src/main/resources/mybatis/mapper" -type f -name '*.xml' 2>/dev/null \
  | grep -Ev '/(strategy|strategy_award|strategy_rule|rule_tree|rule_tree_node|rule_tree_node_line)_mapper\.xml$' || true)
if [[ -z "$BAD_STRATEGY_MAPPERS" ]]; then
  pass "strategy-service mapper XML filenames are strategy-only"
else
  fail "strategy-service has non-strategy mapper XML files"
  printf '%s\n' "$BAD_STRATEGY_MAPPERS" | sed 's#^#       #'
fi
for mapper in strategy_mapper.xml strategy_award_mapper.xml strategy_rule_mapper.xml rule_tree_mapper.xml rule_tree_node_mapper.xml rule_tree_node_line_mapper.xml; do
  assert_file_exists "strategy-service owns mapper $mapper" "$STRATEGY/src/main/resources/mybatis/mapper/mysql/$mapper"
done

echo ""
echo "── 3. rebate-service does not use shared task repository DAO directly ──"
assert_file_exists "rebate-service provider exists" "$REBATE/src/main/java/com/dyx/market/rebate/provider/RebateServiceRPC.java"
assert_zero "rebate-service does not import ITaskDao directly" "$REBATE/src/main/java" 'ITaskDao|com\.dyx\.market\.infrastructure\.dao\.ITaskDao'
assert_zero "rebate-service does not use TaskRepository directly" "$REBATE/src/main/java" 'TaskRepository'
assert_zero "rebate-service mapper XMLs exclude legacy task mapper" "$REBATE/src/main/resources" 'task_mapper\.xml'

echo ""
echo "── 4. fulfillment-service repositories do not use foreign DAOs directly ──"
assert_file_exists "fulfillment-service provider exists" "$FULFILLMENT/src/main/java/com/dyx/market/fulfillment/provider/FulfillmentAwardServiceRPC.java"
assert_zero "fulfillment-service has no direct activity/account/credit DAO import" "$FULFILLMENT/src/main/java" 'IRaffleActivity|IUserRaffleOrderDao|IUserCredit|ICreditAwardTaskDao|IRaffleQuota|com\.dyx\.market\.infrastructure\.dao\.(IRaffle|IUserCredit|ICredit)'
BAD_FULFILLMENT_MAPPERS=$(find "$FULFILLMENT/src/main/resources/mybatis/mapper" -type f -name '*.xml' 2>/dev/null \
  | grep -E '/(raffle_|user_credit_|credit_award_task)' \
  | grep -Ev '/(user_credit_account_mapper|credit_award_task_mapper)\.xml$' || true)
if [[ -z "$BAD_FULFILLMENT_MAPPERS" ]]; then
  pass "fulfillment-service has no unapproved repository-owned foreign mapper XML"
else
  fail "fulfillment-service has unapproved repository-owned foreign mapper XML"
  printf '%s\n' "$BAD_FULFILLMENT_MAPPERS" | sed 's#^#       #'
fi
for mapper in user_credit_account_mapper.xml credit_award_task_mapper.xml; do
  assert_file_exists "fulfillment-service local learning compatibility mapper present: $mapper" "$FULFILLMENT/src/main/resources/mybatis/mapper/mysql/$mapper"
done

echo ""
echo "── 5. message-job-service credit-award task job uses port boundary ──"
JOB="$MESSAGE_JOB/src/main/java/com/dyx/market/message/job/config/DispatchCreditAwardTaskJob.java"
assert_file_exists "DispatchCreditAwardTaskJob exists" "$JOB"
if [[ -f "$JOB" ]] && grep -q 'ICreditAwardTaskDispatchPort' "$JOB" 2>/dev/null; then
  pass "DispatchCreditAwardTaskJob uses ICreditAwardTaskDispatchPort"
else
  fail "DispatchCreditAwardTaskJob does not use ICreditAwardTaskDispatchPort"
fi
assert_zero "message-job-service job does not import ICreditAwardTaskDao directly" "$JOB" 'ICreditAwardTaskDao|com\.dyx\.market\.infrastructure\.dao\.ICreditAwardTaskDao'

echo ""
echo "Summary: $PASS PASS, $FAIL FAIL"
if [[ "$FAIL" -eq 0 ]]; then
  echo "RESULT: ALL CHECKS PASSED - service module ownership boundaries hold"
  exit 0
fi
echo "RESULT: $FAIL CHECK(S) FAILED"
exit 1
