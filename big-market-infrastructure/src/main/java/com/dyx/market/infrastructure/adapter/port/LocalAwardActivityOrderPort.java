package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.award.adapter.port.IAwardActivityOrderPort;
import com.dyx.market.infrastructure.dao.IUserRaffleOrderDao;
import com.dyx.market.infrastructure.dao.po.UserRaffleOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * {@link IAwardActivityOrderPort} 的本地（进程内）实现。
 *
 * <p>预备工作（AL-5）：原先 {@code AwardRepository} 直接注入
 * {@code IUserRaffleOrderDao}；本端口封装相同的、带守卫的
 * {@code user_raffle_order} 状态流转，不改变事务或路由行为——
 * {@code AwardRepository} 仍控制 {@code dbRouter} 与 {@code transactionTemplate}。</p>
 *
 * <p>激活条件：无远程替代实现时始终使用本本地端口（当前无对应远程 Bean）。</p>
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
