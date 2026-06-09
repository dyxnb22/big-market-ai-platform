package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * Local-only fallback implementation of IAccountReadAdapter.
 *
 * Registered only when no other IAccountReadAdapter bean is present — i.e.,
 * in big-market-app (monolith) or any service that does not provide
 * AccountRemoteReadAdapter from market-service.
 *
 * Always delegates to local domain services. No Dubbo calls, no feature flag.
 * This keeps big-market-app compiling and running without account-service.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IAccountReadAdapter.class)
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

}
