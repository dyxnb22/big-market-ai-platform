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

    List<ChatCreditSession> queryPendingRefunds(@Param("maxRetry") int maxRetry, @Param("limit") int limit);
}
