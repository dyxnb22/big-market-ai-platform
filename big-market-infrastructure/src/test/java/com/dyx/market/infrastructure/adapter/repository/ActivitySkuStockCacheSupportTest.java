package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.dyx.market.infrastructure.dao.IActivitySkuStockDecrementLedgerDao;
import com.dyx.market.infrastructure.dao.IRaffleActivitySkuDao;
import com.dyx.market.infrastructure.dao.po.ActivitySkuStockDecrementLedger;
import com.dyx.market.infrastructure.redis.IRedisService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.RBlockingQueue;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BM-008: SKU 队列落库按 lockSurplus + MySQL ledger 幂等。
 */
@RunWith(MockitoJUnitRunner.class)
public class ActivitySkuStockCacheSupportTest {

    private static final Long SKU = 9011L;
    private static final Long ACTIVITY_ID = 100401L;

    @Mock
    private IRedisService redisService;

    @Mock
    private IRaffleActivitySkuDao raffleActivitySkuDao;

    @Mock
    private IActivitySkuStockDecrementLedgerDao activitySkuStockDecrementLedgerDao;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ActivitySkuStockCacheSupport support;

    @Mock
    @SuppressWarnings("rawtypes")
    private RBlockingQueue blockingQueue;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        when(redisService.getBlockingQueue(anyString())).thenReturn(blockingQueue);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    public void syncFromQueue_decrementsDbOncePerLockSurplus() {
        ActivitySkuStockKeyVO first = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(5L).build();
        ActivitySkuStockKeyVO second = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(4L).build();

        when(blockingQueue.peek()).thenReturn(first, second, null);
        when(blockingQueue.isEmpty()).thenReturn(false, false);
        when(redisService.setNx(startsWith("sku_mysql_decrement:" + SKU + ":"), eq(7L), eq(TimeUnit.DAYS)))
                .thenReturn(true);

        support.syncActivitySkuStockFromQueue(SKU);
        support.syncActivitySkuStockFromQueue(SKU);

        verify(raffleActivitySkuDao, times(2)).updateActivitySkuStock(SKU);
        verify(activitySkuStockDecrementLedgerDao, times(2)).insert(any());
        verify(blockingQueue, times(2)).poll();
    }

    @Test
    public void syncFromQueue_dbFailure_removesDedupeAndDoesNotAck() {
        ActivitySkuStockKeyVO stockKey = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(3L).build();
        String dedupeKey = "sku_mysql_decrement:" + SKU + ":3";

        when(blockingQueue.peek()).thenReturn(stockKey);
        when(redisService.setNx(eq(dedupeKey), eq(7L), eq(TimeUnit.DAYS))).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(raffleActivitySkuDao).updateActivitySkuStock(SKU);

        support.syncActivitySkuStockFromQueue(SKU);

        verify(redisService).remove(dedupeKey);
        verify(blockingQueue, never()).poll();
    }

    @Test
    public void syncFromQueue_crashWindow_setnxWithoutLedger_retriesDb() {
        ActivitySkuStockKeyVO stockKey = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(2L).build();
        String dedupeKey = "sku_mysql_decrement:" + SKU + ":2";

        when(blockingQueue.peek()).thenReturn(stockKey);
        when(redisService.setNx(eq(dedupeKey), eq(7L), eq(TimeUnit.DAYS))).thenReturn(false, true);
        when(activitySkuStockDecrementLedgerDao.queryBySkuAndLockSurplus(any())).thenReturn(null);

        support.syncActivitySkuStockFromQueue(SKU);

        verify(redisService).remove(dedupeKey);
        verify(activitySkuStockDecrementLedgerDao).insert(any());
        verify(raffleActivitySkuDao).updateActivitySkuStock(SKU);
        verify(blockingQueue).poll();
    }

    @Test
    public void syncFromQueue_duplicateLedger_acksWithoutSecondDecrement() {
        ActivitySkuStockKeyVO stockKey = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(1L).build();
        String dedupeKey = "sku_mysql_decrement:" + SKU + ":1";

        when(blockingQueue.peek()).thenReturn(stockKey);
        when(redisService.setNx(eq(dedupeKey), eq(7L), eq(TimeUnit.DAYS))).thenReturn(true);
        doThrow(new DuplicateKeyException("dup")).when(activitySkuStockDecrementLedgerDao).insert(any());

        support.syncActivitySkuStockFromQueue(SKU);

        verify(raffleActivitySkuDao, never()).updateActivitySkuStock(SKU);
        verify(blockingQueue).poll();
    }

    @Test
    public void syncFromQueue_ledgerExists_acksWithoutDb() {
        ActivitySkuStockKeyVO stockKey = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(7L).build();
        String dedupeKey = "sku_mysql_decrement:" + SKU + ":7";

        when(blockingQueue.peek()).thenReturn(stockKey);
        when(redisService.setNx(eq(dedupeKey), eq(7L), eq(TimeUnit.DAYS))).thenReturn(false);
        when(activitySkuStockDecrementLedgerDao.queryBySkuAndLockSurplus(any()))
                .thenReturn(ActivitySkuStockDecrementLedger.builder().sku(SKU).lockSurplus(7L).build());

        support.syncActivitySkuStockFromQueue(SKU);

        verify(raffleActivitySkuDao, never()).updateActivitySkuStock(SKU);
        verify(blockingQueue).poll();
    }
}
