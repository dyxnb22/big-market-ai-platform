package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for the quota rollback (saga compensation) RPC.
 *
 * Requires the account-service idempotency ledger DDL and smoke validation when
 * service-oriented quota decrement mode is enabled.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountQuotaRollbackRequestDTO implements Serializable {

    private String userId;

    private Long activityId;

    /** Idempotency key — same value used in the matching decrementQuota call. */
    private String outBusinessNo;

}
