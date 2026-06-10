package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.service.IAwardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@ConditionalOnMissingBean(IAwardDispatchAdapter.class)
public class LocalAwardDispatchAdapter implements IAwardDispatchAdapter {

    @Resource
    private IAwardService awardService;

    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception {
        awardService.distributeAward(distributeAwardEntity);
    }

}
