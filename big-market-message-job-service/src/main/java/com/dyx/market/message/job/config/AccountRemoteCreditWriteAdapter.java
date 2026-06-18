package com.dyx.market.message.job.config;

import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 远程积分写适配器（message-job-service）。
 * <p>
 * 仅在 {@code account.service.remote-credit-write.enabled=true} 时激活；
 * 远程异常或非成功响应时回退到本地 {@link ICreditAdjustService}。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "account.service.remote-credit-write.enabled", havingValue = "true")
public class AccountRemoteCreditWriteAdapter implements IAccountCreditWriteAdapter {

    @Resource
    private ICreditAdjustService creditAdjustService;

    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;

    @Override
    public String createOrder(TradeEntity tradeEntity) {
        try {
            Response<String> resp = accountCreditService.createOrder(CreditTradeRequestDTO.builder()
                    .userId(tradeEntity.getUserId())
                    .tradeName(tradeEntity.getTradeName().name())
                    .tradeType(tradeEntity.getTradeType().getCode())
                    .amount(tradeEntity.getAmount())
                    .outBusinessNo(tradeEntity.getOutBusinessNo())
                    .build());
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[AccountRemoteCreditWriteAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                        tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());
                return resp.getData();
            }
            log.warn("[AccountRemoteCreditWriteAdapter] createOrder non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null, tradeEntity.getUserId(), tradeEntity.getOutBusinessNo());
        } catch (Exception e) {
            log.error("[AccountRemoteCreditWriteAdapter] createOrder remote failed, falling back to local userId:{} outBusinessNo:{}",
                    tradeEntity.getUserId(), tradeEntity.getOutBusinessNo(), e);
        }
        return creditAdjustService.createOrder(tradeEntity);
    }

}
