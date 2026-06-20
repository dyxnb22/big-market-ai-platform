package com.dyx.market.fulfillment.application;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.valobj.AwardStateVO;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.trigger.api.dto.FulfillmentDistributeAwardRequestDTO;
import com.dyx.market.trigger.api.dto.FulfillmentSaveUserAwardRecordRequestDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class FulfillmentAwardApplicationService {

    @Resource
    private IAwardService awardService;

    public void saveUserAwardRecord(FulfillmentSaveUserAwardRecordRequestDTO request) {
        validateSaveRequest(request);
        AwardStateVO awardState = AwardStateVO.getByCode(request.getAwardState());
        if (awardState == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        awardService.saveUserAwardRecord(UserAwardRecordEntity.builder()
                .userId(request.getUserId())
                .activityId(request.getActivityId())
                .strategyId(request.getStrategyId())
                .orderId(request.getOrderId())
                .awardId(request.getAwardId())
                .awardTitle(request.getAwardTitle())
                .awardTime(request.getAwardTime())
                .awardState(awardState)
                .awardConfig(request.getAwardConfig())
                .build());
    }

    public void distributeAward(FulfillmentDistributeAwardRequestDTO request) {
        if (request == null
                || StringUtils.isBlank(request.getUserId())
                || StringUtils.isBlank(request.getOrderId())
                || request.getAwardId() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        try {
            awardService.distributeAward(DistributeAwardEntity.builder()
                    .userId(request.getUserId())
                    .orderId(request.getOrderId())
                    .awardId(request.getAwardId())
                    .awardConfig(request.getAwardConfig())
                    .build());
        } catch (Exception e) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo(), e);
        }
    }

    private static void validateSaveRequest(FulfillmentSaveUserAwardRecordRequestDTO request) {
        if (request == null
                || StringUtils.isBlank(request.getUserId())
                || request.getActivityId() == null
                || request.getStrategyId() == null
                || StringUtils.isBlank(request.getOrderId())
                || request.getAwardId() == null
                || StringUtils.isBlank(request.getAwardState())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
    }
}
