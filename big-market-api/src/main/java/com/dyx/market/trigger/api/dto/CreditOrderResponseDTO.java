package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户积分流水应答对象（服务端积分账本）。
 *
 * <p>{@code tradeName} 为库内交易名称展示值（如“行为返利”“AI对话消耗”），
 * {@code tradeType} 为 forward（+积分）/ reverse（-积分）。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreditOrderResponseDTO implements Serializable {

    /** 积分订单ID */
    private String orderId;
    /** 交易名称（展示值） */
    private String tradeName;
    /** 交易类型；forward-正向（+积分）、reverse-逆向（-积分） */
    private String tradeType;
    /** 交易金额（正数，方向由 tradeType 表达） */
    private BigDecimal tradeAmount;
    /** 交易时间 */
    private Date createTime;

}
