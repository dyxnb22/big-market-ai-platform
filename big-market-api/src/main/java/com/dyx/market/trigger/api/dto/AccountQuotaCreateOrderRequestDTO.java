package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建活动额度充值订单请求对象。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountQuotaCreateOrderRequestDTO implements Serializable {

    /** 用户 ID */
    private String userId;

    /** SKU 商品编号 */
    private Long sku;

    /** 业务幂等号，由调用方保证唯一 */
    private String outBusinessNo;

    /**
     * 订单交易类型：credit_pay_trade | rebate_no_pay_trade
     */
    private String orderTradeType;

}
