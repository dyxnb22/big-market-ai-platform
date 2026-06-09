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
 * Dead-letter queue configuration for the 4 MQ consumers that run in this service.
 *
 * Main queues declare x-dead-letter-exchange=dlx via @RabbitListener @Argument.
 * This class provides the DLX DirectExchange, the four *.dlq queues, and the
 * bindings that route dead-lettered messages into those queues.
 *
 * DLQ consumers only log — no automatic replay. Manual inspection or external
 * tooling is needed to replay dead-lettered messages.
 */
@Slf4j
@Configuration
public class RabbitMQDlqConfig {

    static final String DLX = "dlx";

    // ─── DLX exchange ────────────────────────────────────────────────────────

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX, true, false);
    }

    // ─── DLQ queues ──────────────────────────────────────────────────────────

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

    // ─── Bindings: DLQ → DLX (routing key = original queue name) ────────────

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

    // ─── DLQ monitoring consumers (log-only — manual replay required) ────────

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
