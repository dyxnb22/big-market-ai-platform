package com.dyx.market.account.provider;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.domain.activity.model.valobj.OrderTradeTypeVO;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.AccountQuotaCreateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaDecrementRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaRollbackRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaUpdateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.UnpaidActivityOrderResponseDTO;
import com.dyx.market.trigger.api.dto.UserActivityAccountResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
 * Dubbo provider for activity account quota operations.
 *
 * Dark-launch Phase 2.2-A: provider is registered but receives no traffic.
 * Delegates to the existing IRaffleActivityAccountQuotaService domain service unchanged.
 */
@Slf4j
@DubboService(version = "1.0")
public class AccountQuotaServiceRPC implements IAccountQuotaService {

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    @Override
    public Response<UnpaidActivityOrderResponseDTO> createOrder(AccountQuotaCreateOrderRequestDTO request) {
        if (request == null) {
            log.warn("account quota createOrder request is null");
            return Response.<UnpaidActivityOrderResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                    .build();
        }
        log.info("account quota createOrder userId:{} sku:{} outBusinessNo:{}", request.getUserId(), request.getSku(), request.getOutBusinessNo());
        try {
            if (request.getSku() == null
                    || StringUtils.isBlank(request.getUserId())
                    || StringUtils.isBlank(request.getOutBusinessNo())
                    || StringUtils.isBlank(request.getOrderTradeType())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            UnpaidActivityOrderEntity entity = raffleActivityAccountQuotaService.createOrder(SkuRechargeEntity.builder()
                    .userId(request.getUserId())
                    .sku(request.getSku())
                    .outBusinessNo(request.getOutBusinessNo())
                    .orderTradeType(resolveOrderTradeType(request.getOrderTradeType()))
                    .build());
            UnpaidActivityOrderResponseDTO dto = entity == null ? null : UnpaidActivityOrderResponseDTO.builder()
                    .userId(entity.getUserId())
                    .orderId(entity.getOrderId())
                    .outBusinessNo(entity.getOutBusinessNo())
                    .payAmount(entity.getPayAmount())
                    .build();
            return Response.<UnpaidActivityOrderResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(dto)
                    .build();
        } catch (AppException e) {
            log.error("account quota createOrder appException userId:{} code:{}", request.getUserId(), e.getCode(), e);
            return Response.<UnpaidActivityOrderResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("account quota createOrder failed userId:{}", request.getUserId(), e);
            return Response.<UnpaidActivityOrderResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<Boolean> updateOrder(AccountQuotaUpdateOrderRequestDTO request) {
        if (request == null) {
            log.warn("account quota updateOrder request is null");
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                    .build();
        }
        log.info("account quota updateOrder userId:{} outBusinessNo:{}", request.getUserId(), request.getOutBusinessNo());
        try {
            if (StringUtils.isBlank(request.getUserId()) || StringUtils.isBlank(request.getOutBusinessNo())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            raffleActivityAccountQuotaService.updateOrder(DeliveryOrderEntity.builder()
                    .userId(request.getUserId())
                    .outBusinessNo(request.getOutBusinessNo())
                    .build());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            log.error("account quota updateOrder appException userId:{} code:{}", request.getUserId(), e.getCode(), e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("account quota updateOrder failed userId:{}", request.getUserId(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    public Response<UserActivityAccountResponseDTO> queryActivityAccountEntity(Long activityId, String userId) {
        log.info("account quota queryActivityAccountEntity activityId:{} userId:{}", activityId, userId);
        try {
            if (activityId == null || StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            ActivityAccountEntity entity = raffleActivityAccountQuotaService.queryActivityAccountEntity(activityId, userId);
            if (entity == null) {
                return Response.<UserActivityAccountResponseDTO>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(null)
                        .build();
            }
            UserActivityAccountResponseDTO dto = UserActivityAccountResponseDTO.builder()
                    .totalCount(entity.getTotalCount())
                    .totalCountSurplus(entity.getTotalCountSurplus())
                    .dayCount(entity.getDayCount())
                    .dayCountSurplus(entity.getDayCountSurplus())
                    .monthCount(entity.getMonthCount())
                    .monthCountSurplus(entity.getMonthCountSurplus())
                    .build();
            return Response.<UserActivityAccountResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(dto)
                    .build();
        } catch (AppException e) {
            log.error("account quota queryActivityAccountEntity appException activityId:{} userId:{}", activityId, userId, e);
            return Response.<UserActivityAccountResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("account quota queryActivityAccountEntity failed activityId:{} userId:{}", activityId, userId, e);
            return Response.<UserActivityAccountResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<Integer> queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        log.info("account quota queryRaffleActivityAccountPartakeCount activityId:{} userId:{}", activityId, userId);
        try {
            if (activityId == null || StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            Integer count = raffleActivityAccountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);
            return Response.<Integer>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (AppException e) {
            log.error("account quota queryRaffleActivityAccountPartakeCount appException activityId:{} userId:{}", activityId, userId, e);
            return Response.<Integer>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("account quota queryRaffleActivityAccountPartakeCount failed activityId:{} userId:{}", activityId, userId, e);
            return Response.<Integer>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<Integer> queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        log.info("account quota queryRaffleActivityAccountDayPartakeCount activityId:{} userId:{}", activityId, userId);
        try {
            if (activityId == null || StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            Integer count = raffleActivityAccountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
            return Response.<Integer>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (AppException e) {
            log.error("account quota queryRaffleActivityAccountDayPartakeCount appException activityId:{} userId:{}", activityId, userId, e);
            return Response.<Integer>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("account quota queryRaffleActivityAccountDayPartakeCount failed activityId:{} userId:{}", activityId, userId, e);
            return Response.<Integer>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<Boolean> decrementQuota(AccountQuotaDecrementRequestDTO request) {
        // Phase 2.2-B12: real idempotent implementation.
        // Ledger table (raffle_quota_decrement_ledger) must be applied to the shard DBs
        // before this path is reachable in production. RaffleActivityPartakeService is
        // NOT wired to call this via remote yet (remote-quota-decrement.enabled=false).
        if (request == null
                || StringUtils.isBlank(request.getUserId())
                || request.getActivityId() == null
                || StringUtils.isBlank(request.getOutBusinessNo())) {
            log.warn("[AccountQuotaServiceRPC] decrementQuota illegal parameter request:{}", request);
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                    .data(false)
                    .build();
        }
        log.info("[AccountQuotaServiceRPC] decrementQuota userId:{} activityId:{} outBusinessNo:{}",
                request.getUserId(), request.getActivityId(), request.getOutBusinessNo());
        try {
            boolean decremented = raffleActivityAccountQuotaService.decrementQuota(
                    request.getUserId(), request.getActivityId(), request.getOutBusinessNo());
            if (decremented) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.SUCCESS.getCode())
                        .info(ResponseCode.SUCCESS.getInfo())
                        .data(true)
                        .build();
            } else {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ACCOUNT_QUOTA_ERROR.getCode())
                        .info(ResponseCode.ACCOUNT_QUOTA_ERROR.getInfo())
                        .data(false)
                        .build();
            }
        } catch (AppException e) {
            log.error("[AccountQuotaServiceRPC] decrementQuota appException userId:{} code:{}", request.getUserId(), e.getCode(), e);
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(false)
                    .build();
        } catch (Exception e) {
            log.error("[AccountQuotaServiceRPC] decrementQuota failed userId:{}", request.getUserId(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    public Response<Boolean> rollbackQuota(AccountQuotaRollbackRequestDTO request) {
        // Phase 2.2-B12: rollback deferred to B13.
        // The idempotency ledger (raffle_quota_decrement_ledger) DDL is now designed.
        // Ledger status update (applied → rolled_back) and quota restore will be wired
        // once the ledger is deployed to staging and end-to-end validation passes (B13).
        // No callers are wired to this method at this stage.
        log.warn("[AccountQuotaServiceRPC] rollbackQuota not yet implemented (B12 — pending staging DDL deployment) — userId:{} activityId:{} outBusinessNo:{}",
                request == null ? "null" : request.getUserId(),
                request == null ? "null" : request.getActivityId(),
                request == null ? "null" : request.getOutBusinessNo());
        return Response.<Boolean>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info("rollbackQuota not yet implemented (Phase 2.2-B12 — pending ledger deployment and B13 validation)")
                .data(false)
                .build();
    }

    private OrderTradeTypeVO resolveOrderTradeType(String code) {
        for (OrderTradeTypeVO type : OrderTradeTypeVO.values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "Unknown orderTradeType: " + code + ". Expected: credit_pay_trade | rebate_no_pay_trade");
    }

}
