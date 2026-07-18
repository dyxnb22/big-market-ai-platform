package com.dyx.market.domain.activity.service.quota.rule;

import com.dyx.market.domain.activity.model.entity.ActivityCountEntity;
import com.dyx.market.domain.activity.model.entity.ActivityEntity;
import com.dyx.market.domain.activity.model.entity.ActivitySkuEntity;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 下单规则过滤接口
 * @create 2024-03-23 09:40
 */
public interface IActionChain extends IActionChainArmory {

    boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity, ActivityCountEntity activityCountEntity);

    /**
     * Execute the chain with an optional durable reservation key. Existing
     * callers keep the legacy behavior; credit-pay orders pass their business
     * number so SKU compensation can cancel/restore the exact reservation.
     */
    default boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity,
                           ActivityCountEntity activityCountEntity, String reservationId) {
        return action(activitySkuEntity, activityEntity, activityCountEntity);
    }

}
