-- Learning/reference DDL for the completed local microservices architecture.
-- Apply locally to enable the full feature set in development.

CREATE TABLE IF NOT EXISTS `credit_award_task_000` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT                       COMMENT 'Auto-increment row id',
    `user_id`         VARCHAR(32)  NOT NULL                                      COMMENT 'User id (shard key)',
    `award_order_id`  VARCHAR(64)  NOT NULL                                      COMMENT 'Idempotency key — orderId from UserAwardRecordEntity; unique per award dispatch',
    `credit_amount`   DECIMAL(10,2) NOT NULL                                     COMMENT 'Credit amount to issue to user',
    `state`           VARCHAR(16)  NOT NULL DEFAULT 'pending'                    COMMENT 'pending | dispatched | failed',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0                            COMMENT 'Number of failed dispatch attempts',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP            COMMENT 'Row creation time',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP         COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    -- Idempotency constraint: a given award order can only produce one outbox row.
    -- The INSERT inside the transaction will fail with DuplicateKeyException on retry,
    -- which the caller treats as an already-processed event and rolls back cleanly.
    UNIQUE KEY `uq_award_order_id` (`user_id`, `award_order_id`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Award credit outbox — learning DDL';

-- Repeat for shards _001, _002, _003 (router creates 4 tables per DB).
CREATE TABLE IF NOT EXISTS `credit_award_task_001` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_002` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_003` LIKE `credit_award_task_000`;
