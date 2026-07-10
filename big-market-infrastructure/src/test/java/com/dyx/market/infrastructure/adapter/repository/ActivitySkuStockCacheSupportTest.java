package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.model.valobj.ActivitySkuStockKeyVO;
import com.dyx.market.infrastructure.dao.IRaffleActivitySkuDao;
import com.dyx.market.infrastructure.redis.IRedisService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.RBlockingQueue;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BM-008: SKU 队列落库按 lockSurplus 幂等，每条 Redis 扣减对应一次 MySQL -1。
 */
@RunWith(MockitoJUnitRunner.class)
public class ActivitySkuStockCacheSupportTest {

    private static final Long SKU = 9011L;
    private static final Long ACTIVITY_ID = 100401L;

    @Mock
    private IRedisService redisService;

    @Mock
    private IRaffleActivitySkuDao raffleActivitySkuDao;

    @InjectMocks
    private ActivitySkuStockCacheSupport support;

    @Mock
    @SuppressWarnings("rawtypes")
    private RBlockingQueue blockingQueue;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        when(redisService.getBlockingQueue(anyString())).thenReturn(blockingQueue);
    }

    @Test
    public void syncFromQueue_decrementsDbOncePerLockSurplus() {
        ActivitySkuStockKeyVO first = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(5L).build();
        ActivitySkuStockKeyVO second = ActivitySkuStockKeyVO.builder()
                .sku(SKU).activityId(ACTIVITY_ID).lockSurplus(4L).build();

        when(blockingQueue.peek()).thenReturn(first, second, null);
        when(redisService.setNx(startsWith("sku_mysql_decrement:" + SKU + ":"), eq(7L), eq(TimeUnit.DAYS)))
                .thenReturn(true);

        support.syncActivitySkuStockFromQueue(SKU);
        support.syncActivitySkuStockFromQueue(SKU);

        verify(raffleActivitySkuDao, times(2)).updateActivitySkuStock(SKU);
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
}
