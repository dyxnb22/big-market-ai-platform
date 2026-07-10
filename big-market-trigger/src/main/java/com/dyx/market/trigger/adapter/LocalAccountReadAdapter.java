package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 只读账户查询的本地进程内实现。
 * <p>
 * 无其他 {@link IAccountReadAdapter} Bean 时注册（例如未提供 market-service 中
 * AccountRemoteReadAdapter 的服务实例）。
 * 始终委托本地领域服务，不经 Dubbo、不依赖功能开关，保证本地 market-service 模式无需 account-service 即可运行。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "account.service.remote-read.enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnMissingBean(name = "accountRemoteReadAdapter")
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
