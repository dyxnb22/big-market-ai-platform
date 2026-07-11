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
 * NR-002: requestId 幂等按 userId 隔离，原子占位。
 */
@RunWith(MockitoJUnitRunner.class)
public class ChatRequestIdempotencySupportTest {

    @Mock
    private IRedisService redisService;

    @InjectMocks
    private ChatRequestIdempotencySupport support;

    @Test
    public void tryMarkProcessing_usesUserScopedKeyAndAtomicSet() {
        when(redisService.setValueIfAbsent(eq("chat:request:user-1:req-1"), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);

        assertTrue(support.tryMarkProcessing("user-1", "req-1"));

        verify(redisService).setValueIfAbsent(eq("chat:request:user-1:req-1"),
                argThat(v -> v instanceof String && ((String) v).contains("processing")),
                anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(redisService, never()).setValue(anyString(), any(), anyLong());
    }

    @Test
    public void tryMarkProcessing_returnsFalseWhenKeyExists() {
        when(redisService.setValueIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        assertFalse(support.tryMarkProcessing("user-2", "req-2"));
    }

    @Test
    public void clearProcessing_removesOnlyProcessingState() {
        when(redisService.getValue("chat:request:user-3:req-3"))
                .thenReturn("{\"status\":\"processing\"}");

        support.clearProcessing("user-3", "req-3");

        verify(redisService).remove("chat:request:user-3:req-3");
    }

    @Test
    public void findCompleted_isolatedByUser() {
        when(redisService.getValue("chat:request:user-a:shared-req"))
                .thenReturn("{\"status\":\"completed\",\"answer\":\"secret\",\"success\":true}");

        ChatRequestIdempotencySupport.CachedChatResponse cached =
                support.findCompleted("user-a", "shared-req");
        assertNotNull(cached);
        assertEquals("secret", cached.getAnswer());

        assertNull(support.findCompleted("user-b", "shared-req"));
    }
}
