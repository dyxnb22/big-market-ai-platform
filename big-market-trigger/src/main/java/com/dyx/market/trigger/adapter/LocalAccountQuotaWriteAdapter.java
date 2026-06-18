package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 活动配额写操作的本地进程内实现。
 * <p>
 * 无其他 {@link IAccountQuotaWriteAdapter} Bean 时注册，直接委托本地
 * {@link IRaffleActivityAccountQuotaService}，不经 Dubbo、不依赖远程开关。
 */
@Component
@ConditionalOnMissingBean(IAccountQuotaWriteAdapter.class) // 远程适配器未注册时的本地回退
public class LocalAccountQuotaWriteAdapter implements IAccountQuotaWriteAdapter {

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    @Override
    public UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity) {
        return raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
    }

    @Override
    public void updateOrder(DeliveryOrderEntity deliveryOrderEntity) {
        raffleActivityAccountQuotaService.updateOrder(deliveryOrderEntity);
    }

}
