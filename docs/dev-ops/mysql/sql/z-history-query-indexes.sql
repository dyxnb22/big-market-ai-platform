-- V20260812__history_query_indexes: additive migration for reused MySQL volumes.
-- Replaces the single-column idx_user_id on user_award_record_00X / user_credit_order_00X
-- with composite indexes so the server-side draw-history / credit-ledger queries
-- (where user_id = ? order by award_time|create_time desc limit 50) avoid filesort.
-- Fresh Docker volumes already receive the composite indexes from the schema dumps;
-- this file is idempotent and safe to re-run.

USE `big_market`;

CREATE TABLE IF NOT EXISTS `schema_migration` (
    `version`      VARCHAR(64)  NOT NULL,
    `description`  VARCHAR(255) NOT NULL,
    `applied_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Big Market schema migration ledger';

DROP PROCEDURE IF EXISTS `ensure_history_query_index`;
DELIMITER $$
CREATE PROCEDURE `ensure_history_query_index`(
    IN p_schema VARCHAR(64), IN p_table VARCHAR(64),
    IN p_index VARCHAR(64), IN p_columns VARCHAR(255), IN p_drop_index VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = p_schema AND table_name = p_table
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = p_schema AND table_name = p_table AND index_name = p_index
        ) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` ADD INDEX `', p_index, '` (', p_columns, ')');
            PREPARE history_idx_stmt FROM @ddl;
            EXECUTE history_idx_stmt;
            DEALLOCATE PREPARE history_idx_stmt;
        END IF;
        IF EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = p_schema AND table_name = p_table AND index_name = p_drop_index
        ) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_schema, '`.`', p_table, '` DROP INDEX `', p_drop_index, '`');
            PREPARE history_idx_stmt FROM @ddl;
            EXECUTE history_idx_stmt;
            DEALLOCATE PREPARE history_idx_stmt;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL `ensure_history_query_index`('big_market_01', 'user_award_record_000', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_01', 'user_award_record_001', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_01', 'user_award_record_002', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_01', 'user_award_record_003', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_award_record_000', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_award_record_001', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_award_record_002', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_award_record_003', 'idx_user_id_award_time', '`user_id`,`award_time`', 'idx_user_id');

CALL `ensure_history_query_index`('big_market_01', 'user_credit_order_000', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_01', 'user_credit_order_001', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_01', 'user_credit_order_002', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_01', 'user_credit_order_003', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_credit_order_000', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_credit_order_001', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_credit_order_002', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');
CALL `ensure_history_query_index`('big_market_02', 'user_credit_order_003', 'idx_user_id_create_time', '`user_id`,`create_time`', 'idx_user_id');

DROP PROCEDURE IF EXISTS `ensure_history_query_index`;
INSERT INTO `schema_migration` (`version`, `description`)
VALUES ('V20260812__history_query_indexes', 'Composite (user_id, time) indexes for draw-history and credit-ledger queries')
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`), `applied_time` = CURRENT_TIMESTAMP;
