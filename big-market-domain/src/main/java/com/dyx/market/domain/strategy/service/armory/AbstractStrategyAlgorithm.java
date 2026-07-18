package com.dyx.market.domain.strategy.service.armory;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.entity.StrategyEntity;
import com.dyx.market.domain.strategy.model.entity.StrategyRuleEntity;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖策略算法抽象类
 * @create 2024-08-24 10:28
 */
public abstract class AbstractStrategyAlgorithm implements IStrategyArmory, IStrategyDispatch {

    @Resource
    protected IStrategyRepository repository;

    protected final SecureRandom secureRandom = new SecureRandom();

    @Override
    public boolean assembleLotteryStrategyByActivityId(Long activityId) {
        Long strategyId = repository.queryStrategyIdByActivityId(activityId);
        return assembleLotteryStrategy(strategyId);
    }

    @Override
    public boolean assembleLotteryStrategy(Long strategyId) {
        // 1. 查询策略配置
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);

        // 2. 缓存奖品库存【用于decr扣减库存使用】
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardId = strategyAward.getAwardId();
            Integer awardCount = strategyAward.getAwardCountSurplus();
            cacheStrategyAwardCount(strategyId, awardId, awardCount);
        }

        // 3.1 默认装配配置【全量抽奖概率】
        armoryAlgorithm(String.valueOf(strategyId), strategyAwardEntities);

        // 3.2 权重策略配置 - 适用于 rule_weight 权重规则配置【4000:102,103,104,105 5000:102,103,104,105,106,107 6000:102,103,104,105,106,107,108,109】
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        String ruleWeight = strategyEntity.getRuleWeight();
        if (null == ruleWeight) return true;

        StrategyRuleEntity strategyRuleEntity = repository.queryStrategyRule(strategyId, ruleWeight);
        // 业务异常，策略规则中 rule_weight 权重规则已适用但未配置
        if (null == strategyRuleEntity) {
            throw new AppException(ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getCode(), ResponseCode.STRATEGY_RULE_WEIGHT_IS_NULL.getInfo());
        }

        Map<String, List<Integer>> ruleWeightValueMap = strategyRuleEntity.getRuleWeightValues();
        for (String key : ruleWeightValueMap.keySet()) {
            List<Integer> ruleWeightValues = ruleWeightValueMap.get(key);
            ArrayList<StrategyAwardEntity> strategyAwardEntitiesClone = new ArrayList<>(strategyAwardEntities);
            strategyAwardEntitiesClone.removeIf(entity -> !ruleWeightValues.contains(entity.getAwardId()));
            armoryAlgorithm(String.valueOf(strategyId).concat(Constants.UNDERLINE).concat(key), strategyAwardEntitiesClone);
        }

        return true;
    }

    /**
     * 装配算法
     * @param key 为策略ID、权重ID
     * @param strategyAwardEntities 对应的奖品概率
     */
    protected abstract void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities);

    /**
     * 缓存奖品库存到Redis
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @param awardCount 奖品库存
     */
    private void cacheStrategyAwardCount(Long strategyId, Integer awardId, Integer awardCount) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        repository.cacheStrategyAwardCount(cacheKey, awardCount);
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        return dispatchAlgorithm(String.valueOf(strategyId));
    }

    @Override
    public Integer getRandomAwardId(Long strategyId, String ruleWeightValue) {
        String key = String.valueOf(strategyId).concat(Constants.UNDERLINE).concat(ruleWeightValue);
        return getRandomAwardId(key);
    }

    @Override
    public Integer getRandomAwardId(String key) {
        return dispatchAlgorithm(key);
    }

    protected abstract Integer dispatchAlgorithm(String key);

    @Override
    public Boolean subtractionAwardStock(Long strategyId, Integer awardId, Date endDateTime) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        return repository.subtractionAwardStock(cacheKey, endDateTime);
    }

    // 概率最小值
    protected BigDecimal minAwardRate(List<StrategyAwardEntity> strategyAwardEntities){
        return strategyAwardEntities.stream()
                .map(StrategyAwardEntity::getAwardRate)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    // 概率范围值，百分位、千分位、万分位
    protected double convert(double min) {
        return convert(BigDecimal.valueOf(min));
    }

    // 概率范围值，百分位、千分位、万分位
    protected double convert(BigDecimal min) {
        if (BigDecimal.ZERO.compareTo(min) == 0) return 1D;

        String minStr = min.stripTrailingZeros().toPlainString();
        int dot = minStr.indexOf(".");
        if (dot < 0) {
            // 整数概率值，无小数部分
            return Math.pow(10, minStr.length());
        }

        // 小数点前
        String beginVale = minStr.substring(0, dot);
        int beginLength = 0;
        if (Long.parseLong(beginVale) > 0) {
            beginLength = beginVale.length();
        }

        // 小数点后
        String endValue = minStr.substring(dot + 1);
        int endLength = 0;
        if (Long.parseLong(endValue) > 0) {
            endLength = endValue.length();
        }

        return Math.pow(10, beginLength + endLength);
    }

}
