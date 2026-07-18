package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.dyx.market.infrastructure.dao.IUserCreditAccountDao;
import com.dyx.market.infrastructure.dao.po.UserCreditAccount;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * {@link IActivityAccountPort} 的本地（进程内）实现。
 *
 * <p>委托给与 account-service 侧 {@code AccountQuotaServiceRPC} 相同的、
 * 带账本守卫的仓储方法，消除空操作行为，使本地路径在功能上与远程路径等价，便于测试。</p>
 *
 * <p>仅在 {@code dev}、{@code local}、{@code test} Profile 生效；Docker 由
 * market-service 提供远程 account-service Port。</p>
 */
@Slf4j
@Component
@Profile({"dev", "local", "test"})
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
    public void savePartakeOrder(CreatePartakeOrderAggregate aggregate) {
        activityRepository.saveCreatePartakeOrderAggregate(aggregate);
    }

    @Override
    public void compensatePartakeOrder(String userId, Long activityId, String orderId, java.util.Date orderTime) {
        activityRepository.compensatePartakeQuota(userId, activityId, orderId, orderTime);
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
