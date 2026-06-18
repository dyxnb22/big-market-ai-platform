package com.dyx.market.domain.credit.adapter.port;

import com.dyx.market.domain.credit.model.entity.CreditAwardTaskEntity;

import java.util.List;

/**
 * 领域端口：隔离 DispatchCreditAwardTaskJob 对积分发奖发件箱 DAO 的直接依赖。
 * <p>
 * （AL-7）message-job-service 不得直接依赖 ICreditAwardTaskDao；credit_award_task 表归 account/credit 边界，
 * 任务调度仅需待处理任务的读取与状态更新。
 * <p>
 * 本地路径（默认）：LocalCreditAwardTaskDispatchPort 直接委托 ICreditAwardTaskDao，保留现有 DB/TB 路由与开关。
 * 远程路径（可配置）：account-service 在运行时接管发件箱调度后可替换本地实现。
 */
public interface ICreditAwardTaskDispatchPort {

    List<CreditAwardTaskEntity> queryPendingTasks();

    int updateDispatched(CreditAwardTaskEntity task);

    int updateRetryFailed(CreditAwardTaskEntity task);

}
