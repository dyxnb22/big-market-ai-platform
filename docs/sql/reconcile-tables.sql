-- Reconcile / DLQ / chat session tables (G-02, G-05, G-06, G-07).
-- Apply to big_market_01 and big_market_02 (Docker: docs/dev-ops/mysql/sql/z-reconcile-tables.sql).

CREATE TABLE IF NOT EXISTS `mq_dead_letter` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `message_id`   VARCHAR(64)  NOT NULL,
    `queue`           VARCHAR(128) NOT NULL,
    `payload`      TEXT         NOT NULL,
    `state`        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending | replayed | manual_pending',
    `retry_count`  TINYINT      NOT NULL DEFAULT 0,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_message_id` (`message_id`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ dead-letter persistence for replay';

CREATE TABLE IF NOT EXISTS `pending_remote_write_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `out_business_no` VARCHAR(128) NOT NULL,
    `operation`       VARCHAR(32)  NOT NULL COMMENT 'credit_create | quota_create | quota_update',
    `payload`         TEXT         NOT NULL,
    `state`           VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending | done | failed',
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
    `refund_state`  VARCHAR(16)  NOT NULL DEFAULT 'none' COMMENT 'none | refunded | pending',
    `retry_count`   TINYINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_request_id` (`request_id`),
    KEY `idx_refund_pending` (`refund_state`, `retry_count`, `create_time`)
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
