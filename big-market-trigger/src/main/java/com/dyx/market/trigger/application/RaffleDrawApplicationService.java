package com.dyx.market.trigger.application;

import com.dyx.market.domain.activity.application.ActivityDrawRequestEntity;
import com.dyx.market.domain.activity.application.ActivityDrawResponseEntity;
import com.dyx.market.domain.activity.application.RaffleApplicationService;
import com.dyx.market.trigger.api.dto.ActivityDrawRequestDTO;
import com.dyx.market.trigger.api.dto.ActivityDrawResponseDTO;
import com.dyx.market.types.annotations.RateLimiterAccessInterceptor;
import com.dyx.market.types.config.RuntimeConfigHolder;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 活动抽奖应用服务。
 */
@Slf4j
@Service
public class RaffleDrawApplicationService {

    @Resource
    private RuntimeConfigHolder runtimeConfigHolder;

    @Resource
    private RaffleApplicationService raffleApplicationService;

    @RateLimiterAccessInterceptor(key = "userId", permitsPerSecond = 20, fallbackMethod = "drawRateLimiterFallback")
    public ActivityDrawResponseDTO draw(ActivityDrawRequestDTO request) {
        log.info("活动抽奖开始 userId:{} activityId:{}", request.getUserId(), request.getActivityId());
        if (runtimeConfigHolder.isDegradeOpen()) {
            throw new AppException(ResponseCode.DEGRADE_SWITCH.getCode(), ResponseCode.DEGRADE_SWITCH.getInfo());
        }
        ActivityDrawResponseEntity result = raffleApplicationService.executeDraw(
                ActivityDrawRequestEntity.builder()
                        .userId(request.getUserId())
                        .activityId(request.getActivityId())
                        .build());
        return ActivityDrawResponseDTO.builder()
                .awardId(result.getAwardId())
                .awardTitle(result.getAwardTitle())
                .awardIndex(result.getAwardIndex())
                .build();
    }

    @SuppressWarnings("unused")
    public ActivityDrawResponseDTO drawRateLimiterFallback(ActivityDrawRequestDTO request) {
        throw new AppException(ResponseCode.RATE_LIMITER.getCode(), ResponseCode.RATE_LIMITER.getInfo());
    }
}
