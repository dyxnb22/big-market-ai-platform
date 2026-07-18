package com.dyx.market.message.job.config;

import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.infrastructure.dao.IRaffleActivityOrderDao;
import com.dyx.market.infrastructure.dao.po.RaffleActivityOrder;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreditPayDeliveryReconcileJobTest {

    private static final String USER_ID = "user-1";
    private static final String OUT_BUSINESS_NO = "convert-sku-001";

    private CreditPayDeliveryReconcileJob job;
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    private IActivityRepository activityRepository;
    private IRaffleActivityOrderDao raffleActivityOrderDao;
    private IRedisService redisService;
    private IDBRouterStrategy dbRouter;

    @Before
    public void setUp() throws Exception {
        job = new CreditPayDeliveryReconcileJob();
        accountCreditWriteAdapter = mock(IAccountCreditWriteAdapter.class);
        activityRepository = mock(IActivityRepository.class);
        raffleActivityOrderDao = mock(IRaffleActivityOrderDao.class);
        redisService = mock(IRedisService.class);
        dbRouter = mock(IDBRouterStrategy.class);

        inject(job, "accountCreditWriteAdapter", accountCreditWriteAdapter);
        inject(job, "activityRepository", activityRepository);
        inject(job, "raffleActivityOrderDao", raffleActivityOrderDao);
        inject(job, "redisService", redisService);
        inject(job, "dbRouter", dbRouter);
        inject(job, "redissonClient", mock(RedissonClient.class));
        inject(job, "restoreSkuStock", true);
    }

    @Test
    public void finishCompensatingOrder_should_not_restore_sku_when_refund_fails() throws Exception {
        RaffleActivityOrder order = buildOrder();
        doThrow(new RuntimeException("refund timeout"))
                .when(accountCreditWriteAdapter).createOrder(any(TradeEntity.class));

        invokePrivate(job, "finishCompensatingOrder", order);

        verify(activityRepository, never()).restoreActivitySkuStock(any());
        verify(raffleActivityOrderDao, never()).updateOrderFailed(any());
    }

    @Test
    public void finishCompensatingOrder_should_complete_when_refund_succeeds() throws Exception {
        RaffleActivityOrder order = buildOrder();
        when(redisService.setNx(any())).thenReturn(true);
        when(raffleActivityOrderDao.updateOrderFailed(any())).thenReturn(1);

        invokePrivate(job, "finishCompensatingOrder", order);

        ArgumentCaptor<TradeEntity> tradeCaptor = ArgumentCaptor.forClass(TradeEntity.class);
        verify(accountCreditWriteAdapter).createOrder(tradeCaptor.capture());
        assertEquals("refund_" + OUT_BUSINESS_NO, tradeCaptor.getValue().getOutBusinessNo());
        verify(activityRepository).restoreActivitySkuStock(eq(order.getSku()), eq(OUT_BUSINESS_NO));
        verify(raffleActivityOrderDao).updateOrderFailed(any());
    }

    @Test
    public void finishCompensatingOrder_should_complete_when_refund_already_exists() throws Exception {
        RaffleActivityOrder order = buildOrder();
        doThrow(new AppException(ResponseCode.INDEX_DUP.getCode(), ResponseCode.INDEX_DUP.getInfo()))
                .when(accountCreditWriteAdapter).createOrder(any(TradeEntity.class));
        when(redisService.setNx(any())).thenReturn(true);
        when(raffleActivityOrderDao.updateOrderFailed(any())).thenReturn(1);

        invokePrivate(job, "finishCompensatingOrder", order);

        verify(activityRepository).restoreActivitySkuStock(eq(order.getSku()), eq(OUT_BUSINESS_NO));
        verify(raffleActivityOrderDao).updateOrderFailed(any());
    }

    @Test
    public void completeCompensation_should_retry_refund_on_second_attempt() throws Exception {
        RaffleActivityOrder order = buildOrder();
        doThrow(new RuntimeException("refund timeout"))
                .doAnswer(invocation -> null)
                .when(accountCreditWriteAdapter).createOrder(any(TradeEntity.class));
        when(redisService.setNx(any())).thenReturn(true);
        when(raffleActivityOrderDao.updateOrderFailed(any())).thenReturn(1);

        invokePrivate(job, "completeCompensation", order);
        verify(activityRepository, never()).restoreActivitySkuStock(any());

        invokePrivate(job, "completeCompensation", order);
        verify(accountCreditWriteAdapter, times(2)).createOrder(any(TradeEntity.class));
        verify(activityRepository).restoreActivitySkuStock(eq(order.getSku()), eq(OUT_BUSINESS_NO));
        verify(raffleActivityOrderDao).updateOrderFailed(any());
    }

    @Test
    public void completeCompensation_should_not_mark_failed_when_sku_restore_fails() throws Exception {
        RaffleActivityOrder order = buildOrder();
        when(redisService.setNx(any())).thenReturn(true);
        doThrow(new RuntimeException("sku restore failed"))
                .when(activityRepository).restoreActivitySkuStock(eq(order.getSku()), eq(OUT_BUSINESS_NO));

        invokePrivate(job, "completeCompensation", order);

        verify(accountCreditWriteAdapter).createOrder(any(TradeEntity.class));
        verify(raffleActivityOrderDao, never()).updateOrderFailed(any());
    }

    @Test
    public void completeCompensation_should_retry_sku_restore_on_second_attempt() throws Exception {
        RaffleActivityOrder order = buildOrder();
        when(redisService.setNx(any())).thenReturn(true);
        doThrow(new RuntimeException("sku restore failed"))
                .doNothing()
                .when(activityRepository).restoreActivitySkuStock(eq(order.getSku()), eq(OUT_BUSINESS_NO));
        when(raffleActivityOrderDao.updateOrderFailed(any())).thenReturn(1);

        invokePrivate(job, "completeCompensation", order);
        verify(raffleActivityOrderDao, never()).updateOrderFailed(any());

        invokePrivate(job, "completeCompensation", order);
        verify(activityRepository, times(2)).restoreActivitySkuStock(eq(order.getSku()), eq(OUT_BUSINESS_NO));
        verify(raffleActivityOrderDao).updateOrderFailed(any());
    }

    private static RaffleActivityOrder buildOrder() {
        RaffleActivityOrder order = new RaffleActivityOrder();
        order.setUserId(USER_ID);
        order.setOutBusinessNo(OUT_BUSINESS_NO);
        order.setPayAmount(new BigDecimal("-100"));
        order.setSku(9001L);
        return order;
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = CreditPayDeliveryReconcileJob.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokePrivate(Object target, String methodName, RaffleActivityOrder order) throws Exception {
        java.lang.reflect.Method method = CreditPayDeliveryReconcileJob.class.getDeclaredMethod(methodName, RaffleActivityOrder.class);
        method.setAccessible(true);
        method.invoke(target, order);
    }
}
