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
 * default strategy Dubbo provider hosted by market-service.
 *
 * Keeps the HTTP controller registered while making the default Dubbo provider
 * configurable for local service-oriented runs.
 */
@DubboService(version = "1.0")
@ConditionalOnProperty(name = "strategy.embedded-rpc-provider.enabled", havingValue = "true", matchIfMissing = true)
public class RaffleStrategyServiceRPC implements IRaffleStrategyService {

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
