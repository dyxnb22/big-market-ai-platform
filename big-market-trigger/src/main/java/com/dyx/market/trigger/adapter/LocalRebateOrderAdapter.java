package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
@ConditionalOnMissingBean(IRebateOrderAdapter.class)
public class LocalRebateOrderAdapter implements IRebateOrderAdapter {

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Override
    public List<String> createOrder(BehaviorEntity behaviorEntity) {
        return behaviorRebateService.createOrder(behaviorEntity);
    }

}
