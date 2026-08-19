-- 同时适配新建卷与复用卷的学习冻结演示数据，且脚本可幂等执行。
--
-- 已上架活动（100401）绝不能选择本地栈中不存在外部履约端点的奖品。
-- 保留更丰富的历史策略作为阅读样例，但让默认阶段保持确定性：
-- 一次 SKU 兑换消耗 5 积分，一次抽奖也奖励相同的 5 积分。

USE `big_market`;

-- 历史 100301 策略引用了七服务栈中不存在的履约处理器。
-- 在数据库门禁和复用卷的 Nacos 展示配置中都将其关闭。
UPDATE `raffle_activity`
SET `state` = 'close', `update_time` = NOW()
WHERE `activity_id` = 100301;

UPDATE `nacos_config`.`config_info`
SET `content` = REPLACE(`content`, 'activity.100301.state.value=online', 'activity.100301.state.value=closed'),
    `md5` = MD5(REPLACE(`content`, 'activity.100301.state.value=online', 'activity.100301.state.value=closed')),
    `gmt_modified` = NOW()
WHERE `data_id` = 'big-market-platform-config'
  AND `group_id` = 'DEFAULT_GROUP';

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
