package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.strategy.adapter.port.IStrategyStockConfirmCompensationPort;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardStockConfirmTaskEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.infrastructure.dao.IStrategyAwardStockConfirmTaskDao;
import com.dyx.market.infrastructure.dao.po.StrategyAwardStockConfirmTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class LocalStrategyStockConfirmCompensationPort implements IStrategyStockConfirmCompensationPort {

    @Resource
    private IStrategyAwardStockConfirmTaskDao strategyAwardStockConfirmTaskDao;

    @Override
    public void enqueuePendingConfirm(String userId, StrategyAwardStockKeyVO reservation) {
        if (StringUtils.isBlank(userId) || reservation == null || StringUtils.isBlank(reservation.getReservationId())) {
            return;
        }
        try {
            strategyAwardStockConfirmTaskDao.insert(StrategyAwardStockConfirmTask.builder()
                    .userId(userId)
                    .orderId(reservation.getReservationId())
                    .strategyId(reservation.getStrategyId())
                    .awardId(reservation.getAwardId())
                    .reservationId(reservation.getReservationId())
                    .lockSurplus(reservation.getLockSurplus())
                    .state("pending")
                    .retryCount(0)
                    .build());
            log.warn("[StockConfirmCompensation] enqueued userId:{} orderId:{}", userId, reservation.getReservationId());
        } catch (DuplicateKeyException e) {
            log.warn("[StockConfirmCompensation] duplicate orderId:{}", reservation.getReservationId());
        }
    }

    @Override
    public List<StrategyAwardStockConfirmTaskEntity> queryPendingTasks(int limit) {
        List<StrategyAwardStockConfirmTask> rows = strategyAwardStockConfirmTaskDao.queryPendingTasks(limit);
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

    @Override
    public int markConfirmed(String userId, String orderId) {
        return strategyAwardStockConfirmTaskDao.updateConfirmed(StrategyAwardStockConfirmTask.builder()
                .userId(userId)
                .orderId(orderId)
                .build());
    }
}
