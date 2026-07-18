package com.dyx.market.infrastructure.event;

import com.dyx.market.types.event.BaseEvent;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 消息发送
 * @create 2024-03-30 12:40
 */
@Slf4j
@Component
public class EventPublisher {

    private static final long CONFIRM_TIMEOUT_MILLIS = 5000L;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publish(String topic, BaseEvent.EventMessage<?> eventMessage) {
        try {
            String messageJson = JSON.toJSONString(eventMessage);
            publishAndConfirm(topic, messageJson, eventMessage.getId());
            log.info("发送MQ消息 topic:{} message:{}", topic, messageJson);
        } catch (Exception e) {
            log.error("发送MQ消息失败 topic:{} message:{}", topic, JSON.toJSONString(eventMessage), e);
            throw e;
        }
    }

    public void publish(String topic, String eventMessageJSON){
        publish(topic, eventMessageJSON, null);
    }

    public void publish(String topic, String eventMessageJSON, String messageId){
        try {
            publishAndConfirm(topic, eventMessageJSON, messageId);
            log.info("发送MQ消息 topic:{} messageId:{} message:{}", topic, messageId, eventMessageJSON);
        } catch (Exception e) {
            log.error("发送MQ消息失败 topic:{} message:{}", topic, eventMessageJSON, e);
            throw e;
        }
    }

    /**
     * The application uses the default exchange and queue names as routing keys.
     * A task is only allowed to become completed after the broker confirms the
     * publish and mandatory routing did not return the message.
     */
    private void publishAndConfirm(String routingKey, String payload, String messageId) {
        CorrelationData correlationData = new CorrelationData(messageId);
        rabbitTemplate.convertAndSend("", routingKey, payload, message -> {
            if (messageId != null && !messageId.isEmpty()) {
                message.getMessageProperties().setMessageId(messageId);
            }
            return message;
        }, correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ publisher confirm rejected: "
                        + (confirm == null ? "null" : confirm.getReason()));
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ message was returned as unroutable: "
                        + correlationData.getReturned());
            }
        } catch (Exception e) {
            throw new IllegalStateException("RabbitMQ publish was not durably confirmed", e);
        }
    }

}
