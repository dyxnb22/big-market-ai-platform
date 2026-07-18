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
public class MqDeadLetterPersistenceSupport {

    @Resource
    private IMqDeadLetterDao mqDeadLetterDao;

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
