package com.dyx.market.domain.credit.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 交易名称枚举值
 * @create 2024-06-01 09:04
 */
@Getter
@AllArgsConstructor
public enum TradeNameVO {

    REBATE("行为返利"),
    CONVERT_SKU("兑换抽奖"),
    /** Phase 2.2-B6: credit issued via award-credit outbox poller */
    AWARD_CREDIT("奖品积分发放"),

    ;

    private final String name;

}
