-- Docker MySQL init: reconcile / DLQ / chat session tables (G-02, G-05, G-06, G-07).
-- Canonical reference: docs/sql/reconcile-tables.sql
-- Stock decrement ledgers live on shared big_market (same as strategy_award / raffle_activity_sku).

USE `big_market`;

CREATE TABLE IF NOT EXISTS `strategy_award_stock_decrement_ledger` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `reservation_id`  VARCHAR(64)  NOT NULL,
    `strategy_id`     BIGINT       NOT NULL,
    `award_id`        INT          NOT NULL,
    `lock_surplus`    BIGINT       DEFAULT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'applied',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_reservation_id` (`reservation_id`),
    KEY `idx_strategy_award` (`strategy_id`, `award_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `activity_sku_stock_decrement_ledger` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `sku`             BIGINT       NOT NULL,
    `activity_id`     BIGINT       DEFAULT NULL,
    `lock_surplus`    BIGINT       NOT NULL,
    `reservation_id`  VARCHAR(128) DEFAULT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'applied',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sku_lock_surplus` (`sku`, `lock_surplus`),
    KEY `idx_activity` (`activity_id`),
    KEY `idx_reservation` (`reservation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `activity_sku_stock_restore_ledger` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `sku`             BIGINT       NOT NULL,
    `reservation_id`  VARCHAR(128) NOT NULL,
    `status`          VARCHAR(16) NOT NULL DEFAULT 'reserved',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_restore_reservation` (`reservation_id`),
    KEY `idx_restore_sku` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A central copy is the independent hand-off for cross-service compensation.
-- It is intentionally outside the user's market shard, so a market shard
-- outage cannot erase the only pending rollback/continuation record.
USE `big_market`;
CREATE TABLE IF NOT EXISTS `pending_remote_write_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `out_business_no` VARCHAR(128) NOT NULL,
    `operation`       VARCHAR(32)  NOT NULL,
    `payload`         TEXT         NOT NULL,
    `state`           VARCHAR(24)  NOT NULL DEFAULT 'pending',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_out_business_no_op` (`out_business_no`, `operation`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    `state`           VARCHAR(24)  NOT NULL DEFAULT 'pending',
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
    `deduct_state`  VARCHAR(16)  NOT NULL DEFAULT 'deducted',
    `refund_state`  VARCHAR(16)  NOT NULL DEFAULT 'none',
    `retry_count`   TINYINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_request` (`user_id`, `request_id`),
    KEY `idx_refund_pending` (`refund_state`, `retry_count`, `create_time`),
    KEY `idx_deduct_state` (`deduct_state`, `create_time`)
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

-- Quota-decrement idempotency ledger. The account-service routes the physical
-- table by user id, so all four tables must exist in both account shards.
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_000` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         VARCHAR(128) NOT NULL,
    `activity_id`     BIGINT       NOT NULL,
    `out_business_no` VARCHAR(64)  NOT NULL,
    `month`           VARCHAR(7)   NOT NULL,
    `day`             VARCHAR(10)  NOT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'applied',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_user_activity_biz` (`user_id`, `activity_id`, `out_business_no`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_001` LIKE `raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_002` LIKE `raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_003` LIKE `raffle_quota_decrement_ledger_000`;

USE `big_market_02`;

CREATE TABLE IF NOT EXISTS `mq_dead_letter` LIKE `big_market_01`.`mq_dead_letter`;
CREATE TABLE IF NOT EXISTS `pending_remote_write_task` LIKE `big_market_01`.`pending_remote_write_task`;
CREATE TABLE IF NOT EXISTS `chat_credit_session` LIKE `big_market_01`.`chat_credit_session`;
CREATE TABLE IF NOT EXISTS `strategy_award_stock_confirm_task` LIKE `big_market_01`.`strategy_award_stock_confirm_task`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_000` LIKE `big_market_01`.`raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_001` LIKE `big_market_01`.`raffle_quota_decrement_ledger_001`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_002` LIKE `big_market_01`.`raffle_quota_decrement_ledger_002`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_003` LIKE `big_market_01`.`raffle_quota_decrement_ledger_003`;

-- Older volumes: deduct_state is added by scripts/apply-reconcile-ddl.sh (idempotent).
