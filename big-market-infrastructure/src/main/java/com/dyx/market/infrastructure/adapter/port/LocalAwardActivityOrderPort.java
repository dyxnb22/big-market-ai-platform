package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.award.adapter.port.IAwardActivityOrderPort;
import com.dyx.market.infrastructure.dao.IUserRaffleOrderDao;
import com.dyx.market.infrastructure.dao.po.UserRaffleOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Local (in-process) implementation of IAwardActivityOrderPort.
 *
 * Phase 7-A prep (AL-5): AwardRepository previously injected
 * IUserRaffleOrderDao directly. This port encapsulates the same guarded
 * user_raffle_order state transition without changing transaction or routing
 * behavior; AwardRepository still controls dbRouter and transactionTemplate.
 */
@Slf4j
@Component
public class LocalAwardActivityOrderPort implements IAwardActivityOrderPort {

    @Resource
    private IUserRaffleOrderDao userRaffleOrderDao;

    @Override
    public int markUserRaffleOrderUsed(String userId, String orderId) {
        UserRaffleOrder userRaffleOrderReq = new UserRaffleOrder();
        userRaffleOrderReq.setUserId(userId);
        userRaffleOrderReq.setOrderId(orderId);
        return userRaffleOrderDao.updateUserRaffleOrderStateUsed(userRaffleOrderReq);
    }

}
