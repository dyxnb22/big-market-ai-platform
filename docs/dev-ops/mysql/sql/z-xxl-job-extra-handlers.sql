-- Idempotent seed for reconcile / stock-confirm XXL handlers (jobs 7–13).
-- Older dev MySQL volumes may only have jobs 1–6 from an earlier xxl_job.sql dump.
USE `xxl_job`;

INSERT INTO `xxl_job_info` (`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`, `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`)
VALUES
	(7,1,'策略奖品库存确认(DB1)','2024-08-10 09:10:09','2024-08-10 09:15:00','XXL','','CRON','0/5 * * * * ?','DO_NOTHING','FIRST','StrategyAwardStockConfirmJob_DB1','','SERIAL_EXECUTION',0,0,'BEAN','','GLUE代码初始化','2024-08-10 09:10:09','',1,0,0),
	(8,1,'策略奖品库存确认(DB2)','2024-08-10 09:10:09','2024-08-10 09:15:00','XXL','','CRON','0/5 * * * * ?','DO_NOTHING','FIRST','StrategyAwardStockConfirmJob_DB2','','SERIAL_EXECUTION',0,0,'BEAN','','GLUE代码初始化','2024-08-10 09:10:09','',1,0,0),
	(9,1,'积分支付履约对账(DB1)','2024-08-10 09:10:09','2024-08-10 09:15:00','XXL','','CRON','0/10 * * * * ?','DO_NOTHING','FIRST','CreditPayDeliveryReconcileJob_DB1','','SERIAL_EXECUTION',0,0,'BEAN','','GLUE代码初始化','2024-08-10 09:10:09','',1,0,0),
	(10,1,'积分支付履约对账(DB2)','2024-08-10 09:10:09','2024-08-10 09:15:00','XXL','','CRON','0/10 * * * * ?','DO_NOTHING','FIRST','CreditPayDeliveryReconcileJob_DB2','','SERIAL_EXECUTION',0,0,'BEAN','','GLUE代码初始化','2024-08-10 09:10:09','',1,0,0),
	(11,1,'远程写对账','2024-08-10 09:10:09','2024-08-10 09:15:00','XXL','','CRON','0/10 * * * * ?','DO_NOTHING','FIRST','RemoteWriteReconcileJob','','SERIAL_EXECUTION',0,0,'BEAN','','GLUE代码初始化','2024-08-10 09:10:09','',1,0,0),
	(12,1,'DLQ重放','2024-08-10 09:10:09','2024-08-10 09:15:00','XXL','','CRON','0/10 * * * * ?','DO_NOTHING','FIRST','DlqReplayJob','','SERIAL_EXECUTION',0,0,'BEAN','','GLUE代码初始化','2024-08-10 09:10:09','',1,0,0),
	(13,1,'Chat退款对账','2024-08-10 09:10:09','2024-08-10 09:15:00','XXL','','CRON','0/10 * * * * ?','DO_NOTHING','FIRST','ChatRefundReconcileJob','','SERIAL_EXECUTION',0,0,'BEAN','','GLUE代码初始化','2024-08-10 09:10:09','',1,0,0)
ON DUPLICATE KEY UPDATE
  `job_desc` = VALUES(`job_desc`),
  `executor_handler` = VALUES(`executor_handler`),
  `schedule_conf` = VALUES(`schedule_conf`),
  `trigger_status` = VALUES(`trigger_status`);

-- The credit award Outbox is always enabled, so its two shard dispatchers must
-- run. The account service still deduplicates by award_order_id/out_business_no.
UPDATE `xxl_job_info`
SET `trigger_status` = 1,
    `schedule_type` = 'CRON',
    `schedule_conf` = '0/5 * * * * ?',
    `update_time` = NOW()
WHERE `executor_handler` IN (
  'DispatchCreditAwardTaskJob_DB1',
  'DispatchCreditAwardTaskJob_DB2'
);
