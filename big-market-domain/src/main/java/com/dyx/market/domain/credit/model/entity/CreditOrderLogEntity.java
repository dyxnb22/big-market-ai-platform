package com.dyx.market.domain.credit.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户积分流水读模型（积分账本查询）。
 *
 * <p>与写模型 {@link CreditOrderEntity} 分离：{@code trade_name} 落库为中文展示值
 * （{@code TradeNameVO#getName()}），读路径保留原始字符串，不做枚举反解。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreditOrderLogEntity {

    /** 积分订单ID */
    private String orderId;
    /** 交易名称（库内展示值，如“行为返利”） */
    private String tradeName;
    /** 交易类型；forward-正向、reverse-逆向 */
    private String tradeType;
    /** 交易金额（正数，方向由 tradeType 表达） */
    private BigDecimal tradeAmount;
    /** 交易时间 */
    private Date createTime;

}
