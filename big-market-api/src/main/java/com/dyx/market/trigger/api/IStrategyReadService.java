package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.trigger.api.response.Response;

import java.util.List;

/**
 * 策略限界上下文只读 Dubbo 契约。
 *
 * <p>由 big-market-strategy-service 导出的窄只读面。
 * 抽奖执行与库存变更不在此接口暴露——这些路径仍留在 market-service。</p>
 */
public interface IStrategyReadService {

    /**
     * 按活动查询奖品列表（activityId → strategyId 映射）。
     * 等价于 HTTP GET /raffle/strategy/query_raffle_award_list。
     *
     * @param request userId + activityId
     * @return 含锁定次数元数据的策略奖品有序列表
     */
    Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO request);

    /**
     * 查询指定活动的规则权重解锁进度。
     * 等价于 HTTP GET /raffle/strategy/query_raffle_strategy_rule_weight。
     *
     * @param request userId + activityId
     * @return 权重规则列表及用户当前解锁次数
     */
    Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request);

}
