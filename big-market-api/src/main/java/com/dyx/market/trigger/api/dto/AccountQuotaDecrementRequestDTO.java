package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for the deferred quota-decrement RPC.
 *
 * default implementation. The corresponding provider method returns
 * UN_ERROR until RaffleActivityPartakeService quota-decrement wiring is
 * fully validated and promoted. No callers are wired at this stage.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountQuotaDecrementRequestDTO implements Serializable {

    private String userId;

    private Long activityId;

    /** Idempotency key — matches the raffle order's outBusinessNo. */
    private String outBusinessNo;

}
