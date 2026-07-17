package com.dyx.market.trigger.adapter;

/**
 * 返利订单读取适配器契约。
 *
 * <p>当前最终拓扑固定由 market-service 内的本地返利领域服务执行，
 * 当前固定由 market-local 返利领域服务提供。</p>
 */
public interface IRebateReadAdapter {

    boolean isCalendarSignRebate(String userId, String outBusinessNo);

}
