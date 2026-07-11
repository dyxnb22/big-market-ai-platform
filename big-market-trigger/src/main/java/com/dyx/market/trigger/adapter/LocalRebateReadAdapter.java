package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 返利读查询的本地进程内实现。
 * <p>
 * 无其他 {@link IRebateReadAdapter} Bean 时注册，直接委托本地 {@link IBehaviorRebateService}，
 * 不经 Dubbo、不依赖远程开关。
 */
@Component
@ConditionalOnProperty(name = "rebate.service.remote-read.enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnMissingBean(IRebateReadAdapter.class)
public class LocalRebateReadAdapter implements IRebateReadAdapter {

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Override
    public boolean isCalendarSignRebate(String userId, String outBusinessNo) {
        return !behaviorRebateService.queryOrderByOutBusinessNo(userId, outBusinessNo).isEmpty();
    }

}
