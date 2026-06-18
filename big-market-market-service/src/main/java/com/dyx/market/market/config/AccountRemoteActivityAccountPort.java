package com.dyx.market.market.config;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import java.math.BigDecimal;
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
 * {@link com.dyx.market.domain.activity.adapter.port.IActivityAccountPort} 的远程（Dubbo）实现。
 * <p>
 * 仅在 {@code account.service.remote-quota-decrement.enabled=true} 时激活；
 * 默认使用 infrastructure 中的 {@code LocalActivityAccountPort}，保持嵌入式行为不变。
 * <p>
 * 启用前需确认：幂等账本 DDL 已落库、AccountQuotaServiceRPC 已实现、
 * 端到端幂等校验通过。
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
    public BigDecimal queryUserCreditAccountAmount(String userId) {
        // 积分余额读的扩展点；本 Bean 仅在远程配额扣减开启时激活，开发环境默认走本地路径
        log.warn("[AccountRemoteActivityAccountPort] credit-balance read uses local development default; userId:{}", userId);
        return BigDecimal.ZERO;
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
