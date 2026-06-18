package com.dyx.market.trigger.rpc;

import com.dyx.market.trigger.api.IRaffleStrategyService;
import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.http.RaffleStrategyController;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import javax.annotation.Resource;
import java.util.List;

/**
 * market-service 内置的默认抽奖策略 Dubbo 提供者。
 * <p>
 * HTTP Controller 保持注册的同时，通过 {@code strategy.embedded-rpc-provider.enabled}
 * 控制是否对外暴露 Dubbo 接口，便于本地单体与服务化模式切换。
 */
@DubboService(version = "1.0")
@ConditionalOnProperty(name = "strategy.embedded-rpc-provider.enabled", havingValue = "true", matchIfMissing = true)
public class RaffleStrategyServiceRPC implements IRaffleStrategyService {

    // Dubbo 入口复用 HTTP Controller，避免重复实现业务逻辑
    @Resource
    private RaffleStrategyController raffleStrategyController;

    @Override
    public Response<Boolean> strategyArmory(Long strategyId) {
        return raffleStrategyController.strategyArmory(strategyId);
    }

    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardListByToken(String token, RaffleAwardListRequestDTO request) {
        return raffleStrategyController.queryRaffleAwardListByToken(token, request);
    }

    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        return raffleStrategyController.queryRaffleAwardList(request);
    }

    @Override
    public Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request) {
        return raffleStrategyController.queryRaffleStrategyRuleWeight(request);
    }

    @Override
    public Response<RaffleStrategyResponseDTO> randomRaffle(RaffleStrategyRequestDTO requestDTO) {
        return raffleStrategyController.randomRaffle(requestDTO);
    }

}
