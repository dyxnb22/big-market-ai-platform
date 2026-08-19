-- Docker MySQL 初始化：微服务栈的 credit_award_task Outbox 分片（G-03）。
-- 规范定义：docs/sql/credit-award-task-outbox.sql

USE `big_market_01`;

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
    UNIQUE KEY `uq_award_order_id` (`user_id`, `award_order_id`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Award credit outbox';

CREATE TABLE IF NOT EXISTS `credit_award_task_001` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_002` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_003` LIKE `credit_award_task_000`;

USE `big_market_02`;

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
    UNIQUE KEY `uq_award_order_id` (`user_id`, `award_order_id`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Award credit outbox';

CREATE TABLE IF NOT EXISTS `credit_award_task_001` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_002` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_003` LIKE `credit_award_task_000`;
