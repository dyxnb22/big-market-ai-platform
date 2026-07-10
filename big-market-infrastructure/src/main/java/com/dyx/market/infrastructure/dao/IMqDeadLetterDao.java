package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.MqDeadLetter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IMqDeadLetterDao {

    int insert(MqDeadLetter record);

    List<MqDeadLetter> queryPendingReplay(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    int updateReplayed(MqDeadLetter record);

    int updateRetryFailed(@Param("id") Long id, @Param("maxRetry") int maxRetry);

    int updateManualPending(MqDeadLetter record);

    int reactivateReplayed(@Param("businessMessageId") String businessMessageId,
                           @Param("maxConsumeFailures") int maxConsumeFailures);

    MqDeadLetter queryLatestByBusinessMessageId(@Param("businessMessageId") String businessMessageId);

    int countByState(@Param("state") String state);
}
