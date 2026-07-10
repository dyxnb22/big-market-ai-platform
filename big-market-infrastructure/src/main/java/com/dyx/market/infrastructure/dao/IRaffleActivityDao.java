package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.RaffleActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动表Dao
 * @create 2024-03-09 10:04
 */
@Mapper
public interface IRaffleActivityDao {

    RaffleActivity queryRaffleActivityByActivityId(Long activityId);

    Long queryStrategyIdByActivityId(Long activityId);

    Long queryActivityIdByStrategyId(Long strategyId);

    Integer updateRaffleActivityStateByActivityId(RaffleActivity raffleActivity);

}
