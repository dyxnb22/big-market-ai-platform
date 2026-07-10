package com.dyx.market.market.config;

import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.RemoteWriteOutcome;
import com.dyx.market.types.enums.ResponseCode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountRemoteCreditWriteAdapterTest {

    @Test
    public void classify_successAndIndexDup() {
        assertEquals(RemoteWriteOutcome.SUCCESS,
                AccountRemoteCreditWriteAdapter.classify(Response.<String>builder()
                        .code(ResponseCode.SUCCESS.getCode()).build()));
        assertEquals(RemoteWriteOutcome.SUCCESS,
                AccountRemoteCreditWriteAdapter.classify(Response.<String>builder()
                        .code(ResponseCode.INDEX_DUP.getCode()).build()));
    }

    @Test
    public void classify_rejectedVsUnknown() {
        assertEquals(RemoteWriteOutcome.REJECTED,
                AccountRemoteCreditWriteAdapter.classify(Response.<String>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode()).build()));
        assertEquals(RemoteWriteOutcome.REJECTED,
                AccountRemoteCreditWriteAdapter.classify(Response.<String>builder()
                        .code(ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode()).build()));
        assertEquals(RemoteWriteOutcome.UNKNOWN,
                AccountRemoteCreditWriteAdapter.classify(Response.<String>builder()
                        .code(ResponseCode.UN_ERROR.getCode()).build()));
        assertEquals(RemoteWriteOutcome.UNKNOWN, AccountRemoteCreditWriteAdapter.classify(null));
    }
}
