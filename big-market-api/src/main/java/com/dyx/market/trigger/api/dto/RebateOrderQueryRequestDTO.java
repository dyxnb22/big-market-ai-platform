package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request DTO for rebate order read queries (Phase 3-A/B: rebate read RPC contract).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RebateOrderQueryRequestDTO implements Serializable {

    /** 用户ID */
    private String userId;
    /** 业务ID；签到则是日期字符串 */
    private String outBusinessNo;

}
