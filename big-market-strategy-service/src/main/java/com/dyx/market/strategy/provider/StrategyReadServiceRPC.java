package com.dyx.market.strategy.provider;

import com.alibaba.fastjson2.JSON;
import com.dyx.market.strategy.application.StrategyReadApplicationService;
import com.dyx.market.trigger.api.IStrategyReadService;
import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.trigger.api.support.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@DubboService(version = "1.0")
public class StrategyReadServiceRPC implements IStrategyReadService {

    @Resource
    private StrategyReadApplicationService strategyReadApplicationService;

    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        String userId = request == null ? null : request.getUserId();
        Long activityId = request == null ? null : request.getActivityId();
        log.info("策略读服务-查询奖品列表开始 userId:{} activityId:{}", userId, activityId);
        Response<List<RaffleAwardListResponseDTO>> response =
                ApiResponses.execute(() -> strategyReadApplicationService.queryRaffleAwardList(request));
        log.info("策略读服务-查询奖品列表完成 userId:{} activityId:{} response:{}", userId, activityId, JSON.toJSONString(response));
        return response;
    }

    @Override
    public Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(
            RaffleStrategyRuleWeightRequestDTO request) {
        String userId = request == null ? null : request.getUserId();
        Long activityId = request == null ? null : request.getActivityId();
        log.info("策略读服务-查询规则权重开始 userId:{} activityId:{}", userId, activityId);
        Response<List<RaffleStrategyRuleWeightResponseDTO>> response =
                ApiResponses.execute(() -> strategyReadApplicationService.queryRaffleStrategyRuleWeight(request));
        log.info("策略读服务-查询规则权重完成 userId:{} activityId:{} response:{}", userId, activityId, JSON.toJSONString(response));
        return response;
    }
}
