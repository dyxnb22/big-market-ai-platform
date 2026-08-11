package com.dyx.market.trigger.listener;

import com.dyx.market.domain.award.adapter.event.SendAwardMessageEvent;
import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.trigger.adapter.IAwardDispatchAdapter;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.event.BaseEvent;
import com.dyx.market.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户奖品记录消息消费者
 * @create 2024-04-06 12:09
 */
@Slf4j
@Component
public class SendAwardConsumer {

    @Value("${spring.rabbitmq.topic.send_award}")
    private String topic;

    @Resource
    private IAwardDispatchAdapter awardDispatchAdapter;

    /**
     * 消费 send_award MQ 消息并触发异步发奖。
     * <p>{@code INDEX_DUP} 视为成功：表示该 {@code orderId} 已发放（MQ 重复投递），
     * 不可将其他异常当作重复吞掉。</p>
     */
    @RabbitListener(queuesToDeclare = @Queue(
            value = "${spring.rabbitmq.topic.send_award}",
            arguments = @Argument(name = "x-dead-letter-exchange", value = "dlx")
    ))
    public void listener(String message) {
        try {
            log.info("监听用户奖品发送消息，发奖开始 topic: {} payloadLength: {}", topic, message.length());
            BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> eventMessage = JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage>>() {
            }.getType());
            SendAwardMessageEvent.SendAwardMessage sendAwardMessage = eventMessage.getData();

            // 发放奖品
            DistributeAwardEntity distributeAwardEntity = new DistributeAwardEntity();
            distributeAwardEntity.setUserId(sendAwardMessage.getUserId());
            distributeAwardEntity.setOrderId(sendAwardMessage.getOrderId());
            distributeAwardEntity.setAwardId(sendAwardMessage.getAwardId());
            distributeAwardEntity.setAwardConfig(sendAwardMessage.getAwardConfig());
            awardDispatchAdapter.distributeAward(distributeAwardEntity);

            log.info("监听用户奖品发送消息，发奖完成 topic: {} payloadLength: {}", topic, message.length());
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("监听用户奖品发送消息，消费重复 topic: {} payloadLength: {}", topic, message.length(), e);
                return;
            }
            throw e;
        } catch (Exception e) {
            log.error("监听用户奖品发送消息，消费失败 topic: {} payloadLength: {}", topic, message.length(), e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

}
