package com.dyx.market.message.job.config;

import com.dyx.market.infrastructure.dao.IMqDeadLetterDao;
import com.dyx.market.infrastructure.dao.po.MqDeadLetter;
import com.dyx.market.infrastructure.event.EventPublisher;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DLQ 死信重放 Job：将 mq_dead_letter 中 pending 消息重投原队列，消费端幂等保证安全。
 */
@Slf4j
@Component
public class DlqReplayJob {

    @Value("${job.dlq-replay.max-retries:5}")
    private int maxRetries;

    @Value("${job.dlq-replay.scan-limit:50}")
    private int scanLimit;

    @Resource
    private IMqDeadLetterDao mqDeadLetterDao;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private RedissonClient redissonClient;

    @Timed(value = "DlqReplayJob", description = "DLQ dead-letter replay")
    @XxlJob("DlqReplayJob")
    public void exec() {
        RLock lock = redissonClient.getLock("big-market-DlqReplayJob");
        try {
            boolean isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) {
                return;
            }

            List<MqDeadLetter> pending = mqDeadLetterDao.queryPendingReplay(maxRetries, scanLimit);
            for (MqDeadLetter deadLetter : pending) {
                replay(deadLetter);
            }
        } catch (Exception e) {
            log.error("[DlqReplayJob] scan failed", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void replay(MqDeadLetter deadLetter) {
        try {
            String businessMessageId = deadLetter.getBusinessMessageId() != null
                    ? deadLetter.getBusinessMessageId()
                    : deadLetter.getMessageId();
            int attempt = (deadLetter.getRetryCount() != null ? deadLetter.getRetryCount() : 0) + 1;
            String replayMessageId = businessMessageId + ":replay:" + attempt;
            eventPublisher.publish(deadLetter.getQueue(), deadLetter.getPayload(), replayMessageId);
            MqDeadLetter replayed = new MqDeadLetter();
            replayed.setId(deadLetter.getId());
            int updated = mqDeadLetterDao.updateReplayed(replayed);
            if (updated == 1) {
                log.info("[DlqReplayJob] replayed messageId:{} queue:{}", deadLetter.getMessageId(), deadLetter.getQueue());
            }
        } catch (Exception e) {
            log.error("[DlqReplayJob] replay failed messageId:{} queue:{} retry:{}",
                    deadLetter.getMessageId(), deadLetter.getQueue(), deadLetter.getRetryCount(), e);
            int updated = mqDeadLetterDao.updateRetryFailed(deadLetter.getId(), maxRetries);
            if (updated == 1 && deadLetter.getRetryCount() + 1 >= maxRetries) {
                log.error("[DlqReplayJob] MANUAL_PENDING messageId:{} queue:{} — exceeded max retries",
                        deadLetter.getMessageId(), deadLetter.getQueue());
            }
        }
    }

}
