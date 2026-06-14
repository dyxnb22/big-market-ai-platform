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
 * Local (in-process) implementation of IActivityAccountPort.
 *
 * delegates to the same ledger-guarded repository methods used by
 * AccountQuotaServiceRPC on the account-service side. This removes the no-op behavior
 * and makes the local path functionally equivalent to the remote path for testing.
 *
 * Active when account.service.remote-quota-decrement.enabled=false (the default).
 * AccountRemoteActivityAccountPort (market-service) overrides this bean when the flag
 * is true.
 *
 * Note: when RaffleActivityPartakeService.remoteQuotaDecrementEnabled=false, this port
 * is never called — saveCreatePartakeOrderAggregate still owns quota decrement. This
 * port is only invoked on the flag=true path, allowing consistent ledger semantics in
 * local-only deployments without a live account-service.
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
