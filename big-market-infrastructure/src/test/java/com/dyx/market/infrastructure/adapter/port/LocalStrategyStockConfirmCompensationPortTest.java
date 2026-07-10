package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.infrastructure.dao.IStrategyAwardStockConfirmTaskDao;
import com.dyx.market.infrastructure.dao.po.StrategyAwardStockConfirmTask;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LocalStrategyStockConfirmCompensationPortTest {

    private static final String USER_ID = "user-db1";
    private static final String ORDER_ID = "order-confirm-001";

    private LocalStrategyStockConfirmCompensationPort port;
    private IStrategyAwardStockConfirmTaskDao taskDao;
    private IDBRouterStrategy dbRouter;

    @Before
    public void setUp() throws Exception {
        port = new LocalStrategyStockConfirmCompensationPort();
        taskDao = mock(IStrategyAwardStockConfirmTaskDao.class);
        dbRouter = mock(IDBRouterStrategy.class);
        inject(port, "strategyAwardStockConfirmTaskDao", taskDao);
        inject(port, "dbRouter", dbRouter);
    }

    @Test
    public void enqueuePendingConfirm_should_route_by_userId() {
        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(100001L)
                .awardId(101)
                .reservationId(ORDER_ID)
                .lockSurplus(9L)
                .build();

        port.enqueuePendingConfirm(USER_ID, reservation);

        verify(dbRouter).doRouter(USER_ID);
        verify(dbRouter).clear();
        ArgumentCaptor<StrategyAwardStockConfirmTask> captor = ArgumentCaptor.forClass(StrategyAwardStockConfirmTask.class);
        verify(taskDao).insert(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(ORDER_ID, captor.getValue().getOrderId());
    }

    @Test
    public void markConfirmed_should_use_scan_db_key() {
        when(taskDao.updateConfirmed(any())).thenReturn(1);

        int updated = port.markConfirmed(1, USER_ID, ORDER_ID);

        assertEquals(1, updated);
        verify(dbRouter).setDBKey(1);
        verify(dbRouter).clear();
    }

    @Test
    public void claimProcessing_should_use_scan_db_key() {
        when(taskDao.claimProcessing(any())).thenReturn(1);

        int claimed = port.claimProcessing(2, USER_ID, ORDER_ID);

        assertEquals(1, claimed);
        verify(dbRouter).setDBKey(2);
        verify(dbRouter).clear();
    }

    @Test
    public void revertStaleProcessing_should_use_scan_db_key() {
        when(taskDao.revertStaleProcessing(any(), eq(10))).thenReturn(2);

        java.util.Date staleBefore = new java.util.Date();
        int reverted = port.revertStaleProcessing(1, staleBefore, 10);

        assertEquals(2, reverted);
        verify(dbRouter).setDBKey(1);
        verify(dbRouter).clear();
        verify(taskDao).revertStaleProcessing(staleBefore, 10);
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = LocalStrategyStockConfirmCompensationPort.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
