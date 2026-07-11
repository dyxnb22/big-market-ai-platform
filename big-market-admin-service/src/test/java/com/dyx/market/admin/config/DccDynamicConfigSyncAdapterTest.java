package com.dyx.market.admin.config;

import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DccDynamicConfigSyncAdapterTest {

    private DccDynamicConfigSyncAdapter adapter;
    private RestTemplate restTemplate;

    @Before
    public void setUp() {
        adapter = new DccDynamicConfigSyncAdapter();
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(adapter, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(adapter, "gatewayApiHost", "http://127.0.0.1:8080");
        ReflectionTestUtils.setField(adapter, "apiVersion", "v1");
        ReflectionTestUtils.setField(adapter, "adminToken", "admin-dev-token");
    }

    @Test
    public void syncRateLimiterSwitch_should_throw_when_http_not_success() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"code\":\"0001\"}", HttpStatus.OK));

        try {
            adapter.syncRateLimiterSwitch("open");
            fail("expected AppException");
        } catch (AppException e) {
            assertEquals(ResponseCode.UN_ERROR.getCode(), e.getCode());
        }
    }

    @Test
    public void syncRateLimiterSwitch_should_throw_when_network_fails() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        try {
            adapter.syncRateLimiterSwitch("open");
            fail("expected AppException");
        } catch (AppException e) {
            assertEquals(ResponseCode.UN_ERROR.getCode(), e.getCode());
        }
    }

    @Test
    public void syncRateLimiterSwitch_should_succeed_when_body_code_is_0000() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"code\":\"0000\"}", HttpStatus.OK));

        adapter.syncRateLimiterSwitch("open");
    }
}
