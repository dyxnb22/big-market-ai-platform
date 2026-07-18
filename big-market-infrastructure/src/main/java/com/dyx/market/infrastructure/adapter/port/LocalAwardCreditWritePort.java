package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.award.adapter.port.IAwardCreditWritePort;
import com.dyx.market.infrastructure.dao.ICreditAwardTaskDao;
import com.dyx.market.infrastructure.dao.po.CreditAwardTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * {@link IAwardCreditWritePort} 的本地（进程内）实现。
 *
 * <p>调用方负责 Redis 锁、{@code dbRouter} 与 {@code transactionTemplate}；
 * 本适配器只在同一事务中写入积分发奖 Outbox。</p>
 *
 * <p>{@code insertCreditAwardTask} 在同一事务边界内写入积分发奖任务行，由调用方协调事务与
 * Outbox 一致性。</p>
 */
@Slf4j
@Component
public class LocalAwardCreditWritePort implements IAwardCreditWritePort {

    @Resource
    private ICreditAwardTaskDao creditAwardTaskDao;

    @Override
    public void insertCreditAwardTask(String userId, String awardOrderId, BigDecimal creditAmount) {
        CreditAwardTask task = new CreditAwardTask();
        task.setUserId(userId);
        task.setAwardOrderId(awardOrderId);
        task.setCreditAmount(creditAmount);
        creditAwardTaskDao.insert(task);
    }
}
