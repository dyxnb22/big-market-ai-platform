-- Stock MySQL decrement idempotency ledgers (durable exactly-once flush).
-- Applied to shared big_market DB (same as strategy_award / raffle_activity_sku).

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Strategy award stock decrement ledger';

CREATE TABLE IF NOT EXISTS `activity_sku_stock_decrement_ledger` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `sku`             BIGINT       NOT NULL,
    `activity_id`     BIGINT       DEFAULT NULL,
    `lock_surplus`    BIGINT       NOT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'applied',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_sku_lock_surplus` (`sku`, `lock_surplus`),
    KEY `idx_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Activity SKU stock decrement ledger';
