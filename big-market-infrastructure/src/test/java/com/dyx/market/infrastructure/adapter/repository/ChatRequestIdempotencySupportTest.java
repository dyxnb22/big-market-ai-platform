package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.redis.IRedisService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BM-010: requestId 幂等 — 原子占位、失败清除、完成缓存。
 */
@RunWith(MockitoJUnitRunner.class)
public class ChatRequestIdempotencySupportTest {

    @Mock
    private IRedisService redisService;

    @InjectMocks
    private ChatRequestIdempotencySupport support;

    @Test
    public void tryMarkProcessing_usesSetNx() {
        when(redisService.setNx(eq("chat:request:req-1"), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        assertTrue(support.tryMarkProcessing("req-1"));

        verify(redisService).setValue(eq("chat:request:req-1"), argThat(v -> v != null && v.toString().contains("processing")), anyLong());
    }

    @Test
    public void tryMarkProcessing_returnsFalseWhenKeyExists() {
        when(redisService.setNx(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        assertFalse(support.tryMarkProcessing("req-2"));
        verify(redisService, never()).setValue(anyString(), any(), anyLong());
    }

    @Test
    public void clearProcessing_removesOnlyProcessingState() {
        when(redisService.getValue("chat:request:req-3"))
                .thenReturn("{\"status\":\"processing\"}");

        support.clearProcessing("req-3");

        verify(redisService).remove("chat:request:req-3");
    }

    @Test
    public void clearProcessing_skipsCompleted() {
        when(redisService.getValue("chat:request:req-4"))
                .thenReturn("{\"status\":\"completed\",\"answer\":\"ok\"}");

        support.clearProcessing("req-4");

        verify(redisService, never()).remove(anyString());
    }
}
