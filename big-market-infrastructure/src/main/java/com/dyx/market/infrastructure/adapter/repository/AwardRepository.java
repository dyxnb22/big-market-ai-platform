package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.award.adapter.repository.IAwardRepository;
import com.dyx.market.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.dyx.market.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.dyx.market.domain.award.model.entity.UserAwardRecordLogEntity;
import com.dyx.market.infrastructure.dao.IAwardDao;
import com.dyx.market.infrastructure.dao.IUserAwardRecordDao;
import com.dyx.market.infrastructure.dao.po.UserAwardRecord;
import com.dyx.market.middleware.db.router.DBRouterTemplate;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品仓储服务
 * @create 2024-04-06 10:09
 */
@Slf4j
@Repository
public class AwardRepository implements IAwardRepository {

    @Resource
    private IAwardDao awardDao;
    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private AwardDispatchSupport awardDispatchSupport;
    @Resource
    private AwardCreditGrantSupport awardCreditGrantSupport;

    @Override
    public void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate) {
        awardDispatchSupport.saveUserAwardRecord(userAwardRecordAggregate);
    }

    @Override
    public String queryAwardConfig(Integer awardId) {
        return awardDao.queryAwardConfigByAwardId(awardId);
    }

    @Override
    public void saveGiveOutPrizesAggregate(GiveOutPrizesAggregate giveOutPrizesAggregate) {
        awardCreditGrantSupport.saveGiveOutPrizesAggregate(giveOutPrizesAggregate);
    }

    @Override
    public String queryAwardKey(Integer awardId) {
        return awardDao.queryAwardKeyByAwardId(awardId);
    }

    /**
     * 查询用户中奖记录读模型。user_award_record 按 userId 分库分表，
     * 需显式路由到用户分片（@DBRouter 注解无 AOP 切面，仅作文档标记）。
     */
    @Override
    public List<UserAwardRecordLogEntity> queryUserAwardRecords(String userId, int limit) {
        UserAwardRecord query = new UserAwardRecord();
        query.setUserId(userId);
        List<UserAwardRecord> records = DBRouterTemplate.executeOnShard(dbRouter, userId,
                () -> userAwardRecordDao.queryUserAwardRecordListByUserId(query));
        List<UserAwardRecordLogEntity> result = new ArrayList<>();
        if (records == null) {
            return result;
        }
        for (UserAwardRecord record : records) {
            if (result.size() >= limit) {
                break;
            }
            result.add(UserAwardRecordLogEntity.builder()
                    .activityId(record.getActivityId())
                    .orderId(record.getOrderId())
                    .awardId(record.getAwardId())
                    .awardTitle(record.getAwardTitle())
                    .awardState(record.getAwardState())
                    .awardTime(record.getAwardTime())
                    .build());
        }
        return result;
    }

}
