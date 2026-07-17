package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;

/**
 * 发奖派发适配器契约。
 *
 * <p>最终拓扑固定由 message-job-service 使用本地 {@code IAwardService} 完成派发，
 * 积分奖再通过 outbox 交给 account-service；不再保留远程履约实现。</p>
 */
public interface IAwardDispatchAdapter {

    void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception;

}
