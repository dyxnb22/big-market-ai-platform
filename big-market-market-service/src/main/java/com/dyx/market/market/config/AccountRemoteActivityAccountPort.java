package com.dyx.market.market.config;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.AccountQuotaDecrementRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaRollbackRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * {@link IActivityAccountPort} 的远程（Dubbo）实现。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "account.service.remote-quota-decrement.enabled", havingValue = "true")
public class AccountRemoteActivityAccountPort implements IActivityAccountPort {

    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;

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
                return Boolean.TRUE.equals(resp.getData());
            }
            log.warn("[AccountRemoteActivityAccountPort] decrementQuota non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : "null", userId, outBusinessNo);
            return false;
        } catch (Exception e) {
            log.error("[AccountRemoteActivityAccountPort] decrementQuota remote failed userId:{} outBusinessNo:{}",
                    userId, outBusinessNo, e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "远程配额扣减结果未知，请按业务号对账后重试: " + outBusinessNo);
        }
    }

    @Override
    public BigDecimal queryUserCreditAccountAmount(String userId) {
        try {
            Response<BigDecimal> resp = accountCreditService.queryUserCreditAccount(userId);
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode()) && resp.getData() != null) {
                return resp.getData();
            }
            log.warn("[AccountRemoteActivityAccountPort] queryUserCreditAccountAmount non-success userId:{} code:{}",
                    userId, resp != null ? resp.getCode() : "null");
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "远程积分余额查询失败，请稍后重试");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AccountRemoteActivityAccountPort] queryUserCreditAccountAmount failed userId:{}", userId, e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "远程积分余额查询结果未知，请按业务号对账后重试");
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
