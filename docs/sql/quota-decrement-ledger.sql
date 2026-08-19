-- 已完成微服务架构的规范 DDL。
-- 第一段在 big_market_01 执行，第二段在 big_market_02 执行。

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
    -- 幂等约束：每个（用户、活动、业务操作）组合只能有一条账本记录。
    -- 事务内重试 INSERT 时会抛出 DuplicateKeyException；调用方将其视为已处理事件，
    -- 并立即返回 true。
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
