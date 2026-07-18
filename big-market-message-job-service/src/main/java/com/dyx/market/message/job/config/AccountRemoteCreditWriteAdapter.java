package com.dyx.market.message.job.config;

import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.infrastructure.adapter.repository.PendingRemoteWriteSupport;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.types.enums.RemoteWriteOutcome;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;

import jakarta.annotation.Resource;

/**
 * 积分写路径：远程 account-service 失败时写 pending 任务，不回退本地写。
 * REJECTED 不入 pending；UNKNOWN（含超时）先 exists 探测再入 pending。
 */
@Slf4j
public class AccountRemoteCreditWriteAdapter implements IAccountCreditWriteAdapter {

    @Resource
    private PendingRemoteWriteSupport pendingRemoteWriteSupport;

    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;

    @Override
    public String createOrder(TradeEntity tradeEntity) {
        CreditTradeRequestDTO request = CreditTradeRequestDTO.builder()
                .userId(tradeEntity.getUserId())
                .tradeName(tradeEntity.getTradeName().name())
                .tradeType(tradeEntity.getTradeType().getCode())
                .amount(tradeEntity.getAmount())
                .outBusinessNo(tradeEntity.getOutBusinessNo())
                .build();
        try {
            Response<String> resp = accountCreditService.createOrder(request);
            RemoteWriteOutcome outcome = classify(resp);
            if (outcome == RemoteWriteOutcome.SUCCESS) {
                log.info("[AccountRemoteCreditWriteAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                        tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());
                return resp.getData() != null ? resp.getData() : tradeEntity.getOutBusinessNo();
            }
            if (outcome == RemoteWriteOutcome.REJECTED) {
                throw new AppException(resp.getCode(), resp.getInfo() != null ? resp.getInfo() : "远程积分写入被拒绝");
            }
            log.warn("[AccountRemoteCreditWriteAdapter] createOrder unknown code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null, tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AccountRemoteCreditWriteAdapter] createOrder remote failed userId:{} outBusinessNo:{}",
                    tradeEntity.getUserId(), tradeEntity.getOutBusinessNo(), e);
            if (remoteAlreadySucceeded(tradeEntity.getUserId(), tradeEntity.getOutBusinessNo())) {
                return tradeEntity.getOutBusinessNo();
            }
        }
        if (remoteAlreadySucceeded(tradeEntity.getUserId(), tradeEntity.getOutBusinessNo())) {
            return tradeEntity.getOutBusinessNo();
        }
        if (!pendingRemoteWriteSupport.enqueue(tradeEntity.getOutBusinessNo(), RemoteWriteOperations.CREDIT_CREATE, request, tradeEntity.getUserId())) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程积分写入失败，补偿任务参数无效");
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程积分写入失败，已记录待对账任务");
    }

    private boolean remoteAlreadySucceeded(String userId, String outBusinessNo) {
        try {
            Response<Boolean> exists = accountCreditService.existsCreditOrder(userId, outBusinessNo);
            return exists != null
                    && ResponseCode.SUCCESS.getCode().equals(exists.getCode())
                    && Boolean.TRUE.equals(exists.getData());
        } catch (Exception probeEx) {
            log.warn("[AccountRemoteCreditWriteAdapter] existsCreditOrder probe failed userId:{} outBusinessNo:{}",
                    userId, outBusinessNo, probeEx);
            return false;
        }
    }

    static RemoteWriteOutcome classify(Response<String> resp) {
        if (resp == null) {
            return RemoteWriteOutcome.UNKNOWN;
        }
        if (ResponseCode.SUCCESS.getCode().equals(resp.getCode())
                || ResponseCode.INDEX_DUP.getCode().equals(resp.getCode())) {
            return RemoteWriteOutcome.SUCCESS;
        }
        if (ResponseCode.ILLEGAL_PARAMETER.getCode().equals(resp.getCode())
                || ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode().equals(resp.getCode())) {
            return RemoteWriteOutcome.REJECTED;
        }
        return RemoteWriteOutcome.UNKNOWN;
    }
}
