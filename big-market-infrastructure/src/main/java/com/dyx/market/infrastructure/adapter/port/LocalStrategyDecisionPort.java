package com.dyx.market.infrastructure.adapter.port;

import com.dyx.market.domain.activity.adapter.port.IStrategyDecisionPort;
import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.domain.strategy.service.IRaffleStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * {@link IStrategyDecisionPort} 的本地（进程内）实现。
 *
 * <p>直接委托给现有 {@code IRaffleStrategy} Bean，行为与 5-D 之前
 * {@code RaffleApplicationService} 中的直接注入完全一致：无网络跳转，无行为变更。</p>
 *
 * <p>激活条件：默认通过 {@code @ConditionalOnMissingBean} 生效——若未注册其他
 * {@code IStrategyDecisionPort} Bean，则使用本实现。当
 * {@code strategy.service.remote-decision.enabled=true} 时，
 * big-market-market-service 中配置的远程实现（由该开关守卫）将优先生效；
 * 该远程实现为文档化的扩展点。</p>
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IStrategyDecisionPort.class)
public class LocalStrategyDecisionPort implements IStrategyDecisionPort {

    @Resource
    private IRaffleStrategy raffleStrategy;

    @Override
    public RaffleAwardEntity performRaffle(RaffleFactorEntity factor) {
        log.debug("[LocalStrategyDecisionPort] performRaffle userId:{} strategyId:{}",
                factor.getUserId(), factor.getStrategyId());
        return raffleStrategy.performRaffle(factor);
    }

}
