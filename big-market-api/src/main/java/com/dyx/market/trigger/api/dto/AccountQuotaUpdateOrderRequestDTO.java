package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 更新活动额度订单（标记发货/到账）请求对象。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountQuotaUpdateOrderRequestDTO implements Serializable {

    /** 用户 ID */
    private String userId;

    /** 业务幂等号 */
    private String outBusinessNo;

}
