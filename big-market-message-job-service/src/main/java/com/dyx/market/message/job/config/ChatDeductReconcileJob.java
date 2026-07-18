package com.dyx.market.message.job.config;

import com.dyx.market.infrastructure.adapter.repository.ChatCreditSessionRepository;
import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 对账 account 成功与 market 会话确认之间的 deducting 窗口。
 * 只按不可变幂等键查询 account 订单，不重复扣费。
 */
@Slf4j
@Component
public class ChatDeductReconcileJob {

    @Value("${job.chat-deduct-reconcile.max-retries:5}")
    private int maxRetries;
    @Value("${job.chat-deduct-reconcile.scan-limit:50}")
    private int scanLimit;

    @Resource
    private IChatCreditSessionDao chatCreditSessionDao;
    @Resource
    private ChatCreditSessionRepository chatCreditSessionRepository;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private RedissonClient redissonClient;

    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;

    @Timed(value = "ChatDeductReconcileJob", description = "Chat credit deduction reconcile")
    @XxlJob("ChatDeductReconcileJob")
    public void exec() {
        RLock lock = redissonClient.getLock("big-market-ChatDeductReconcileJob");
        try {
            if (!lock.tryLock(3, 0, TimeUnit.SECONDS)) {
                return;
            }
            for (int dbIdx = 1; dbIdx <= 2; dbIdx++) {
                dbRouter.setDBKey(dbIdx);
                List<ChatCreditSession> sessions = chatCreditSessionDao.queryPendingDeductions(maxRetries, scanLimit);
                for (ChatCreditSession session : sessions) {
                    try {
                        reconcile(session);
                    } finally {
                        dbRouter.setDBKey(dbIdx);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[ChatDeductReconcileJob] scan failed", e);
        } finally {
            dbRouter.clear();
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void reconcile(ChatCreditSession session) {
        String outBusinessNo = "chat_" + session.getUserId() + "_" + session.getRequestId();
        try {
            Response<Boolean> response = accountCreditService.existsCreditOrder(session.getUserId(), outBusinessNo);
            if (response == null || !ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                throw new IllegalStateException("account order probe is inconclusive");
            }
            if (Boolean.TRUE.equals(response.getData())) {
                chatCreditSessionRepository.markDeducted(session.getUserId(), session.getRequestId());
                log.info("[ChatDeductReconcileJob] marked deducted userId:{} requestId:{}", session.getUserId(), session.getRequestId());
            } else {
                chatCreditSessionRepository.markDeductFailed(session.getUserId(), session.getRequestId());
                log.info("[ChatDeductReconcileJob] marked failed userId:{} requestId:{}", session.getUserId(), session.getRequestId());
            }
        } catch (Exception e) {
            try {
                dbRouter.doRouter(session.getUserId());
                chatCreditSessionDao.updateRetryFailed(session);
            } finally {
                dbRouter.clear();
            }
            log.warn("[ChatDeductReconcileJob] probe inconclusive userId:{} requestId:{} retry:{}",
                    session.getUserId(), session.getRequestId(), session.getRetryCount(), e);
        }
    }
}
