package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.domain.chat.model.ChatCreditSessionSnapshot;
import com.dyx.market.infrastructure.dao.IChatCreditSessionDao;
import com.dyx.market.infrastructure.dao.po.ChatCreditSession;
import com.dyx.market.middleware.db.router.DBRouterTemplate;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

@Slf4j
@Repository
/**
 * Chat 积分会话仓储。
 *
 * <p>该表位于用户分片库，所有 DAO 操作都必须通过 userId 路由；
 * deduct/refund 状态使用 CAS，使超时重试只能推进一次状态。</p>
 */
public class ChatCreditSessionRepository implements IChatCreditSessionRepository {

    public static final String REFUND_NONE = "none";
    public static final String REFUND_REFUNDING = "refunding";
    public static final String REFUND_REFUNDED = "refunded";
    public static final String REFUND_PENDING = "pending";

    @Resource
    private IChatCreditSessionDao chatCreditSessionDao;

    @Resource
    private IDBRouterStrategy dbRouter;

    /** 先记录 deducting 意图，再允许远程账户扣费，确保超时后仍可对账。 */
    @Override
    public void recordDeductingIntent(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || amount <= 0) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            try {
                chatCreditSessionDao.insert(ChatCreditSession.builder()
                        .userId(userId)
                        .requestId(requestId)
                        .deducted(false)
                        .deductAmount(amount)
                        .deductState(DEDUCT_DEDUCTING)
                        .refundState(REFUND_NONE)
                        .retryCount(0)
                        .build());
            } catch (DuplicateKeyException e) {
                log.debug("chat_credit_session intent 已存在 userId:{} requestId:{}", userId, requestId);
            } catch (Exception e) {
                log.error("记录 chat_credit_session intent 失败 userId:{} requestId:{}", userId, requestId, e);
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "记录会话扣费意图失败");
            }
        });
    }

    /** 将会话推进为已扣费；不存在记录时补建零金额占位记录。 */
    @Override
    public void markDeducted(String userId, String requestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            try {
                int affected = chatCreditSessionDao.markDeducted(userId, requestId);
                if (affected == 0) {
                    try {
                        chatCreditSessionDao.insert(ChatCreditSession.builder()
                                .userId(userId)
                                .requestId(requestId)
                                .deducted(true)
                                .deductAmount(0)
                                .deductState(DEDUCT_DEDUCTED)
                                .refundState(REFUND_NONE)
                                .retryCount(0)
                                .build());
                    } catch (DuplicateKeyException ignored) {
                        chatCreditSessionDao.markDeducted(userId, requestId);
                    }
                }
            } catch (Exception e) {
                log.error("标记 chat_credit_session deducted 失败 userId:{} requestId:{}", userId, requestId, e);
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "确认会话扣费状态失败");
            }
        });
    }

    /** 将仍处于 deducting 的会话标记失败，供上层决定是否重试。 */
    @Override
    public void markDeductFailed(String userId, String requestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId,
                () -> chatCreditSessionDao.casDeductState(userId, requestId, DEDUCT_DEDUCTING, DEDUCT_FAILED));
    }

    /** 记录已完成的扣费会话；重复插入只修正状态，不重复写入金额。 */
    @Override
    public void recordDeduction(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || amount <= 0) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            try {
                chatCreditSessionDao.insert(ChatCreditSession.builder()
                        .userId(userId)
                        .requestId(requestId)
                        .deducted(true)
                        .deductAmount(amount)
                        .deductState(DEDUCT_DEDUCTED)
                        .refundState(REFUND_NONE)
                        .retryCount(0)
                        .build());
            } catch (DuplicateKeyException e) {
                chatCreditSessionDao.markDeducted(userId, requestId);
                log.debug("chat_credit_session 扣费记录已存在 userId:{} requestId:{}", userId, requestId);
            } catch (Exception e) {
                log.error("记录 chat_credit_session 扣费失败 userId:{} requestId:{}", userId, requestId, e);
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "记录会话补偿状态失败");
            }
        });
    }

    /** 查询当前用户分片上的权威扣费/退款快照。 */
    @Override
    public ChatCreditSessionSnapshot findSession(String userId, String requestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return null;
        }
        return DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            ChatCreditSession row = chatCreditSessionDao.queryByUserIdAndRequestId(userId, requestId);
            return toSnapshot(row);
        });
    }

    /** CAS 抢占退款任务，只有一个执行者可以进入 refunding。 */
    @Override
    public boolean tryBeginRefund(String userId, String requestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return false;
        }
        return DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            if (chatCreditSessionDao.casRefundState(userId, requestId, REFUND_NONE, REFUND_REFUNDING) == 1) {
                return true;
            }
            return chatCreditSessionDao.casRefundState(userId, requestId, REFUND_PENDING, REFUND_REFUNDING) == 1;
        });
    }

    /** 持久化退款结果或 pending 状态，所有更新都在用户分片内完成。 */
    @Override
    public void updateRefundState(String userId, String requestId, String refundState) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            try {
                int affected = chatCreditSessionDao.updateRefundState(ChatCreditSession.builder()
                        .userId(userId)
                        .requestId(requestId)
                        .refundState(refundState)
                        .build());
                if (affected == 0) {
                    log.warn("chat_credit_session 退款状态未更新 userId:{} requestId:{} state:{}", userId, requestId, refundState);
                }
            } catch (Exception e) {
                log.error("更新 chat_credit_session 退款状态失败 userId:{} requestId:{}", userId, requestId, e);
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "记录退款状态失败");
            }
        });
    }

    /** 将退款标记为待补偿；已退款状态不可被回退。 */
    @Override
    public void markRefundPending(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || amount <= 0) {
            return;
        }
        DBRouterTemplate.executeOnShard(dbRouter, userId, () -> {
            try {
                ChatCreditSession existing = chatCreditSessionDao.queryByUserIdAndRequestId(userId, requestId);
                if (existing == null) {
                    insertPendingSession(userId, requestId, amount);
                    return;
                }
                if (REFUND_REFUNDED.equals(existing.getRefundState())) {
                    log.debug("chat_credit_session 已退款，跳过 pending userId:{} requestId:{}", userId, requestId);
                    return;
                }
                if (chatCreditSessionDao.casRefundState(userId, requestId, REFUND_NONE, REFUND_PENDING) == 1) {
                    return;
                }
                if (chatCreditSessionDao.casRefundState(userId, requestId, REFUND_REFUNDING, REFUND_PENDING) == 1) {
                    return;
                }
                if (REFUND_PENDING.equals(existing.getRefundState())) {
                    return;
                }
                log.warn("chat_credit_session 无法标记 pending userId:{} requestId:{} state:{}",
                        userId, requestId, existing.getRefundState());
            } catch (DuplicateKeyException e) {
                log.debug("chat_credit_session pending 并发插入 userId:{} requestId:{}", userId, requestId);
            } catch (Exception e) {
                log.error("标记 chat_credit_session pending 失败 userId:{} requestId:{}", userId, requestId, e);
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "记录会话补偿状态失败");
            }
        });
    }

    private void insertPendingSession(String userId, String requestId, int amount) {
        chatCreditSessionDao.insert(ChatCreditSession.builder()
                .userId(userId)
                .requestId(requestId)
                .deducted(true)
                .deductAmount(amount)
                .deductState(DEDUCT_DEDUCTED)
                .refundState(REFUND_PENDING)
                .retryCount(0)
                .build());
    }

    private static ChatCreditSessionSnapshot toSnapshot(ChatCreditSession row) {
        if (row == null) {
            return null;
        }
        boolean deducted = Boolean.TRUE.equals(row.getDeducted())
                || DEDUCT_DEDUCTED.equals(row.getDeductState());
        return ChatCreditSessionSnapshot.builder()
                .userId(row.getUserId())
                .requestId(row.getRequestId())
                .deductAmount(row.getDeductAmount() != null ? row.getDeductAmount() : 0)
                .deducted(deducted)
                .deductState(row.getDeductState())
                .refundState(row.getRefundState())
                .build();
    }
}
