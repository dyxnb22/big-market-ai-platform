package com.dyx.market.message.job.config;

import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.ICreditAwardTaskDao;
import com.dyx.market.infrastructure.dao.IMqDeadLetterDao;
import com.dyx.market.infrastructure.dao.IPendingRemoteWriteTaskDao;
import com.dyx.market.infrastructure.dao.IStrategyAwardStockConfirmTaskDao;
import com.dyx.market.infrastructure.dao.ITaskDao;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
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
    private final ITaskDao taskDao;
    private final IStrategyAwardStockConfirmTaskDao strategyAwardStockConfirmTaskDao;
    private final ICreditAwardTaskDao creditAwardTaskDao;
    private final IDBRouterStrategy dbRouter;
    private final MeterRegistry meterRegistry;

    private final AtomicInteger pendingRemoteWrites = new AtomicInteger(0);
    private final AtomicInteger continuationRemoteWrites = new AtomicInteger(0);
    private final AtomicInteger pendingDlq = new AtomicInteger(0);
    private final AtomicInteger pendingChatRefunds = new AtomicInteger(0);
    private final AtomicInteger pendingStockConfirm = new AtomicInteger(0);
    private final AtomicInteger failedRemoteWrites = new AtomicInteger(0);
    private final AtomicInteger failedCreditAwards = new AtomicInteger(0);
    private final AtomicInteger manualPendingStockConfirm = new AtomicInteger(0);
    private final AtomicInteger deductingChatSessions = new AtomicInteger(0);
    private final AtomicInteger legacyTaskBacklog = new AtomicInteger(0);
    private final AtomicInteger legacyTaskPoisonRows = new AtomicInteger(0);
    private final AtomicInteger legacyTaskOldestAgeSeconds = new AtomicInteger(0);
    private final AtomicInteger manualPendingChatSessions = new AtomicInteger(0);

    public BusinessMetricsPublisher(IPendingRemoteWriteTaskDao pendingRemoteWriteTaskDao,
                                    IMqDeadLetterDao mqDeadLetterDao,
                                    IChatCreditSessionDao chatCreditSessionDao,
                                    ITaskDao taskDao,
                                    IStrategyAwardStockConfirmTaskDao strategyAwardStockConfirmTaskDao,
                                    ICreditAwardTaskDao creditAwardTaskDao,
                                    IDBRouterStrategy dbRouter,
                                    MeterRegistry meterRegistry) {
        this.pendingRemoteWriteTaskDao = pendingRemoteWriteTaskDao;
        this.mqDeadLetterDao = mqDeadLetterDao;
        this.chatCreditSessionDao = chatCreditSessionDao;
        this.taskDao = taskDao;
        this.strategyAwardStockConfirmTaskDao = strategyAwardStockConfirmTaskDao;
        this.creditAwardTaskDao = creditAwardTaskDao;
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
        Gauge.builder("big_market_remote_write_failed", failedRemoteWrites, AtomicInteger::get)
                .tag("state", "failed")
                .description("remote write tasks exhausted retries")
                .register(meterRegistry);
        Gauge.builder("big_market_credit_award_failed", failedCreditAwards, AtomicInteger::get)
                .description("credit award outbox tasks exhausted retries")
                .register(meterRegistry);
        Gauge.builder("big_market_strategy_stock_confirm_manual_pending", manualPendingStockConfirm, AtomicInteger::get)
                .description("strategy stock confirmation tasks awaiting manual review")
                .register(meterRegistry);
        Gauge.builder("big_market_chat_deducting", deductingChatSessions, AtomicInteger::get)
                .description("chat sessions waiting for account debit reconciliation")
                .register(meterRegistry);
        Gauge.builder("big_market_legacy_task_backlog", legacyTaskBacklog, AtomicInteger::get)
                .description("legacy task outbox rows eligible for retry")
                .register(meterRegistry);
        Gauge.builder("big_market_legacy_task_oldest_age_seconds", legacyTaskOldestAgeSeconds, AtomicInteger::get)
                .description("age of the oldest eligible legacy task outbox row")
                .register(meterRegistry);
        Gauge.builder("big_market_legacy_task_manual_pending", legacyTaskPoisonRows, AtomicInteger::get)
                .description("legacy task outbox rows parked after exhausting retries")
                .register(meterRegistry);
        Gauge.builder("big_market_chat_manual_pending", manualPendingChatSessions, AtomicInteger::get)
                .description("chat credit sessions requiring manual compensation")
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
            int failedRemoteWriteCount = 0;
            int failedCreditAwardCount = 0;
            int manualPendingStockConfirmCount = 0;
            int deductingChatCount = 0;
            int legacyTaskBacklogCount = 0;
            int legacyTaskPoisonCount = 0;
            int legacyTaskOldestAge = 0;
            int manualPendingChatCount = 0;
            // The central compensation store exists on db00. The historical
            // per-shard copies are only compatibility data. MQ/DLQ, chat and
            // strategy tables, however, exist on the business shards only.
            for (int dbIdx = 0; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                pendingRemoteWriteCount += pendingRemoteWriteTaskDao.countByState("pending");
                continuationRemoteWriteCount += pendingRemoteWriteTaskDao.countByState("continuation_pending");
                failedRemoteWriteCount += pendingRemoteWriteTaskDao.countByState("failed");
            }
            for (int dbIdx = 1; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                dbRouter.setTBKey(0);
                legacyTaskBacklogCount += taskDao.countLegacyTaskBacklog();
                legacyTaskPoisonCount += taskDao.countLegacyTaskPoisonRows();
                Integer oldestAge = taskDao.queryOldestLegacyTaskAgeSeconds();
                legacyTaskOldestAge = Math.max(legacyTaskOldestAge, oldestAge == null ? 0 : oldestAge);
                pendingDlqCount += mqDeadLetterDao.countByState("pending");
                pendingChatRefundCount += chatCreditSessionDao.countPendingRefunds();
                pendingStockConfirmCount += strategyAwardStockConfirmTaskDao.countPending();
                manualPendingStockConfirmCount += strategyAwardStockConfirmTaskDao.countByState("manual_pending");
                deductingChatCount += chatCreditSessionDao.countByDeductState("deducting");
                manualPendingChatCount += chatCreditSessionDao.countByRefundState("manual_pending");
            }
            for (int dbIdx = 1; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                for (int tbIdx = 0; tbIdx < 4; tbIdx++) {
                    dbRouter.setTBKey(tbIdx);
                    failedCreditAwardCount += creditAwardTaskDao.countByState("failed");
                }
            }
            pendingRemoteWrites.set(pendingRemoteWriteCount);
            continuationRemoteWrites.set(continuationRemoteWriteCount);
            pendingDlq.set(pendingDlqCount);
            pendingChatRefunds.set(pendingChatRefundCount);
            pendingStockConfirm.set(pendingStockConfirmCount);
            failedRemoteWrites.set(failedRemoteWriteCount);
            failedCreditAwards.set(failedCreditAwardCount);
            manualPendingStockConfirm.set(manualPendingStockConfirmCount);
            deductingChatSessions.set(deductingChatCount);
            legacyTaskBacklog.set(legacyTaskBacklogCount);
            legacyTaskPoisonRows.set(legacyTaskPoisonCount);
            legacyTaskOldestAgeSeconds.set(legacyTaskOldestAge);
            manualPendingChatSessions.set(manualPendingChatCount);
        } catch (Exception e) {
            log.warn("Business metrics refresh failed: {}", e.getMessage());
        } finally {
            dbRouter.clear();
        }
    }
}
