package com.dyx.market.message.job.config;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * message-job 本地只读账户适配器（BM-002）：供 {@code ChatCreditApplicationService} 等使用。
 */
@Component
@Primary
@Profile({"dev", "local", "test"})
public class MessageJobLocalAccountReadAdapter implements IAccountReadAdapter {

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
