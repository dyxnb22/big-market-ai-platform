-- Docker MySQL init: reconcile / DLQ / chat session tables (G-02, G-05, G-06, G-07).
-- Canonical reference: docs/sql/reconcile-tables.sql

USE `big_market_01`;

CREATE TABLE IF NOT EXISTS `mq_dead_letter` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `message_id`   VARCHAR(128) NOT NULL,
    `business_message_id` VARCHAR(128) NOT NULL DEFAULT '',
    `queue`           VARCHAR(128) NOT NULL,
    `payload`      TEXT         NOT NULL,
    `state`        VARCHAR(16)  NOT NULL DEFAULT 'pending',
    `retry_count`  TINYINT      NOT NULL DEFAULT 0,
    `consume_fail_count` TINYINT NOT NULL DEFAULT 0,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_message_id` (`message_id`),
    KEY `idx_business_state` (`business_message_id`, `state`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `pending_remote_write_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `out_business_no` VARCHAR(128) NOT NULL,
    `operation`       VARCHAR(32)  NOT NULL,
    `payload`         TEXT         NOT NULL,
    `state`           VARCHAR(16)  NOT NULL DEFAULT 'pending',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_out_business_no_op` (`out_business_no`, `operation`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chat_credit_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       VARCHAR(32)  NOT NULL,
    `request_id`    VARCHAR(64)  NOT NULL,
    `deducted`      TINYINT(1)   NOT NULL DEFAULT 0,
    `deduct_amount` INT          NOT NULL DEFAULT 0,
    `refund_state`  VARCHAR(16)  NOT NULL DEFAULT 'none',
    `retry_count`   TINYINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_request_id` (`request_id`),
    KEY `idx_refund_pending` (`refund_state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `strategy_award_stock_confirm_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         VARCHAR(32)  NOT NULL,
    `order_id`        VARCHAR(64)  NOT NULL,
    `strategy_id`     BIGINT       NOT NULL,
    `award_id`        INT          NOT NULL,
    `reservation_id`  VARCHAR(64)  NOT NULL,
    `lock_surplus`    BIGINT       DEFAULT NULL,
    `state`           VARCHAR(16)  NOT NULL DEFAULT 'pending',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_order_id` (`user_id`, `order_id`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE `big_market_02`;

CREATE TABLE IF NOT EXISTS `mq_dead_letter` LIKE `big_market_01`.`mq_dead_letter`;
CREATE TABLE IF NOT EXISTS `pending_remote_write_task` LIKE `big_market_01`.`pending_remote_write_task`;
CREATE TABLE IF NOT EXISTS `chat_credit_session` LIKE `big_market_01`.`chat_credit_session`;
CREATE TABLE IF NOT EXISTS `strategy_award_stock_confirm_task` LIKE `big_market_01`.`strategy_award_stock_confirm_task`;
