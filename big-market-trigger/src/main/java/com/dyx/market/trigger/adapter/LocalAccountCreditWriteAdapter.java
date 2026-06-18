package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 积分写操作的本地进程内实现。
 * <p>
 * 无其他 {@link IAccountCreditWriteAdapter} Bean 时注册，直接委托本地 {@link ICreditAdjustService}，
 * 不经 Dubbo、不依赖远程开关。
 */
@Component
@ConditionalOnMissingBean(IAccountCreditWriteAdapter.class) // 远程适配器未注册时的本地回退
public class LocalAccountCreditWriteAdapter implements IAccountCreditWriteAdapter {

    @Resource
    private ICreditAdjustService creditAdjustService;

    @Override
    public String createOrder(TradeEntity tradeEntity) {
        return creditAdjustService.createOrder(tradeEntity);
    }

}
