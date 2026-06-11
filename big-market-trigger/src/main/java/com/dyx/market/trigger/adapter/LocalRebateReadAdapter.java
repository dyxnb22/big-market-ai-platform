package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@ConditionalOnMissingBean(IRebateReadAdapter.class)
public class LocalRebateReadAdapter implements IRebateReadAdapter {

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Override
    public boolean isCalendarSignRebate(String userId, String outBusinessNo) {
        return !behaviorRebateService.queryOrderByOutBusinessNo(userId, outBusinessNo).isEmpty();
    }

}
