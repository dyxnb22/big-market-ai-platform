package com.dyx.market.domain.credit.repository;

import com.dyx.market.domain.credit.model.aggregate.TradeAggregate;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.CreditOrderLogEntity;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户积分仓储
 * @create 2024-06-01 09:11
 */
public interface ICreditRepository {

    void saveUserCreditTradeOrder(TradeAggregate tradeAggregate);

    CreditAccountEntity queryUserCreditAccount(String userId);

    /**
     * 查询用户积分流水（按交易时间倒序）。
     */
    List<CreditOrderLogEntity> queryUserCreditOrders(String userId, int limit);

}
