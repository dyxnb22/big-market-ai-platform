package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Persistent object for raffle_quota_decrement_ledger_{000..003}.
 *
 * Phase 2.2-B12 idempotency ledger for AccountQuotaServiceRPC.decrementQuota.
 * The UNIQUE KEY (user_id, activity_id, out_business_no) is the guard against
 * double-decrement on duplicate RPC calls.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleQuotaDecrementLedger {

    private Long id;

    /** Shard key — must match the user's raffle_activity_account shard. */
    private String userId;

    private Long activityId;

    /** Idempotency key — equals the raffle order's outBusinessNo. */
    private String outBusinessNo;

    /** applied | rolled_back */
    private String status;

    private Date createTime;

    private Date updateTime;

}
