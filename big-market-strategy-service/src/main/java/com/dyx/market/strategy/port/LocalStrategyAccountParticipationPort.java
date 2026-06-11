package com.dyx.market.strategy.port;

import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * Account-participation port backed by IAccountQuotaService via Dubbo.
 *
 * strategy-service has no direct access to the activity/account domain tables.
 * This port delegates to account-service (IAccountQuotaService) using an
 * existing API contract — no new cross-domain import is introduced.
 *
 * check=false: startup succeeds even when account-service is not registered in Nacos.
 * On failure, both methods return 0 (conservative default), which is identical to
 * the StrategyReadServiceRPC scaffold behavior before Phase 4-D. The remote strategy
 * read path must remain disabled (strategy.service.remote-read.enabled=false) until
 * account-service is confirmed reachable from strategy-service in staging.
 */
@Slf4j
@Component
public class LocalStrategyAccountParticipationPort implements IStrategyAccountParticipationPort {

    // check=false: startup succeeds even when account-service is not registered.
    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        try {
            Response<Integer> resp = accountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountPartakeCount success activityId:{} userId:{}", activityId, userId);
                return resp.getData() != null ? resp.getData() : 0;
            }
            log.warn("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountPartakeCount non-success code:{} activityId:{} userId:{}",
                    resp != null ? resp.getCode() : null, activityId, userId);
        } catch (Exception e) {
            log.error("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountPartakeCount failed, returning 0 activityId:{} userId:{}", activityId, userId, e);
        }
        return 0;
    }

    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        try {
            Response<Integer> resp = accountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountDayPartakeCount success activityId:{} userId:{}", activityId, userId);
                return resp.getData() != null ? resp.getData() : 0;
            }
            log.warn("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountDayPartakeCount non-success code:{} activityId:{} userId:{}",
                    resp != null ? resp.getCode() : null, activityId, userId);
        } catch (Exception e) {
            log.error("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountDayPartakeCount failed, returning 0 activityId:{} userId:{}", activityId, userId, e);
        }
        return 0;
    }

}
