-- Learning/reference DDL for MQ dead-letter persistence and replay.
-- Apply to big_market_01 (and big_market_02 if mirrored) before enabling DlqReplayJob.

CREATE TABLE IF NOT EXISTS `mq_dead_letter` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT                       COMMENT 'Auto-increment row id',
    `message_id`      VARCHAR(128) NOT NULL                                      COMMENT 'Unique per DLQ event or replay attempt',
    `business_message_id` VARCHAR(128) NOT NULL DEFAULT ''                     COMMENT 'Stable business key for re-DLQ reactivation',
    `queue`           VARCHAR(128) NOT NULL                                      COMMENT 'Original queue name before DLQ routing',
    `payload`         TEXT         NOT NULL                                      COMMENT 'Raw message body',
    `state`           VARCHAR(16)  NOT NULL DEFAULT 'pending'                    COMMENT 'pending | replayed | manual_pending',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0                            COMMENT 'Number of failed replay attempts',
    `consume_fail_count` TINYINT   NOT NULL DEFAULT 0                            COMMENT 'Consumer failures after replay',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP            COMMENT 'Row creation time',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP         COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_message_id` (`message_id`),
    KEY `idx_business_state` (`business_message_id`, `state`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ dead-letter outbox for automatic replay';
