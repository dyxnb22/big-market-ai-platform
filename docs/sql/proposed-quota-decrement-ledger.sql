-- PROPOSED ONLY — Phase 2.2-B12 quota-decrement idempotency foundation.
-- DO NOT run against production. DO NOT add to any Flyway/Liquibase migration.
-- This DDL is documentation of the intended raffle_quota_decrement_ledger table.
-- Production wiring must wait until decrementQuota is validated end-to-end and
-- the remote-quota-decrement flag is promoted (B13+).
--
-- Sharding note: follows the same 2-DB / 4-table router pattern as raffle_activity_account.
-- raffle_quota_decrement_ledger_{000..003} maps to the same user shard as
-- raffle_activity_account so they land in the same physical DB and share one
-- transactionTemplate.execute() block.
--
-- Idempotency contract:
--   The UNIQUE KEY on (user_id, activity_id, out_business_no) ensures that
--   AccountQuotaServiceRPC.decrementQuota is idempotent:
--     - First call: INSERT succeeds → quota decrement proceeds atomically.
--     - Duplicate call: DuplicateKeyException caught → return true without re-decrement.
--     - Rollback: status updated to 'rolled_back'; the slot is restored separately.
--
-- Status values:
--   applied      — quota was decremented; raffle may proceed.
--   rolled_back  — decrement was reversed via rollbackQuota (saga compensation).
--
-- Usage in AccountQuotaServiceRPC.decrementQuota (B12):
--   Inside transactionTemplate.execute():
--     a. INSERT raffle_quota_decrement_ledger row (status='applied')
--        — DuplicateKeyException → return true (idempotent re-delivery)
--     b. UPDATE raffle_activity_account SET total_count_surplus - 1 WHERE surplus > 0
--        — 0 rows affected → rollback + return false (quota exhausted)
--     c. UPDATE or INSERT raffle_activity_account_month (current month)
--     d. UPDATE or INSERT raffle_activity_account_day (current day)
--     e. Commit → return true

CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_000` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT                      COMMENT 'Auto-increment row id',
    `user_id`         VARCHAR(128)  NOT NULL                                     COMMENT 'User id (shard key)',
    `activity_id`     BIGINT(12)    NOT NULL                                     COMMENT 'Activity id',
    `out_business_no` VARCHAR(64)   NOT NULL                                     COMMENT 'Idempotency key — raffle order outBusinessNo',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Quota-decrement idempotency ledger — proposed Phase 2.2-B12';

CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_001` LIKE `raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_002` LIKE `raffle_quota_decrement_ledger_000`;
CREATE TABLE IF NOT EXISTS `raffle_quota_decrement_ledger_003` LIKE `raffle_quota_decrement_ledger_000`;
