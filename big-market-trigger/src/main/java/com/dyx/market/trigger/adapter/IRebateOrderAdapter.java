package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;

import java.util.List;

/**
 * Routes rebate order creation to either the local domain service (default) or
 * big-market-rebate-service via Dubbo, controlled by rebate.service.remote-create-order.enabled.
 *
 * rebate adapter boundary. Default: local domain service (flag=false).
 * Remote Dubbo call active only when rebate.service.remote-create-order.enabled=true.
 * Do not enable that flag until:
 *   - duplicate IRebateService provider risk is resolved (market-service trigger.rpc default provider disabled)
 *   - shared task outbox ownership is clarified
 *   - local validation passes
 */
public interface IRebateOrderAdapter {

    List<String> createOrder(BehaviorEntity behaviorEntity);

}
