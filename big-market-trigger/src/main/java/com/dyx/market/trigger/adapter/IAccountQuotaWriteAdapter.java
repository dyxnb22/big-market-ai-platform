package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;

/**
 * Phase 2.2-B2 scaffold for quota write routing.
 *
 * Callers are not wired to this adapter yet; local domain services remain the
 * default write path until account.service.remote-quota-write.enabled is
 * deliberately enabled in a later cutover batch.
 */
public interface IAccountQuotaWriteAdapter {

    UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity);

    void updateOrder(DeliveryOrderEntity deliveryOrderEntity);

}
