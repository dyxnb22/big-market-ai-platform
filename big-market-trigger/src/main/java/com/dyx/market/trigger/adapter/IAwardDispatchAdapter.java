package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;

/**
 * 发奖派发路由适配器契约。
 * <p>
 * 衔接 message-job-service 的 SendAwardConsumer 与 fulfillment-service 的 Dubbo 提供者。
 * 默认实现委托本地 {@code IAwardService} Bean；
 * 仅当 {@code account.fulfillment.remote-award.enabled=true} 时启用远程 Dubbo 调用。
 * 启用远程路径前须满足：
 * <ol>
 *   <li>本地验证通过（B17 证据）</li>
 *   <li>发奖积分 outbox DDL 已应用且本地冒烟验证通过（{@code award-credit-outbox.enabled=true}）</li>
 * </ol>
 */
public interface IAwardDispatchAdapter {

    void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception;

}
