package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.StrategyAwardStockConfirmTask;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 奖品库存确认 Outbox DAO；只有任务消费者推进确认/失败状态。 */
public interface IStrategyAwardStockConfirmTaskDao {

    /** 写入抽奖成功后的库存确认任务。 */
    @DBRouter
    void insert(StrategyAwardStockConfirmTask task);

    /** 查询待确认任务，按重试上限和扫描数量限流。 */
    List<StrategyAwardStockConfirmTask> queryPendingTasks(@Param("maxRetries") int maxRetries,
                                                          @Param("limit") int limit);

    /** CAS 抢占任务，避免多个 Job 实例同时确认同一预占。 */
    @DBRouter
    int claimProcessing(StrategyAwardStockConfirmTask task);

    /** 将已完成库存确认的任务标记为 confirmed。 */
    @DBRouter
    int updateConfirmed(StrategyAwardStockConfirmTask task);

    /** 记录失败并按重试上限推进任务状态。 */
    @DBRouter
    int updateRetryFailed(@Param("task") StrategyAwardStockConfirmTask task,
                          @Param("maxRetries") int maxRetries);

    /** 回收超时停留在 processing 的任务，使其重新可被 Job 处理。 */
    int revertStaleProcessing(@Param("staleBefore") java.util.Date staleBefore, @Param("limit") int limit);

    /** 统计待确认任务数量。 */
    int countPending();

    /** 统计指定任务状态数量。 */
    int countByState(@Param("state") String state);
}
