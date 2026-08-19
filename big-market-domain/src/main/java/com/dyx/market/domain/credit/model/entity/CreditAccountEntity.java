package com.dyx.market.domain.credit.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 积分账户实体
 * @create 2024-06-01 09:08
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreditAccountEntity {

    /** 用户ID */
    private String userId;
    /** 查询账户时表示当前可用积分；调整账户时表示本次积分变动额。 */
    private BigDecimal adjustAmount;

}
