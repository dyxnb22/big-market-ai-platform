-- Reconcile / DLQ / chat session tables (G-02, G-05, G-06, G-07).
-- Apply the independent pending_remote_write_task copy to big_market, and the
-- DLQ/chat/stock tables to big_market_01 and big_market_02 (Docker:
-- docs/dev-ops/mysql/sql/z-reconcile-tables.sql).

USE `big_market`;

CREATE TABLE IF NOT EXISTS `pending_remote_write_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `out_business_no` VARCHAR(128) NOT NULL,
    `operation`       VARCHAR(32)  NOT NULL COMMENT 'credit_create | quota_create | quota_update | quota_rollback',
    `payload`         TEXT         NOT NULL,
    `state`           VARCHAR(24)  NOT NULL DEFAULT 'pending' COMMENT 'pending | continuation_pending | done | failed',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_out_business_no_op` (`out_business_no`, `operation`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Central remote RPC write reconcile outbox';

USE `big_market_01`;

CREATE TABLE IF NOT EXISTS `mq_dead_letter` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `message_id`   VARCHAR(128) NOT NULL,
    `business_message_id` VARCHAR(128) NOT NULL DEFAULT '',
    `queue`           VARCHAR(128) NOT NULL,
    `payload`        TEXT         NOT NULL,
    `state`          VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending | replayed | manual_pending',
    `retry_count`    TINYINT      NOT NULL DEFAULT 0,
    `consume_fail_count` TINYINT  NOT NULL DEFAULT 0,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_message_id` (`message_id`),
    KEY `idx_business_state` (`business_message_id`, `state`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ dead-letter persistence for replay';

CREATE TABLE IF NOT EXISTS `pending_remote_write_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `out_business_no` VARCHAR(128) NOT NULL,
    `operation`       VARCHAR(32)  NOT NULL COMMENT 'legacy compatibility copy',
    `payload`         TEXT         NOT NULL,
    `state`           VARCHAR(24)  NOT NULL DEFAULT 'pending' COMMENT 'legacy compatibility copy',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_out_business_no_op` (`out_business_no`, `operation`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Remote RPC write reconcile outbox';

CREATE TABLE IF NOT EXISTS `chat_credit_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       VARCHAR(32)  NOT NULL,
    `request_id`    VARCHAR(64)  NOT NULL,
    `deducted`      TINYINT(1)   NOT NULL DEFAULT 0,
    `deduct_amount` INT          NOT NULL DEFAULT 0,
    `deduct_state`  VARCHAR(16)  NOT NULL DEFAULT 'deducted' COMMENT 'deducting | deducted | failed',
    `refund_state`  VARCHAR(16)  NOT NULL DEFAULT 'none' COMMENT 'none | pending | refunding | refunded | manual_pending',
    `retry_count`   TINYINT      NOT NULL DEFAULT 0,
    `last_error`    VARCHAR(512) DEFAULT NULL,
    `next_retry_time` DATETIME   DEFAULT NULL,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_request` (`user_id`, `request_id`),
    KEY `idx_refund_pending` (`refund_state`, `next_retry_time`, `retry_count`, `create_time`),
    KEY `idx_deduct_state` (`deduct_state`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chatbot credit deduct/refund session';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Pending strategy award stock confirm after award save';
