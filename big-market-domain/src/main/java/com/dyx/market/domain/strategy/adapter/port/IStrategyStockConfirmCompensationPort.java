package com.dyx.market.domain.strategy.adapter.port;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardStockConfirmTaskEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;

import java.util.List;

/**
 * 奖品库存确认补偿：中奖记录已落库但 Redis confirm/入队失败时写入待补偿任务。
 */
public interface IStrategyStockConfirmCompensationPort {

    void enqueuePendingConfirm(String userId, StrategyAwardStockKeyVO reservation);

    List<StrategyAwardStockConfirmTaskEntity> queryPendingTasks(int limit);

    int claimProcessing(int scanDbIdx, String userId, String orderId);

    int markConfirmed(int scanDbIdx, String userId, String orderId);

    int incrementRetryFailed(int scanDbIdx, String userId, String orderId);

    int revertStaleProcessing(int scanDbIdx, java.util.Date staleBefore, int limit);
}
