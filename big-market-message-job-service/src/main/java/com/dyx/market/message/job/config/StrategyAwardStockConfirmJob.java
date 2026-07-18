package com.dyx.market.message.job.config;

import com.dyx.market.domain.strategy.adapter.port.IStrategyStockConfirmCompensationPort;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardStockConfirmTaskEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.middleware.db.router.DBRouterTemplate;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 奖品库存确认补偿 Job：中奖记录已落库但 confirm 失败时，重试 Redis confirm + 入队写 MySQL。
 */
@Slf4j
@Component
public class StrategyAwardStockConfirmJob {

    @Value("${job.strategy-stock-confirm.max-retries:5}")
    private int maxRetries;

    @Value("${job.strategy-stock-confirm.scan-limit:20}")
    private int scanLimit;

    @Value("${job.strategy-stock-confirm.processing-lease-minutes:5}")
    private int processingLeaseMinutes;

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
            DBRouterTemplate.executeOnDb(dbRouter, dbIdx, () -> {
                Date staleBefore = new Date(System.currentTimeMillis()
                        - TimeUnit.MINUTES.toMillis(processingLeaseMinutes));
                int reverted = strategyStockConfirmCompensationPort.revertStaleProcessing(dbIdx, staleBefore, scanLimit);
                if (reverted > 0) {
                    log.warn("[StrategyAwardStockConfirmJob] reverted {} stale processing tasks on DB{}", reverted, dbIdx);
                }
                List<StrategyAwardStockConfirmTaskEntity> tasks =
                        strategyStockConfirmCompensationPort.queryPendingTasks(maxRetries, scanLimit);
                for (StrategyAwardStockConfirmTaskEntity task : tasks) {
                    confirmTask(task, dbIdx);
                }
            });
        } catch (Exception e) {
            log.error("[StrategyAwardStockConfirmJob] DB{} scan failed", dbIdx, e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void confirmTask(StrategyAwardStockConfirmTaskEntity task, int scanDbIdx) {
        int claimed = strategyStockConfirmCompensationPort.claimProcessing(scanDbIdx, task.getUserId(), task.getOrderId());
        if (claimed != 1) {
            log.info("[StrategyAwardStockConfirmJob] skip task already claimed userId:{} orderId:{}",
                    task.getUserId(), task.getOrderId());
            return;
        }

        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(task.getStrategyId())
                .awardId(task.getAwardId())
                .reservationId(task.getReservationId())
                .lockSurplus(task.getLockSurplus())
                .build();
        try {
            strategyRepository.confirmAwardStockReservation(reservation);
        } catch (Exception e) {
            log.warn("[StrategyAwardStockConfirmJob] confirm failed userId:{} orderId:{}",
                    task.getUserId(), task.getOrderId(), e);
            strategyStockConfirmCompensationPort.incrementRetryFailed(scanDbIdx, task.getUserId(), task.getOrderId(), maxRetries);
            return;
        }

        int updated = strategyStockConfirmCompensationPort.markConfirmed(scanDbIdx, task.getUserId(), task.getOrderId());
        if (updated == 1) {
            log.info("[StrategyAwardStockConfirmJob] confirmed userId:{} orderId:{}", task.getUserId(), task.getOrderId());
        } else {
            log.warn("[StrategyAwardStockConfirmJob] markConfirmed missed userId:{} orderId:{} on DB{}",
                    task.getUserId(), task.getOrderId(), scanDbIdx);
            strategyStockConfirmCompensationPort.incrementRetryFailed(scanDbIdx, task.getUserId(), task.getOrderId(), maxRetries);
        }
    }
}
