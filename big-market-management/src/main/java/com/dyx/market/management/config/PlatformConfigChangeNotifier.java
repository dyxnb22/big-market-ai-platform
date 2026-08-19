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
 * Nacos 配置变更的进程间广播器。
 *
 * <p>Nacos 3.x SDK 将默认的 {@code public} 命名空间映射为空租户存储，而监听器仍以
 * {@code public} 键订阅，因此复用已有数据卷时长轮询可能漏掉更新。Redis 发布/订阅
 * 用于保证学习环境中 Admin 到 market/chatbot 的刷新及时到达，同时不改变资金类路径
 * 的业务契约。</p>
 *
 * <p>Nacos 持久化是提交点。Redis 广播在后台重试，并通过 {@code notificationPending}
 * 告知 Admin；Nacos 监听器和启动读取仍是持久化投递兜底。</p>
 */
@Component
public class PlatformConfigChangeNotifier {

    public static final String RUNTIME_TOPIC = "big-market:config:runtime";
    public static final String PLATFORM_TOPIC = "big-market:config:platform";

    private static final Logger log = LoggerFactory.getLogger(PlatformConfigChangeNotifier.class);

    private static final int NOTIFY_ATTEMPTS = 3;
    private static final long NOTIFY_BACKOFF_MS = 100L;

    @Autowired(required = false)
    /** Redis 客户端；未装配时保留 Nacos 监听器兜底。 */
    private RedissonClient redissonClient;

    /** 按 topic 保存尚未成功广播的最新配置代。 */
    private final Map<String, PendingNotification> pending = new ConcurrentHashMap<>();
    /** 全局发布代号，用于淘汰旧配置重试任务。 */
    private final AtomicLong publishGeneration = new AtomicLong();
    /** 单线程重试调度器，保证同一进程内广播重试有序。 */
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
            // 较新的成功发布会覆盖旧的重试；继续执行旧重试可能在当前代之后投递过期配置，
            // 使消费者回滚到旧状态。
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
        // 每个 topic 只保留一个待重试代；不能让过期重试在新配置代之后到达。
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
        /** Redis topic 名称。 */
        private final String topicName;
        /** 待广播的完整配置内容。 */
        private final String content;
        /** 配置发布代号，用于比较新旧通知。 */
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
