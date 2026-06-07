package com.dyx.market.domain.strategy.service.armory;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.entity.StrategyEntity;
import com.dyx.market.domain.strategy.model.entity.StrategyRuleEntity;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.domain.strategy.service.armory.algorithm.AbstractAlgorithm;
import com.dyx.market.domain.strategy.service.armory.algorithm.IAlgorithm;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 策略装配库(兵工厂)，负责初始化策略计算
 * @create 2023-12-23 10:02
 */
@Slf4j
@Service("strategyArmoryDispatch")
public class StrategyArmoryDispatch extends AbstractStrategyAlgorithm {

    // 抽奖策略算法
    private final Map<String, IAlgorithm> algorithmMap;
    // 抽奖算法阈值，在多少范围内开始选择不同选择
    private final Integer ALGORITHM_THRESHOLD_VALUE = 10000;

    public StrategyArmoryDispatch(Map<String, IAlgorithm> algorithmMap) {
        this.algorithmMap = algorithmMap;
    }

    @Override
    protected void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities) {
        // 1. 概率最小值
        BigDecimal minAwardRate = minAwardRate(strategyAwardEntities);
        // 2. 概率范围值
        double rateRange = convert(BigDecimal.valueOf(minAwardRate.doubleValue()));
        // 3. 根据概率值范围选择算法
        if (rateRange <= ALGORITHM_THRESHOLD_VALUE) {
            IAlgorithm o1Algorithm = algorithmMap.get(AbstractAlgorithm.Algorithm.O1.getKey());
            o1Algorithm.armoryAlgorithm(key, strategyAwardEntities, new BigDecimal(rateRange));
            repository.cacheStrategyArmoryAlgorithm(key, AbstractAlgorithm.Algorithm.O1.getKey());
        } else {
            IAlgorithm oLogNAlgorithm = algorithmMap.get(AbstractAlgorithm.Algorithm.OLogN.getKey());
            oLogNAlgorithm.armoryAlgorithm(key, strategyAwardEntities, new BigDecimal(rateRange));
            repository.cacheStrategyArmoryAlgorithm(key, AbstractAlgorithm.Algorithm.OLogN.getKey());
        }
    }

    @Override
    protected Integer dispatchAlgorithm(String key) {
        String beanName = repository.queryStrategyArmoryAlgorithmFromCache(key);
        if (null == beanName) throw new RuntimeException("key " + key + " beanName is " + beanName);
        IAlgorithm algorithm = algorithmMap.get(beanName);
        return algorithm.dispatchAlgorithm(key);
    }

}
