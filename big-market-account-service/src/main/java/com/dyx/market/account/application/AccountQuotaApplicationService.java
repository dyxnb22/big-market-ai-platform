package com.dyx.market.account.application;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.domain.activity.model.valobj.OrderTradeTypeVO;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class AccountQuotaApplicationService {

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    public UnpaidActivityOrderResponseDTO createOrder(AccountQuotaCreateOrderRequestDTO request) {
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
        if (entity == null) {
            return null;
        }
        return UnpaidActivityOrderResponseDTO.builder()
                .userId(entity.getUserId())
                .orderId(entity.getOrderId())
                .outBusinessNo(entity.getOutBusinessNo())
                .payAmount(entity.getPayAmount())
                .build();
    }

    public void updateOrder(AccountQuotaUpdateOrderRequestDTO request) {
        if (StringUtils.isBlank(request.getUserId()) || StringUtils.isBlank(request.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        raffleActivityAccountQuotaService.updateOrder(DeliveryOrderEntity.builder()
                .userId(request.getUserId())
                .outBusinessNo(request.getOutBusinessNo())
                .build());
    }

    public UserActivityAccountResponseDTO queryActivityAccountEntity(Long activityId, String userId) {
        if (activityId == null || StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        ActivityAccountEntity entity = raffleActivityAccountQuotaService.queryActivityAccountEntity(activityId, userId);
        if (entity == null) {
            return null;
        }
        return UserActivityAccountResponseDTO.builder()
                .totalCount(entity.getTotalCount())
                .totalCountSurplus(entity.getTotalCountSurplus())
                .dayCount(entity.getDayCount())
                .dayCountSurplus(entity.getDayCountSurplus())
                .monthCount(entity.getMonthCount())
                .monthCountSurplus(entity.getMonthCountSurplus())
                .build();
    }

    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        validateActivityUser(activityId, userId);
        return raffleActivityAccountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);
    }

    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        validateActivityUser(activityId, userId);
        return raffleActivityAccountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
    }

    public boolean decrementQuota(AccountQuotaDecrementRequestDTO request) {
        validateDecrementRequest(request);
        return raffleActivityAccountQuotaService.decrementQuota(
                request.getUserId(), request.getActivityId(), request.getOutBusinessNo());
    }

    public boolean rollbackQuota(AccountQuotaRollbackRequestDTO request) {
        validateRollbackRequest(request);
        return raffleActivityAccountQuotaService.rollbackQuota(
                request.getUserId(), request.getActivityId(), request.getOutBusinessNo());
    }

    private static void validateActivityUser(Long activityId, String userId) {
        if (activityId == null || StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
    }

    private static void validateDecrementRequest(AccountQuotaDecrementRequestDTO request) {
        if (request == null
                || StringUtils.isBlank(request.getUserId())
                || request.getActivityId() == null
                || StringUtils.isBlank(request.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
    }

    private static void validateRollbackRequest(AccountQuotaRollbackRequestDTO request) {
        if (request == null
                || StringUtils.isBlank(request.getUserId())
                || request.getActivityId() == null
                || StringUtils.isBlank(request.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
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
