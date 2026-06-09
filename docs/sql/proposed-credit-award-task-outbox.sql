-- PROPOSED ONLY — Phase 2.2-B5 outbox readiness scaffold.
-- DO NOT run against production. DO NOT add to any Flyway/Liquibase migration.
-- This DDL is documentation of the intended credit_award_task outbox table.
-- Production wiring must wait until the outbox producer/consumer is implemented
-- and validated in a dedicated batch (Phase 2.2-B6 or later).
--
-- Sharding note: this table follows the same 2-DB / 4-table router pattern used by
-- big_market_01..02. credit_award_task_00..03 maps to the same user shard as
-- user_award_record so they always land in the same physical DB and can share one
-- transactionTemplate.execute() block.
--
-- Usage in AwardRepository.saveGiveOutPrizesAggregate (future):
--   Step 1 (inside transactionTemplate):
--     a. updateAwardRecordCompletedState  (user_award_record)
--     b. INSERT credit_award_task row     (state = 'pending')
--        -- user_credit_account write is REMOVED from this transaction
--   Step 2 (outside transaction, idempotent poller):
--     Scan credit_award_task WHERE state = 'pending' AND retry_count < max
--     Call IAccountCreditWriteAdapter.createOrder(userId, creditAmount, awardOrderId)
--     On success: UPDATE state = 'dispatched'
--     On failure: retry_count++; keep pending until max retries, then mark failed

CREATE TABLE IF NOT EXISTS `credit_award_task_00` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT                       COMMENT 'Auto-increment row id',
    `user_id`         VARCHAR(32)  NOT NULL                                      COMMENT 'User id (shard key)',
    `activity_id`     BIGINT       NOT NULL                                      COMMENT 'Activity id for audit trail',
    `strategy_id`     BIGINT       NOT NULL                                      COMMENT 'Strategy id for audit trail',
    `award_order_id`  VARCHAR(64)  NOT NULL                                      COMMENT 'Idempotency key — orderId from UserAwardRecordEntity; unique per award dispatch',
    `credit_amount`   DECIMAL(10,2) NOT NULL                                     COMMENT 'Credit amount to issue to user',
    `state`           VARCHAR(16)  NOT NULL DEFAULT 'pending'                    COMMENT 'pending | dispatched | failed',
    `retry_count`     TINYINT      NOT NULL DEFAULT 0                            COMMENT 'Number of failed dispatch attempts',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP            COMMENT 'Row creation time',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP         COMMENT 'Last update time',
    PRIMARY KEY (`id`),
    -- Idempotency constraint: a given award order can only produce one outbox row.
    -- The INSERT inside the transaction will fail with DuplicateKeyException on retry,
    -- which the caller treats as an already-processed event and rolls back cleanly.
    UNIQUE KEY `uq_award_order_id` (`user_id`, `award_order_id`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Award credit outbox — proposed Phase 2.2-B5';

-- Repeat for shards _01, _02, _03 (router creates 4 tables per DB).
CREATE TABLE IF NOT EXISTS `credit_award_task_01` LIKE `credit_award_task_00`;
CREATE TABLE IF NOT EXISTS `credit_award_task_02` LIKE `credit_award_task_00`;
CREATE TABLE IF NOT EXISTS `credit_award_task_03` LIKE `credit_award_task_00`;
