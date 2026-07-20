package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.infrastructure.dao.IMqDeadLetterDao;
import com.dyx.market.infrastructure.dao.po.MqDeadLetter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
/**
 * RabbitMQ 死信持久化适配器。
 *
 * <p>只保存队列名、消息摘要和原始 payload；消费者失败时先尽力落库，
 * 后续由人工审核或受控 Job 决定是否重放。</p>
 */
public class MqDeadLetterPersistenceSupport {

    @Resource
    private IMqDeadLetterDao mqDeadLetterDao;

    /** 持久化一条待审核死信，空消息直接忽略。 */
    public void persist(String queue, String payload) {
        if (StringUtils.isBlank(queue) || StringUtils.isBlank(payload)) {
            return;
        }
        String messageId = hashMessageId(queue, payload);
        try {
            mqDeadLetterDao.insert(MqDeadLetter.builder()
                    .messageId(messageId)
                    .queue(queue)
                    .payload(payload)
                    .state("pending")
                    .retryCount(0)
                    .build());
            log.info("[DLQ] persisted dead letter queue:{} messageId:{}", queue, messageId);
        } catch (Exception e) {
            log.error("[DLQ] failed to persist dead letter queue:{} messageId:{}", queue, messageId, e);
        }
    }

    /** 生成短 SHA-256 摘要，作为同队列同 payload 的稳定消息标识。 */
    private static String hashMessageId(String queue, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((queue + ":" + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(16, hash.length); i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return queue + "_" + payload.hashCode();
        }
    }
}
