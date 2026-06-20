package com.dyx.market.fulfillment.provider;

import com.dyx.market.fulfillment.application.FulfillmentAwardApplicationService;
import com.dyx.market.trigger.api.IFulfillmentAwardService;
import com.dyx.market.trigger.api.dto.FulfillmentDistributeAwardRequestDTO;
import com.dyx.market.trigger.api.dto.FulfillmentSaveUserAwardRecordRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.api.support.ApiResponses;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

@Slf4j
@DubboService(version = "1.0")
public class FulfillmentAwardServiceRPC implements IFulfillmentAwardService {

    @Resource
    private FulfillmentAwardApplicationService fulfillmentAwardApplicationService;

    @Override
    public Response<Void> saveUserAwardRecord(FulfillmentSaveUserAwardRecordRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("fulfillment saveUserAwardRecord userId:{} orderId:{}", request.getUserId(), request.getOrderId());
        return ApiResponses.executeVoid(() -> fulfillmentAwardApplicationService.saveUserAwardRecord(request));
    }

    @Override
    public Response<Void> distributeAward(FulfillmentDistributeAwardRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("fulfillment distributeAward userId:{} orderId:{} awardId:{}",
                request.getUserId(), request.getOrderId(), request.getAwardId());
        return ApiResponses.executeVoid(() -> fulfillmentAwardApplicationService.distributeAward(request));
    }
}
