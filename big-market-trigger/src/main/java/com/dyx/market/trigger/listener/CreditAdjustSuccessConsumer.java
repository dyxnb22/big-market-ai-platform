package com.dyx.market.trigger.listener;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
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
 * @description 积分调整成功消息
 * @create 2024-06-08 19:38
 */
@Slf4j
@Component
public class CreditAdjustSuccessConsumer {

    @Value("${spring.rabbitmq.topic.credit_adjust_success}")
    private String topic;
    @Resource
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;

    @RabbitListener(queuesToDeclare = @Queue(
            value = "${spring.rabbitmq.topic.credit_adjust_success}",
            arguments = @Argument(name = "x-dead-letter-exchange", value = "dlx")
    ))
    public void listener(String message) {
        try {
            log.info("监听积分账户调整成功消息，进行交易商品发货 topic: {} payloadLength: {}", topic, message.length());
            BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> eventMessage = JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage>>() {
            }.getType());
            CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage creditAdjustSuccessMessage = eventMessage.getData();

            // 积分发货
            DeliveryOrderEntity deliveryOrderEntity = new DeliveryOrderEntity();
            deliveryOrderEntity.setUserId(creditAdjustSuccessMessage.getUserId());
            deliveryOrderEntity.setOutBusinessNo(creditAdjustSuccessMessage.getOutBusinessNo());
            accountQuotaWriteAdapter.updateOrder(deliveryOrderEntity);
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("监听积分账户调整成功消息，进行交易商品发货，消费重复 topic: {} payloadLength: {}", topic, message.length(), e);
                return;
            }
            throw e;
        } catch (Exception e) {
            log.error("监听积分账户调整成功消息，进行交易商品发货失败 topic: {} payloadLength: {}", topic, message.length(), e);
            throw e;
        }
    }

}
