package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 未支付活动订单应答对象（额度充值下单返回）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UnpaidActivityOrderResponseDTO implements Serializable {

    /** 用户 ID */
    private String userId;

    /** 系统订单号 */
    private String orderId;

    /** 业务幂等号 */
    private String outBusinessNo;

    /** 应付金额 */
    private BigDecimal payAmount;

}
