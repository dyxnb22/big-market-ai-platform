package com.dyx.market.trigger.application;

import com.dyx.market.domain.activity.application.ActivityDrawRequestEntity;
import com.dyx.market.domain.activity.application.ActivityDrawResponseEntity;
import com.dyx.market.domain.activity.application.RaffleApplicationService;
import com.dyx.market.trigger.api.dto.ActivityDrawRequestDTO;
import com.dyx.market.trigger.api.dto.ActivityDrawResponseDTO;
import com.dyx.market.types.annotations.DCCValue;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 活动抽奖应用服务。
 */
@Slf4j
@Service
public class RaffleDrawApplicationService {

    @DCCValue("degradeSwitch:close")
    private String degradeSwitch;

    @Resource
    private RaffleApplicationService raffleApplicationService;

    public ActivityDrawResponseDTO draw(ActivityDrawRequestDTO request) {
        log.info("活动抽奖开始 userId:{} activityId:{}", request.getUserId(), request.getActivityId());
        if (StringUtils.isNotBlank(degradeSwitch) && "open".equals(degradeSwitch)) {
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
}
