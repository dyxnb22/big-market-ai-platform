-- Idempotent learning-freeze demo alignment for both fresh and reused volumes.
--
-- The staged activity (100401) must never pick an award whose external
-- fulfillment endpoint is absent from the local stack. Keep the richer legacy
-- strategies as reading fixtures, but make the default stage deterministic:
-- one SKU exchange costs 5 credits and one draw awards the same 5 credits.

USE `big_market`;

UPDATE `award`
SET `award_key` = 'user_credit_random',
    `award_config` = '5,5',
    `award_desc` = '固定 5 积分【本地学习冻结演示奖品】',
    `update_time` = NOW()
WHERE `award_id` = 101;

UPDATE `strategy_award`
SET `award_rate` = CASE WHEN `award_id` = 101 THEN 1.0000 ELSE 0.0000 END,
    `award_title` = CASE WHEN `award_id` = 101 THEN '5积分' ELSE `award_title` END,
    `update_time` = NOW()
WHERE `strategy_id` = 10007;

USE `xxl_job`;

UPDATE `xxl_job_info`
SET `trigger_status` = 1,
    `schedule_type` = 'CRON',
    `schedule_conf` = '0/5 * * * * ?',
    `update_time` = NOW()
WHERE `executor_handler` IN (
  'DispatchCreditAwardTaskJob_DB1',
  'DispatchCreditAwardTaskJob_DB2'
);
