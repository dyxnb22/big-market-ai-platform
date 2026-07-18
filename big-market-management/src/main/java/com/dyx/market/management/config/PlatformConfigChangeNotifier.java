package com.dyx.market.management.config;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fan-out for Nacos config changes.
 *
 * <p>Nacos 3.x SDK maps the default {@code public} namespace to empty-tenant storage while
 * listeners subscribe under the {@code public} key, so long-polling often misses updates on
 * reused volumes. Redis pub/sub keeps admin → market/chatbot refresh reliable for the
 * learning stack without changing money-path contracts.</p>
 *
 * <p>Notify is fail-closed: missing Redisson or transport exceptions fail the admin save.
 * A publish that reaches zero confirmed receivers still succeeds (subscribers may join later).</p>
 */
@Component
public class PlatformConfigChangeNotifier {

    public static final String RUNTIME_TOPIC = "big-market:config:runtime";
    public static final String PLATFORM_TOPIC = "big-market:config:platform";

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigChangeNotifier.class);

    private static final int NOTIFY_ATTEMPTS = 3;
    private static final long NOTIFY_BACKOFF_MS = 100L;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    public void notifyRuntime(String content) {
        publish(RUNTIME_TOPIC, content);
    }

    public void notifyPlatform(String content) {
        publish(PLATFORM_TOPIC, content);
    }

    private void publish(String topicName, String content) {
        if (redissonClient == null) {
            throw new IllegalStateException("RedissonClient unavailable; config fan-out rejected (fail-closed)");
        }
        if (content == null) {
            throw new IllegalStateException("config fan-out payload must not be null");
        }
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= NOTIFY_ATTEMPTS; attempt++) {
            try {
                RTopic topic = redissonClient.getTopic(topicName);
                long receivers = topic.publish(content);
                log.info("Published config change to Redis topic={}, receivers={}, attempt={}",
                        topicName, receivers, attempt);
                return;
            } catch (RuntimeException ex) {
                lastError = ex;
                log.warn("Redis config fan-out attempt {}/{} failed for topic={}: {}",
                        attempt, NOTIFY_ATTEMPTS, topicName, ex.toString());
                if (attempt < NOTIFY_ATTEMPTS) {
                    sleepQuietly(NOTIFY_BACKOFF_MS * attempt);
                }
            }
        }
        throw new IllegalStateException("Failed to publish config change to Redis topic=" + topicName
                + " after " + NOTIFY_ATTEMPTS + " attempts", lastError);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
