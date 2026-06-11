package com.dyx.market.rebate.provider;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.model.valobj.BehaviorTypeVO;
import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import com.dyx.market.trigger.api.IRebateService;
import com.dyx.market.trigger.api.dto.RebateOrderQueryRequestDTO;
import com.dyx.market.trigger.api.dto.RebateRequestDTO;
import com.dyx.market.trigger.api.request.Request;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Dark-launch rebate RPC provider hosted by big-market-rebate-service.
 * The implementation mirrors the existing market-service provider while keeping
 * the new service boundary isolated from trigger.http, trigger.listener, and
 * trigger.job packages.
 */
@Slf4j
@DubboService(version = "1.0")
public class RebateServiceRPC implements IRebateService {

    @Resource
    private Map<String, String> appTokenMap;

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Override
    public Response<Boolean> rebate(Request<RebateRequestDTO> request) {
        RebateRequestDTO requestDTO = request == null ? null : request.getData();
        String userId = requestDTO == null ? null : requestDTO.getUserId();
        log.info("返利操作开始 userId:{} request:{}", userId, JSON.toJSONString(requestDTO));
        try {
            if (request == null || requestDTO == null
                    || StringUtils.isBlank(requestDTO.getUserId())
                    || StringUtils.isBlank(requestDTO.getBehaviorType())
                    || StringUtils.isBlank(requestDTO.getOutBusinessNo())
                    || StringUtils.isBlank(request.getAppId())
                    || StringUtils.isBlank(request.getAppToken())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            if (!request.getAppToken().equals(appTokenMap.get(request.getAppId()))) {
                throw new AppException(ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
            }

            BehaviorEntity behaviorEntity = new BehaviorEntity();
            behaviorEntity.setUserId(requestDTO.getUserId());
            behaviorEntity.setBehaviorTypeVO(BehaviorTypeVO.valueOf(requestDTO.getBehaviorType().toUpperCase()));
            behaviorEntity.setOutBusinessNo(requestDTO.getOutBusinessNo());
            List<String> orderIds = behaviorRebateService.createOrder(behaviorEntity);

            log.info("返利操作完成 userId:{} orderIds:{}", requestDTO.getUserId(), JSON.toJSONString(orderIds));
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            log.error("返利操作异常 userId:{} ", userId, e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("返利操作失败 userId:{}", userId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    public Response<Boolean> isCalendarSignRebate(Request<RebateOrderQueryRequestDTO> request) {
        RebateOrderQueryRequestDTO requestDTO = request == null ? null : request.getData();
        String userId = requestDTO == null ? null : requestDTO.getUserId();
        log.info("查询返利签到开始 userId:{}", userId);
        try {
            if (request == null || requestDTO == null
                    || StringUtils.isBlank(requestDTO.getUserId())
                    || StringUtils.isBlank(requestDTO.getOutBusinessNo())
                    || StringUtils.isBlank(request.getAppId())
                    || StringUtils.isBlank(request.getAppToken())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            if (!request.getAppToken().equals(appTokenMap.get(request.getAppId()))) {
                throw new AppException(ResponseCode.APP_TOKEN_ERROR.getCode(), ResponseCode.APP_TOKEN_ERROR.getInfo());
            }

            List<?> orders = behaviorRebateService.queryOrderByOutBusinessNo(requestDTO.getUserId(), requestDTO.getOutBusinessNo());
            boolean exists = !orders.isEmpty();
            log.info("查询返利签到完成 userId:{} outBusinessNo:{} exists:{}", userId, requestDTO.getOutBusinessNo(), exists);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(exists)
                    .build();
        } catch (AppException e) {
            log.error("查询返利签到异常 userId:{}", userId, e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询返利签到失败 userId:{}", userId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

}
