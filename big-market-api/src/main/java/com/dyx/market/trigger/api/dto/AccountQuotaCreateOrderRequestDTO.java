package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountQuotaCreateOrderRequestDTO implements Serializable {

    private String userId;

    private Long sku;

    private String outBusinessNo;

    /**
     * credit_pay_trade | rebate_no_pay_trade
     */
    private String orderTradeType;

}
