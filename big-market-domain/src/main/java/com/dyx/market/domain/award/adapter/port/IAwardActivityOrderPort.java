package com.dyx.market.domain.award.adapter.port;

/**
 * 领域端口：隔离 AwardRepository 对活动订单 DAO 的直接依赖。
 * <p>
 * （AL-5）AwardRepository 不得直接依赖 IUserRaffleOrderDao；user_raffle_order 表归活动订单边界，
 * 履约仅需按 userId 与 orderId 将 create 状态安全流转为 used。
 * <p>
 * 本地路径（默认）：LocalAwardActivityOrderPort 直接委托 IUserRaffleOrderDao.updateUserRaffleOrderStateUsed。
 * 当前最终实现固定使用本地端口完成状态流转。
 */
public interface IAwardActivityOrderPort {

    int markUserRaffleOrderUsed(String userId, String orderId);

}
