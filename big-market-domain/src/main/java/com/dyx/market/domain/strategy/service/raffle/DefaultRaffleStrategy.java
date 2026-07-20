package com.dyx.market.domain.strategy.service.raffle;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.valobj.RuleTreeVO;
import com.dyx.market.domain.strategy.model.valobj.RuleWeightVO;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardRuleModelVO;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.domain.strategy.service.AbstractRaffleStrategy;
import com.dyx.market.domain.strategy.service.IRaffleAward;
import com.dyx.market.domain.strategy.service.IRaffleRule;
import com.dyx.market.domain.strategy.service.IRaffleStock;
import com.dyx.market.domain.strategy.service.armory.IStrategyDispatch;
import com.dyx.market.domain.strategy.service.rule.chain.ILogicChain;
import com.dyx.market.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.dyx.market.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import com.dyx.market.domain.strategy.service.rule.tree.factory.engine.IDecisionTreeEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 默认抽奖策略实现。
 *
 * <p>策略本身只负责打开责任链/规则树、读取库存与规则数据；库存预占的确认或释放由
 * 上层抽奖应用服务和补偿任务负责，避免策略层吞掉跨聚合状态。</p>
 */
@Slf4j
@Service
public class DefaultRaffleStrategy extends AbstractRaffleStrategy implements IRaffleStock, IRaffleAward, IRaffleRule {

    public DefaultRaffleStrategy(IStrategyRepository repository, IStrategyDispatch strategyDispatch, DefaultChainFactory defaultChainFactory, DefaultTreeFactory defaultTreeFactory) {
        super(repository, strategyDispatch, defaultChainFactory, defaultTreeFactory);
    }

    /** 执行策略责任链，完成黑名单、权重等前置规则筛选。 */
    @Override
    public DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId) {
        log.info("抽奖策略-责任链 userId:{} strategyId:{}", userId, strategyId);
        ILogicChain logicChain = defaultChainFactory.openLogicChain(strategyId);
        return logicChain.logic(userId, strategyId);
    }

    /** 使用指定奖品的规则树进行二次校验。 */
    @Override
    public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId) {
        return raffleLogicTree(userId, strategyId, awardId, null, null);
    }

    /** 取出全局库存队列中的一条预占结果。 */
    @Override
    public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId, Date endDateTime, String orderId) {
        StrategyAwardRuleModelVO strategyAwardRuleModelVO = repository.queryStrategyAwardRuleModelVO(strategyId, awardId);
        if (null == strategyAwardRuleModelVO) {
            return DefaultTreeFactory.StrategyAwardVO.builder().awardId(awardId).build();
        }
        log.info("抽奖策略-规则树 userId:{} strategyId:{} awardId:{}", userId, strategyId, awardId);
        RuleTreeVO ruleTreeVO = repository.queryRuleTreeVOByTreeId(strategyAwardRuleModelVO.getRuleModels());
        if (null == ruleTreeVO) {
            throw new RuntimeException("存在抽奖策略配置的规则模型 Key，未在库表 rule_tree、rule_tree_node、rule_tree_line 配置对应的规则树信息 " + strategyAwardRuleModelVO.getRuleModels());
        }
        IDecisionTreeEngine treeEngine = defaultTreeFactory.openLogicTree(ruleTreeVO);
        return treeEngine.process(userId, strategyId, awardId, endDateTime, orderId);
    }

    /** 取出指定策略奖品的库存预占结果。 */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException {
        return repository.takeQueueValue();
    }

    /** 刷新指定奖品的 Redis 库存计数。 */
    @Override
    public StrategyAwardStockKeyVO takeQueueValue(Long strategyId, Integer awardId) throws InterruptedException {
        return repository.takeQueueValue(strategyId, awardId);
    }

    /** 只执行一次库存刷新，供补偿任务使用。 */
    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        repository.updateStrategyAwardStock(strategyId, awardId);
    }

    /** 将库存队列中的结果同步回持久化库存。 */
    @Override
    public void updateStrategyAwardStockOnce(StrategyAwardStockKeyVO stockKey) {
        repository.updateStrategyAwardStockOnce(stockKey);
    }

    /** 查询策略下的奖品列表。 */
    @Override
    public void syncStrategyAwardStockFromQueue(Long strategyId, Integer awardId) {
        repository.syncStrategyAwardStockFromQueue(strategyId, awardId);
    }

    /** 通过活动反查策略并查询奖品列表。 */
    @Override
    public List<StrategyAwardEntity> queryRaffleStrategyAwardList(Long strategyId) {
        return repository.queryStrategyAwardList(strategyId);
    }

    /** 查询当前已上架活动的奖品库存键。 */
    @Override
    public List<StrategyAwardEntity> queryRaffleStrategyAwardListByActivityId(Long activityId) {
        Long strategyId = repository.queryStrategyIdByActivityId(activityId);
        return queryRaffleStrategyAwardList(strategyId);
    }

    /** 查询规则树中各奖品的锁定数量。 */
    @Override
    public List<StrategyAwardStockKeyVO> queryOpenActivityStrategyAwardList() {
        return repository.queryOpenActivityStrategyAwardList();
    }

    /** 查询策略权重规则。 */
    @Override
    public Map<String, Integer> queryAwardRuleLockCount(String[] treeIds) {
        return repository.queryAwardRuleLockCount(treeIds);
    }

    /** 通过活动反查策略并查询权重规则。 */
    @Override
    public List<RuleWeightVO> queryAwardRuleWeight(Long strategyId) {
        return repository.queryAwardRuleWeight(strategyId);
    }

    @Override
    public List<RuleWeightVO> queryAwardRuleWeightByActivityId(Long activityId) {
        Long strategyId = repository.queryStrategyIdByActivityId(activityId);
        return queryAwardRuleWeight(strategyId);
    }

}
