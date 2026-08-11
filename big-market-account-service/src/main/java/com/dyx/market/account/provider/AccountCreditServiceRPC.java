package com.dyx.market.account.provider;

import com.dyx.market.account.application.AccountCreditApplicationService;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.dto.CreditOrderResponseDTO;
import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.api.support.ApiResponses;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

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

    /** 创建积分交易；account 侧按 outBusinessNo 做唯一性和幂等保护。 */
    @Override
    public Response<String> createOrder(CreditTradeRequestDTO request) {
        if (request == null) {
            return ApiResponses.of(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo(), null);
        }
        log.info("account credit createOrder userId:{}", request.getUserId());
        return ApiResponses.execute(() -> accountCreditApplicationService.createOrder(request));
    }

    /** 查询账户余额。 */
    @Override
    public Response<BigDecimal> queryUserCreditAccount(String userId) {
        log.info("account credit queryUserCreditAccount userId:{}", userId);
        return ApiResponses.execute(() -> accountCreditApplicationService.queryUserCreditAccount(userId));
    }

    /** 供远程 UNKNOWN 结果探测积分订单是否已经落库。 */
    @Override
    public Response<Boolean> existsCreditOrder(String userId, String outBusinessNo) {
        return ApiResponses.execute(() -> accountCreditApplicationService.existsCreditOrder(userId, outBusinessNo));
    }

    /** 查询用户积分流水（积分账本展示）。 */
    @Override
    public Response<List<CreditOrderResponseDTO>> queryUserCreditOrders(String userId, int limit) {
        log.info("account credit queryUserCreditOrders userId:{} limit:{}", userId, limit);
        return ApiResponses.execute(() -> accountCreditApplicationService.queryUserCreditOrders(userId, limit));
    }
}
