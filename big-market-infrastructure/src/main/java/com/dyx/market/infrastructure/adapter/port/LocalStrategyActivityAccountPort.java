package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.strategy.adapter.port.IStrategyActivityAccountPort;
import com.dyx.market.infrastructure.dao.IRaffleActivityAccountDao;
import com.dyx.market.infrastructure.dao.IRaffleActivityAccountDayDao;
import com.dyx.market.infrastructure.dao.po.RaffleActivityAccount;
import com.dyx.market.infrastructure.dao.po.RaffleActivityAccountDay;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Local (in-process) implementation of IStrategyActivityAccountPort.
 *
 * Phase 7-A prep (AL-2/AL-3): StrategyRepository previously injected
 * IRaffleActivityAccountDao and IRaffleActivityAccountDayDao directly.
 * This port encapsulates those reads behind the strategy domain boundary.
 *
 * Shard routing follows the same pattern as the original StrategyRepository
 * methods: doRouter(userId) + finally clear(). Semantics are identical.
 */
@Slf4j
@Component
public class LocalStrategyActivityAccountPort implements IStrategyActivityAccountPort {

    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IDBRouterStrategy dbRouter;

    @Override
    public Integer queryTodayRaffleCount(String userId, Long activityId) {
        try {
            dbRouter.doRouter(userId);
            RaffleActivityAccountDay req = new RaffleActivityAccountDay();
            req.setUserId(userId);
            req.setActivityId(activityId);
            req.setDay(RaffleActivityAccountDay.currentDay());
            RaffleActivityAccountDay account = raffleActivityAccountDayDao.queryActivityAccountDayByUserId(req);
            if (account == null) return 0;
            return account.getDayCount() - account.getDayCountSurplus();
        } finally {
            dbRouter.clear();
        }
    }

    @Override
    public Integer queryTotalUseCount(String userId, Long activityId) {
        try {
            dbRouter.doRouter(userId);
            RaffleActivityAccount account = raffleActivityAccountDao.queryActivityAccountByUserId(
                    RaffleActivityAccount.builder()
                            .userId(userId)
                            .activityId(activityId)
                            .build());
            return account.getTotalCount() - account.getTotalCountSurplus();
        } finally {
            dbRouter.clear();
        }
    }

}
