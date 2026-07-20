package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 远程 RPC 写入对账任务 DAO。 */
public interface IPendingRemoteWriteTaskDao {

    /** 写入一条待对账任务。 */
    int insert(PendingRemoteWriteTask task);

    /** 查询到期且未超过最大重试次数的任务。 */
    List<PendingRemoteWriteTask> queryPendingTasks(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    /** 将任务转入继续处理状态，等待远程前置写成功后续接。 */
    int updateContinuationPending(PendingRemoteWriteTask task);

    /** 标记远程写入已完成。 */
    int updateDone(PendingRemoteWriteTask task);

    /** 记录失败并按重试上限推进状态。 */
    int updateRetryFailed(@Param("id") Long id, @Param("maxRetry") int maxRetry);

    /** 统计指定状态的对账任务数。 */
    int countByState(@Param("state") String state);
}
