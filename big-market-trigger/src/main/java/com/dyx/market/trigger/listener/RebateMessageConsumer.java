package com.dyx.market.trigger.listener;

import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.valobj.OrderTradeTypeVO;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.domain.rebate.event.SendRebateMessageEvent;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
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

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 监听；行为返利消息
 * @create 2024-05-01 13:58
 */
@Slf4j
@Component
public class RebateMessageConsumer {

    @Value("${spring.rabbitmq.topic.send_rebate}")
    private String topic;
    @Resource
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;

    @RabbitListener(queuesToDeclare = @Queue(
            value = "${spring.rabbitmq.topic.send_rebate}",
            arguments = @Argument(name = "x-dead-letter-exchange", value = "dlx")
    ))
    public void listener(String message) {
        try {
            log.info("监听用户行为返利消息 topic: {} message: {}", topic, message);
            // 1. 转换消息
            BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> eventMessage = JSON.parseObject(message, new TypeReference<BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage>>() {
            }.getType());
            SendRebateMessageEvent.RebateMessage rebateMessage = eventMessage.getData();

            // 2. 入账奖励
            switch (rebateMessage.getRebateType()) {
                case "sku":
                    SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
                    skuRechargeEntity.setUserId(rebateMessage.getUserId());
                    skuRechargeEntity.setSku(Long.valueOf(rebateMessage.getRebateConfig()));
                    skuRechargeEntity.setOutBusinessNo(rebateMessage.getBizId());
                    skuRechargeEntity.setOrderTradeType(OrderTradeTypeVO.rebate_no_pay_trade);
                    accountQuotaWriteAdapter.createOrder(skuRechargeEntity);
                    break;
                case "integral":
                    TradeEntity tradeEntity = new TradeEntity();
                    tradeEntity.setUserId(rebateMessage.getUserId());
                    tradeEntity.setTradeName(TradeNameVO.REBATE);
                    tradeEntity.setTradeType(TradeTypeVO.FORWARD);
                    tradeEntity.setAmount(new BigDecimal(rebateMessage.getRebateConfig()));
                    tradeEntity.setOutBusinessNo(rebateMessage.getBizId());
                    accountCreditWriteAdapter.createOrder(tradeEntity);
                    break;
            }
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("监听用户行为返利消息，消费重复 topic: {} message: {}", topic, message, e);
                return;
            }
            if (ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode().equals(e.getCode())) {
                log.warn("监听用户行为返利消息，活动库存不足 topic: {} message: {}", topic, message, e);
                return;
            }
            throw e;
        } catch (Exception e) {
            log.error("监听用户行为返利消息，消费失败 topic: {} message: {}", topic, message, e);
            throw e;
        }
    }

}
