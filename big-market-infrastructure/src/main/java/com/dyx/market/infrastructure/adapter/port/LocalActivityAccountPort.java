package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.infrastructure.dao.IUserCreditAccountDao;
import com.dyx.market.infrastructure.dao.po.UserCreditAccount;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * {@link IActivityAccountPort} 的本地（进程内）实现。
 *
 * <p>委托给与 account-service 侧 {@code AccountQuotaServiceRPC} 相同的、
 * 带账本守卫的仓储方法，消除空操作行为，使本地路径在功能上与远程路径等价，便于测试。</p>
 *
 * <p>激活条件：当 {@code account.service.remote-quota-decrement.enabled=false}
 *（默认值）时生效。标志为 {@code true} 时，market-service 中的
 * {@code AccountRemoteActivityAccountPort} 将覆盖本 Bean。</p>
 *
 * <p>注意：当 {@code RaffleActivityPartakeService.remoteQuotaDecrementEnabled=false}
 * 时，本端口不会被调用——额度扣减仍由 {@code saveCreatePartakeOrderAggregate} 负责。
 * 本端口仅在 {@code remoteQuotaDecrementEnabled=true} 路径下被调用，
 * 以便在无 live account-service 的纯本地部署中保持一致的账本语义。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "account.service.remote-quota-decrement.enabled", havingValue = "false", matchIfMissing = true)
public class LocalActivityAccountPort implements IActivityAccountPort {

    @Resource
    private IActivityRepository activityRepository;
    @Resource
    private IUserCreditAccountDao userCreditAccountDao;
    @Resource
    private IDBRouterStrategy dbRouter;

    @Override
    public boolean decrementQuota(String userId, Long activityId, String outBusinessNo) {
        log.info("[LocalActivityAccountPort] decrementQuota userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
        return activityRepository.decrementQuotaWithLedger(userId, activityId, outBusinessNo);
    }

    @Override
    public void rollbackQuota(String userId, Long activityId, String outBusinessNo) {
        log.info("[LocalActivityAccountPort] rollbackQuota userId:{} activityId:{} outBusinessNo:{}",
                userId, activityId, outBusinessNo);
        boolean ok = activityRepository.rollbackQuotaWithLedger(userId, activityId, outBusinessNo);
        if (!ok) {
            log.warn("[LocalActivityAccountPort] rollbackQuotaWithLedger returned false userId:{} activityId:{} outBusinessNo:{}",
                    userId, activityId, outBusinessNo);
        }
    }

    @Override
    public BigDecimal queryUserCreditAccountAmount(String userId) {
        try {
            dbRouter.doRouter(userId);
            UserCreditAccount req = new UserCreditAccount();
            req.setUserId(userId);
            UserCreditAccount account = userCreditAccountDao.queryUserCreditAccount(req);
            if (account == null) return BigDecimal.ZERO;
            return account.getAvailableAmount();
        } finally {
            dbRouter.clear();
        }
    }

}
