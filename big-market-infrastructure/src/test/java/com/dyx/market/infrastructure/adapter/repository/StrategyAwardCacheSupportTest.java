package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.infrastructure.dao.IStrategyAwardDao;
import com.dyx.market.infrastructure.dao.IStrategyAwardStockDecrementLedgerDao;
import com.dyx.market.infrastructure.dao.po.StrategyAwardStockDecrementLedger;
import com.dyx.market.infrastructure.redis.IRedisService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.api.RBlockingQueue;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * G-01 / durable ledger: 预占失败回滚、确认入队、MySQL ledger 幂等。
 */
@RunWith(MockitoJUnitRunner.class)
public class StrategyAwardCacheSupportTest {

    private static final Long STRATEGY_ID = 100001L;
    private static final Integer AWARD_ID = 101;
    private static final String ORDER_ID = "order-test-001";
    private static final String DEDUPE_KEY = "stock_confirm:" + ORDER_ID;
    private static final String MYSQL_DEDUPE_KEY = "stock_mysql_decrement:" + ORDER_ID;

    @Mock
    private IRedisService redisService;

    @Mock
    private IStrategyAwardDao strategyAwardDao;

    @Mock
    private IStrategyAwardStockDecrementLedgerDao strategyAwardStockDecrementLedgerDao;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private StrategyAwardCacheSupport support;

    private String cacheKey;

    @Before
    public void setUp() {
        cacheKey = com.dyx.market.types.common.Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY
                + STRATEGY_ID + com.dyx.market.types.common.Constants.UNDERLINE + AWARD_ID;
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
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
        verify(redisService).remove(cacheKey + com.dyx.market.types.common.Constants.UNDERLINE + "5");
    }

    @Test
    public void confirmReservation_enqueuesThenSetsDedupeKey() {
        StrategyAwardStockKeyVO reservation = buildReservation();
        when(redisService.isExists(DEDUPE_KEY)).thenReturn(false);
        when(redisService.getBlockingQueue(anyString())).thenReturn(mock(org.redisson.api.RBlockingQueue.class));
        when(redisService.getDelayedQueue(any())).thenReturn(mock(org.redisson.api.RDelayedQueue.class));

        support.confirmReservation(reservation);

        InOrder inOrder = inOrder(redisService);
        inOrder.verify(redisService).isExists(DEDUPE_KEY);
        inOrder.verify(redisService).getDelayedQueue(any());
        inOrder.verify(redisService).setNx(eq(DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS));
        verify(redisService, never()).incr(cacheKey);
        verify(redisService).addToSet(eq(com.dyx.market.types.common.Constants.RedisKey.STRATEGY_AWARD_STOCK_PENDING_SET), anyString());
    }

