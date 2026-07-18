package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.dao.IUserRaffleOrderDao;
import com.dyx.market.infrastructure.dao.IRaffleActivityAccountDao;
import com.dyx.market.infrastructure.dao.IRaffleActivityAccountDayDao;
import com.dyx.market.infrastructure.dao.IRaffleActivityAccountMonthDao;
import com.dyx.market.infrastructure.dao.IRaffleQuotaDecrementLedgerDao;
import com.dyx.market.infrastructure.dao.po.RaffleQuotaDecrementLedger;
import com.dyx.market.infrastructure.dao.po.RaffleActivityAccountDay;
import com.dyx.market.infrastructure.dao.po.RaffleActivityAccountMonth;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ActivityQuotaLedgerSupportTest {

    @Mock
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Mock
    private IRaffleActivityAccountMonthDao raffleActivityAccountMonthDao;
    @Mock
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;
    @Mock
    private IUserRaffleOrderDao userRaffleOrderDao;
    @Mock
    private IRaffleQuotaDecrementLedgerDao raffleQuotaDecrementLedgerDao;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private IDBRouterStrategy dbRouter;

    @InjectMocks
    private ActivityQuotaLedgerSupport support;

    @Test
    public void rollbackWithoutRemoteLedgerIsIdempotentSuccessAndDoesNotRestoreQuota() {
        executeTransactionCallback();
        when(raffleQuotaDecrementLedgerDao.queryByKey(any(RaffleQuotaDecrementLedger.class)))
                .thenReturn(null);

        assertTrue(support.rollbackQuotaWithLedger("u1", 100401L, "draw-not-applied"));

        verify(raffleQuotaDecrementLedgerDao, never()).updateStatusToRolledBack(any());
        verifyNoInteractions(raffleActivityAccountDao, raffleActivityAccountMonthDao,
                raffleActivityAccountDayDao);
    }

    @Test
    public void appliedLedgerCasRestoresExactlyOnceAcrossRepeatedRollback() {
        executeTransactionCallback();
        RaffleQuotaDecrementLedger applied = RaffleQuotaDecrementLedger.builder()
                .userId("u1")
                .activityId(100401L)
                .outBusinessNo("draw-applied")
                .month("2026-07")
                .day("2026-07-18")
                .status("applied")
                .build();
        RaffleQuotaDecrementLedger rolledBack = RaffleQuotaDecrementLedger.builder()
                .userId("u1")
                .activityId(100401L)
                .outBusinessNo("draw-applied")
                .month("2026-07")
                .day("2026-07-18")
                .status("rolled_back")
                .build();
        when(raffleQuotaDecrementLedgerDao.queryByKey(any(RaffleQuotaDecrementLedger.class)))
                .thenReturn(applied, rolledBack);
        when(raffleQuotaDecrementLedgerDao.updateStatusToRolledBack(any())).thenReturn(1);
        when(raffleActivityAccountMonthDao.queryActivityAccountMonthByUserId(any()))
                .thenReturn(RaffleActivityAccountMonth.builder().month("2026-07").build());
        when(raffleActivityAccountDayDao.queryActivityAccountDayByUserId(any()))
                .thenReturn(RaffleActivityAccountDay.builder().day("2026-07-18").build());

        assertTrue(support.rollbackQuotaWithLedger("u1", 100401L, "draw-applied"));
        assertTrue(support.rollbackQuotaWithLedger("u1", 100401L, "draw-applied"));

        verify(raffleQuotaDecrementLedgerDao, times(1)).updateStatusToRolledBack(any());
        verify(raffleActivityAccountDao, times(1)).addAccountTotalSurplusQuota(any());
        verify(raffleActivityAccountMonthDao, times(1)).addAccountQuota(any());
        verify(raffleActivityAccountDayDao, times(1)).addAccountQuota(any());
    }

    @SuppressWarnings("unchecked")
    private void executeTransactionCallback() {
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = (TransactionCallback<Object>) invocation.getArguments()[0];
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }
}
