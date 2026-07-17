package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;

import java.util.List;

/**
 * 返利订单创建适配器契约。
 *
 * <p>当前最终拓扑固定由 market-service 内的本地返利领域服务执行，
 * 当前固定由 market-local 返利领域服务提供。</p>
 */
public interface IRebateOrderAdapter {

    List<String> createOrder(BehaviorEntity behaviorEntity);

}
