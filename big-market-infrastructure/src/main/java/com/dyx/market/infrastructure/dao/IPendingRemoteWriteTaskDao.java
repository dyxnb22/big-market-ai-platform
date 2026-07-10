package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IPendingRemoteWriteTaskDao {

    int insert(PendingRemoteWriteTask task);

    List<PendingRemoteWriteTask> queryPendingTasks(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    int updateContinuationPending(PendingRemoteWriteTask task);

    int updateDone(PendingRemoteWriteTask task);

    int updateRetryFailed(@Param("id") Long id, @Param("maxRetry") int maxRetry);

    int countByState(@Param("state") String state);
}
