package com.dyx.market.rebate.provider;

import com.alibaba.fastjson2.JSON;
import com.dyx.market.domain.rebate.application.RebateApplicationService;
import com.dyx.market.trigger.api.IRebateService;
import com.dyx.market.trigger.api.dto.RebateOrderQueryRequestDTO;
import com.dyx.market.trigger.api.dto.RebateRequestDTO;
import com.dyx.market.trigger.api.request.Request;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.api.support.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
 * {@link IRebateService} 的 Dubbo Provider 实现：返利操作与签到查询。
 *
 * <p>委托 {@link RebateApplicationService} 编排领域服务，响应统一封装为 {@link Response}。</p>
 */
@Slf4j
@DubboService(version = "1.0")
public class RebateServiceRPC implements IRebateService {

    @Resource
    private RebateApplicationService rebateApplicationService;

    @Override
    public Response<Boolean> rebate(Request<RebateRequestDTO> request) {
        RebateRequestDTO requestDTO = request == null ? null : request.getData();
        if (log.isInfoEnabled()) {
            log.info("返利操作开始 userId:{} request:{}", requestDTO == null ? null : requestDTO.getUserId(),
                    JSON.toJSONString(requestDTO));
        }
        return ApiResponses.execute(() -> rebateApplicationService.rebate(request));
    }

    @Override
    public Response<Boolean> isCalendarSignRebate(Request<RebateOrderQueryRequestDTO> request) {
        RebateOrderQueryRequestDTO requestDTO = request == null ? null : request.getData();
        log.info("查询返利签到开始 userId:{}", requestDTO == null ? null : requestDTO.getUserId());
        return ApiResponses.execute(() -> rebateApplicationService.isCalendarSignRebate(request));
    }
}
