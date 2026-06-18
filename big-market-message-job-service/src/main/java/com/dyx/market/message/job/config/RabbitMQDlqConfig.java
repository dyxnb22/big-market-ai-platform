package com.dyx.market.message.job.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 死信队列（DLQ）配置：本服务运行的 4 个 MQ 消费者。
 * <p>
 * 主队列通过 {@code @RabbitListener @Argument} 声明 {@code x-dead-letter-exchange=dlx}；
 * 本类提供 DLX DirectExchange、四个 *.dlq 队列及绑定，将死信路由到对应 DLQ。
 * <p>
 * DLQ 消费者仅记录日志，不自动重放；须人工检查或通过外部工具重放死信消息。
 */
@Slf4j
@Configuration
public class RabbitMQDlqConfig {

    static final String DLX = "dlx";

    // DLX 交换机

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // DLQ 队列

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

    // 绑定：DLQ → DLX（routing key = 原队列名）

    @Bean
    public Binding activitySkuStockZeroDlqBinding() {
        return BindingBuilder.bind(activitySkuStockZeroDlq()).to(dlxExchange()).with("activity_sku_stock_zero");
    }

    @Bean
    public Binding creditAdjustSuccessDlqBinding() {
        return BindingBuilder.bind(creditAdjustSuccessDlq()).to(dlxExchange()).with("credit_adjust_success");
    }

    @Bean
    public Binding sendRebateDlqBinding() {
        return BindingBuilder.bind(sendRebateDlq()).to(dlxExchange()).with("send_rebate");
    }

    @Bean
    public Binding sendAwardDlqBinding() {
        return BindingBuilder.bind(sendAwardDlq()).to(dlxExchange()).with("send_award");
    }

    // DLQ 监控消费者（仅日志，须人工重放）

    @RabbitListener(queues = "activity_sku_stock_zero.dlq")
    public void onActivitySkuStockZeroDlq(String message) {
        log.error("[DLQ] activity_sku_stock_zero dead-lettered: {}", message);
    }

    @RabbitListener(queues = "credit_adjust_success.dlq")
    public void onCreditAdjustSuccessDlq(String message) {
        log.error("[DLQ] credit_adjust_success dead-lettered: {}", message);
    }

    @RabbitListener(queues = "send_rebate.dlq")
    public void onSendRebateDlq(String message) {
        log.error("[DLQ] send_rebate dead-lettered: {}", message);
    }

    @RabbitListener(queues = "send_award.dlq")
    public void onSendAwardDlq(String message) {
        log.error("[DLQ] send_award dead-lettered: {}", message);
    }
}
