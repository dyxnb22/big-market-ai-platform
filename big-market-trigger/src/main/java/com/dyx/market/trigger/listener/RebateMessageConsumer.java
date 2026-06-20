package com.dyx.market.trigger.listener;

import com.dyx.market.domain.rebate.event.SendRebateMessageEvent;
import com.dyx.market.trigger.application.RebateMessageApplicationService;
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

import javax.annotation.Resource;

@Slf4j
@Component
public class RebateMessageConsumer {

    @Value("${spring.rabbitmq.topic.send_rebate}")
    private String topic;
    @Resource
    private RebateMessageApplicationService rebateMessageApplicationService;

    @RabbitListener(queuesToDeclare = @Queue(
            value = "${spring.rabbitmq.topic.send_rebate}",
            arguments = @Argument(name = "x-dead-letter-exchange", value = "dlx")
    ))
    public void listener(String message) {
        try {
            log.info("监听用户行为返利消息 topic: {} message: {}", topic, message);
            BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> eventMessage = JSON.parseObject(message,
                    new TypeReference<BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage>>() {
                    }.getType());
            rebateMessageApplicationService.processRebateMessage(eventMessage.getData());
        } catch (AppException e) {
            if (rebateMessageApplicationService.isBenignConsumerError(e)) {
                log.warn("监听用户行为返利消息，可忽略的业务异常 topic: {} message: {}", topic, message, e);
                return;
            }
            throw e;
        } catch (Exception e) {
            log.error("监听用户行为返利消息，消费失败 topic: {} message: {}", topic, message, e);
            throw e;
        }
    }
}
