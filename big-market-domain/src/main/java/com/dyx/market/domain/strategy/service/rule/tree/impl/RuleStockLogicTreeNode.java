package com.dyx.market.domain.strategy.service.rule.tree.impl;

import com.dyx.market.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.domain.strategy.service.rule.tree.ILogicTreeNode;
import com.dyx.market.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Date;

/**
 * 策略规则树 — 库存预占节点。
 * <p>控制流与直觉相反：预占成功返回 {@code TAKE_OVER}（终止规则树、锁定当前奖品），
 * 预占失败返回 {@code ALLOW}（继续遍历，通常落到 luck-award 兜底奖品）。</p>
 * <p>{@code orderId} 即库存 reservationId；后续由 {@code RaffleApplicationService} 在抽奖落库后
 * 触发 confirm/release saga（见 {@code StrategyAwardStockConfirmJob}）。</p>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-01-27 11:25
 */
@Slf4j
@Component("rule_stock")
public class RuleStockLogicTreeNode implements ILogicTreeNode {

    @Resource
    private IStrategyRepository strategyRepository;

    /**
     * 尝试预占有限库存奖品。
     * <ul>
     *   <li>成功 → {@code TAKE_OVER}，携带 {@code stockReserved=true} 与 reservation 信息</li>
     *   <li>不足 → {@code ALLOW}，决策树继续下一节点（兜底发奖）</li>
     * </ul>
     */
    @Override
    public DefaultTreeFactory.TreeActionEntity logic(String userId, Long strategyId, Integer awardId, String ruleValue, Date endDateTime, String orderId) {
        log.info("规则过滤-库存扣减 userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);
        StrategyAwardStockKeyVO reservation = strategyRepository.reserveAwardStock(strategyId, awardId, endDateTime, orderId);
        if (null != reservation) {
            log.info("规则过滤-库存扣减-成功 userId:{} strategyId:{} awardId:{} reservationId:{}", userId, strategyId, awardId, reservation.getReservationId());

            return DefaultTreeFactory.TreeActionEntity.builder()
                    .ruleLogicCheckType(RuleLogicCheckTypeVO.TAKE_OVER)
                    .strategyAwardVO(DefaultTreeFactory.StrategyAwardVO.builder()
                            .awardId(awardId)
                            .awardRuleValue(ruleValue)
                            .stockReserved(true)
                            .stockReservation(reservation)
                            .build())
                    .build();
        }

        log.warn("规则过滤-库存扣减-告警，库存不足。userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);
        return DefaultTreeFactory.TreeActionEntity.builder()
                .ruleLogicCheckType(RuleLogicCheckTypeVO.ALLOW)
                .build();
    }

}
