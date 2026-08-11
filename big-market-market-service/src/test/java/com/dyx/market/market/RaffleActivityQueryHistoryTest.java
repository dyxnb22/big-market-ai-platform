package com.dyx.market.market;

import com.dyx.market.domain.award.model.entity.UserAwardRecordLogEntity;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.domain.activity.service.IRaffleActivitySkuProductService;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.api.dto.CreditOrderResponseDTO;
import com.dyx.market.trigger.api.dto.UserAwardRecordResponseDTO;
import com.dyx.market.trigger.application.RaffleActivityQueryApplicationService;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 服务端抽奖历史 / 积分账本查询：读模型 → DTO 映射与参数校验。
 */
@RunWith(MockitoJUnitRunner.class)
public class RaffleActivityQueryHistoryTest {

    @Mock
    private IAccountReadAdapter accountRemoteReadAdapter;
    @Mock
    private IRaffleActivitySkuProductService raffleActivitySkuProductService;
    @Mock
    private IAwardService awardService;

    @InjectMocks
    private RaffleActivityQueryApplicationService service;

    @Test
    public void queryUserAwardRecords_mapsReadModelToDto() {
        Date awardTime = new Date();
        when(awardService.queryUserAwardRecords(eq("user-1"), anyInt())).thenReturn(Collections.singletonList(
                UserAwardRecordLogEntity.builder()
                        .activityId(100301L)
                        .orderId("order-1")
                        .awardId(101)
                        .awardTitle("随机积分")
                        .awardState("create")
                        .awardTime(awardTime)
                        .build()));

        List<UserAwardRecordResponseDTO> result = service.queryUserAwardRecords("user-1");

        assertEquals(1, result.size());
        UserAwardRecordResponseDTO dto = result.get(0);
        assertEquals(Long.valueOf(100301L), dto.getActivityId());
        assertEquals("order-1", dto.getOrderId());
        assertEquals(Integer.valueOf(101), dto.getAwardId());
        assertEquals("随机积分", dto.getAwardTitle());
        assertEquals("create", dto.getAwardState());
        assertEquals(awardTime, dto.getAwardTime());
    }

    @Test
    public void queryUserAwardRecords_rejectsBlankUserId() {
        try {
            service.queryUserAwardRecords(" ");
        } catch (AppException e) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), e.getCode());
            return;
        }
        throw new AssertionError("expected AppException for blank userId");
    }

    @Test
    public void queryUserCreditOrders_delegatesToAccountReadAdapter() {
        Date createTime = new Date();
        when(accountRemoteReadAdapter.queryUserCreditOrders(eq("user-1"), anyInt())).thenReturn(Collections.singletonList(
                CreditOrderResponseDTO.builder()
                        .orderId("credit-order-1")
                        .tradeName("行为返利")
                        .tradeType("forward")
                        .tradeAmount(BigDecimal.TEN)
                        .createTime(createTime)
                        .build()));

        List<CreditOrderResponseDTO> result = service.queryUserCreditOrders("user-1");

        assertEquals(1, result.size());
        assertEquals("credit-order-1", result.get(0).getOrderId());
        assertEquals("forward", result.get(0).getTradeType());
        assertEquals(BigDecimal.TEN, result.get(0).getTradeAmount());
    }

    @Test
    public void queryUserCreditOrders_rejectsBlankUserId() {
        try {
            service.queryUserCreditOrders("");
        } catch (AppException e) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), e.getCode());
            return;
        }
        throw new AssertionError("expected AppException for blank userId");
    }
}
