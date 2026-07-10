package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Slf4j
@Repository
public class ChatCreditSessionRepository implements IChatCreditSessionRepository {

    @Resource
    private IChatCreditSessionDao chatCreditSessionDao;

    @Override
    public void recordDeduction(String userId, String requestId, int amount) {
        upsert(userId, requestId, amount, true, "none");
    }

    @Override
    public void updateRefundState(String requestId, String refundState) {
        try {
            chatCreditSessionDao.updateRefundState(ChatCreditSession.builder()
                    .requestId(requestId)
                    .refundState(refundState)
                    .build());
        } catch (Exception e) {
            log.error("更新 chat_credit_session 退款状态失败 requestId:{}", requestId, e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "记录退款状态失败");
        }
    }

    @Override
    public void markRefundPending(String userId, String requestId, int amount) {
        upsert(userId, requestId, amount, true, "pending");
    }

    private void upsert(String userId, String requestId, int amount, boolean deducted, String refundState) {
        try {
            ChatCreditSession existing = chatCreditSessionDao.queryByRequestId(requestId);
            if (existing != null) {
                chatCreditSessionDao.updateRefundState(ChatCreditSession.builder()
                        .requestId(requestId)
                        .refundState(refundState)
                        .build());
                return;
            }
            chatCreditSessionDao.insert(ChatCreditSession.builder()
                    .userId(userId)
                    .requestId(requestId)
                    .deducted(deducted)
                    .deductAmount(amount)
                    .refundState(refundState)
                    .retryCount(0)
                    .build());
        } catch (DuplicateKeyException e) {
            log.warn("chat_credit_session 重复 requestId:{}", requestId);
        } catch (Exception e) {
            log.error("记录 chat_credit_session 失败 userId:{} requestId:{}", userId, requestId, e);
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "记录会话补偿状态失败");
        }
    }
}
