package com.dyx.market.account.application;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.domain.activity.model.valobj.OrderTradeTypeVO;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.infrastructure.dao.IRaffleActivityOrderDao;
import com.dyx.market.infrastructure.dao.po.RaffleActivityOrder;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 活动账户额度应用服务：下单、更新、查询与扣减/回滚额度。
 *
 * <p>适配 RPC 入参，委托 {@link IRaffleActivityAccountQuotaService} 完成领域逻辑。</p>
 */
@Service
public class AccountQuotaApplicationService {

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;
    @Resource
    private IRaffleActivityOrderDao raffleActivityOrderDao;

    /** 创建活动额度充值/兑换订单。 */
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

    /** 更新订单发货状态（支付完成后触发）。 */
    public void updateOrder(AccountQuotaUpdateOrderRequestDTO request) {
        if (StringUtils.isBlank(request.getUserId()) || StringUtils.isBlank(request.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        raffleActivityAccountQuotaService.updateOrder(DeliveryOrderEntity.builder()
                .userId(request.getUserId())
                .outBusinessNo(request.getOutBusinessNo())
                .build());
    }

    /** 查询用户在指定活动下的额度账户（总/日/月剩余次数）。 */
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

    /** 扣减活动参与额度（抽奖前调用）。 */
    public boolean decrementQuota(AccountQuotaDecrementRequestDTO request) {
        validateDecrementRequest(request);
        return raffleActivityAccountQuotaService.decrementQuota(
                request.getUserId(), request.getActivityId(), request.getOutBusinessNo());
    }

    /** 回滚已扣减的活动额度（抽奖失败补偿）。 */
    public boolean rollbackQuota(AccountQuotaRollbackRequestDTO request) {
        validateRollbackRequest(request);
        return raffleActivityAccountQuotaService.rollbackQuota(
                request.getUserId(), request.getActivityId(), request.getOutBusinessNo());
    }

    public boolean existsActivityOrder(String userId, String outBusinessNo) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        RaffleActivityOrder query = new RaffleActivityOrder();
        query.setUserId(userId);
        query.setOutBusinessNo(outBusinessNo);
        return raffleActivityOrderDao.queryRaffleActivityOrder(query) != null;
    }

    public boolean isActivityOrderCompleted(String userId, String outBusinessNo) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(outBusinessNo)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        RaffleActivityOrder query = new RaffleActivityOrder();
        query.setUserId(userId);
        query.setOutBusinessNo(outBusinessNo);
        RaffleActivityOrder order = raffleActivityOrderDao.queryRaffleActivityOrder(query);
        return order != null && "completed".equalsIgnoreCase(order.getState());
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
