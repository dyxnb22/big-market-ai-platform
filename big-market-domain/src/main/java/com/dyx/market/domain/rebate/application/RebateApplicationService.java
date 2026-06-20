package com.dyx.market.domain.rebate.application;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.model.valobj.BehaviorTypeVO;
import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import com.dyx.market.domain.rebate.support.RebateAppTokenValidator;
import com.dyx.market.trigger.api.dto.RebateOrderQueryRequestDTO;
import com.dyx.market.trigger.api.dto.RebateRequestDTO;
import com.dyx.market.trigger.api.request.Request;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class RebateApplicationService {

    @Resource
    private IBehaviorRebateService behaviorRebateService;
    @Resource
    private RebateAppTokenValidator rebateAppTokenValidator;

    public boolean rebate(Request<RebateRequestDTO> request) {
        rebateAppTokenValidator.validate(request);
        RebateRequestDTO requestDTO = request.getData();
        if (requestDTO == null
                || StringUtils.isBlank(requestDTO.getUserId())
                || StringUtils.isBlank(requestDTO.getBehaviorType())
                || StringUtils.isBlank(requestDTO.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        BehaviorEntity behaviorEntity = new BehaviorEntity();
        behaviorEntity.setUserId(requestDTO.getUserId());
        behaviorEntity.setBehaviorTypeVO(BehaviorTypeVO.valueOf(requestDTO.getBehaviorType().toUpperCase()));
        behaviorEntity.setOutBusinessNo(requestDTO.getOutBusinessNo());
        behaviorRebateService.createOrder(behaviorEntity);
        return true;
    }

    public boolean isCalendarSignRebate(Request<RebateOrderQueryRequestDTO> request) {
        rebateAppTokenValidator.validate(request);
        RebateOrderQueryRequestDTO requestDTO = request.getData();
        if (requestDTO == null
                || StringUtils.isBlank(requestDTO.getUserId())
                || StringUtils.isBlank(requestDTO.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        List<?> orders = behaviorRebateService.queryOrderByOutBusinessNo(
                requestDTO.getUserId(), requestDTO.getOutBusinessNo());
        return !orders.isEmpty();
    }
}
