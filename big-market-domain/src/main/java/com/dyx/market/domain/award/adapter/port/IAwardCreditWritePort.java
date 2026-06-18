package com.dyx.market.domain.award.adapter.port;

import java.math.BigDecimal;

/**
 * 领域端口：隔离 AwardRepository 对积分账户与积分发奖发件箱 DAO 的直接写入。
 * <p>
 * （AL-6/AL-11）履约保留现有本地事务、锁与分片路由，积分表写入隐藏在本窄边界之后；
 * 本地实现委托 IUserCreditAccountDao 与 ICreditAwardTaskDao，不启用远程流量。
 */
public interface IAwardCreditWritePort {

    void updateOrCreateCreditAccount(String userId, BigDecimal creditAmount);

    void insertCreditAwardTask(String userId, String awardOrderId, BigDecimal creditAmount);

}
