package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;

/**
 * Quota write routing adapter — 
 *
 * Wired callers ():
 *   - CreditAdjustSuccessConsumer.updateOrder   (message-job-service)
 *   - RebateMessageConsumer.createOrder for sku  (message-job-service)
 *   - RaffleActivityController.creditPayExchangeSku createOrder (market-service, )
 *
 * Still pending wiring:
 *   - RaffleActivityPartakeService quota decrement — deferred (high risk, needs dedicated RPC)
 *
 * Default behavior: local domain service (flag=false). Remote Dubbo call active only when
 * account.service.remote-quota-write.enabled=true.
 */
public interface IAccountQuotaWriteAdapter {

    UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity);

    void updateOrder(DeliveryOrderEntity deliveryOrderEntity);

}
