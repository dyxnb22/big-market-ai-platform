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

import jakarta.annotation.Resource;

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

    /** 创建活动额度订单；outBusinessNo 是远程重试复用的业务幂等号。 */
    @Override
    public Response<UnpaidActivityOrderResponseDTO> createOrder(AccountQuotaCreateOrderRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("account quota createOrder userId:{} sku:{}", request.getUserId(), request.getSku());
        return ApiResponses.execute(() -> accountQuotaApplicationService.createOrder(request));
    }

    /** 将已支付的额度订单推进为已发货。 */
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

    /** 查询用户在活动下的总/月/日额度快照。 */
    @Override
    public Response<UserActivityAccountResponseDTO> queryActivityAccountEntity(Long activityId, String userId) {
        log.info("account quota queryActivityAccountEntity activityId:{} userId:{}", activityId, userId);
        return ApiResponses.execute(() -> accountQuotaApplicationService.queryActivityAccountEntity(activityId, userId));
    }

    /** 查询活动累计参与次数。 */
    @Override
    public Response<Integer> queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        return ApiResponses.execute(() -> accountQuotaApplicationService.queryRaffleActivityAccountPartakeCount(activityId, userId));
    }

    /** 查询活动当日参与次数。 */
    @Override
    public Response<Integer> queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        return ApiResponses.execute(() -> accountQuotaApplicationService.queryRaffleActivityAccountDayPartakeCount(activityId, userId));
    }

    /** 抽奖前扣减额度；相同 outBusinessNo 重复调用安全。 */
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

    /** 抽奖失败时按原 outBusinessNo 执行 Saga 回滚。 */
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

    /** 供远程写入恢复流程探测额度订单是否存在。 */
    @Override
    public Response<Boolean> existsActivityOrder(String userId, String outBusinessNo) {
        return ApiResponses.execute(() -> accountQuotaApplicationService.existsActivityOrder(userId, outBusinessNo));
    }

    /** 查询额度订单详情。 */
    @Override
    public Response<UnpaidActivityOrderResponseDTO> queryActivityOrder(String userId, String outBusinessNo) {
        return ApiResponses.execute(() -> accountQuotaApplicationService.queryActivityOrder(userId, outBusinessNo));
    }

    /** 查询额度订单是否已经完成发货。 */
    @Override
    public Response<Boolean> isActivityOrderCompleted(String userId, String outBusinessNo) {
        return ApiResponses.execute(() -> accountQuotaApplicationService.isActivityOrderCompleted(userId, outBusinessNo));
    }
}
