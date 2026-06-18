package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.CreditTradeRequestDTO;
import com.dyx.market.trigger.api.response.Response;

import java.math.BigDecimal;

/**
 * 跨服务 Dubbo 接口：积分账户操作。
 *
 * <p>接口定义在本模块；Provider 实现在 big-market-account-service。
 * market-service、message-job-service 现有调用方仍走进程内领域服务，尚未默认路由到远程。</p>
 */
public interface IAccountCreditService {

    /**
     * 创建积分交易订单（赚取或消费积分）。
     *
     * @param request 交易参数：userId、tradeName、tradeType、amount、outBusinessNo
     * @return 成功时返回 orderId
     */
    Response<String> createOrder(CreditTradeRequestDTO request);

    /**
     * 查询用户当前可用积分余额。
     *
     * @param userId 用户 ID
     * @return 可用积分金额
     */
    Response<BigDecimal> queryUserCreditAccount(String userId);

}
