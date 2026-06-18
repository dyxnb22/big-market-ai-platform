package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;

/**
 * 领域端口：抽奖流程中的策略决策步骤。
 * <p>
 * RaffleApplicationService 原先直接注入 IRaffleStrategy；本端口是可配置的路由接缝，
 * 可在进程内调用与远程 Dubbo 调用之间切换，而不改动编排器。
 * <p>
 * 本地路径（默认）：LocalStrategyDecisionPort 委托现有 IRaffleStrategy Bean，行为与直接调用一致。
 * 远程路径：可配置 StrategyRemoteDecisionPort 对接远程策略决策服务。
 */
public interface IStrategyDecisionPort {

    /**
     * 对已确认的抽奖参与执行策略决策。
     * <p>
     * 语义与 IRaffleStrategy.performRaffle 一致：执行规则链与规则树、扣减 Redis 奖品库存并返回中奖结果。
     * Redis 库存扣减在本批次内不可逆，此处勿添加重试或补偿逻辑。
     */
    RaffleAwardEntity performRaffle(RaffleFactorEntity factor);

}
