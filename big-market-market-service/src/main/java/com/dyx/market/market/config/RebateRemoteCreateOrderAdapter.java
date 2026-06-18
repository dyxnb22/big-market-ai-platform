package com.dyx.market.market.config;

import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import com.dyx.market.trigger.adapter.IRebateOrderAdapter;
import com.dyx.market.trigger.api.IRebateService;
import com.dyx.market.trigger.api.dto.RebateRequestDTO;
import com.dyx.market.trigger.api.request.Request;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 返利建单路径路由：按 {@code rebate.service.remote-create-order.enabled} 调用 rebate-service 或本地领域服务。
 * <p>
 * 远程成功时返回空列表（{@code IRebateService.rebate} 返回 Boolean，调用方仅打日志，空列表安全且幂等）。
 * 远程失败时回退本地 {@code IBehaviorRebateService.createOrder}。
 */
@Slf4j
@Component
public class RebateRemoteCreateOrderAdapter implements IRebateOrderAdapter {

    @Value("${rebate.service.remote-create-order.enabled:false}")
    private boolean remoteCreateOrderEnabled;

    @Value("${rebate.service.remote-create-order.app-id:chatgpt-data}")
    private String appId;

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Resource
    private Map<String, String> appTokenMap;

    // check=false：rebate-service 未注册到 Nacos 时仍可启动
    @DubboReference(version = "1.0", check = false)
    private IRebateService rebateService;

    @Override
    public List<String> createOrder(BehaviorEntity behaviorEntity) {
        if (remoteCreateOrderEnabled) {
            try {
                Response<Boolean> resp = rebateService.rebate(Request.<RebateRequestDTO>builder()
                        .appId(appId)
                        .appToken(appTokenMap.get(appId))
                        .data(RebateRequestDTO.builder()
                                .userId(behaviorEntity.getUserId())
                                .behaviorType(behaviorEntity.getBehaviorTypeVO().getCode())
                                .outBusinessNo(behaviorEntity.getOutBusinessNo())
                                .build())
                        .build());
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[RebateRemoteCreateOrderAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                            behaviorEntity.getUserId(), behaviorEntity.getOutBusinessNo());
                    // rebate 接口返回 Boolean 而非订单 ID；返回空列表对仅打日志的调用方安全
                    return Collections.emptyList();
                }
                log.warn("[RebateRemoteCreateOrderAdapter] createOrder remote non-success code:{} userId:{} outBusinessNo:{}",
                        resp != null ? resp.getCode() : null,
                        behaviorEntity.getUserId(), behaviorEntity.getOutBusinessNo());
            } catch (Exception e) {
                log.error("[RebateRemoteCreateOrderAdapter] createOrder remote failed, falling back to local userId:{} outBusinessNo:{}",
                        behaviorEntity.getUserId(), behaviorEntity.getOutBusinessNo(), e);
            }
        }
        return behaviorRebateService.createOrder(behaviorEntity);
    }

}
