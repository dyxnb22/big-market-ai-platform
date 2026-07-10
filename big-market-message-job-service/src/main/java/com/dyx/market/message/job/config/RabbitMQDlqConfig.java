package com.dyx.market.message.job.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dyx.market.infrastructure.dao.IMqDeadLetterDao;
import com.dyx.market.infrastructure.dao.po.MqDeadLetter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ 死信队列（DLQ）配置：本服务运行的 4 个 MQ 消费者。
 * <p>
 * 主队列通过 {@code @RabbitListener @Argument} 声明 {@code x-dead-letter-exchange=dlx}；
 * 本类提供 DLX DirectExchange、四个 *.dlq 队列及绑定，将死信路由到对应 DLQ。
 * <p>
 * DLQ 消费者将消息持久化到 {@code mq_dead_letter} 表后 ack，由 {@link DlqReplayJob} 自动重放。
 */
@Slf4j
@Configuration
public class RabbitMQDlqConfig {

    static final String DLX = "dlx";

    static final String QUEUE_ACTIVITY_SKU_STOCK_ZERO = "activity_sku_stock_zero";
    static final String QUEUE_CREDIT_ADJUST_SUCCESS = "credit_adjust_success";
    static final String QUEUE_SEND_REBATE = "send_rebate";
    static final String QUEUE_SEND_AWARD = "send_award";

    @Resource
    private IMqDeadLetterDao mqDeadLetterDao;

    @Resource
    private com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy dbRouter;

    @Value("${job.dlq-replay.max-consume-failures:5}")
    private int maxConsumeFailures;

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue activitySkuStockZeroDlq() {
        return QueueBuilder.durable("activity_sku_stock_zero.dlq").build();
    }

    @Bean
    public Queue creditAdjustSuccessDlq() {
        return QueueBuilder.durable("credit_adjust_success.dlq").build();
    }

    @Bean
    public Queue sendRebateDlq() {
        return QueueBuilder.durable("send_rebate.dlq").build();
    }

    @Bean
    public Queue sendAwardDlq() {
        return QueueBuilder.durable("send_award.dlq").build();
    }

    @Bean
    public Binding activitySkuStockZeroDlqBinding() {
        return BindingBuilder.bind(activitySkuStockZeroDlq()).to(dlxExchange()).with(QUEUE_ACTIVITY_SKU_STOCK_ZERO);
    }

    @Bean
    public Binding creditAdjustSuccessDlqBinding() {
        return BindingBuilder.bind(creditAdjustSuccessDlq()).to(dlxExchange()).with(QUEUE_CREDIT_ADJUST_SUCCESS);
    }

    @Bean
    public Binding sendRebateDlqBinding() {
        return BindingBuilder.bind(sendRebateDlq()).to(dlxExchange()).with(QUEUE_SEND_REBATE);
    }

    @Bean
    public Binding sendAwardDlqBinding() {
        return BindingBuilder.bind(sendAwardDlq()).to(dlxExchange()).with(QUEUE_SEND_AWARD);
    }

    @RabbitListener(queues = "activity_sku_stock_zero.dlq")
    public void onActivitySkuStockZeroDlq(Message message) {
        persistDeadLetter(QUEUE_ACTIVITY_SKU_STOCK_ZERO, message);
    }

    @RabbitListener(queues = "credit_adjust_success.dlq")
    public void onCreditAdjustSuccessDlq(Message message) {
        persistDeadLetter(QUEUE_CREDIT_ADJUST_SUCCESS, message);
    }

    @RabbitListener(queues = "send_rebate.dlq")
    public void onSendRebateDlq(Message message) {
        persistDeadLetter(QUEUE_SEND_REBATE, message);
    }

    @RabbitListener(queues = "send_award.dlq")
    public void onSendAwardDlq(Message message) {
        persistDeadLetter(QUEUE_SEND_AWARD, message);
    }

