package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.service.IAwardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Local (in-process) implementation of IAwardFulfillmentPort.
 *
 * delegates directly to the existing IAwardService bean, preserving
 * identical behavior to the pre-5-E direct injection in RaffleApplicationService.
 * No network hop. No remote flag. No transaction behavior change.
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
