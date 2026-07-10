package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IChatCreditSessionDao {

    int insert(ChatCreditSession session);

    int updateRefundState(ChatCreditSession session);

    int updateRetryFailed(ChatCreditSession session);

    ChatCreditSession queryByRequestId(@Param("requestId") String requestId);

    ChatCreditSession queryByUserIdAndRequestId(@Param("userId") String userId,
                                                @Param("requestId") String requestId);

    int casRefundState(@Param("userId") String userId,
                       @Param("requestId") String requestId,
                       @Param("fromState") String fromState,
                       @Param("toState") String toState);

    List<ChatCreditSession> queryPendingRefunds(@Param("maxRetry") int maxRetry, @Param("limit") int limit);
}
