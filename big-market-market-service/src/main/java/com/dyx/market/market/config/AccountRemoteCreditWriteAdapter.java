package com.dyx.market.market.config;

import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.infrastructure.adapter.repository.PendingRemoteWriteSupport;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;

import javax.annotation.Resource;

/**
 * 积分写路径：远程 account-service 失败时写 pending 任务，不回退本地写。
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
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[AccountRemoteCreditWriteAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                        tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());
                return resp.getData();
            }
            log.warn("[AccountRemoteCreditWriteAdapter] createOrder non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null, tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());
        } catch (Exception e) {
            log.error("[AccountRemoteCreditWriteAdapter] createOrder remote failed userId:{} outBusinessNo:{}",
                    tradeEntity.getUserId(), tradeEntity.getOutBusinessNo(), e);
        }
        if (!pendingRemoteWriteSupport.enqueue(tradeEntity.getOutBusinessNo(), RemoteWriteOperations.CREDIT_CREATE, request)) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程积分写入失败，补偿任务参数无效");
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程积分写入失败，已记录待对账任务");
    }
}
