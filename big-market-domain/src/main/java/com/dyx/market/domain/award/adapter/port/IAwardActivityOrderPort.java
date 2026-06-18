package com.dyx.market.domain.award.adapter.port;

/**
 * 领域端口：隔离 AwardRepository 对活动订单 DAO 的直接依赖。
 * <p>
 * （AL-5）AwardRepository 不得直接依赖 IUserRaffleOrderDao；user_raffle_order 表归 activity-service 所有，
 * 履约仅需按 userId 与 orderId 将 create 状态安全流转为 used。
 * <p>
 * 本地路径（默认）：LocalAwardActivityOrderPort 直接委托 IUserRaffleOrderDao.updateUserRaffleOrderStateUsed。
 * 远程路径（可配置）：activity-service 接管订单写入后可替换本地实现。
 */
public interface IAwardActivityOrderPort {

    int markUserRaffleOrderUsed(String userId, String orderId);

}