    @Test
    public void confirmReservation_skipsDuplicateEnqueue() {
        StrategyAwardStockKeyVO reservation = buildReservation();
        when(redisService.isExists(DEDUPE_KEY)).thenReturn(true);

        support.confirmReservation(reservation);

        verify(redisService, never()).getDelayedQueue(any());
        verify(redisService, never()).setNx(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test(expected = RuntimeException.class)
    public void confirmReservation_offerFailure_doesNotSetDedupeKey() {
        StrategyAwardStockKeyVO reservation = buildReservation();
        when(redisService.isExists(DEDUPE_KEY)).thenReturn(false);
        when(redisService.getBlockingQueue(anyString())).thenThrow(new RuntimeException("redis down"));

        try {
            support.confirmReservation(reservation);
        } finally {
            verify(redisService, never()).setNx(anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Test
    public void updateStrategyAwardStockOnce_decrements_once_per_reservationId() {
        StrategyAwardStockKeyVO stockKey = buildReservation();
        when(redisService.setNx(eq(MYSQL_DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS))).thenReturn(true);

        support.updateStrategyAwardStockOnce(stockKey);
        // Second call: SETNX fails but ledger exists → skip DB
        when(redisService.setNx(eq(MYSQL_DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS))).thenReturn(false);
        when(strategyAwardStockDecrementLedgerDao.queryByReservationId(ORDER_ID))
                .thenReturn(StrategyAwardStockDecrementLedger.builder().reservationId(ORDER_ID).build());
        support.updateStrategyAwardStockOnce(stockKey);

        verify(strategyAwardDao, times(1)).updateStrategyAwardStock(any());
        verify(strategyAwardStockDecrementLedgerDao, times(1)).insert(any());
    }

    @Test
    public void updateStrategyAwardStockOnce_crashWindow_setnxWithoutLedger_retriesDb() {
        StrategyAwardStockKeyVO stockKey = buildReservation();
        // Simulate crash: Redis SETNX already set, but MySQL ledger missing
        when(redisService.setNx(eq(MYSQL_DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS))).thenReturn(false, true);
        when(strategyAwardStockDecrementLedgerDao.queryByReservationId(ORDER_ID)).thenReturn(null);

        support.updateStrategyAwardStockOnce(stockKey);

        verify(redisService).remove(MYSQL_DEDUPE_KEY);
        verify(strategyAwardStockDecrementLedgerDao).insert(any());
        verify(strategyAwardDao).updateStrategyAwardStock(any());
    }

    @Test
    public void updateStrategyAwardStockOnce_duplicateLedger_skipsStockUpdate() {
        StrategyAwardStockKeyVO stockKey = buildReservation();
        when(redisService.setNx(eq(MYSQL_DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS))).thenReturn(true);
        doThrow(new DuplicateKeyException("dup")).when(strategyAwardStockDecrementLedgerDao).insert(any());

        support.updateStrategyAwardStockOnce(stockKey);

        verify(strategyAwardDao, never()).updateStrategyAwardStock(any());
    }

    @Test
    public void updateStrategyAwardStockOnce_lostReservedCas_doesNotDecrementStock() {
        StrategyAwardStockKeyVO stockKey = buildReservation();
        when(redisService.setNx(eq(MYSQL_DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS))).thenReturn(true);
        when(strategyAwardStockDecrementLedgerDao.queryByReservationId(ORDER_ID))
                .thenReturn(StrategyAwardStockDecrementLedger.builder()
                        .reservationId(ORDER_ID).strategyId(STRATEGY_ID).awardId(AWARD_ID)
                        .status("reserved").build());
        when(strategyAwardStockDecrementLedgerDao.updateStatusApplied(ORDER_ID)).thenReturn(0);

        support.updateStrategyAwardStockOnce(stockKey);

        verify(strategyAwardStockDecrementLedgerDao).updateStatusApplied(ORDER_ID);
        verify(strategyAwardDao, never()).updateStrategyAwardStock(any());
    }

    @Test
    public void updateStrategyAwardStockOnce_removes_dedupe_when_db_update_fails() {
        StrategyAwardStockKeyVO stockKey = buildReservation();
        when(redisService.setNx(eq(MYSQL_DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS))).thenReturn(true);
        doThrow(new RuntimeException("db down"))
                .when(strategyAwardDao).updateStrategyAwardStock(any());

        try {
            support.updateStrategyAwardStockOnce(stockKey);
            fail("expected db failure");
        } catch (RuntimeException e) {
            assertEquals("db down", e.getMessage());
        }

        verify(redisService).remove(MYSQL_DEDUPE_KEY);
    }

    @Test
    public void syncStrategyAwardStockFromQueue_resumesDurableReservationWhenQueueMissing() {
        RBlockingQueue<StrategyAwardStockKeyVO> queue = mock(RBlockingQueue.class);
        doReturn(queue).when(redisService).getBlockingQueue(anyString());
        when(queue.peek()).thenReturn(null);
        when(strategyAwardStockDecrementLedgerDao.queryReservedByStrategyAward(STRATEGY_ID, AWARD_ID))
                .thenReturn(java.util.Collections.singletonList(StrategyAwardStockDecrementLedger.builder()
                        .strategyId(STRATEGY_ID).awardId(AWARD_ID).reservationId(ORDER_ID).lockSurplus(5L)
                        .status("reserved").build()));
        when(redisService.setNx(eq(MYSQL_DEDUPE_KEY), eq(7L), eq(TimeUnit.DAYS))).thenReturn(true);

        support.syncStrategyAwardStockFromQueue(STRATEGY_ID, AWARD_ID);

        verify(strategyAwardDao).updateStrategyAwardStock(any());
    }

    private static StrategyAwardStockKeyVO buildReservation() {
        return StrategyAwardStockKeyVO.builder()
                .strategyId(STRATEGY_ID)
                .awardId(AWARD_ID)
                .reservationId(ORDER_ID)
                .lockSurplus(5L)
                .build();
    }
}