    private void persistDeadLetter(String originalQueue, Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        String businessMessageId = resolveBusinessMessageId(originalQueue, payload);
        String userId = resolveUserId(payload);
        try {
            if (StringUtils.isNotBlank(userId)) {
                dbRouter.doRouter(userId);
            } else {
                dbRouter.setDBKey(1);
            }
            persistDeadLetterOnCurrentShard(originalQueue, payload, businessMessageId);
        } finally {
            dbRouter.clear();
        }
    }

    private void persistDeadLetterOnCurrentShard(String originalQueue, String payload, String businessMessageId) {
        int reactivated = mqDeadLetterDao.reactivateReplayed(businessMessageId, maxConsumeFailures);
        if (reactivated > 0) {
            MqDeadLetter latest = mqDeadLetterDao.queryLatestByBusinessMessageId(businessMessageId);
            if (latest != null && "manual_pending".equals(latest.getState())) {
                log.error("[DLQ] MANUAL_PENDING businessMessageId:{} queue:{} consumeFailCount:{} — exceeded max consume failures",
                        businessMessageId, originalQueue, latest.getConsumeFailCount());
            } else {
                log.warn("[DLQ] reactivated replayed record businessMessageId:{} queue:{}", businessMessageId, originalQueue);
            }
            return;
        }
        String messageId = businessMessageId + ":dlq:" + System.nanoTime();
        try {
            MqDeadLetter record = new MqDeadLetter();
            record.setMessageId(messageId);
            record.setBusinessMessageId(businessMessageId);
            record.setQueue(originalQueue);
            record.setPayload(payload);
            mqDeadLetterDao.insert(record);
            log.error("[DLQ] persisted dead-letter messageId:{} businessMessageId:{} queue:{}",
                    messageId, businessMessageId, originalQueue);
        } catch (DuplicateKeyException e) {
            int retryReactivate = mqDeadLetterDao.reactivateReplayed(businessMessageId, maxConsumeFailures);
            if (retryReactivate > 0) {
                MqDeadLetter latest = mqDeadLetterDao.queryLatestByBusinessMessageId(businessMessageId);
                if (latest != null && "manual_pending".equals(latest.getState())) {
                    log.error("[DLQ] MANUAL_PENDING businessMessageId:{} queue:{} consumeFailCount:{} — exceeded max consume failures",
                            businessMessageId, originalQueue, latest.getConsumeFailCount());
                } else {
                    log.warn("[DLQ] reactivated after duplicate businessMessageId:{} queue:{}", businessMessageId, originalQueue);
                }
            } else {
                log.warn("[DLQ] duplicate dead-letter ignored messageId:{} queue:{}", messageId, originalQueue);
            }
        } catch (Exception e) {
            log.error("[DLQ] failed to persist dead-letter queue:{} payload:{}", originalQueue, payload, e);
            throw e;
        }
    }

    static String resolveBusinessMessageId(String queue, String payload) {
        try {
            JSONObject json = JSON.parseObject(payload);
            if (json != null) {
                if (json.containsKey("outBusinessNo")) {
                    return queue + ":" + json.getString("outBusinessNo");
                }
                if (json.containsKey("orderId")) {
                    return queue + ":" + json.getString("orderId");
                }
                if (json.containsKey("data")) {
                    JSONObject data = json.getJSONObject("data");
                    if (data != null && data.containsKey("outBusinessNo")) {
                        return queue + ":" + data.getString("outBusinessNo");
                    }
                    if (data != null && data.containsKey("orderId")) {
                        return queue + ":" + data.getString("orderId");
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to hash
        }
        return DigestUtils.md5DigestAsHex((queue + ":" + payload).getBytes(StandardCharsets.UTF_8));
    }

    static String resolveUserId(String payload) {
        try {
            JSONObject json = JSON.parseObject(payload);
            if (json == null) {
                return null;
            }
            if (json.containsKey("userId")) {
                return json.getString("userId");
            }
            if (json.containsKey("data")) {
                JSONObject data = json.getJSONObject("data");
                if (data != null && data.containsKey("userId")) {
                    return data.getString("userId");
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

}
