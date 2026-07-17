package com.dyx.market.message.job.config;

import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.IMqDeadLetterDao;
import com.dyx.market.infrastructure.dao.IPendingRemoteWriteTaskDao;
import com.dyx.market.infrastructure.dao.IStrategyAwardStockConfirmTaskDao;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BM-016: expose pending remote-write and DLQ backlog as Prometheus gauges.
 */
@Slf4j
@Component
public class BusinessMetricsPublisher {

    private final IPendingRemoteWriteTaskDao pendingRemoteWriteTaskDao;
    private final IMqDeadLetterDao mqDeadLetterDao;
    private final IChatCreditSessionDao chatCreditSessionDao;
    private final IStrategyAwardStockConfirmTaskDao strategyAwardStockConfirmTaskDao;
    private final IDBRouterStrategy dbRouter;
    private final MeterRegistry meterRegistry;

    private final AtomicInteger pendingRemoteWrites = new AtomicInteger(0);
    private final AtomicInteger continuationRemoteWrites = new AtomicInteger(0);
    private final AtomicInteger pendingDlq = new AtomicInteger(0);
    private final AtomicInteger pendingChatRefunds = new AtomicInteger(0);
    private final AtomicInteger pendingStockConfirm = new AtomicInteger(0);

    public BusinessMetricsPublisher(IPendingRemoteWriteTaskDao pendingRemoteWriteTaskDao,
                                    IMqDeadLetterDao mqDeadLetterDao,
                                    IChatCreditSessionDao chatCreditSessionDao,
                                    IStrategyAwardStockConfirmTaskDao strategyAwardStockConfirmTaskDao,
                                    IDBRouterStrategy dbRouter,
                                    MeterRegistry meterRegistry) {
        this.pendingRemoteWriteTaskDao = pendingRemoteWriteTaskDao;
        this.mqDeadLetterDao = mqDeadLetterDao;
        this.chatCreditSessionDao = chatCreditSessionDao;
        this.strategyAwardStockConfirmTaskDao = strategyAwardStockConfirmTaskDao;
        this.dbRouter = dbRouter;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("big_market_pending_remote_write_tasks", pendingRemoteWrites, AtomicInteger::get)
                .tag("state", "pending")
                .description("pending_remote_write_task rows in pending state")
                .register(meterRegistry);
        Gauge.builder("big_market_pending_remote_write_tasks", continuationRemoteWrites, AtomicInteger::get)
                .tag("state", "continuation_pending")
                .description("pending_remote_write_task rows in continuation_pending state")
                .register(meterRegistry);
        Gauge.builder("big_market_mq_dead_letter_pending", pendingDlq, AtomicInteger::get)
                .description("mq_dead_letter rows in pending state")
                .register(meterRegistry);
        Gauge.builder("big_market_chat_refund_pending", pendingChatRefunds, AtomicInteger::get)
                .description("chat_credit_session rows with refund_state=pending")
                .register(meterRegistry);
        Gauge.builder("big_market_strategy_stock_confirm_pending", pendingStockConfirm, AtomicInteger::get)
                .description("strategy_award_stock_confirm_task rows in pending state")
                .register(meterRegistry);
        refresh();
    }

    @Scheduled(fixedDelayString = "${big-market.metrics.refresh-ms:30000}")
    public void refresh() {
        try {
            int pendingRemoteWriteCount = 0;
            int continuationRemoteWriteCount = 0;
            int pendingDlqCount = 0;
            int pendingChatRefundCount = 0;
            int pendingStockConfirmCount = 0;
            for (int dbIdx = 1; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                pendingRemoteWriteCount += pendingRemoteWriteTaskDao.countByState("pending");
                continuationRemoteWriteCount += pendingRemoteWriteTaskDao.countByState("continuation_pending");
                pendingDlqCount += mqDeadLetterDao.countByState("pending");
                pendingChatRefundCount += chatCreditSessionDao.countPendingRefunds();
                pendingStockConfirmCount += strategyAwardStockConfirmTaskDao.countPending();
            }
            pendingRemoteWrites.set(pendingRemoteWriteCount);
            continuationRemoteWrites.set(continuationRemoteWriteCount);
            pendingDlq.set(pendingDlqCount);
            pendingChatRefunds.set(pendingChatRefundCount);
            pendingStockConfirm.set(pendingStockConfirmCount);
        } catch (Exception e) {
            log.warn("Business metrics refresh failed: {}", e.getMessage());
        } finally {
            dbRouter.clear();
        }
    }
}
