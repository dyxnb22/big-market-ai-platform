package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.award.adapter.port.IAwardCreditWritePort;
import com.dyx.market.domain.award.model.valobj.AccountStatusVO;
import com.dyx.market.infrastructure.dao.ICreditAwardTaskDao;
import com.dyx.market.infrastructure.dao.IUserCreditAccountDao;
import com.dyx.market.infrastructure.dao.po.CreditAwardTask;
import com.dyx.market.infrastructure.dao.po.UserCreditAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * {@link IAwardCreditWritePort} 的本地（进程内）实现。
 *
 * <p>保持 {@code AwardRepository} 原有行为：调用方负责 Redis 锁、
 * {@code dbRouter}、{@code transactionTemplate} 以及
 * {@code account.award-credit-outbox} 开关；本适配器仅执行原先内联的相同 DAO 操作。</p>
 *
 * <p>激活条件：无远程替代实现时始终使用本本地端口（当前无对应远程 Bean）。</p>
 *
 * <p>Outbox 说明：当 {@code account.award-credit-outbox} 开启时，
 * {@code insertCreditAwardTask} 在同一事务边界内写入积分发奖任务行，由调用方协调事务与 Outbox 一致性。</p>
 */
@Slf4j
@Component
public class LocalAwardCreditWritePort implements IAwardCreditWritePort {

    @Resource
    private IUserCreditAccountDao userCreditAccountDao;
    @Resource
    private ICreditAwardTaskDao creditAwardTaskDao;

    @Override
    public void updateOrCreateCreditAccount(String userId, BigDecimal creditAmount) {
        UserCreditAccount userCreditAccountReq = buildCreditAccountReq(userId, creditAmount);
        UserCreditAccount existing = userCreditAccountDao.queryUserCreditAccount(userCreditAccountReq);
        if (null == existing) {
            userCreditAccountDao.insert(userCreditAccountReq);
        } else {
            userCreditAccountDao.updateAddAmount(userCreditAccountReq);
        }
    }

    @Override
    public void insertCreditAwardTask(String userId, String awardOrderId, BigDecimal creditAmount) {
        CreditAwardTask task = new CreditAwardTask();
        task.setUserId(userId);
        task.setAwardOrderId(awardOrderId);
        task.setCreditAmount(creditAmount);
        creditAwardTaskDao.insert(task);
    }

    private UserCreditAccount buildCreditAccountReq(String userId, BigDecimal creditAmount) {
        UserCreditAccount req = new UserCreditAccount();
        req.setUserId(userId);
        req.setTotalAmount(creditAmount);
        req.setAvailableAmount(creditAmount);
        req.setAccountStatus(AccountStatusVO.open.getCode());
        return req;
    }

}
