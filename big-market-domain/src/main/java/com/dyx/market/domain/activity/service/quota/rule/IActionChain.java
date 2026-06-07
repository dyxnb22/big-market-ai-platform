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

}
