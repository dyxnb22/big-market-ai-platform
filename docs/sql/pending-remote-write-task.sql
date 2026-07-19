-- G-05: PendingRemoteWriteTask table (canonical: docs/sql/reconcile-tables.sql).

CREATE TABLE IF NOT EXISTS `pending_remote_write_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `out_business_no` VARCHAR(128) NOT NULL,
    `operation`       VARCHAR(32)  NOT NULL COMMENT 'credit_create | quota_create | quota_update',
    `payload`         TEXT         NOT NULL,
    `state`           VARCHAR(24)  NOT NULL DEFAULT 'pending' COMMENT 'pending | continuation_pending | done | failed',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_out_business_no_op` (`out_business_no`, `operation`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Remote RPC write reconcile outbox';
