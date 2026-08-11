package com.dyx.market.domain.strategy.service.rule.tree.impl;

import com.dyx.market.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.dyx.market.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * 策略规则树 — 次数锁节点。
 * <p>语义：用户当日抽奖次数 <b>达到</b> {@code ruleValue} 后才放行该奖品，否则拦截。
 * 因此 {@code userRaffleCount >= raffleCount} 返回 {@code ALLOW}，未达到则 {@code TAKE_OVER}。</p>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-01-27 11:22
 */
@Slf4j
@Component("rule_lock")
public class RuleLockLogicTreeNode implements ILogicTreeNode {

    @Resource
    private IStrategyRepository repository;

    /**
     * 按当日抽奖次数判断是否解锁当前奖品。
     *
     * @param ruleValue 解锁所需的最小抽奖次数（含）
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId, String ruleValue, Date endDateTime, String orderId) {
        log.info("规则过滤-次数锁 userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);

        long raffleCount = 0L;
        try {
            raffleCount = Long.parseLong(ruleValue);
        } catch (Exception e) {
            throw new RuntimeException("规则过滤-次数锁异常 ruleValue: " + ruleValue + " 配置不正确");
        }

        // 查询用户抽奖次数 - 当天的；策略ID:活动ID 1:1 的配置，可以直接用 strategyId 查询。
        Integer userRaffleCount = repository.queryTodayUserRaffleCount(userId, strategyId);

        // 用户抽奖次数大于规则限定值，规则放行
        if (userRaffleCount >= raffleCount) {
            log.info("规则过滤-次数锁【放行】 userId:{} strategyId:{} awardId:{} raffleCount:{} userRaffleCount:{}", userId, strategyId, awardId, userRaffleCount, userRaffleCount);
            return DefaultTreeFactory.TreeActionEntity.builder()
                    .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                    .build();
        }

        log.info("规则过滤-次数锁【拦截】 userId:{} strategyId:{} awardId:{} raffleCount:{} userRaffleCount:{}", userId, strategyId, awardId, userRaffleCount, userRaffleCount);

        // 用户抽奖次数小于规则限定值，规则拦截
        return DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                .build();
    }

}
