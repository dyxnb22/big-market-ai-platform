-- Canonical DDL for the completed microservices architecture.
-- Run the first block in big_market_01 and the second block in big_market_02.

USE `big_market_01`;

CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_000` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT                      COMMENT 'Auto-increment row id',
    `user_id`         VARCHAR(128)  NOT NULL                                     COMMENT 'User id (shard key)',
    `activity_id`     BIGINT(12)    NOT NULL                                     COMMENT 'Activity id',
    `out_business_no` VARCHAR(64)   NOT NULL                                     COMMENT 'Idempotency key — raffle order outBusinessNo',
    `month`           VARCHAR(7)    NOT NULL                                     COMMENT 'Original monthly quota bucket',
    `day`             VARCHAR(10)   NOT NULL                                     COMMENT 'Original daily quota bucket',
    `status`          VARCHAR(16)   NOT NULL DEFAULT 'applied'                   COMMENT 'applied | rolled_back',
    `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP           COMMENT 'Row creation time',
    `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                             ON UPDATE CURRENT_TIMESTAMP        COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    -- Idempotency constraint: one ledger row per (user, activity, business-operation).
    -- The INSERT inside the transaction fails with DuplicateKeyException on retry;
    -- the caller treats this as an already-processed event and returns true immediately.
    UNIQUE KEY `uq_user_activity_biz` (`user_id`, `activity_id`, `out_business_no`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quota-decrement idempotency ledger — learning DDL';

CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_001` LIKE `raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_002` LIKE `raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_003` LIKE `raffle_quota_decrement_ledger_000`;

USE `big_market_02`;

CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_000` LIKE `big_market_01`.`raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_001` LIKE `big_market_01`.`raffle_quota_decrement_ledger_001`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_002` LIKE `big_market_01`.`raffle_quota_decrement_ledger_002`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_003` LIKE `big_market_01`.`raffle_quota_decrement_ledger_003`;
