package com.dyx.market.trigger.http;

import com.dyx.market.trigger.api.IRaffleStrategyService;
import com.dyx.market.trigger.api.dto.*;
import com.dyx.market.trigger.application.RaffleStrategyArmoryApplicationService;
import com.dyx.market.trigger.application.RaffleStrategyDrawApplicationService;
import com.dyx.market.trigger.application.RaffleStrategyQueryApplicationService;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.support.AuthenticatedUserSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/strategy/")
public class RaffleStrategyController implements IRaffleStrategyService {

    @Resource
    private AuthenticatedUserSupport authenticatedUserSupport;
    @Resource
    private RaffleStrategyArmoryApplicationService raffleStrategyArmoryApplicationService;
    @Resource
    private RaffleStrategyQueryApplicationService raffleStrategyQueryApplicationService;
    @Resource
    private RaffleStrategyDrawApplicationService raffleStrategyDrawApplicationService;

    @GetMapping("strategy_armory")
    @Override
    public Response<Boolean> strategyArmory(@RequestParam Long strategyId) {
        return TriggerApiResponses.ok(raffleStrategyArmoryApplicationService.strategyArmory(strategyId));
    }

    @PostMapping("query_raffle_award_list_by_token")
    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardListByToken(
            @RequestHeader("Authorization") String token,
            @RequestBody RaffleAwardListRequestDTO request) {
        request.setUserId(authenticatedUserSupport.requireUserId(token));
        return queryRaffleAwardList(request);
    }

    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(@RequestBody RaffleAwardListRequestDTO request) {
        return TriggerApiResponses.ok(raffleStrategyQueryApplicationService.queryRaffleAwardList(request));
    }

    @Override
    public Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(
            @RequestBody RaffleStrategyRuleWeightRequestDTO request) {
        return TriggerApiResponses.ok(raffleStrategyQueryApplicationService.queryRaffleStrategyRuleWeight(request));
    }

    @Override
    public Response<RaffleStrategyResponseDTO> randomRaffle(@RequestBody RaffleStrategyRequestDTO requestDTO) {
        return TriggerApiResponses.ok(raffleStrategyDrawApplicationService.randomRaffle(requestDTO));
    }
}
