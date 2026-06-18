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
 * {@link IStrategyActivityAccountPort} 的本地（进程内）实现。
 *
 * <p>预备工作（AL-2/AL-3）：原先 {@code StrategyRepository} 直接注入
 * {@code IRaffleActivityAccountDao} 与 {@code IRaffleActivityAccountDayDao}；
 * 本端口将这些读操作封装在策略域边界之后。</p>
 *
 * <p>分片路由沿用原 {@code StrategyRepository} 方法的模式：
 * {@code doRouter(userId)}，并在 {@code finally} 中 {@code clear()}，语义完全一致。</p>
 *
 * <p>激活条件：无远程替代实现时始终使用本本地端口（当前无对应远程 Bean）。</p>
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
