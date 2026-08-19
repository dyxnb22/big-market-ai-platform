-- 已完成本地微服务架构的学习/参考 DDL。
-- 在开发环境本地执行后，才能启用完整功能集。

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
    -- 幂等约束：同一个奖品订单最多只能生成一条 Outbox 记录。
    -- 事务内重试 INSERT 时会抛出 DuplicateKeyException，调用方将其视为已处理事件，
    -- 并安全地回滚当前事务。
    UNIQUE KEY `uq_award_order_id` (`user_id`, `award_order_id`),
    KEY `idx_state_retry` (`state`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Award credit outbox — learning DDL';

-- 对 _001、_002、_003 分片重复执行（路由器在每个数据库中创建 4 张表）。
CREATE TABLE IF NOT EXISTS `credit_award_task_001` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_002` LIKE `credit_award_task_000`;
CREATE TABLE IF NOT EXISTS `credit_award_task_003` LIKE `credit_award_task_000`;
