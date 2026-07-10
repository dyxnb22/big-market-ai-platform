-- Strategy award stock confirm outbox (G-01 P0).
-- Apply to big_market_01 and big_market_02 before enabling StrategyAwardStockConfirmJob.

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Pending strategy award stock confirm after award record saved';
