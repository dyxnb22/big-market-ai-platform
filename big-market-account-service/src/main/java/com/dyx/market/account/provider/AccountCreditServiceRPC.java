package com.dyx.market.account.provider;

import com.dyx.market.account.application.AccountCreditApplicationService;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.api.support.ApiResponses;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import jakarta.annotation.Resource;
import java.math.BigDecimal;

/**
 * {@link IAccountCreditService} 的 Dubbo Provider 实现：积分账户操作。
 *
 * <p>委托 {@link AccountCreditApplicationService} 编排领域服务，响应统一封装为 {@link Response}。</p>
 */
@Slf4j
@DubboService(version = "1.0")
public class AccountCreditServiceRPC implements IAccountCreditService {

    @Resource
    private AccountCreditApplicationService accountCreditApplicationService;

    @Override
    public Response<String> createOrder(CreditTradeRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("account credit createOrder userId:{}", request.getUserId());
        return ApiResponses.execute(() -> accountCreditApplicationService.createOrder(request));
    }

    @Override
    public Response<BigDecimal> queryUserCreditAccount(String userId) {
        log.info("account credit queryUserCreditAccount userId:{}", userId);
        return ApiResponses.execute(() -> accountCreditApplicationService.queryUserCreditAccount(userId));
    }

    @Override
    public Response<Boolean> existsCreditOrder(String userId, String outBusinessNo) {
        return ApiResponses.execute(() -> accountCreditApplicationService.existsCreditOrder(userId, outBusinessNo));
    }
}
