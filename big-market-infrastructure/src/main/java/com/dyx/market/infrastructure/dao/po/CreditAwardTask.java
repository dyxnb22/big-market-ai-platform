package com.dyx.market.infrastructure.dao.po;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Outbox row for award credit dispatch — Phase 2.2-B6 scaffold.
 *
 * One row is inserted inside the same transactionTemplate block as updateAwardRecordCompletedState
 * when account.award-credit-outbox.enabled=true. The outbox poller (DispatchCreditAwardTaskJob)
 * reads pending rows and calls IAccountCreditWriteAdapter.createOrder() using awardOrderId as
 * outBusinessNo. On idempotent success or duplicate-key the row is marked dispatched; on
 * repeated failure it is marked failed after max retries.
 *
 * Table: credit_award_task_000 .. credit_award_task_003 (same shard as user_award_record).
 */
@Data
public class CreditAwardTask {

    /** Auto-increment row id */
    private Long id;
    /** User id (shard key, matches user_award_record shard) */
    private String userId;
    /** Idempotency key: orderId from UserAwardRecordEntity; unique per award dispatch */
    private String awardOrderId;
    /** Credit amount to issue */
    private BigDecimal creditAmount;
    /** pending | dispatched | failed */
    private String state;
    /** Number of failed dispatch attempts */
    private Integer retryCount;
    /** Row creation time */
    private Date createTime;
    /** Last update time */
    private Date updateTime;

}
