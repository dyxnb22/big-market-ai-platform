package com.dyx.market.management.config;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fan-out for Nacos config changes.
 *
 * <p>Nacos 3.x SDK maps the default {@code public} namespace to empty-tenant storage while
 * listeners subscribe under the {@code public} key, so long-polling often misses updates on
 * reused volumes. Redis pub/sub keeps admin → market/chatbot refresh reliable for the
 * learning stack without changing money-path contracts.</p>
 *
 * <p>Nacos persistence is the commit point. Redis fan-out is retried in the
 * background and is reported to Admin as {@code notificationPending}; Nacos
 * listeners and startup reads remain the durable delivery fallback.</p>
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

    private final Map<String, PendingNotification> pending = new ConcurrentHashMap<>();
    private final AtomicLong publishGeneration = new AtomicLong();
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "platform-config-fanout-retry");
        thread.setDaemon(true);
        return thread;
    });

    /** 发布 runtime 开关变更；Redis 失败时由 Nacos listener/启动读取兜底。 */
    public boolean notifyRuntime(String content) {
        return publish(RUNTIME_TOPIC, content);
    }

    /** 发布平台配置变更；Nacos 持久化成功不因 Redis fan-out 失败而回滚。 */
    public boolean notifyPlatform(String content) {
        return publish(PLATFORM_TOPIC, content);
    }

    private boolean publish(String topicName, String content) {
        if (content == null) {
            throw new IllegalStateException("config fan-out payload must not be null");
        }
        PendingNotification notification = new PendingNotification(
                topicName, content, publishGeneration.incrementAndGet());
        boolean delivered = attemptPublish(topicName, content);
        if (delivered) {
            // A newer successful publish supersedes any older retry. Keeping an
            // older retry alive could deliver stale configuration after the
            // current generation and roll a consumer back.
            pending.computeIfPresent(topicName, (key, current) ->
                    current.generation <= notification.generation ? null : current);
        } else {
            scheduleRetry(notification);
        }
        return delivered;
    }

    private boolean attemptPublish(String topicName, String content) {
        if (redissonClient == null) {
            log.warn("Redis config fan-out unavailable for topic={}; waiting for Nacos listener fallback", topicName);
            return false;
        }
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= NOTIFY_ATTEMPTS; attempt++) {
            try {
                RTopic topic = redissonClient.getTopic(topicName);
                long receivers = topic.publish(content);
                if (receivers <= 0) {
                    lastError = new IllegalStateException("Redis topic has no active receivers");
                    log.warn("Redis config fan-out attempt {}/{} reached zero receivers for topic={}",
                            attempt, NOTIFY_ATTEMPTS, topicName);
                    if (attempt < NOTIFY_ATTEMPTS) {
                        sleepQuietly(NOTIFY_BACKOFF_MS * attempt);
                    }
                    continue;
                }
                log.info("Published config change to Redis topic={}, receivers={}, attempt={}",
                        topicName, receivers, attempt);
                return true;
            } catch (RuntimeException ex) {
                lastError = ex;
                log.warn("Redis config fan-out attempt {}/{} failed for topic={}: {}",
                        attempt, NOTIFY_ATTEMPTS, topicName, ex.toString());
                if (attempt < NOTIFY_ATTEMPTS) {
                    sleepQuietly(NOTIFY_BACKOFF_MS * attempt);
                }
            }
        }
        log.warn("Redis config fan-out remains pending topic={} after {} attempts: {}",
                topicName, NOTIFY_ATTEMPTS, lastError == null ? "unknown" : lastError.getMessage());
        return false;
    }

    private void scheduleRetry(PendingNotification notification) {
        // Keep one pending generation per topic. A stale retry must never be
        // allowed to arrive after a newer configuration generation.
        PendingNotification previous = pending.compute(notification.topicName, (key, current) ->
                current == null || current.generation <= notification.generation ? notification : current);
        if (previous == notification) {
            retryExecutor.schedule(() -> retry(notification), 5, TimeUnit.SECONDS);
        }
    }

    private void retry(PendingNotification notification) {
        if (pending.get(notification.topicName) != notification) {
            return;
        }
        if (attemptPublish(notification.topicName, notification.content)) {
            pending.remove(notification.topicName, notification);
        } else {
            retryExecutor.schedule(() -> retry(notification), 30, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    void shutdown() {
        retryExecutor.shutdownNow();
    }

    private static final class PendingNotification {
        private final String topicName;
        private final String content;
        private final long generation;

        private PendingNotification(String topicName, String content, long generation) {
            this.topicName = topicName;
            this.content = content;
            this.generation = generation;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
