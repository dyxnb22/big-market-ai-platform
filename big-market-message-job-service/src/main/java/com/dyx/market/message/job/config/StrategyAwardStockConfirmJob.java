package com.dyx.market.message.job.config;

import com.dyx.market.domain.strategy.adapter.port.IStrategyStockConfirmCompensationPort;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardStockConfirmTaskEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 奖品库存确认补偿 Job：中奖记录已落库但 confirm 失败时，重试 Redis confirm + 入队写 MySQL。
 */
@Slf4j
@Component
public class StrategyAwardStockConfirmJob {

    @Value("${job.strategy-stock-confirm.scan-limit:20}")
    private int scanLimit;

    @Resource
    private IStrategyStockConfirmCompensationPort strategyStockConfirmCompensationPort;
    @Resource
    private IStrategyRepository strategyRepository;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private RedissonClient redissonClient;

    @Timed(value = "StrategyAwardStockConfirmJob_DB1", description = "Strategy stock confirm compensation DB1")
    @XxlJob("StrategyAwardStockConfirmJob_DB1")
    public void execDb01() {
        scanDb(1, "big-market-StrategyAwardStockConfirmJob_DB1");
    }

    @Timed(value = "StrategyAwardStockConfirmJob_DB2", description = "Strategy stock confirm compensation DB2")
    @XxlJob("StrategyAwardStockConfirmJob_DB2")
    public void execDb02() {
        scanDb(2, "big-market-StrategyAwardStockConfirmJob_DB2");
    }

    private void scanDb(int dbIdx, String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        try {
            boolean isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) {
                return;
            }
            dbRouter.setDBKey(dbIdx);
            List<StrategyAwardStockConfirmTaskEntity> tasks = strategyStockConfirmCompensationPort.queryPendingTasks(scanLimit);
            for (StrategyAwardStockConfirmTaskEntity task : tasks) {
                confirmTask(task);
            }
        } catch (Exception e) {
            log.error("[StrategyAwardStockConfirmJob] DB{} scan failed", dbIdx, e);
        } finally {
            dbRouter.clear();
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void confirmTask(StrategyAwardStockConfirmTaskEntity task) {
        try {
            dbRouter.doRouter(task.getUserId());
            StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                    .strategyId(task.getStrategyId())
                    .awardId(task.getAwardId())
                    .reservationId(task.getReservationId())
                    .lockSurplus(task.getLockSurplus())
                    .build();
            strategyRepository.confirmAwardStockReservation(reservation);
            int updated = strategyStockConfirmCompensationPort.markConfirmed(task.getUserId(), task.getOrderId());
            if (updated == 1) {
                log.info("[StrategyAwardStockConfirmJob] confirmed userId:{} orderId:{}", task.getUserId(), task.getOrderId());
            }
        } catch (Exception e) {
            log.warn("[StrategyAwardStockConfirmJob] confirm failed userId:{} orderId:{}", task.getUserId(), task.getOrderId(), e);
        } finally {
            dbRouter.clear();
        }
    }
}
