package com.dyx.market.domain.activity.application;

import com.dyx.market.domain.activity.adapter.port.IAwardFulfillmentPort;
import com.dyx.market.domain.activity.adapter.port.IActivityAccountPort;
import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.model.entity.UserRaffleOrderEntity;
import com.dyx.market.domain.activity.model.valobj.UserRaffleOrderStateVO;
import com.dyx.market.domain.activity.service.IRaffleActivityPartakeService;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.domain.strategy.adapter.port.IStrategyStockConfirmCompensationPort;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RaffleApplicationServiceStockRollbackTest {

    private static final String USER_ID = "user-1";
    private static final Long ACTIVITY_ID = 100301L;
    private static final Long STRATEGY_ID = 100001L;
    private static final String ORDER_ID = "order-rollback-001";

    private IRaffleActivityPartakeService partakeService;
    private IStrategyDecisionPort strategyDecisionPort;
    private IAwardFulfillmentPort awardFulfillmentPort;
    private IActivityRepository activityRepository;
    private IStrategyRepository strategyRepository;
    private IStrategyStockConfirmCompensationPort strategyStockConfirmCompensationPort;
    private RaffleApplicationService raffleApplicationService;

    @Before
    public void setUp() throws Exception {
        partakeService = mock(IRaffleActivityPartakeService.class);
        strategyDecisionPort = mock(IStrategyDecisionPort.class);
        awardFulfillmentPort = mock(IAwardFulfillmentPort.class);
        activityRepository = mock(IActivityRepository.class);
        strategyRepository = mock(IStrategyRepository.class);
        strategyStockConfirmCompensationPort = mock(IStrategyStockConfirmCompensationPort.class);

        raffleApplicationService = new RaffleApplicationService();
        inject(raffleApplicationService, "raffleActivityPartakeService", partakeService);
        inject(raffleApplicationService, "strategyDecisionPort", strategyDecisionPort);
        inject(raffleApplicationService, "awardFulfillmentPort", awardFulfillmentPort);
        inject(raffleApplicationService, "activityRepository", activityRepository);
        inject(raffleApplicationService, "activityAccountPort", mock(IActivityAccountPort.class));
        inject(raffleApplicationService, "strategyRepository", strategyRepository);
        inject(raffleApplicationService, "strategyStockConfirmCompensationPort", strategyStockConfirmCompensationPort);
        inject(raffleApplicationService, "remoteQuotaDecrementEnabled", false);

        when(partakeService.createOrder(USER_ID, ACTIVITY_ID)).thenReturn(UserRaffleOrderEntity.builder()
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .strategyId(STRATEGY_ID)
                .orderId(ORDER_ID)
                .orderTime(new Date())
                .orderState(UserRaffleOrderStateVO.create)
                .endDateTime(new Date(System.currentTimeMillis() + 86_400_000L))
                .build());
    }

    @Test
    public void should_release_stock_and_compensate_quota_when_save_award_record_fails() throws Exception {
        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(STRATEGY_ID)
                .awardId(101)
                .reservationId(ORDER_ID)
                .lockSurplus(9L)
                .build();
        when(strategyDecisionPort.performRaffle(any(RaffleFactorEntity.class))).thenReturn(RaffleAwardEntity.builder()
                .awardId(101)
                .awardTitle("测试奖品")
                .awardConfig("rule")
                .sort(1)
                .stockReserved(true)
                .stockReservation(reservation)
                .build());
        doThrow(new RuntimeException("saveUserAwardRecord failed"))
                .when(awardFulfillmentPort).saveUserAwardRecord(any(UserAwardRecordEntity.class));

        try {
            raffleApplicationService.executeDraw(ActivityDrawRequestEntity.builder()
                    .userId(USER_ID)
                    .activityId(ACTIVITY_ID)
                    .build());
            fail("expected save failure");
        } catch (RuntimeException ex) {
            assertEquals("saveUserAwardRecord failed", ex.getMessage());
        }

        ArgumentCaptor<RaffleFactorEntity> factorCaptor = ArgumentCaptor.forClass(RaffleFactorEntity.class);
        verify(strategyDecisionPort).performRaffle(factorCaptor.capture());
        assertEquals(ORDER_ID, factorCaptor.getValue().getOrderId());

        verify(strategyRepository).releaseAwardStockReservation(reservation);
        verify(strategyRepository, never()).confirmAwardStockReservation(any());
        verify(activityRepository).compensatePartakeQuota(eq(USER_ID), eq(ACTIVITY_ID), eq(ORDER_ID), any(Date.class));
        verify(awardFulfillmentPort).saveUserAwardRecord(any(UserAwardRecordEntity.class));
    }

    @Test
    public void should_confirm_stock_after_save_award_record_succeeds() throws Exception {
        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(STRATEGY_ID)
                .awardId(101)
                .reservationId(ORDER_ID)
                .lockSurplus(9L)
                .build();
        when(strategyDecisionPort.performRaffle(any(RaffleFactorEntity.class))).thenReturn(RaffleAwardEntity.builder()
                .awardId(101)
                .awardTitle("测试奖品")
                .awardConfig("rule")
                .sort(1)
                .stockReserved(true)
                .stockReservation(reservation)
                .build());

        ActivityDrawResponseEntity response = raffleApplicationService.executeDraw(ActivityDrawRequestEntity.builder()
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .build());

        assertEquals(Integer.valueOf(101), response.getAwardId());
        verify(strategyRepository).confirmAwardStockReservation(reservation);
        verify(strategyRepository, never()).releaseAwardStockReservation(any());
        verify(activityRepository, never()).compensatePartakeQuota(any(), any(), any(), any());
    }

    @Test
    public void should_enqueue_confirm_task_and_not_release_when_confirm_fails_after_award_saved() throws Exception {
        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(STRATEGY_ID)
                .awardId(101)
                .reservationId(ORDER_ID)
                .lockSurplus(9L)
                .build();
        when(strategyDecisionPort.performRaffle(any(RaffleFactorEntity.class))).thenReturn(RaffleAwardEntity.builder()
                .awardId(101)
                .awardTitle("测试奖品")
                .awardConfig("rule")
                .sort(1)
                .stockReserved(true)
                .stockReservation(reservation)
                .build());
        doThrow(new RuntimeException("confirm failed"))
                .when(strategyRepository).confirmAwardStockReservation(reservation);

        ActivityDrawResponseEntity response = raffleApplicationService.executeDraw(ActivityDrawRequestEntity.builder()
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .build());

        assertEquals(Integer.valueOf(101), response.getAwardId());
        verify(strategyStockConfirmCompensationPort).enqueuePendingConfirm(USER_ID, reservation);
        verify(strategyRepository, never()).releaseAwardStockReservation(any());
        verify(activityRepository, never()).compensatePartakeQuota(any(), any(), any(), any());
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = RaffleApplicationService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
