package com.dyx.market.message.job.config;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.chat.adapter.repository.IChatCreditSessionRepository;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaCreateOrderRequestDTO;
import com.dyx.market.trigger.application.CreditPayExchangeApplicationService;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * pending 远程写对账成功后的业务 continuation（NR-006）。
 * 失败必须继续向上抛出，使 Job 保留 {@code continuation_pending} 状态并在下一轮重试。
 */
@Slf4j
@Component
public class RemoteWriteContinuationDispatcher {

    @Resource
    private CreditPayExchangeApplicationService creditPayExchangeApplicationService;
    @Resource
    private IChatCreditSessionRepository chatCreditSessionRepository;

    public void dispatch(PendingRemoteWriteTask task) {
        if (task == null || StringUtils.isBlank(task.getOperation())) {
            return;
        }
        if (RemoteWriteOperations.QUOTA_CREATE.equals(task.getOperation())) {
            continueCreditPayIfNeeded(task);
            return;
        }
        if (RemoteWriteOperations.CREDIT_CREATE.equals(task.getOperation())) {
            CreditTradeRequestDTO dto = JSON.parseObject(task.getPayload(), CreditTradeRequestDTO.class);
            if (dto != null && "CONVERT_SKU".equals(dto.getTradeName())) {
                creditPayExchangeApplicationService.continueAfterRemoteCreditCreated(
                        dto.getUserId(), dto.getOutBusinessNo());
            } else {
                continueChatDeductIfNeeded(task);
            }
        }
    }

    private void continueChatDeductIfNeeded(PendingRemoteWriteTask task) {
        CreditTradeRequestDTO dto = JSON.parseObject(task.getPayload(), CreditTradeRequestDTO.class);
        if (dto == null || StringUtils.isBlank(dto.getUserId()) || StringUtils.isBlank(dto.getOutBusinessNo())
                || !"OPENAI_PAY".equals(dto.getTradeName()) || !"reverse".equals(dto.getTradeType())) {
            return;
        }
        String prefix = "chat_" + dto.getUserId() + "_";
        if (!dto.getOutBusinessNo().startsWith(prefix) || dto.getOutBusinessNo().length() == prefix.length()) {
            return;
        }
        String requestId = dto.getOutBusinessNo().substring(prefix.length());
        chatCreditSessionRepository.markDeducted(dto.getUserId(), requestId);
        log.info("[RemoteWriteContinuation] chat deduct completed userId:{} requestId:{}",
                dto.getUserId(), requestId);
    }

    private void continueCreditPayIfNeeded(PendingRemoteWriteTask task) {
        AccountQuotaCreateOrderRequestDTO dto = JSON.parseObject(task.getPayload(), AccountQuotaCreateOrderRequestDTO.class);
        if (dto == null || !"credit_pay_trade".equals(dto.getOrderTradeType())) {
            return;
        }
        creditPayExchangeApplicationService.continueAfterRemoteQuotaCreated(
                dto.getUserId(), dto.getOutBusinessNo(), dto.getSku());
        log.info("[RemoteWriteContinuation] credit pay continued userId:{} outBusinessNo:{}",
                dto.getUserId(), dto.getOutBusinessNo());
    }
}
