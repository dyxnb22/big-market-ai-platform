package com.dyx.market.message.job.config;

import com.dyx.market.domain.strategy.adapter.port.IStrategyStockConfirmCompensationPort;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardStockConfirmTaskEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StrategyAwardStockConfirmJobTest {

    private StrategyAwardStockConfirmJob job;
    private IStrategyStockConfirmCompensationPort compensationPort;
    private IStrategyRepository strategyRepository;
    private IDBRouterStrategy dbRouter;
    private RedissonClient redissonClient;
    private RLock lock;

    @Before
    public void setUp() throws Exception {
        job = new StrategyAwardStockConfirmJob();
        compensationPort = mock(IStrategyStockConfirmCompensationPort.class);
        strategyRepository = mock(IStrategyRepository.class);
        dbRouter = mock(IDBRouterStrategy.class);
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        inject(job, "strategyStockConfirmCompensationPort", compensationPort);
        inject(job, "strategyRepository", strategyRepository);
        inject(job, "dbRouter", dbRouter);
        inject(job, "redissonClient", redissonClient);
        inject(job, "scanLimit", 20);
        inject(job, "processingLeaseMinutes", 5);
    }

    @Test
    public void scanDb_should_revert_stale_processing_before_claiming_tasks() throws Exception {
        StrategyAwardStockConfirmTaskEntity task = buildTask();
        when(compensationPort.revertStaleProcessing(eq(1), any(), eq(20))).thenReturn(1);
        when(compensationPort.queryPendingTasks(20)).thenReturn(Collections.singletonList(task));
        when(compensationPort.claimProcessing(1, task.getUserId(), task.getOrderId())).thenReturn(1);
        when(compensationPort.markConfirmed(1, task.getUserId(), task.getOrderId())).thenReturn(1);

        invokeScanDb(job, 1, "lock");

        org.mockito.InOrder inOrder = inOrder(compensationPort);
        inOrder.verify(compensationPort).revertStaleProcessing(eq(1), any(), eq(20));
        inOrder.verify(compensationPort).queryPendingTasks(20);
        inOrder.verify(compensationPort).claimProcessing(1, task.getUserId(), task.getOrderId());
        verify(compensationPort).markConfirmed(1, task.getUserId(), task.getOrderId());
    }

    @Test
    public void confirmTask_should_mark_on_scan_db_when_confirm_succeeds() throws Exception {
        StrategyAwardStockConfirmTaskEntity task = buildTask();
        when(compensationPort.claimProcessing(1, task.getUserId(), task.getOrderId())).thenReturn(1);
        when(compensationPort.markConfirmed(1, task.getUserId(), task.getOrderId())).thenReturn(1);

        invokeConfirmTask(job, task, 1);

        verify(strategyRepository).confirmAwardStockReservation(any(StrategyAwardStockKeyVO.class));
        verify(compensationPort).markConfirmed(1, task.getUserId(), task.getOrderId());
        verify(compensationPort, never()).incrementRetryFailed(any(Integer.class), any(), any());
    }

    @Test
    public void confirmTask_should_increment_retry_when_mark_confirmed_missed() throws Exception {
        StrategyAwardStockConfirmTaskEntity task = buildTask();
        when(compensationPort.claimProcessing(1, task.getUserId(), task.getOrderId())).thenReturn(1);
        when(compensationPort.markConfirmed(1, task.getUserId(), task.getOrderId())).thenReturn(0);

        invokeConfirmTask(job, task, 1);

        verify(compensationPort).incrementRetryFailed(1, task.getUserId(), task.getOrderId());
    }

    @Test
    public void confirmTask_should_increment_retry_when_confirm_fails() throws Exception {
        StrategyAwardStockConfirmTaskEntity task = buildTask();
        when(compensationPort.claimProcessing(1, task.getUserId(), task.getOrderId())).thenReturn(1);
        doThrow(new RuntimeException("redis down"))
                .when(strategyRepository).confirmAwardStockReservation(any(StrategyAwardStockKeyVO.class));

        invokeConfirmTask(job, task, 1);

        verify(compensationPort).incrementRetryFailed(1, task.getUserId(), task.getOrderId());
        verify(compensationPort, never()).markConfirmed(any(Integer.class), any(), any());
    }

    @Test
    public void confirmTask_should_skip_when_claim_fails() throws Exception {
        StrategyAwardStockConfirmTaskEntity task = buildTask();
        when(compensationPort.claimProcessing(2, task.getUserId(), task.getOrderId())).thenReturn(0);

        invokeConfirmTask(job, task, 2);

        verify(strategyRepository, never()).confirmAwardStockReservation(any());
        verify(compensationPort, never()).markConfirmed(any(Integer.class), any(), any());
    }

    private static StrategyAwardStockConfirmTaskEntity buildTask() {
        return StrategyAwardStockConfirmTaskEntity.builder()
                .userId("user-1")
                .orderId("order-1")
                .strategyId(100001L)
                .awardId(101)
                .reservationId("order-1")
                .lockSurplus(5L)
                .build();
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = StrategyAwardStockConfirmJob.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        if (field.getType() == int.class) {
            field.setInt(target, (Integer) value);
        } else {
            field.set(target, value);
        }
    }

    private static void invokeScanDb(StrategyAwardStockConfirmJob target, int dbIdx, String lockName) throws Exception {
        java.lang.reflect.Method method = StrategyAwardStockConfirmJob.class.getDeclaredMethod("scanDb", int.class, String.class);
        method.setAccessible(true);
        method.invoke(target, dbIdx, lockName);
    }

    private static void invokeConfirmTask(StrategyAwardStockConfirmJob target,
                                        StrategyAwardStockConfirmTaskEntity task,
                                        int scanDbIdx) throws Exception {
        java.lang.reflect.Method method = StrategyAwardStockConfirmJob.class.getDeclaredMethod(
                "confirmTask", StrategyAwardStockConfirmTaskEntity.class, int.class);
        method.setAccessible(true);
        method.invoke(target, task, scanDbIdx);
    }
}
