package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.activity.model.event.DrawOutboxEvent;

/**
 * 领域端口：抽奖 Saga 发件箱发布步骤的可选扩展点。
 * <p>
 * 当前 RaffleApplicationService 在 AwardRepository.saveUserAwardRecord 的单库事务内
 * 持久化中奖记录与任务发件箱行；编排步骤均在进程内时，本地事务已足够。
 * 跨服务执行抽奖步骤时，需持久化发件箱以保证进程重启后 Saga 可恢复。
 * <p>
 * 本地路径（默认）：LocalDrawOutboxPort 为 no-op（仅日志），共享 task 表发件箱已提供持久化。
 * 远程路径：可写入 activity 侧 draw_saga_outbox 表（学习 DDL 见 docs/sql/）。
 * 当前本地学习路径未注入本端口，仅文档化该边界。
 */
public interface IDrawOutboxPort {

    /**
     * 将抽奖 Saga 步骤事件写入持久化发件箱。
     * <p>
     * 本地路径为 no-op；远程路径与主写操作同事务写入 draw_saga_outbox 行。
     */
    void publishDrawSagaStep(DrawOutboxEvent event);
}
