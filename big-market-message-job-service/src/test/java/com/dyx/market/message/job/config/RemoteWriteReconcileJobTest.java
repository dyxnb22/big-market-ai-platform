package com.dyx.market.message.job.config;

import com.dyx.market.infrastructure.dao.IPendingRemoteWriteTaskDao;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.types.enums.ResponseCode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RemoteWriteReconcileJobTest {

    @Mock
    private IPendingRemoteWriteTaskDao pendingRemoteWriteTaskDao;
    @Mock
    private IDBRouterStrategy dbRouter;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RemoteWriteContinuationDispatcher remoteWriteContinuationDispatcher;
    @Mock
    private IAccountQuotaService accountQuotaService;
    @Mock
    private RLock lock;

    @InjectMocks
    private RemoteWriteReconcileJob job;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(job, "maxRetries", 5);
        ReflectionTestUtils.setField(job, "scanLimit", 50);
    }

    @Test
    public void continuationPending_failureKeepsRescannable_andRestoresShard() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        PendingRemoteWriteTask task = PendingRemoteWriteTask.builder()
                .id(9L)
                .outBusinessNo("biz-1")
                .operation(RemoteWriteOperations.QUOTA_CREATE)
                .payload("{\"userId\":\"u1\",\"outBusinessNo\":\"biz-1\",\"sku\":1,\"orderTradeType\":\"credit_pay_trade\"}")
                .state("continuation_pending")
                .retryCount(0)
                .build();
        when(pendingRemoteWriteTaskDao.queryPendingTasks(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(task))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("continuation boom"))
                .when(remoteWriteContinuationDispatcher).dispatch(task);

        job.exec();

        verify(pendingRemoteWriteTaskDao, never()).updateDone(any());
        verify(pendingRemoteWriteTaskDao).updateRetryFailed(eq(9L), anyInt());
        // The first scan is the central compensation store: setDBKey is
        // called for the scan and once more after the continuation attempt.
        verify(dbRouter, atLeast(2)).setDBKey(0);
    }

    @Test
    public void continuationPending_successMarksDone() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        PendingRemoteWriteTask task = PendingRemoteWriteTask.builder()
                .id(10L)
                .outBusinessNo("biz-2")
                .operation(RemoteWriteOperations.CREDIT_CREATE)
                .payload("{}")
                .state("continuation_pending")
                .retryCount(0)
                .build();
        when(pendingRemoteWriteTaskDao.queryPendingTasks(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(task))
                .thenReturn(Collections.emptyList());

        job.exec();

        verify(remoteWriteContinuationDispatcher).dispatch(task);
        verify(pendingRemoteWriteTaskDao).updateDone(task);
        verify(pendingRemoteWriteTaskDao, never()).updateRetryFailed(anyLong(), anyInt());
    }

    @Test
    public void quotaRollback_unknownIsRetriedWithTheOriginalBusinessKey() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        PendingRemoteWriteTask task = PendingRemoteWriteTask.builder()
                .id(11L)
                .outBusinessNo("draw-rollback-1")
                .operation(RemoteWriteOperations.QUOTA_ROLLBACK)
                .payload("{\"userId\":\"u1\",\"activityId\":100401,\"outBusinessNo\":\"draw-rollback-1\"}")
                .state("pending")
                .retryCount(0)
                .build();
        when(pendingRemoteWriteTaskDao.queryPendingTasks(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(task))
                .thenReturn(Collections.emptyList());
        when(accountQuotaService.rollbackQuota(any())).thenReturn(Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode()).data(true).build());

        job.exec();

        verify(accountQuotaService).rollbackQuota(argThat(request ->
                "u1".equals(request.getUserId())
                        && Long.valueOf(100401L).equals(request.getActivityId())
                        && "draw-rollback-1".equals(request.getOutBusinessNo())));
        verify(pendingRemoteWriteTaskDao).updateDone(task);
    }
}
