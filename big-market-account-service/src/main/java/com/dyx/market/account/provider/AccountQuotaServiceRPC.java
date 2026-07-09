package com.dyx.market.account.provider;

import com.dyx.market.account.application.AccountQuotaApplicationService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.api.support.ApiResponses;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
 * {@link IAccountQuotaService} 的 Dubbo Provider 实现：活动账户额度（quota）操作。
 *
 * <p>委托 {@link AccountQuotaApplicationService} 编排领域服务，响应统一封装为 {@link Response}。</p>
 */
@Slf4j
@DubboService(version = "1.0")
public class AccountQuotaServiceRPC implements IAccountQuotaService {

    @Resource
    private AccountQuotaApplicationService accountQuotaApplicationService;

    @Override
    public Response<UnpaidActivityOrderResponseDTO> createOrder(AccountQuotaCreateOrderRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("account quota createOrder userId:{} sku:{}", request.getUserId(), request.getSku());
        return ApiResponses.execute(() -> accountQuotaApplicationService.createOrder(request));
    }

    @Override
    public Response<Boolean> updateOrder(AccountQuotaUpdateOrderRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("account quota updateOrder userId:{}", request.getUserId());
        return ApiResponses.execute(() -> {
            accountQuotaApplicationService.updateOrder(request);
            return true;
        });
    }

    @Override
    public Response<UserActivityAccountResponseDTO> queryActivityAccountEntity(Long activityId, String userId) {
        log.info("account quota queryActivityAccountEntity activityId:{} userId:{}", activityId, userId);
        return ApiResponses.execute(() -> accountQuotaApplicationService.queryActivityAccountEntity(activityId, userId));
    }

    @Override
    public Response<Integer> queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        return ApiResponses.execute(() -> accountQuotaApplicationService.queryRaffleActivityAccountPartakeCount(activityId, userId));
    }

    @Override
    public Response<Integer> queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        return ApiResponses.execute(() -> accountQuotaApplicationService.queryRaffleActivityAccountDayPartakeCount(activityId, userId));
    }

    @Override
    public Response<Boolean> decrementQuota(AccountQuotaDecrementRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("account quota decrementQuota userId:{} activityId:{}", request.getUserId(), request.getActivityId());
        return ApiResponses.execute(() -> {
            if (!accountQuotaApplicationService.decrementQuota(request)) {
                throw new AppException(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode(), ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo());
            }
            return true;
        });
    }

    @Override
    public Response<Boolean> rollbackQuota(AccountQuotaRollbackRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        return ApiResponses.execute(() -> {
            boolean rolledBack = accountQuotaApplicationService.rollbackQuota(request);
            if (!rolledBack) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo());
            }
            return true;
        });
    }
}
