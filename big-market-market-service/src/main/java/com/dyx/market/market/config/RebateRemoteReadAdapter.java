package com.dyx.market.market.config;

import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import com.dyx.market.trigger.adapter.IRebateReadAdapter;
import com.dyx.market.trigger.api.IRebateService;
import com.dyx.market.trigger.api.dto.RebateOrderQueryRequestDTO;
import com.dyx.market.trigger.api.request.Request;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 返利读路径路由：按 {@code rebate.service.remote-read.enabled} 查询日历签到返利状态。
 * <p>
 * 远程失败时回退本地 {@code IBehaviorRebateService.queryOrderByOutBusinessNo}。
 */
@Slf4j
@Component
public class RebateRemoteReadAdapter implements IRebateReadAdapter {

    @Value("${rebate.service.remote-read.enabled:false}")
    private boolean remoteReadEnabled;

    @Value("${rebate.service.remote-read.app-id:chatgpt-data}")
    private String appId;

    @Resource
    private IBehaviorRebateService behaviorRebateService;

    @Resource
    private Map<String, String> appTokenMap;

    // check=false：rebate-service 未注册到 Nacos 时仍可启动
    @DubboReference(version = "1.0", check = false)
    private IRebateService rebateService;

    @Override
    public boolean isCalendarSignRebate(String userId, String outBusinessNo) {
        if (remoteReadEnabled) {
            try {
                Response<Boolean> resp = rebateService.isCalendarSignRebate(
                        Request.<RebateOrderQueryRequestDTO>builder()
                                .appId(appId)
                                .appToken(appTokenMap.get(appId))
                                .data(RebateOrderQueryRequestDTO.builder()
                                        .userId(userId)
                                        .outBusinessNo(outBusinessNo)
                                        .build())
                                .build());
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[RebateRemoteReadAdapter] isCalendarSignRebate remote success userId:{} outBusinessNo:{} result:{}",
                            userId, outBusinessNo, resp.getData());
                    return Boolean.TRUE.equals(resp.getData());
                }
                log.warn("[RebateRemoteReadAdapter] isCalendarSignRebate remote non-success code:{} userId:{} outBusinessNo:{}",
                        resp != null ? resp.getCode() : null, userId, outBusinessNo);
            } catch (Exception e) {
                log.error("[RebateRemoteReadAdapter] isCalendarSignRebate remote failed, falling back to local userId:{} outBusinessNo:{}",
                        userId, outBusinessNo, e);
            }
        }
        return !behaviorRebateService.queryOrderByOutBusinessNo(userId, outBusinessNo).isEmpty();
    }

}
