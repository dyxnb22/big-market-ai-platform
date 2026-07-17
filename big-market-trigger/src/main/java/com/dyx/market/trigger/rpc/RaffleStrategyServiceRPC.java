package com.dyx.market.trigger.rpc;

import com.dyx.market.trigger.api.IRaffleStrategyService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.application.RaffleStrategyQueryApplicationService;
import com.dyx.market.trigger.http.TriggerApiResponses;
import com.dyx.market.trigger.support.AuthenticatedUserSupport;
import com.dyx.market.trigger.support.DubboRpcAuthSupport;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.List;

/**
 * {@link IRaffleStrategyService} 的 Dubbo Provider 实现：策略奖品列表等只读查询。
 *
 * <p>委托 {@link RaffleStrategyQueryApplicationService} 编排应用服务，响应统一封装为 {@link Response}。</p>
 */
@DubboService(version = "1.0")
public class RaffleStrategyServiceRPC implements IRaffleStrategyService {

    @Resource
    private RaffleStrategyQueryApplicationService raffleStrategyQueryApplicationService;
    @Resource
    private AuthenticatedUserSupport authenticatedUserSupport;

    @Override
    public Response<Boolean> strategyArmory(Long strategyId) {
        DubboRpcAuthSupport.rejectInternalRpc("strategyArmory");
        throw new AssertionError("unreachable");
    }

    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardListByToken(
            String token, RaffleAwardListRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return TriggerApiResponses.ok(raffleStrategyQueryApplicationService.queryRaffleAwardList(request));
    }

    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        DubboRpcAuthSupport.rejectInternalRpc("queryRaffleAwardList");
        throw new AssertionError("unreachable");
    }

    @Override
    public Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(
            RaffleStrategyRuleWeightRequestDTO request) {
        DubboRpcAuthSupport.rejectInternalRpc("queryRaffleStrategyRuleWeight");
        throw new AssertionError("unreachable");
    }

    @Override
    public Response<RaffleStrategyResponseDTO> randomRaffle(RaffleStrategyRequestDTO requestDTO) {
        DubboRpcAuthSupport.rejectInternalRpc("randomRaffle");
        throw new AssertionError("unreachable");
    }
}
