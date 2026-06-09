package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@ConditionalOnMissingBean(IAccountQuotaWriteAdapter.class)
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
