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
 * Local (in-process) implementation of IAwardCreditWritePort.
 *
 * Preserves AwardRepository's behavior: caller owns Redis locking,
 * dbRouter, transactionTemplate, and the account.award-credit-outbox flag; this
 * adapter only performs the same DAO operations that were previously inline.
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
