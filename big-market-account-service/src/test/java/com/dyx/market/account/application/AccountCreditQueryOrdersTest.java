package com.dyx.market.account.application;

import com.dyx.market.domain.credit.model.entity.CreditOrderLogEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.infrastructure.dao.IUserCreditOrderDao;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.api.dto.CreditOrderResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * account 侧积分流水查询：读模型 → RPC DTO 映射与参数校验。
 */
@RunWith(MockitoJUnitRunner.class)
public class AccountCreditQueryOrdersTest {

    @Mock
    private ICreditAdjustService creditAdjustService;
    @Mock
    private IUserCreditOrderDao userCreditOrderDao;
    @Mock
    private IDBRouterStrategy dbRouter;

    @InjectMocks
    private AccountCreditApplicationService service;

    @Test
    public void queryUserCreditOrders_mapsReadModelToDto() {
        Date createTime = new Date();
        when(creditAdjustService.queryUserCreditOrders("user-1", 50)).thenReturn(Arrays.asList(
                CreditOrderLogEntity.builder()
                        .orderId("order-1")
                        .tradeName("行为返利")
                        .tradeType("forward")
                        .tradeAmount(BigDecimal.TEN)
                        .createTime(createTime)
                        .build(),
                CreditOrderLogEntity.builder()
                        .orderId("order-2")
                        .tradeName("AI对话消耗")
                        .tradeType("reverse")
                        .tradeAmount(BigDecimal.valueOf(-1))
                        .createTime(createTime)
                        .build()));

        List<CreditOrderResponseDTO> result = service.queryUserCreditOrders("user-1", 50);

        assertEquals(2, result.size());
        assertEquals("order-1", result.get(0).getOrderId());
        assertEquals("行为返利", result.get(0).getTradeName());
        assertEquals("forward", result.get(0).getTradeType());
        assertEquals(BigDecimal.TEN, result.get(0).getTradeAmount());
        assertEquals(createTime, result.get(0).getCreateTime());
        assertEquals("reverse", result.get(1).getTradeType());
        assertEquals(BigDecimal.valueOf(-1), result.get(1).getTradeAmount());
    }

    @Test
    public void queryUserCreditOrders_rejectsBlankUserId() {
        try {
            service.queryUserCreditOrders(" ", 50);
        } catch (AppException e) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), e.getCode());
            return;
        }
        throw new AssertionError("expected AppException for blank userId");
    }

    @Test
    public void queryUserCreditOrders_rejectsNonPositiveLimit() {
        try {
            service.queryUserCreditOrders("user-1", 0);
        } catch (AppException e) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), e.getCode());
            return;
        }
        throw new AssertionError("expected AppException for non-positive limit");
    }
}
