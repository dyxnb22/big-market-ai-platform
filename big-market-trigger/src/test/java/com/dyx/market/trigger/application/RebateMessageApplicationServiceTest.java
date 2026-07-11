package com.dyx.market.trigger.application;

import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * GOV-C03: send_rebate replay / INDEX_DUP (and SKU stock) are benign for the consumer.
 */
public class RebateMessageApplicationServiceTest {

    private final RebateMessageApplicationService service = new RebateMessageApplicationService();

    @Test
    public void isBenignConsumerError_indexDup() {
        assertTrue(service.isBenignConsumerError(
                new AppException(ResponseCode.INDEX_DUP.getCode(), ResponseCode.INDEX_DUP.getInfo())));
    }

    @Test
    public void isBenignConsumerError_activitySkuStock() {
        assertTrue(service.isBenignConsumerError(
                new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode(),
                        ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getInfo())));
    }

    @Test
    public void isBenignConsumerError_otherCodesAreNotBenign() {
        assertFalse(service.isBenignConsumerError(
                new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        ResponseCode.ILLEGAL_PARAMETER.getInfo())));
        assertFalse(service.isBenignConsumerError(
                new AppException(ResponseCode.UN_ERROR.getCode(), ResponseCode.UN_ERROR.getInfo())));
    }
}
