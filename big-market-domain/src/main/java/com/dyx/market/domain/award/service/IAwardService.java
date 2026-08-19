package com.dyx.market.domain.award.service;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordLogEntity;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品服务接口
 * @create 2024-04-06 09:03
 */
public interface IAwardService {

    /**
     * 保存用户中奖记录。
     *
     * <p>调用方应以抽奖订单 ID 作为幂等键；记录成功落库后才允许进入后续发奖确认流程。</p>
     *
     * @param userAwardRecordEntity 用户中奖记录
     */
    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity);

    /**
     * 配送发货奖品
     */
    void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception;

    /**
     * 查询用户中奖记录（按中奖时间倒序，服务端抽奖历史）。
     *
     * @param userId 用户ID
     * @param limit  最大返回条数
     * @return 中奖记录读模型集合
     */
    List<UserAwardRecordLogEntity> queryUserAwardRecords(String userId, int limit);

}
