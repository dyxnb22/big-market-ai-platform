package com.dyx.market.trigger.account;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.AccountQuotaDecrementRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaRollbackRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * Docker Profile 下统一使用 account-service 的活动账户 Port。
 * <p>抽奖额度扣减仍是同步门禁；订单写入失败时按抽奖订单号执行 Saga 回滚。</p>
 */
@Slf4j
@Component
@Profile("docker")
public class RemoteActivityAccountPort implements IActivityAccountPort {

    @Resource
    private IActivityRepository activityRepository;

    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;

    @Override
    public boolean decrementQuota(String userId, Long activityId, String outBusinessNo) {
        log.info("[RemoteActivityAccountPort] decrementQuota userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
        try {
            Response<Boolean> response = accountQuotaService.decrementQuota(
                    AccountQuotaDecrementRequestDTO.builder()
                            .userId(userId)
                            .activityId(activityId)
                            .outBusinessNo(outBusinessNo)
                            .build());
            if (response != null && ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                return Boolean.TRUE.equals(response.getData());
            }
            log.warn("[RemoteActivityAccountPort] decrementQuota non-success code:{} userId:{} outBusinessNo:{}",
                    response == null ? "null" : response.getCode(), userId, outBusinessNo);
            return false;
        } catch (Exception e) {
            log.error("[RemoteActivityAccountPort] decrementQuota remote failed userId:{} outBusinessNo:{}",
                    userId, outBusinessNo, e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "远程配额扣减结果未知，请按业务号对账后重试: " + outBusinessNo);
        }
    }

    @Override
    public BigDecimal queryUserCreditAccountAmount(String userId) {
        try {
            Response<BigDecimal> response = accountCreditService.queryUserCreditAccount(userId);
            if (response != null && ResponseCode.SUCCESS.getCode().equals(response.getCode())
                    && response.getData() != null) {
                return response.getData();
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程积分余额查询失败，请稍后重试");
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[RemoteActivityAccountPort] queryUserCreditAccountAmount failed userId:{}", userId, e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "远程积分余额查询结果未知，请按业务号对账后重试");
        }
    }

    @Override
    public void rollbackQuota(String userId, Long activityId, String outBusinessNo) {
        log.info("[RemoteActivityAccountPort] rollbackQuota userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
        try {
            Response<Boolean> response = accountQuotaService.rollbackQuota(
                    AccountQuotaRollbackRequestDTO.builder()
                            .userId(userId)
                            .activityId(activityId)
                            .outBusinessNo(outBusinessNo)
                            .build());
            if (response != null && ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                return;
            }
            log.warn("[RemoteActivityAccountPort] rollbackQuota non-success code:{} userId:{} outBusinessNo:{}",
                    response == null ? "null" : response.getCode(), userId, outBusinessNo);
        } catch (Exception e) {
            log.error("[RemoteActivityAccountPort] rollbackQuota remote failed userId:{} outBusinessNo:{}",
                    userId, outBusinessNo, e);
        }
    }

    @Override
    public void savePartakeOrder(CreatePartakeOrderAggregate aggregate) {
        String userId = aggregate.getUserId();
        Long activityId = aggregate.getActivityId();
        String outBusinessNo = aggregate.getUserRaffleOrderEntity().getOrderId();
        if (!decrementQuota(userId, activityId, outBusinessNo)) {
            throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo());
        }
        try {
            activityRepository.savePartakeOrderOnly(aggregate);
        } catch (Exception e) {
            log.error("[RemoteActivityAccountPort] savePartakeOrderOnly failed, compensating userId:{} activityId:{} outBusinessNo:{}",
                    userId, activityId, outBusinessNo, e);
            rollbackQuota(userId, activityId, outBusinessNo);
            throw e;
        }
    }

    @Override
    public void compensatePartakeOrder(String userId, Long activityId, String orderId, java.util.Date orderTime) {
        if (activityRepository.markRaffleOrderFailed(userId, orderId)) {
            rollbackQuota(userId, activityId, orderId);
        } else {
            log.warn("[RemoteActivityAccountPort] order is no longer create, skip rollback userId:{} activityId:{} orderId:{}",
                    userId, activityId, orderId);
        }
    }
}
