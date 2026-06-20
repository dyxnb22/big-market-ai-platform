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

@Service
public class RebateMessageApplicationService {

    @Resource
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;

    public void processRebateMessage(SendRebateMessageEvent.RebateMessage rebateMessage) {
        switch (rebateMessage.getRebateType()) {
            case "sku":
                SkuRechargeEntity skuRechargeEntity = new SkuRechargeEntity();
                skuRechargeEntity.setUserId(rebateMessage.getUserId());
                skuRechargeEntity.setSku(Long.valueOf(rebateMessage.getRebateConfig()));
                skuRechargeEntity.setOutBusinessNo(rebateMessage.getBizId());
                skuRechargeEntity.setOrderTradeType(OrderTradeTypeVO.rebate_no_pay_trade);
                accountQuotaWriteAdapter.createOrder(skuRechargeEntity);
                break;
            case "integral":
                TradeEntity tradeEntity = new TradeEntity();
                tradeEntity.setUserId(rebateMessage.getUserId());
                tradeEntity.setTradeName(TradeNameVO.REBATE);
                tradeEntity.setTradeType(TradeTypeVO.FORWARD);
                tradeEntity.setAmount(new BigDecimal(rebateMessage.getRebateConfig()));
                tradeEntity.setOutBusinessNo(rebateMessage.getBizId());
                accountCreditWriteAdapter.createOrder(tradeEntity);
                break;
            default:
                break;
        }
    }

    public boolean isBenignConsumerError(AppException e) {
        return ResponseCode.INDEX_DUP.getCode().equals(e.getCode())
                || ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode().equals(e.getCode());
    }
}
