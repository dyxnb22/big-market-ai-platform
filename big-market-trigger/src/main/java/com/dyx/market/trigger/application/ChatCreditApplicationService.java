package com.dyx.market.trigger.application;

import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.domain.chat.model.ChatCreditSessionSnapshot;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * AI Chat 积分扣减/退还应用服务。
 */
@Slf4j
@Service
public class ChatCreditApplicationService {

    @Resource
    /** 账户服务积分扣减与退款写操作适配器。 */
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    /** 账户服务积分余额查询适配器；查询失败时仅影响返回值，不回滚已完成的扣费。 */
    private IAccountReadAdapter accountRemoteReadAdapter;
    @Resource
    /** Chat 扣费会话仓储，用于记录扣费意图、幂等状态和退款状态。 */
    private IChatCreditSessionRepository chatCreditSessionRepository;

    /**
     * 扣减 Chat 请求所需积分。
     *
     * <p>先以 {@code userId + requestId} 持久化扣费意图，再调用账户服务扣费，
     * 使远程调用超时或本地进程崩溃时仍可由对账/退款流程接管。账户交易号使用
     * {@code chat_{userId}_{requestId}}，重复请求直接复用已完成交易；明确的业务拒绝会
     * 将意图置为失败，未知结果则保留 {@code deducting} 状态等待后续确认。</p>
     *
     * @param userId 用户 ID
     * @param amount 扣减积分数量，必须大于 0
     * @param requestId Chat 请求幂等 ID
     * @return 扣减完成后的账户余额；余额查询失败时返回 0，不代表扣费失败
     */
    public BigDecimal deduct(String userId, int amount, String requestId) {
        validate(userId, requestId, amount);
        // 先持久化扣费意图；即使远程扣费后本地写状态失败，也能依靠会话记录退款或对账。
        chatCreditSessionRepository.recordDeductingIntent(userId, requestId, amount);
        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(userId)
                    .tradeName(TradeNameVO.OPENAI_PAY)
                    .tradeType(TradeTypeVO.REVERSE)
                    .amount(BigDecimal.valueOf(amount).negate())
                    .outBusinessNo(chatOutBusinessNo(userId, requestId))
                    .build());
            chatCreditSessionRepository.markDeducted(userId, requestId);
            log.info("AI Chat积分扣减完成 userId:{} amount:{} orderId:{}", userId, amount, orderId);
            return queryCreditBalanceSafe(userId);
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("AI Chat积分扣减重复 userId:{} requestId:{}", userId, requestId);
                chatCreditSessionRepository.markDeducted(userId, requestId);
                return queryCreditBalanceSafe(userId);
            }
            // 明确的业务拒绝清理扣费意图；UNKNOWN/处理中路径保留 deducting，等待后续确认。
            if (isExplicitReject(e)) {
                chatCreditSessionRepository.markDeductFailed(userId, requestId);
            }
            throw e;
        }
    }

    private static boolean isExplicitReject(AppException e) {
        if (e == null || e.getCode() == null) {
            return false;
        }
        String code = e.getCode();
        return ResponseCode.ILLEGAL_PARAMETER.getCode().equals(code)
                || ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode().equals(code);
    }

    /**
     * 按扣费会话退款：金额取自 session，拒绝无 session 或已退款的伪造请求。
     */
    public BigDecimal refund(String userId, String originalRequestId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(originalRequestId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        ChatCreditSessionSnapshot session = chatCreditSessionRepository.findSession(userId, originalRequestId);
        if (session == null || !session.isDeducted() || session.getDeductAmount() <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "未找到可退款的扣费会话");
        }
        if (IChatCreditSessionRepository.REFUND_REFUNDED.equals(session.getRefundState())) {
            return queryCreditBalanceSafe(userId);
        }
        if (!chatCreditSessionRepository.tryBeginRefund(userId, originalRequestId)) {
            ChatCreditSessionSnapshot latest = chatCreditSessionRepository.findSession(userId, originalRequestId);
            if (latest != null && IChatCreditSessionRepository.REFUND_REFUNDED.equals(latest.getRefundState())) {
                return queryCreditBalanceSafe(userId);
            }
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "退款处理中或状态不可退款");
        }
        int amount = session.getDeductAmount();
        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(userId)
                    .tradeName(TradeNameVO.OPENAI_PAY)
                    .tradeType(TradeTypeVO.FORWARD)
                    .amount(BigDecimal.valueOf(amount))
                    .outBusinessNo(chatRefundOutBusinessNo(userId, originalRequestId))
                    .build());
            chatCreditSessionRepository.updateRefundState(userId, originalRequestId,
                    IChatCreditSessionRepository.REFUND_REFUNDED);
            log.info("AI Chat积分退还完成 userId:{} amount:{} orderId:{}", userId, amount, orderId);
            return queryCreditBalanceSafe(userId);
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("AI Chat积分退还重复 userId:{} requestId:{}", userId, originalRequestId);
                chatCreditSessionRepository.updateRefundState(userId, originalRequestId,
                        IChatCreditSessionRepository.REFUND_REFUNDED);
                return queryCreditBalanceSafe(userId);
            }
            chatCreditSessionRepository.markRefundPending(userId, originalRequestId, amount);
            throw e;
        } catch (Exception e) {
            chatCreditSessionRepository.markRefundPending(userId, originalRequestId, amount);
            throw e;
        }
    }

    /**
     * 将退款标记为待补偿。
     *
     * <p>由消息任务在下游异常或未知结果时调用。金额以已落库会话为准，只有确认远程扣费
     * 成功的会话才允许进入待退款状态，避免伪造请求或未知扣费结果产生错误入账。</p>
     *
     * @param userId 用户 ID
     * @param requestId 原始 Chat 请求幂等 ID
     * @param amount 调用方携带的退款金额兜底值；会话已有金额时忽略该值
     */
    public void markRefundPending(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId)) {
            return;
        }
        ChatCreditSessionSnapshot session = chatCreditSessionRepository.findSession(userId, requestId);
        if (session == null || !session.isDeducted()) {
            return;
        }
        int effectiveAmount = session.getDeductAmount() > 0 ? session.getDeductAmount() : amount;
        if (effectiveAmount <= 0) {
            return;
        }
        chatCreditSessionRepository.markRefundPending(userId, requestId, effectiveAmount);
    }

    /** 构造 Chat 扣费交易的业务幂等号。 */
    static String chatOutBusinessNo(String userId, String requestId) {
        return "chat_" + userId + "_" + requestId;
    }

    /** 构造 Chat 退款交易的业务幂等号。 */
    static String chatRefundOutBusinessNo(String userId, String requestId) {
        return "chat_refund_" + userId + "_" + requestId;
    }

    /** 校验扣费请求的用户、请求幂等号和积分数量。 */
    private static void validate(String userId, String requestId, int amount) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(requestId) || amount <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
    }

    /** 查询余额；远程查询失败时返回安全兜底值，避免覆盖已经完成的写操作结果。 */
    private BigDecimal queryCreditBalanceSafe(String userId) {
        try {
            return accountRemoteReadAdapter.queryUserCreditAccount(userId);
        } catch (Exception e) {
            log.warn("查询用户积分失败 userId:{}", userId, e);
            return BigDecimal.ZERO;
        }
    }
}
