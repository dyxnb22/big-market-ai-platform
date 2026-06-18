package com.dyx.market.trigger.adapter;

import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;

import java.util.List;

/**
 * 抽奖策略读查询适配器契约。
 * <p>
 * 对应 IStrategyReadService 的两个读方法。本地实现委托 IRaffleAward、IRaffleRule 与 IAccountReadAdapter；
 * 远程实现（market-service 中的 StrategyRemoteReadAdapter）在
 * {@code strategy.service.remote-read.enabled=true} 时经 Dubbo 代理 big-market-strategy-service。
 * 引入此契约后，RaffleStrategyController 不再直接调用策略领域服务处理两个纯读接口。
 */
public interface IStrategyReadAdapter {

    /**
     * 查询指定活动与用户的奖品列表及解锁状态。
     *
     * @param request userId + activityId
     * @return 有序奖品列表，含锁定次数元数据
     */
    List<RaffleAwardListResponseDTO> queryRaffleAwardList(RaffleAwardListRequestDTO request);

    /**
     * 查询指定活动与用户的规则权重解锁进度。
     *
     * @param request userId + activityId
     * @return 权重规则列表及用户当前解锁次数
     */
    List<RaffleStrategyRuleWeightResponseDTO> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request);

}
