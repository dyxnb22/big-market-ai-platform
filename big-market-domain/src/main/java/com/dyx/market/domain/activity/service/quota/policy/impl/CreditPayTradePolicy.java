package com.dyx.market.domain.activity.service.quota.policy.impl;

import com.dyx.market.domain.activity.model.aggregate.CreateQuotaOrderAggregate;
import com.dyx.market.domain.activity.model.valobj.OrderStateVO;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.service.quota.policy.ITradePolicy;
import org.springframework.stereotype.Service;

/**
 * 积分兑换支付策略：两阶段履约。
 * <p>阶段一（本类）：创建 {@code wait_pay} 订单并持久化，不扣积分。</p>
 * <p>阶段二（异步）：积分调整 MQ → {@code CreditAdjustSuccessConsumer} 发货；
 * 失败/超时由 {@code CreditPayDeliveryReconcileJob} 驱动补偿（{@code compensating → failed}）。</p>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-06-08 18:12
 */
@Service("credit_pay_trade")
public class CreditPayTradePolicy implements ITradePolicy {

    private final IActivityRepository activityRepository;

    public CreditPayTradePolicy(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public void trade(CreateQuotaOrderAggregate createQuotaOrderAggregate) {
        createQuotaOrderAggregate.setOrderState(OrderStateVO.wait_pay);
        activityRepository.doSaveCreditPayOrder(createQuotaOrderAggregate);
    }

}
