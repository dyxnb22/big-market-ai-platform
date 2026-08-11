package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.UserCreditOrder;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户积分流水单 DAO
 * @create 2024-06-01 08:55
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IUserCreditOrderDao {

    void insert(UserCreditOrder userCreditOrderReq);

    @DBRouter
    UserCreditOrder queryByOutBusinessNo(UserCreditOrder userCreditOrderReq);

    /** 查询用户积分流水，按交易时间倒序，最多 50 条（服务端积分账本）。 */
    @DBRouter(key = "userId")
    List<UserCreditOrder> queryUserCreditOrderListByUserId(UserCreditOrder userCreditOrderReq);

}
