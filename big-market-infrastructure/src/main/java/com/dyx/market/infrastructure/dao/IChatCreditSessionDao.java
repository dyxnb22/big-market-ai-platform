package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** Chat 积分扣费/退款会话 DAO；状态迁移使用 CAS 防止重复扣费或退款。 */
public interface IChatCreditSessionDao {

    /** 记录扣费意图或完成扣费的会话。 */
    int insert(ChatCreditSession session);

    /** 更新退款状态及相关时间/错误字段。 */
    int updateRefundState(ChatCreditSession session);

    /** 记录失败、重试次数和下一次重试时间。 */
    int updateRetryFailed(@Param("session") ChatCreditSession session,
                          @Param("maxRetry") int maxRetry,
                          @Param("lastError") String lastError);

    /** 按 requestId 查询会话；调用方必须先设置用户分片路由。 */
    ChatCreditSession queryByRequestId(@Param("requestId") String requestId);

    /** 按用户和 requestId 查询权威扣费会话。 */
    ChatCreditSession queryByUserIdAndRequestId(@Param("userId") String userId,
                                                @Param("requestId") String requestId);

    /** CAS 推进退款状态，返回 1 表示抢到状态迁移。 */
    int casRefundState(@Param("userId") String userId,
                       @Param("requestId") String requestId,
                       @Param("fromState") String fromState,
                       @Param("toState") String toState);

    /** CAS 推进扣费状态，避免超时补偿重复扣费。 */
    int casDeductState(@Param("userId") String userId,
                       @Param("requestId") String requestId,
                       @Param("fromState") String fromState,
                       @Param("toState") String toState);

    /** 将会话标记为已扣费。 */
    int markDeducted(@Param("userId") String userId,
                     @Param("requestId") String requestId);

    /** 查询到期且未超过重试上限的退款会话。 */
    List<ChatCreditSession> queryPendingRefunds(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    /** 查询到期且未超过重试上限的扣费会话。 */
    List<ChatCreditSession> queryPendingDeductions(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    /** 统计待退款会话数量。 */
    int countPendingRefunds();

    /** 统计指定退款状态的会话数量。 */
    int countByRefundState(@Param("state") String state);

    /** 统计指定扣费状态的会话数量。 */
    int countByDeductState(@Param("state") String state);
}
