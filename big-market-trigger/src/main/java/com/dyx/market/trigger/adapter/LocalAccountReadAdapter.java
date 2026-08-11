package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.CreditOrderLogEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.trigger.api.dto.CreditOrderResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 只读账户查询的本地进程内实现。
 * <p>
 * 仅在 {@code dev}、{@code local}、{@code test} Profile 注册；Docker 使用
 * account-service 的远程只读适配器。
 */
@Slf4j
@Component
@Profile({"dev", "local", "test"})
public class LocalAccountReadAdapter implements IAccountReadAdapter {

    @Resource
    private ICreditAdjustService creditAdjustService;

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    @Override
    public BigDecimal queryUserCreditAccount(String userId) {
        CreditAccountEntity entity = creditAdjustService.queryUserCreditAccount(userId);
        return entity != null ? entity.getAdjustAmount() : BigDecimal.ZERO;
    }

    @Override
    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        return raffleActivityAccountQuotaService.queryActivityAccountEntity(activityId, userId);
    }

    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        return raffleActivityAccountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);
    }

    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        return raffleActivityAccountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
    }

    @Override
    public List<CreditOrderResponseDTO> queryUserCreditOrders(String userId, int limit) {
        List<CreditOrderLogEntity> orders = creditAdjustService.queryUserCreditOrders(userId, limit);
        List<CreditOrderResponseDTO> result = new ArrayList<>(orders.size());
        for (CreditOrderLogEntity order : orders) {
            result.add(CreditOrderResponseDTO.builder()
                    .orderId(order.getOrderId())
                    .tradeName(order.getTradeName())
                    .tradeType(order.getTradeType())
                    .tradeAmount(order.getTradeAmount())
                    .createTime(order.getCreateTime())
                    .build());
        }
        return result;
    }

}
