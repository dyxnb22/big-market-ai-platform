-- Learning-environment DDL reference. Apply manually only in local study environments.
-- This repository does not auto-apply this DDL; keep it as an explicit local learning reference.
-- This DDL is documentation of the intended credit_award_task outbox table.
-- Enable the outbox path only after the producer/consumer is implemented
-- and validated in a local learning run.
--
-- Sharding note: this table follows the same 2-DB / 4-table router pattern used by
-- big_market_01..02. credit_award_task_000..003 maps to the same user shard as
-- user_award_record so they always land in the same physical DB and can share one
-- transactionTemplate.execute() block.
--
-- Usage in AwardRepository.saveGiveOutPrizesAggregate (local learning):
--   Step 1 (inside transactionTemplate):
--     a. updateAwardRecordCompletedState  (user_award_record)
--     b. INSERT credit_award_task row     (state = 'pending')
--        -- user_credit_account write is REMOVED from this transaction
--   Step 2 (outside transaction, idempotent poller):
--     Scan credit_award_task WHERE state = 'pending' AND retry_count < max
--     Call IAccountCreditWriteAdapter.createOrder(userId, creditAmount, awardOrderId)
--     On success: UPDATE state = 'dispatched'
--     On failure: retry_count++; keep pending until max retries, then mark failed

-- Implementation note: activity_id and strategy_id were removed from the PO and mapper
-- because DistributeAwardEntity / buildDistributeUserAwardRecordEntity does not carry
-- them through to GiveOutPrizesAggregate. The unique key on (user_id, award_order_id)
-- is sufficient for dispatch correctness. Audit trail is available via user_award_record JOIN.
CREATE TABLE IF NOT EXISTS `credit_award_task_000` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT                       COMMENT 'Auto-increment row id',
    `user_id`         VARCHAR(32)  NOT NULL                                      COMMENT 'User id (shard key)',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Award credit outbox — learning DDL';

-- Repeat for shards _001, _002, _003 (router creates 4 tables per DB).
CREATE TABLE IF NOT EXISTS `credit_award_task_001` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_002` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_003` LIKE `credit_award_task_000`;
