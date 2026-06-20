package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.award.adapter.repository.IAwardRepository;
import com.dyx.market.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.dyx.market.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.dyx.market.infrastructure.dao.IAwardDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

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

}
