package com.dyx.market.domain.award.adapter.port;

/**
 * 领域端口：隔离 AwardRepository 对积分发奖发件箱 DAO 的直接写入。
 * <p>
 * 履约只在本地事务中写入 {@code credit_award_task}；实际账户积分由
 * message-job 的 Outbox 消费链路处理，不能在奖品履约路径直接写账户表。
 */
public interface IAwardCreditWritePort {

    void insertCreditAwardTask(String userId, String awardOrderId, java.math.BigDecimal creditAmount);

}
