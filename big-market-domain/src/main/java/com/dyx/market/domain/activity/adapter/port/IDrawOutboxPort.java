package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.activity.model.event.DrawOutboxEvent;

/**
 * 领域端口：抽奖 Saga 发件箱发布步骤的可选扩展点。
 * <p>
 * 当前 RaffleApplicationService 在 AwardRepository.saveUserAwardRecord 的单库事务内
 * 持久化中奖记录与任务发件箱行；编排步骤均在进程内时，本地事务已足够。
 * 当前抽奖步骤在本地事务内执行，保留该端口用于明确发件箱边界。
 * <p>
 * 本地路径（默认）：LocalDrawOutboxPort 为 no-op（仅日志），共享 task 表发件箱已提供持久化。
 * 当前本地学习路径未注入本端口，业务任务发件箱已提供持久化能力。
 */
public interface IDrawOutboxPort {

    /**
     * 将抽奖 Saga 步骤事件写入持久化发件箱。
     * <p>
     * 当前实现为 no-op；业务任务发件箱与主写操作同事务持久化。
     */
    void publishDrawSagaStep(DrawOutboxEvent event);
}
