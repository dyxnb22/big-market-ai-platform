package com.dyx.market.management.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformConfigChangeNotifierTest {

    @Test
    void zeroReceivers_areReportedAsPendingAfterRetries() {
        RedissonClient client = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(client.getTopic(PlatformConfigChangeNotifier.RUNTIME_TOPIC)).thenReturn(topic);
        when(topic.publish(anyString())).thenReturn(0L);

        PlatformConfigChangeNotifier notifier = new PlatformConfigChangeNotifier();
        ReflectionTestUtils.setField(notifier, "redissonClient", client);

        assertFalse(notifier.notifyRuntime("key=value\n"));
        verify(topic, times(3)).publish("key=value\n");
    }
}
