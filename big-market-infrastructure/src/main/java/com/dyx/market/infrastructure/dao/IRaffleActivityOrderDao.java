package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.RaffleActivityOrder;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动单Dao
 * @create 2024-03-09 10:08
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IRaffleActivityOrderDao {

    @DBRouter(key = "userId")
    void insert(RaffleActivityOrder raffleActivityOrder);

    @DBRouter
    List<RaffleActivityOrder> queryRaffleActivityOrderByUserId(String userId);

    @DBRouter
    RaffleActivityOrder queryRaffleActivityOrder(RaffleActivityOrder raffleActivityOrderReq);

    int updateOrderCompleted(RaffleActivityOrder raffleActivityOrderReq);

    @DBRouter
    RaffleActivityOrder queryUnpaidActivityOrder(RaffleActivityOrder raffleActivityOrderReq);

    List<RaffleActivityOrder> queryStuckWaitPayOrders(@Param("since") Date since,
                                                        @Param("tradeName") String tradeName,
                                                        @Param("limit") int limit);

    @DBRouter
    int updateOrderCompensating(RaffleActivityOrder raffleActivityOrderReq);

    @DBRouter
    int updateOrderFailed(RaffleActivityOrder raffleActivityOrderReq);

    List<RaffleActivityOrder> queryStuckCompensatingOrders(@Param("since") Date since,
                                                           @Param("limit") int limit);

}
