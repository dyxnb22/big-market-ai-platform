package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IChatCreditSessionDao {

    int insert(ChatCreditSession session);

    int updateRefundState(ChatCreditSession session);

    int updateRetryFailed(@Param("session") ChatCreditSession session,
                          @Param("maxRetry") int maxRetry,
                          @Param("lastError") String lastError);

    ChatCreditSession queryByRequestId(@Param("requestId") String requestId);

    ChatCreditSession queryByUserIdAndRequestId(@Param("userId") String userId,
                                                @Param("requestId") String requestId);

    int casRefundState(@Param("userId") String userId,
                       @Param("requestId") String requestId,
                       @Param("fromState") String fromState,
                       @Param("toState") String toState);

    int casDeductState(@Param("userId") String userId,
                       @Param("requestId") String requestId,
                       @Param("fromState") String fromState,
                       @Param("toState") String toState);

    int markDeducted(@Param("userId") String userId,
                     @Param("requestId") String requestId);

    List<ChatCreditSession> queryPendingRefunds(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    List<ChatCreditSession> queryPendingDeductions(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    int countPendingRefunds();

    int countByRefundState(@Param("state") String state);

    int countByDeductState(@Param("state") String state);
}
