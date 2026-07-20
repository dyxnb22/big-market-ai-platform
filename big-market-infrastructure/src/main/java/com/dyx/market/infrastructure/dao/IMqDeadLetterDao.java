package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.MqDeadLetter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** MQ 死信持久化、审核和重放状态迁移 DAO。 */
public interface IMqDeadLetterDao {

    /** 持久化一条死信；重复消息由业务消息号/唯一键约束处理。 */
    int insert(MqDeadLetter record);

    /** 查询允许自动处理且未超过重试上限的记录。 */
    List<MqDeadLetter> queryPendingReplay(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    /** 标记消息已成功重放。 */
    int updateReplayed(MqDeadLetter record);

    /** 记录一次重放失败，并按上限推进状态。 */
    int updateRetryFailed(@Param("id") Long id, @Param("maxRetry") int maxRetry);

    /** 将消息转入人工待处理状态。 */
    int updateManualPending(MqDeadLetter record);

    /** 新消息再次进入 DLQ 时，按业务消息号复用可重放记录。 */
    int reactivateReplayed(@Param("businessMessageId") String businessMessageId,
                           @Param("maxConsumeFailures") int maxConsumeFailures);

    /** 查询某业务消息号最近一条死信记录。 */
    MqDeadLetter queryLatestByBusinessMessageId(@Param("businessMessageId") String businessMessageId);

    /** 统计指定状态的死信数量，用于监控指标。 */
    int countByState(@Param("state") String state);
}
