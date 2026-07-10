package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.service.IAwardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 发奖派发的本地进程内实现。
 * <p>
 * 无其他 {@link IAwardDispatchAdapter} Bean 时注册，直接委托本地 {@link IAwardService}，
 * 不经 Dubbo、不依赖远程开关。
 */
@Service
@ConditionalOnProperty(name = "account.fulfillment.remote-award.enabled", havingValue = "false", matchIfMissing = true)
public class LocalAwardDispatchAdapter implements IAwardDispatchAdapter {

    @Resource
    private IAwardService awardService;

    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception {
        awardService.distributeAward(distributeAwardEntity);
    }

}
