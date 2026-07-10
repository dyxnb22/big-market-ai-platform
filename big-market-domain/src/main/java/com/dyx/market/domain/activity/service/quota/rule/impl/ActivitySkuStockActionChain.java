package com.dyx.market.domain.activity.service.quota.rule.impl;

import com.dyx.market.domain.activity.model.entity.ActivityCountEntity;
import com.dyx.market.domain.activity.model.entity.ActivityEntity;
import com.dyx.market.domain.activity.model.entity.ActivitySkuEntity;
import com.dyx.market.domain.activity.service.armory.IActivityDispatch;
import com.dyx.market.domain.activity.service.quota.rule.AbstractActionChain;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 商品库存规则节点
 * @create 2024-03-23 10:25
 */
@Slf4j
@Component("activity_sku_stock_action")
public class ActivitySkuStockActionChain extends AbstractActionChain {

    @Resource
    private IActivityDispatch activityDispatch;

    @Override
    public boolean action(ActivitySkuEntity activitySkuEntity, ActivityEntity activityEntity, ActivityCountEntity activityCountEntity) {
        log.info("活动责任链-商品库存处理【有效期、状态、库存(sku)】开始。sku:{} activityId:{}", activitySkuEntity.getSku(), activityEntity.getActivityId());
        // 扣减库存（成功后内部写入延迟队列，携带 lockSurplus 用于落库幂等）
        boolean status = activityDispatch.subtractionActivitySkuStock(
                activitySkuEntity.getSku(), activityEntity.getActivityId(), activityEntity.getEndDateTime());
        // true；库存扣减成功
        if (status) {
            log.info("活动责任链-商品库存处理【有效期、状态、库存(sku)】成功。sku:{} activityId:{}", activitySkuEntity.getSku(), activityEntity.getActivityId());
            return true;
        }

        throw new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode(), ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getInfo());
    }

}
