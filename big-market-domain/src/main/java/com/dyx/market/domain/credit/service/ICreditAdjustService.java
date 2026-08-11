package com.dyx.market.domain.credit.service;

import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.CreditOrderLogEntity;
import com.dyx.market.domain.credit.model.entity.TradeEntity;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 积分调额接口【正逆向，增减积分】
 * @create 2024-06-01 09:35
 */
public interface ICreditAdjustService {

    /**
     * 创建增加积分额度订单
     * @param tradeEntity 交易实体对象
     * @return 单号
     */
    String createOrder(TradeEntity tradeEntity);

    /**
     * 查询用户积分账户
     * @param userId 用户ID
     * @return 积分账户实体
     */
    CreditAccountEntity queryUserCreditAccount(String userId);

    /**
     * 查询用户积分流水（按交易时间倒序，服务端积分账本）。
     *
     * @param userId 用户ID
     * @param limit  最大返回条数
     * @return 积分流水读模型集合
     */
    List<CreditOrderLogEntity> queryUserCreditOrders(String userId, int limit);

}
