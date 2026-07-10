package com.dyx.market.message.job.config;

import com.dyx.market.infrastructure.adapter.repository.ChatCreditSessionSupport;
import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.application.ChatCreditApplicationService;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Chatbot 退款补偿 Job：扫描 refund_state=pending 的会话，重试积分退还（幂等键 chat_refund_{requestId}）。
 */
@Slf4j
@Component
public class ChatRefundReconcileJob {

    @Value("${job.chat-refund-reconcile.max-retries:5}")
    private int maxRetries;

    @Value("${job.chat-refund-reconcile.scan-limit:50}")
    private int scanLimit;

    @Resource
    private IChatCreditSessionDao chatCreditSessionDao;
    @Resource
    private ChatCreditApplicationService chatCreditApplicationService;
    @Resource
    private ChatCreditSessionSupport chatCreditSessionSupport;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private RedissonClient redissonClient;

    @Timed(value = "ChatRefundReconcileJob", description = "Chat credit refund reconcile")
    @XxlJob("ChatRefundReconcileJob")
    public void exec() {
        RLock lock = redissonClient.getLock("big-market-ChatRefundReconcileJob");
        try {
            if (!lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                return;
            }
            for (int dbIdx = 1; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                List<ChatCreditSession> pending = chatCreditSessionDao.queryPendingRefunds(maxRetries, scanLimit);
                for (ChatCreditSession session : pending) {
                    reconcile(session);
                }
            }
        } catch (Exception e) {
            log.error("[ChatRefundReconcileJob] scan failed", e);
        } finally {
            dbRouter.clear();
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void reconcile(ChatCreditSession session) {
        try {
            chatCreditApplicationService.refund(session.getUserId(), session.getDeductAmount(), session.getRequestId());
            chatCreditSessionSupport.markRefunded(session.getUserId(), session.getRequestId());
            log.info("[ChatRefundReconcileJob] refund success userId:{} requestId:{}", session.getUserId(), session.getRequestId());
        } catch (Exception e) {
            log.error("[ChatRefundReconcileJob] refund failed userId:{} requestId:{} retry:{}",
                    session.getUserId(), session.getRequestId(), session.getRetryCount(), e);
            chatCreditSessionDao.updateRetryFailed(session);
        }
    }
}
