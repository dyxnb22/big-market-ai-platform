package com.dyx.market.message.job.config;

import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.infrastructure.dao.ICreditAwardTaskDao;
import com.dyx.market.infrastructure.dao.po.CreditAwardTask;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Phase 2.2-B6: outbox consumer for award credit dispatch.
 *
 * Active ONLY when account.award-credit-outbox.enabled=true. When false (default) this bean
 * is never instantiated and the @XxlJob handlers are never registered — no DB access occurs.
 *
 * When enabled, polls credit_award_task rows in each shard DB and dispatches pending credits
 * via IAccountCreditWriteAdapter.createOrder(). Uses award_order_id as outBusinessNo so the
 * account-service deduplicates duplicate dispatch attempts.
 *
 * Pre-requisite: credit_award_task_000..003 tables must exist in big_market_01 and big_market_02
 * (apply docs/sql/proposed-credit-award-task-outbox.sql) before setting the flag to true.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "account.award-credit-outbox.enabled", havingValue = "true")
public class DispatchCreditAwardTaskJob {

    @Resource
    private ICreditAwardTaskDao creditAwardTaskDao;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private RedissonClient redissonClient;

    @XxlJob("DispatchCreditAwardTaskJob_DB1")
    public void execDb01() {
        scanDb(1, "big-market-DispatchCreditAwardTaskJob_DB1");
    }

    @XxlJob("DispatchCreditAwardTaskJob_DB2")
    public void execDb02() {
        scanDb(2, "big-market-DispatchCreditAwardTaskJob_DB2");
    }

    private void scanDb(int dbIdx, String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        try {
            boolean isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
            if (!isLocked) return;

            for (int tbIdx = 0; tbIdx < 4; tbIdx++) {
                dbRouter.setDBKey(dbIdx);
                dbRouter.setTBKey(tbIdx);
                List<CreditAwardTask> tasks = creditAwardTaskDao.queryPendingTasks();
                for (CreditAwardTask task : tasks) {
                    dispatchTask(task);
                }
            }
        } catch (Exception e) {
            log.error("[DispatchCreditAwardTaskJob] DB{} scan failed", dbIdx, e);
        } finally {
            dbRouter.clear();
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void dispatchTask(CreditAwardTask task) {
        try {
            TradeEntity trade = TradeEntity.builder()
                    .userId(task.getUserId())
                    .tradeName(TradeNameVO.AWARD_CREDIT)
                    .tradeType(TradeTypeVO.FORWARD)
                    .amount(task.getCreditAmount())
                    .outBusinessNo(task.getAwardOrderId())
                    .build();
            accountCreditWriteAdapter.createOrder(trade);
            // Mark dispatched; duplicate-credit on account-service is guarded by outBusinessNo idempotency.
            dbRouter.doRouter(task.getUserId());
            creditAwardTaskDao.updateDispatched(task);
            log.info("[DispatchCreditAwardTaskJob] dispatched userId:{} awardOrderId:{}", task.getUserId(), task.getAwardOrderId());
        } catch (Exception e) {
            log.error("[DispatchCreditAwardTaskJob] dispatch failed userId:{} awardOrderId:{}, incrementing retry",
                    task.getUserId(), task.getAwardOrderId(), e);
            try {
                dbRouter.doRouter(task.getUserId());
                creditAwardTaskDao.updateRetryFailed(task);
            } catch (Exception ex) {
                log.error("[DispatchCreditAwardTaskJob] failed to update retry count userId:{}", task.getUserId(), ex);
            }
        } finally {
            dbRouter.clear();
        }
    }

}
