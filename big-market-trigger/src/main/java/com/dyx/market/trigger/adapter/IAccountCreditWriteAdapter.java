package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.credit.model.entity.TradeEntity;

/**
 * 积分写操作路由适配器契约。
 * <p>
 * 已接入的调用方：
 * <ul>
 *   <li>RebateMessageConsumer.createOrder（积分返利路径，message-job-service）</li>
 *   <li>RaffleActivityController.creditPayExchangeSku createOrder（market-service，积分兑换 SKU）</li>
 * </ul>
 * 待接入：
 * <ul>
 *   <li>UserCreditRandomAward（发奖积分路径）— 需先完成调用链审计</li>
 * </ul>
 * 默认走本地领域服务（flag=false）；仅当 {@code account.service.remote-credit-write.enabled=true} 时启用远程 Dubbo 调用。
 */
public interface IAccountCreditWriteAdapter {

    String createOrder(TradeEntity tradeEntity);

}
