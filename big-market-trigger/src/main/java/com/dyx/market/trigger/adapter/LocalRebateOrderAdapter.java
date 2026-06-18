package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 返利订单创建的本地进程内实现。
 * <p>
 * 无其他 {@link IRebateOrderAdapter} Bean 时注册，直接委托本地 {@link IBehaviorRebateService}，
 * 不经 Dubbo、不依赖远程开关。
 */
@Component
@ConditionalOnMissingBean(IRebateOrderAdapter.class) // 远程适配器未注册时的本地回退
public class LocalRebateOrderAdapter implements IRebateOrderAdapter {

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Override
    public List<String> createOrder(BehaviorEntity behaviorEntity) {
        return behaviorRebateService.createOrder(behaviorEntity);
    }

}
