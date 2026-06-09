package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@ConditionalOnMissingBean(IAccountCreditWriteAdapter.class)
public class LocalAccountCreditWriteAdapter implements IAccountCreditWriteAdapter {

    @Resource
    private ICreditAdjustService creditAdjustService;

    @Override
    public String createOrder(TradeEntity tradeEntity) {
        return creditAdjustService.createOrder(tradeEntity);
    }

}
