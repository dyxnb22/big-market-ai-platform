package com.dyx.market.message.job.config;

import com.dyx.market.domain.credit.adapter.port.ICreditAwardTaskDispatchPort;
import com.dyx.market.domain.credit.model.entity.CreditAwardTaskEntity;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 发奖积分派发 Outbox 消费者。
 * <p>
 * 轮询各分片库中的 credit_award_task 待处理行，经
 * {@link IAccountCreditWriteAdapter#createOrder} 派发；以 award_order_id 作为
 * outBusinessNo，account-service 据此幂等去重。
 * <p>
 * 前置条件：启用本开关时，big_market_01 / big_market_02 须存在 credit_award_task_000..003 表
 * （执行 docs/sql/credit-award-task-outbox.sql）。
 */
@Slf4j
@Component
public class DispatchCreditAwardTaskJob {

    @Resource
    private ICreditAwardTaskDispatchPort creditAwardTaskDispatchPort;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private RedissonClient redissonClient;

    @Timed(value = "DispatchCreditAwardTaskJob_DB1", description = "Award credit outbox dispatch DB1")
    @XxlJob("DispatchCreditAwardTaskJob_DB1")
    public void execDb01() {
        scanDb(1, "big-market-DispatchCreditAwardTaskJob_DB1");
    }

    @Timed(value = "DispatchCreditAwardTaskJob_DB2", description = "Award credit outbox dispatch DB2")
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
                List<CreditAwardTaskEntity> tasks = creditAwardTaskDispatchPort.queryPendingTasks();
                for (CreditAwardTaskEntity task : tasks) {
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

    private void dispatchTask(CreditAwardTaskEntity task) {
        try {
            TradeEntity trade = TradeEntity.builder()
                    .userId(task.getUserId())
                    .tradeName(TradeNameVO.AWARD_CREDIT)
                    .tradeType(TradeTypeVO.FORWARD)
                    .amount(task.getCreditAmount())
                    .outBusinessNo(task.getAwardOrderId())
                    .build();
            accountCreditWriteAdapter.createOrder(trade);
            // 标记已派发；account-service 侧以 outBusinessNo 保证幂等，防止重复入账
            dbRouter.doRouter(task.getUserId());
            creditAwardTaskDispatchPort.updateDispatched(task);
            log.info("[DispatchCreditAwardTaskJob] dispatched userId:{} awardOrderId:{}", task.getUserId(), task.getAwardOrderId());
        } catch (Exception e) {
            log.error("[DispatchCreditAwardTaskJob] dispatch failed userId:{} awardOrderId:{}, incrementing retry",
                    task.getUserId(), task.getAwardOrderId(), e);
            try {
                dbRouter.doRouter(task.getUserId());
                creditAwardTaskDispatchPort.updateRetryFailed(task);
            } catch (Exception ex) {
                log.error("[DispatchCreditAwardTaskJob] failed to update retry count userId:{}", task.getUserId(), ex);
            }
        } finally {
            dbRouter.clear();
        }
    }

}
