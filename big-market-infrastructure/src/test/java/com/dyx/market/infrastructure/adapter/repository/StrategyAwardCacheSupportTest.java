package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.types.common.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * G-01 故障注入：预占失败回滚、确认入队、释放 INCR。
 */
@RunWith(MockitoJUnitRunner.class)
public class StrategyAwardCacheSupportTest {

    private static final Long STRATEGY_ID = 100001L;
    private static final Integer AWARD_ID = 101;
    private static final String ORDER_ID = "order-test-001";

    @Mock
    private IRedisService redisService;

    @InjectMocks
    private StrategyAwardCacheSupport support;

    private String cacheKey;

    @Before
    public void setUp() {
        cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + STRATEGY_ID + Constants.UNDERLINE + AWARD_ID;
    }

    @Test
    public void reserveStock_returnsNullWhenSurplusNegative() {
        when(redisService.decr(cacheKey)).thenReturn(-1L);
        assertNull(support.reserveStock(STRATEGY_ID, AWARD_ID, new Date(), ORDER_ID));
        verify(redisService).setAtomicLong(cacheKey, 0);
        verify(redisService, never()).setNx(anyString());
    }

    @Test
    public void reserveStock_returnsReservationWhenLockSucceeds() {
        when(redisService.decr(cacheKey)).thenReturn(5L);
        when(redisService.setNx(anyString())).thenReturn(true);

        StrategyAwardStockKeyVO vo = support.reserveStock(STRATEGY_ID, AWARD_ID, null, ORDER_ID);

        assertNotNull(vo);
        assertEquals(ORDER_ID, vo.getReservationId());
        assertEquals(Long.valueOf(5L), vo.getLockSurplus());
        verify(redisService, never()).incr(cacheKey);
    }

    @Test
    public void releaseReservation_incrementsAndRemovesLock() {
        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(STRATEGY_ID)
                .awardId(AWARD_ID)
                .reservationId(ORDER_ID)
                .lockSurplus(5L)
                .build();

        support.releaseReservation(reservation);

        verify(redisService).incr(cacheKey);
        verify(redisService).remove(cacheKey + Constants.UNDERLINE + "5");
    }

    @Test
    public void confirmReservation_enqueuesWithoutIncr() {
        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(STRATEGY_ID)
                .awardId(AWARD_ID)
                .reservationId(ORDER_ID)
                .lockSurplus(5L)
                .build();

        when(redisService.getBlockingQueue(anyString())).thenReturn(mock(org.redisson.api.RBlockingQueue.class));
        when(redisService.getDelayedQueue(any())).thenReturn(mock(org.redisson.api.RDelayedQueue.class));

        support.confirmReservation(reservation);

        verify(redisService, never()).incr(cacheKey);
        verify(redisService).getDelayedQueue(any());
    }
}
