-- V20260719__bounded_retry_states: additive migration for reused MySQL volumes.
-- Fresh Docker volumes already receive these columns from the full schema dumps
-- and z-reconcile-tables.sql; this file makes the same contract explicit for
-- old volumes and records the applied version in a small central ledger.

USE `big_market`;

CREATE TABLE IF NOT EXISTS `schema_migration` (
    `version`      VARCHAR(64)  NOT NULL,
    `description`  VARCHAR(255) NOT NULL,
    `applied_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Big Market schema migration ledger';

DROP PROCEDURE IF EXISTS `ensure_bounded_retry_column`;
DELIMITER $$
CREATE PROCEDURE `ensure_bounded_retry_column`(
    IN p_schema VARCHAR(64), IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = p_schema AND table_name = p_table AND column_name = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE bounded_retry_stmt FROM @ddl;
        EXECUTE bounded_retry_stmt;
        DEALLOCATE PREPARE bounded_retry_stmt;
    END IF;
END$$
DELIMITER ;

CALL `ensure_bounded_retry_column`('big_market_01', 'task', 'retry_count', 'TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''发送失败次数'' AFTER `state`');
CALL `ensure_bounded_retry_column`('big_market_01', 'task', 'next_retry_time', 'DATETIME NULL AFTER `retry_count`');
CALL `ensure_bounded_retry_column`('big_market_01', 'task', 'last_error', 'VARCHAR(512) NULL AFTER `next_retry_time`');
CALL `ensure_bounded_retry_column`('big_market_02', 'task', 'retry_count', 'TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''发送失败次数'' AFTER `state`');
CALL `ensure_bounded_retry_column`('big_market_02', 'task', 'next_retry_time', 'DATETIME NULL AFTER `retry_count`');
CALL `ensure_bounded_retry_column`('big_market_02', 'task', 'last_error', 'VARCHAR(512) NULL AFTER `next_retry_time`');
CALL `ensure_bounded_retry_column`('big_market_01', 'chat_credit_session', 'last_error', 'VARCHAR(512) NULL AFTER `retry_count`');
CALL `ensure_bounded_retry_column`('big_market_01', 'chat_credit_session', 'next_retry_time', 'DATETIME NULL AFTER `last_error`');
CALL `ensure_bounded_retry_column`('big_market_02', 'chat_credit_session', 'last_error', 'VARCHAR(512) NULL AFTER `retry_count`');
CALL `ensure_bounded_retry_column`('big_market_02', 'chat_credit_session', 'next_retry_time', 'DATETIME NULL AFTER `last_error`');

DROP PROCEDURE IF EXISTS `ensure_bounded_retry_column`;
INSERT INTO `schema_migration` (`version`, `description`)
VALUES ('V20260719__bounded_retry_states', 'Add bounded retry metadata and manual-pending states')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `applied_time` = CURRENT_TIMESTAMP;
