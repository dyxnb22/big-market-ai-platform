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
     * 以负数扣减 available_amount。
     * 守卫条件为 {@code availableAmount < 0} 且 {@code available_amount + availableAmount >= 0}，
     * 从数据库层保证扣减后余额不会小于 0。
     */
    int updateSubtractionAmount(UserCreditAccount userCreditAccountReq);

    /**
     * {@link #updateSubtractionAmount(UserCreditAccount)} 的语义别名；底层条件同样要求扣减金额为负数。
     */
    int updateDeductAvailableAmount(UserCreditAccount userCreditAccountReq);

}
