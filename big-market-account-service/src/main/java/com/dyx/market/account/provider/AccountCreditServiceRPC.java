package com.dyx.market.account.provider;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * Dubbo provider for credit account operations.
 *
 * Dark-launch Phase 2.2-A: provider is registered but receives no traffic.
 * Delegates to the existing ICreditAdjustService domain service unchanged.
 */
@Slf4j
@DubboService(version = "1.0")
public class AccountCreditServiceRPC implements IAccountCreditService {

    @Resource
    private ICreditAdjustService creditAdjustService;

    @Override
    public Response<String> createOrder(CreditTradeRequestDTO request) {
        if (request == null) {
            log.warn("account credit createOrder request is null");
            return Response.<String>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                    .build();
        }
        log.info("account credit createOrder userId:{} request:{}", request.getUserId(), JSON.toJSONString(request));
        try {
            if (StringUtils.isBlank(request.getUserId())
                    || StringUtils.isBlank(request.getTradeName())
                    || StringUtils.isBlank(request.getTradeType())
                    || request.getAmount() == null
                    || StringUtils.isBlank(request.getOutBusinessNo())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            TradeEntity tradeEntity = TradeEntity.builder()
                    .userId(request.getUserId())
                    .tradeName(resolveTradeNameVO(request.getTradeName()))
                    .tradeType(resolveTradeType(request.getTradeType()))
                    .amount(request.getAmount())
                    .outBusinessNo(request.getOutBusinessNo())
                    .build();

            String orderId = creditAdjustService.createOrder(tradeEntity);
            log.info("account credit createOrder success userId:{} orderId:{}", request.getUserId(), orderId);
            return Response.<String>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(orderId)
                    .build();

        } catch (AppException e) {
            log.error("account credit createOrder appException userId:{} code:{}", request.getUserId(), e.getCode(), e);
            return Response.<String>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("account credit createOrder failed userId:{}", request.getUserId(), e);
            return Response.<String>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<BigDecimal> queryUserCreditAccount(String userId) {
        log.info("account credit queryUserCreditAccount userId:{}", userId);
        try {
            if (StringUtils.isBlank(userId)) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            CreditAccountEntity entity = creditAdjustService.queryUserCreditAccount(userId);
            BigDecimal balance = entity != null ? entity.getAdjustAmount() : BigDecimal.ZERO;
            return Response.<BigDecimal>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(balance)
                    .build();
        } catch (AppException e) {
            log.error("account credit queryUserCreditAccount appException userId:{}", userId, e);
            return Response.<BigDecimal>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("account credit queryUserCreditAccount failed userId:{}", userId, e);
            return Response.<BigDecimal>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private TradeNameVO resolveTradeNameVO(String name) {
        try {
            return TradeNameVO.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "Unknown tradeName: " + name);
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
