package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;

/**
 * 活动配额写操作路由适配器契约。
 * <p>
 * 已接入的调用方：
 * <ul>
 *   <li>CreditAdjustSuccessConsumer.updateOrder（message-job-service）</li>
 *   <li>RebateMessageConsumer.createOrder（SKU 返利路径，message-job-service）</li>
 *   <li>RaffleActivityController.creditPayExchangeSku createOrder（market-service，积分兑换 SKU）</li>
 * </ul>
 * 待接入：
 * <ul>
 *   <li>RaffleActivityPartakeService 配额扣减 — 暂缓（风险较高，需独立 RPC）</li>
 * </ul>
 * 实现由 Spring Profile 选择：本地 Profile 委托本地领域服务，Docker Profile 经 Dubbo
 * 调用 account-service；远程失败只进入既有对账/补偿路径，不回退本地写入。
 */
public interface IAccountQuotaWriteAdapter {

    UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity);

    void updateOrder(DeliveryOrderEntity deliveryOrderEntity);

}
