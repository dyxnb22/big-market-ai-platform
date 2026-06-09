package com.dyx.market.market.config;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.AccountQuotaDecrementRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaRollbackRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Remote (Dubbo) implementation of IActivityAccountPort.
 *
 * Phase 2.2-B11 scaffold. Active only when:
 *   account.service.remote-quota-decrement.enabled=true
 *
 * This bean is NOT activated by default — LocalActivityAccountPort (in
 * big-market-infrastructure) is used instead, preserving existing behavior.
 *
 * Do NOT set remote-quota-decrement.enabled=true until:
 *   1. B12 idempotency ledger DDL is applied to staging account-service DB.
 *   2. AccountQuotaServiceRPC.decrementQuota is fully implemented (not UN_ERROR stub).
 *   3. RaffleActivityPartakeService is rewired to call IActivityAccountPort.
 *   4. End-to-end idempotency validation passes in staging.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "account.service.remote-quota-decrement.enabled", havingValue = "true")
public class AccountRemoteActivityAccountPort implements IActivityAccountPort {

    @Value("${account.service.remote-quota-decrement.enabled:false}")
    private boolean remoteQuotaDecrementEnabled;

    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @Override
    public boolean decrementQuota(String userId, Long activityId, String outBusinessNo) {
        log.info("[AccountRemoteActivityAccountPort] decrementQuota userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
        try {
            Response<Boolean> resp = accountQuotaService.decrementQuota(
                    AccountQuotaDecrementRequestDTO.builder()
                            .userId(userId)
                            .activityId(activityId)
                            .outBusinessNo(outBusinessNo)
                            .build());
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[AccountRemoteActivityAccountPort] decrementQuota remote success userId:{} outBusinessNo:{}",
                        userId, outBusinessNo);
                return Boolean.TRUE.equals(resp.getData());
            }
            log.warn("[AccountRemoteActivityAccountPort] decrementQuota non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : "null", userId, outBusinessNo);
            return false;
        } catch (Exception e) {
            log.error("[AccountRemoteActivityAccountPort] decrementQuota remote failed userId:{} outBusinessNo:{}",
                    userId, outBusinessNo, e);
            return false;
        }
    }

    @Override
    public void rollbackQuota(String userId, Long activityId, String outBusinessNo) {
        log.info("[AccountRemoteActivityAccountPort] rollbackQuota userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
        try {
            Response<Boolean> resp = accountQuotaService.rollbackQuota(
                    AccountQuotaRollbackRequestDTO.builder()
                            .userId(userId)
                            .activityId(activityId)
                            .outBusinessNo(outBusinessNo)
                            .build());
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[AccountRemoteActivityAccountPort] rollbackQuota remote success userId:{} outBusinessNo:{}",
                        userId, outBusinessNo);
                return;
            }
            log.warn("[AccountRemoteActivityAccountPort] rollbackQuota non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : "null", userId, outBusinessNo);
        } catch (Exception e) {
            log.error("[AccountRemoteActivityAccountPort] rollbackQuota remote failed userId:{} outBusinessNo:{}",
                    userId, outBusinessNo, e);
        }
    }

}
