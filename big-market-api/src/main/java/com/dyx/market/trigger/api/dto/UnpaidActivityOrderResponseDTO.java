package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UnpaidActivityOrderResponseDTO implements Serializable {

    private String userId;

    private String orderId;

    private String outBusinessNo;

    private BigDecimal payAmount;

}
