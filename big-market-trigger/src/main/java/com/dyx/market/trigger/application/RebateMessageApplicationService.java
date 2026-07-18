package com.dyx.market.trigger.application;

import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.valobj.OrderTradeTypeVO;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.domain.rebate.event.SendRebateMessageEvent;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * 返利消息应用服务：按返利类型编排活动配额或积分入账。
 */
@Service
public class RebateMessageApplicationService {

    @Resource
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;

    public void processRebateMessage(SendRebateMessageEvent.RebateMessage rebateMessage) {
        if (rebateMessage == null || rebateMessage.getRebateType() == null
                || rebateMessage.getRebateType().trim().isEmpty()
                || rebateMessage.getUserId() == null || rebateMessage.getBizId() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "返利消息参数不完整");
        }
        switch (rebateMessage.getRebateType().toLowerCase(Locale.ROOT)) {
            case "sku":
                SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
                skuRechargeEntity.setUserId(rebateMessage.getUserId());
                try {
                    skuRechargeEntity.setSku(Long.valueOf(rebateMessage.getRebateConfig()));
                } catch (Exception e) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "SKU返利配置非法");
                }
                skuRechargeEntity.setOutBusinessNo(rebateMessage.getBizId());
                skuRechargeEntity.setOrderTradeType(OrderTradeTypeVO.rebate_no_pay_trade);
                accountQuotaWriteAdapter.createOrder(skuRechargeEntity);
                break;
            case "integral":
                TradeEntity tradeEntity = new TradeEntity();
                tradeEntity.setUserId(rebateMessage.getUserId());
                tradeEntity.setTradeName(TradeNameVO.REBATE);
                tradeEntity.setTradeType(TradeTypeVO.FORWARD);
                try {
                    tradeEntity.setAmount(new BigDecimal(rebateMessage.getRebateConfig()));
                } catch (Exception e) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "积分返利配置非法");
                }
                tradeEntity.setOutBusinessNo(rebateMessage.getBizId());
                accountCreditWriteAdapter.createOrder(tradeEntity);
                break;
            default:
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "未知返利类型: " + rebateMessage.getRebateType());
        }
    }

    public boolean isBenignConsumerError(AppException e) {
        return ResponseCode.INDEX_DUP.getCode().equals(e.getCode());
    }
}
