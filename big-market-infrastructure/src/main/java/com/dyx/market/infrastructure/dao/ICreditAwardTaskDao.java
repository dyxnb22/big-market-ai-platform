package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.CreditAwardTask;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 积分发奖 Outbox 表 {@code credit_award_task} 的 DAO（默认实现）。
 * <p>
 * {@code insert}：在 {@code saveGiveOutPrizesAggregate} 事务内调用。
 * {@code queryPendingTasks}：由 {@code DispatchCreditAwardTaskJob} 轮询；调用方先设置分库分表键。
 * {@code updateDispatched} / {@code updateRetryFailed}：通过 {@code @DBRouter} 按 userId 路由。
 * <p>
 * 分表 {@code credit_award_task_000..003} 需执行 {@code docs/sql/credit-award-task-outbox.sql}。
 */
@Mapper
@DBRouterStrategy(splitTable = true)
public interface ICreditAwardTaskDao {

    void insert(CreditAwardTask task);

    List<CreditAwardTask> queryPendingTasks();

    @DBRouter
    int updateDispatched(CreditAwardTask task);

    @DBRouter
    int updateRetryFailed(CreditAwardTask task);

    int countByState(String state);

}
