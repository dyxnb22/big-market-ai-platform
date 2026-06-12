package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.UserCreditAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户积分账户
 * @create 2024-05-24 21:11
 */
@Mapper
public interface IUserCreditAccountDao {

    void insert(UserCreditAccount userCreditAccountReq);

    int updateAddAmount(UserCreditAccount userCreditAccountReq);

    UserCreditAccount queryUserCreditAccount(UserCreditAccount userCreditAccountReq);

    /**
     * Deduct available_amount by a negative value.
     * Guards: #{availableAmount} &lt; 0 AND available_amount + #{availableAmount} &gt;= 0
     */
    int updateSubtractionAmount(UserCreditAccount userCreditAccountReq);

    /**
     * Semantic alias for updateSubtractionAmount — guards enforce negative amount.
     */
    int updateDeductAvailableAmount(UserCreditAccount userCreditAccountReq);

}
