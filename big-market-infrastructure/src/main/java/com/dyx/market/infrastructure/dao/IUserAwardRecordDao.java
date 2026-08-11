package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.UserAwardRecord;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户中奖记录表
 * @create 2024-04-03 15:57
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface IUserAwardRecordDao {

    void insert(UserAwardRecord userAwardRecord);

    int updateAwardRecordCompletedState(UserAwardRecord userAwardRecordReq);

    /** 查询用户中奖记录，按中奖时间倒序，最多 50 条（服务端抽奖历史）。 */
    @DBRouter(key = "userId")
    List<UserAwardRecord> queryUserAwardRecordListByUserId(UserAwardRecord userAwardRecordReq);

}
