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
public class CreditTradeRequestDTO implements Serializable {

    /** 用户ID */
    private String userId;

    /**
     * 交易名称 — REBATE | CONVERT_SKU
     * Maps to {@code TradeNameVO.name()}
     */
    private String tradeName;

    /**
     * 交易类型 — forward (增) | reverse (减)
     * Maps to {@code TradeTypeVO.code()}
     */
    private String tradeType;

    /** 交易金额（正数） */
    private BigDecimal amount;

    /** 业务幂等ID，由调用方保证唯一 */
    private String outBusinessNo;

}
