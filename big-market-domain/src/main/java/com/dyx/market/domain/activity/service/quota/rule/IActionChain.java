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
     * 使用可选的持久化预占键执行规则链。
     *
     * <p>旧调用方继续使用原有行为；积分兑换订单传入业务幂等号，使 SKU 补偿能够精确
     * 取消或恢复对应的库存预占。</p>
     */
    default boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity,
                           ActivityCountEntity activityCountEntity, String reservationId) {
        return action(activitySkuEntity, activityEntity, activityCountEntity);
    }

}
