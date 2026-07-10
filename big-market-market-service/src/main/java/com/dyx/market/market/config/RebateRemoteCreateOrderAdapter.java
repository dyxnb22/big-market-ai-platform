package com.dyx.market.market.config;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.support.RebateAppTokenValidator;
import com.dyx.market.infrastructure.adapter.repository.PendingRemoteWriteSupport;
import com.dyx.market.trigger.adapter.IRebateOrderAdapter;
import com.dyx.market.trigger.api.IRebateService;
import com.dyx.market.trigger.api.dto.RebateRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 返利建单路径路由：微服务模式下远程失败写 pending 任务，不再本地回落双写。
 */
@Slf4j
public class RebateRemoteCreateOrderAdapter implements IRebateOrderAdapter {

    @Value("${rebate.service.remote-create-order.app-id:chatgpt-data}")
    private String appId;

    @Resource
    private RebateAppTokenValidator rebateAppTokenValidator;
    @Resource
    private PendingRemoteWriteSupport pendingRemoteWriteSupport;

    @DubboReference(version = "1.0", check = false)
    private IRebateService rebateService;

    @Override
    public List<String> createOrder(BehaviorEntity behaviorEntity) {
        RebateRequestDTO request = RebateRequestDTO.builder()
                .userId(behaviorEntity.getUserId())
                .behaviorType(behaviorEntity.getBehaviorTypeVO().getCode())
                .outBusinessNo(behaviorEntity.getOutBusinessNo())
                .build();
        try {
            Response<Boolean> resp = rebateService.rebate(rebateAppTokenValidator.buildRequest(appId, request));
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[RebateRemoteCreateOrderAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                        behaviorEntity.getUserId(), behaviorEntity.getOutBusinessNo());
                return Collections.emptyList();
            }
            log.warn("[RebateRemoteCreateOrderAdapter] createOrder remote non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null,
                    behaviorEntity.getUserId(), behaviorEntity.getOutBusinessNo());
        } catch (Exception e) {
            log.error("[RebateRemoteCreateOrderAdapter] createOrder remote failed userId:{} outBusinessNo:{}",
                    behaviorEntity.getUserId(), behaviorEntity.getOutBusinessNo(), e);
        }
        if (!pendingRemoteWriteSupport.enqueue(behaviorEntity.getOutBusinessNo(), RemoteWriteOperations.REBATE_CREATE, request)) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "返利写入失败，补偿任务参数无效");
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "返利写入处理中，请稍后查看");
    }
}
