package com.dyx.market.domain.award.adapter.repository;

import com.dyx.market.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.dyx.market.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.dyx.market.domain.award.model.entity.UserAwardRecordLogEntity;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品仓储服务
 * @create 2024-04-06 09:02
 */
public interface IAwardRepository {

    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);

    String queryAwardConfig(Integer awardId);

    void saveGiveOutPrizesAggregate(GiveOutPrizesAggregate giveOutPrizesAggregate);

    String queryAwardKey(Integer awardId);

    /**
     * 查询用户中奖记录（按中奖时间倒序）。
     */
    List<UserAwardRecordLogEntity> queryUserAwardRecords(String userId, int limit);

}
