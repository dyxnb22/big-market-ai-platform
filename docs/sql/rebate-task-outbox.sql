-- Learning/reference DDL for the completed local microservices architecture.
-- Apply locally to enable the full feature set in development.

CREATE TABLE IF NOT EXISTS `rebate_task_outbox_000` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT                COMMENT 'Auto-increment row id',
    `user_id`       VARCHAR(32)  NOT NULL                               COMMENT 'User id (shard key)',
    `topic`         VARCHAR(128) NOT NULL                               COMMENT 'MQ topic',
    `message_id`    VARCHAR(64)  NOT NULL                               COMMENT 'Idempotency key from rebate task entity',
    `message`       TEXT         NOT NULL                               COMMENT 'Serialized event payload',
    `state`         VARCHAR(16)  NOT NULL DEFAULT 'create'              COMMENT 'create | completed | fail',
    `retry_count`   TINYINT      NOT NULL DEFAULT 0                     COMMENT 'Future isolated dispatcher retry count',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP     COMMENT 'Row creation time',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP      COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_message_id` (`user_id`, `message_id`),
    KEY `idx_state_update` (`state`, `update_time`),
    KEY `idx_topic_state_update` (`topic`, `state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Rebate task outbox - learning DDL';

CREATE TABLE IF NOT EXISTS `rebate_task_outbox_001` LIKE `rebate_task_outbox_000`;
CREATE TABLE IF NOT EXISTS `rebate_task_outbox_002` LIKE `rebate_task_outbox_000`;
CREATE TABLE IF NOT EXISTS `rebate_task_outbox_003` LIKE `rebate_task_outbox_000`;
