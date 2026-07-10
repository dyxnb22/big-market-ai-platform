package com.dyx.market.message.job.config;

import com.alibaba.fastjson.JSON;
import com.dyx.market.trigger.api.dto.AccountQuotaCreateOrderRequestDTO;
import com.dyx.market.trigger.application.CreditPayExchangeApplicationService;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.infrastructure.dao.po.PendingRemoteWriteTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * pending 远程写对账成功后的业务 continuation（NR-006）。
 * Failures must propagate so the job keeps {@code continuation_pending}.
 */
@Slf4j
@Component
public class RemoteWriteContinuationDispatcher {

    @Resource
    private CreditPayExchangeApplicationService creditPayExchangeApplicationService;

    public void dispatch(PendingRemoteWriteTask task) {
        if (task == null || StringUtils.isBlank(task.getOperation())) {
            return;
        }
        if (RemoteWriteOperations.QUOTA_CREATE.equals(task.getOperation())) {
            continueCreditPayIfNeeded(task);
        }
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
