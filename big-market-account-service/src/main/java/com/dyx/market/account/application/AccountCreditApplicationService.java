package com.dyx.market.account.application;

import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Service
public class AccountCreditApplicationService {

    @Resource
    private ICreditAdjustService creditAdjustService;

    public String createOrder(CreditTradeRequestDTO request) {
        if (StringUtils.isBlank(request.getUserId())
                || StringUtils.isBlank(request.getTradeName())
                || StringUtils.isBlank(request.getTradeType())
                || request.getAmount() == null
                || StringUtils.isBlank(request.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        return creditAdjustService.createOrder(TradeEntity.builder()
                .userId(request.getUserId())
                .tradeName(resolveTradeNameVO(request.getTradeName()))
                .tradeType(resolveTradeType(request.getTradeType()))
                .amount(request.getAmount())
                .outBusinessNo(request.getOutBusinessNo())
                .build());
    }

    public BigDecimal queryUserCreditAccount(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        CreditAccountEntity entity = creditAdjustService.queryUserCreditAccount(userId);
        return entity != null ? entity.getAdjustAmount() : BigDecimal.ZERO;
    }

    private TradeNameVO resolveTradeNameVO(String name) {
        try {
            return TradeNameVO.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Unknown tradeName: " + name);
        }
    }

    private TradeTypeVO resolveTradeType(String code) {
        for (TradeTypeVO type : TradeTypeVO.values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                "Unknown tradeType: " + code + ". Expected: forward | reverse");
    }
}
