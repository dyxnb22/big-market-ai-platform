package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.strategy.adapter.port.IStrategyStockConfirmCompensationPort;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardStockConfirmTaskEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.infrastructure.dao.IStrategyAwardStockConfirmTaskDao;
import com.dyx.market.infrastructure.dao.po.StrategyAwardStockConfirmTask;
import com.dyx.market.middleware.db.router.DBRouterTemplate;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 奖品库存确认补偿 Port 的本地持久化实现。
 *
 * <p>抽奖结果已经保存但库存确认失败时写入 Outbox；message-job 后续按分片抢占、确认或重试，
 * reservationId 保证重复任务不会重复确认库存。</p>
 */
@Slf4j
@Component
public class LocalStrategyStockConfirmCompensationPort implements IStrategyStockConfirmCompensationPort {

    @Resource
    private IStrategyAwardStockConfirmTaskDao strategyAwardStockConfirmTaskDao;
    @Resource
    private IDBRouterStrategy dbRouter;

    /** 写入待确认任务；重复 reservationId 视为已入队。 */
    @Override
    public void enqueuePendingConfirm(String userId, StrategyAwardStockKeyVO reservation) {
        if (StringUtils.isBlank(userId) || reservation == null || StringUtils.isBlank(reservation.getReservationId())) {
            return;
        }
        try {
            DBRouterTemplate.executeOnShard(dbRouter, userId, () ->
                    strategyAwardStockConfirmTaskDao.insert(StrategyAwardStockConfirmTask.builder()
                            .userId(userId)
                            .orderId(reservation.getReservationId())
                            .strategyId(reservation.getStrategyId())
                            .awardId(reservation.getAwardId())
                            .reservationId(reservation.getReservationId())
                            .lockSurplus(reservation.getLockSurplus())
                            .state("pending")
                            .retryCount(0)
                            .build()));
            log.warn("[StockConfirmCompensation] enqueued userId:{} orderId:{}", userId, reservation.getReservationId());
        } catch (DuplicateKeyException e) {
            log.warn("[StockConfirmCompensation] duplicate orderId:{}", reservation.getReservationId());
        }
    }

    /** 查询当前分片上可重试的库存确认任务，并转换为领域实体。 */
    @Override
    public List<StrategyAwardStockConfirmTaskEntity> queryPendingTasks(int maxRetries, int limit) {
        List<StrategyAwardStockConfirmTask> rows = strategyAwardStockConfirmTaskDao.queryPendingTasks(maxRetries, limit);
        List<StrategyAwardStockConfirmTaskEntity> result = new ArrayList<>();
        for (StrategyAwardStockConfirmTask row : rows) {
            result.add(StrategyAwardStockConfirmTaskEntity.builder()
                    .userId(row.getUserId())
                    .orderId(row.getOrderId())
                    .strategyId(row.getStrategyId())
                    .awardId(row.getAwardId())
                    .reservationId(row.getReservationId())
                    .lockSurplus(row.getLockSurplus())
                    .state(row.getState())
                    .retryCount(row.getRetryCount())
                    .build());
        }
        return result;
    }

    /** CAS 抢占任务，防止多个 Job 实例同时确认同一笔库存。 */
    @Override
    public int claimProcessing(int scanDbIdx, String userId, String orderId) {
        return DBRouterTemplate.executeOnDb(dbRouter, scanDbIdx,
                () -> strategyAwardStockConfirmTaskDao.claimProcessing(buildTaskKey(userId, orderId)));
    }

    /** 标记库存确认已完成。 */
    @Override
    public int markConfirmed(int scanDbIdx, String userId, String orderId) {
        return DBRouterTemplate.executeOnDb(dbRouter, scanDbIdx,
                () -> strategyAwardStockConfirmTaskDao.updateConfirmed(buildTaskKey(userId, orderId)));
    }

    /** 记录确认失败并按最大重试次数推进状态。 */
    @Override
    public int incrementRetryFailed(int scanDbIdx, String userId, String orderId, int maxRetries) {
        return DBRouterTemplate.executeOnDb(dbRouter, scanDbIdx,
                () -> strategyAwardStockConfirmTaskDao.updateRetryFailed(buildTaskKey(userId, orderId), maxRetries));
    }

    /** 回收超时 processing 任务，使其重新进入可处理队列。 */
    @Override
    public int revertStaleProcessing(int scanDbIdx, Date staleBefore, int limit) {
        return DBRouterTemplate.executeOnDb(dbRouter, scanDbIdx,
                () -> strategyAwardStockConfirmTaskDao.revertStaleProcessing(staleBefore, limit));
    }

    private static StrategyAwardStockConfirmTask buildTaskKey(String userId, String orderId) {
        return StrategyAwardStockConfirmTask.builder()
                .userId(userId)
                .orderId(orderId)
                .build();
    }
}
