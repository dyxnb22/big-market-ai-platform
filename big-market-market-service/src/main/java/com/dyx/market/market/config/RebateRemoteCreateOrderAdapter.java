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
 * Routes calendarSignRebate createOrder to big-market-rebate-service via Dubbo when enabled.
 *
 * Flag: rebate.service.remote-create-order.enabled (default false).
 * This bean overrides LocalRebateOrderAdapter (@ConditionalOnMissingBean) in market-service.
 *
 * When flag=false: falls through to the local IBehaviorRebateService.createOrder path.
 * When flag=true and remote call succeeds: returns an empty list (IRebateService.rebate returns
 *   Boolean, not order IDs; callers only log the size — empty is safe and idempotent).
 * When flag=true and remote call fails: logs the error and falls back to local.
 *
 * Do not enable until:
 *   - default trigger.rpc IRebateService provider in market-service is disabled (duplicate risk)
 *   - shared task outbox ownership is clarified
 *   - local validation passes for rebate-service
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

    // check=false: startup succeeds even when rebate-service is not registered in Nacos.
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
                    // IRebateService.rebate returns Boolean not order IDs; return empty list — safe for log-only callers.
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
