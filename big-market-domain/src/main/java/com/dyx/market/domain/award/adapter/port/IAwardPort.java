package com.dyx.market.domain.award.adapter.port;

/**
 * 奖品对接端口：对外部奖品账户执行额度调整。
 */
public interface IAwardPort {

    void adjustAmount(String userId, Integer increaseQuota) throws Exception;

}
