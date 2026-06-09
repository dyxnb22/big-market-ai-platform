package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.CreditAwardTask;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import com.dyx.market.middleware.db.router.annotation.DBRouterStrategy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Outbox DAO for credit_award_task — Phase 2.2-B6 scaffold.
 *
 * insert: called inside saveGiveOutPrizesAggregate transaction when outbox flag=true.
 *         Caller sets DB/TB key before the transactionTemplate block; no @DBRouter needed.
 * queryPendingTasks: called by DispatchCreditAwardTaskJob; caller sets DB/TB key first.
 * updateDispatched / updateRetryFailed: routed via @DBRouter on userId field.
 *
 * NOTE: the credit_award_task_000..003 tables are proposed-only and must be applied to the
 * database (docs/sql/proposed-credit-award-task-outbox.sql) before enabling the flag.
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

}
