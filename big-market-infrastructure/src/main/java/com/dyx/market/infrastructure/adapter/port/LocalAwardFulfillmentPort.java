package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.service.IAwardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * {@link IAwardFulfillmentPort} 的本地（进程内）实现。
 *
 * <p>直接委托给现有 {@code IAwardService} Bean，行为与 5-E 之前
 * {@code RaffleApplicationService} 中的直接注入完全一致：无网络跳转、
 * 无远程开关、无事务行为变更。</p>
 *
 * <p>激活条件：默认通过 {@code @ConditionalOnMissingBean} 生效——
 * 若未注册其他 {@code IAwardFulfillmentPort} Bean，则使用本实现。</p>
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IAwardFulfillmentPort.class)
public class LocalAwardFulfillmentPort implements IAwardFulfillmentPort {

    @Resource
    private IAwardService awardService;

    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecord) {
        log.debug("[LocalAwardFulfillmentPort] saveUserAwardRecord userId:{} activityId:{} orderId:{} awardId:{}",
                userAwardRecord.getUserId(), userAwardRecord.getActivityId(), userAwardRecord.getOrderId(),
                userAwardRecord.getAwardId());
        awardService.saveUserAwardRecord(userAwardRecord);
    }

}
