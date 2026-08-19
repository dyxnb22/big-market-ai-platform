package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.strategy.adapter.port.IStrategyActivityAccountPort;
import com.dyx.market.domain.strategy.adapter.port.IStrategyActivityMappingPort;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.entity.StrategyEntity;
import com.dyx.market.domain.strategy.model.entity.StrategyRuleEntity;
import com.dyx.market.domain.strategy.model.valobj.*;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.infrastructure.dao.*;
import com.dyx.market.infrastructure.dao.po.*;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 策略服务仓储实现
 * @create 2023-12-23 10:33
 */
@Slf4j
@Repository
public class StrategyRepository implements IStrategyRepository {

    @Resource
    private IStrategyActivityMappingPort strategyActivityMappingPort;
    @Resource
    private IStrategyDao strategyDao;
    @Resource
    private IStrategyRuleDao strategyRuleDao;
    @Resource
    private IStrategyAwardDao strategyAwardDao;
    @Lazy
    @Resource
    private IStrategyActivityAccountPort strategyActivityAccountPort;
    @Resource
    private IRedisService redisService;
    @Resource
    private StrategyAwardCacheSupport strategyAwardCacheSupport;
    @Resource
    private StrategyRuleTreeSupport strategyRuleTreeSupport;

    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        return strategyAwardCacheSupport.queryStrategyAwardList(strategyId);
    }

    @Override
    public <K, V> void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<K, V> strategyAwardSearchRateTable) {
        strategyAwardCacheSupport.storeStrategyAwardSearchRateTable(key, rateRange, strategyAwardSearchRateTable);
    }

    @Override
    public <K, V> Map<K, V> getMap(String key) {
        return strategyAwardCacheSupport.getMap(key);
    }

    @Override
    public Integer getStrategyAwardAssemble(String key, Integer rateKey) {
        return strategyAwardCacheSupport.getStrategyAwardAssemble(key, rateKey);
    }

    @Override
    public int getRateRange(Long strategyId) {
        return strategyAwardCacheSupport.getRateRange(strategyId);
    }

    @Override
    public int getRateRange(String key) {
        return strategyAwardCacheSupport.getRateRange(key);
    }

    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_KEY + strategyId;
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (null != strategyEntity) return strategyEntity;
        Strategy strategy = strategyDao.queryStrategyByStrategyId(strategyId);
        if (null == strategy) return StrategyEntity.builder().build();
        strategyEntity = StrategyEntity.builder()
                .strategyId(strategy.getStrategyId())
                .strategyDesc(strategy.getStrategyDesc())
                .ruleModels(strategy.getRuleModels())
                .build();
        redisService.setValue(cacheKey, strategyEntity);
        return strategyEntity;
    }

    @Override
    public StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel) {
        StrategyRule strategyRuleReq = new StrategyRule();
        strategyRuleReq.setStrategyId(strategyId);
        strategyRuleReq.setRuleModel(ruleModel);
        StrategyRule strategyRuleRes = strategyRuleDao.queryStrategyRule(strategyRuleReq);
        if (null == strategyRuleRes) return null;
        return StrategyRuleEntity.builder()
                .strategyId(strategyRuleRes.getStrategyId())
                .awardId(strategyRuleRes.getAwardId())
                .ruleType(strategyRuleRes.getRuleType())
                .ruleModel(strategyRuleRes.getRuleModel())
                .ruleValue(strategyRuleRes.getRuleValue())
                .ruleDesc(strategyRuleRes.getRuleDesc())
                .build();
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, String ruleModel) {
        return queryStrategyRuleValue(strategyId, null, ruleModel);
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel) {
        StrategyRule strategyRule = new StrategyRule();
        strategyRule.setStrategyId(strategyId);
        strategyRule.setAwardId(awardId);
        strategyRule.setRuleModel(ruleModel);
        return strategyRuleDao.queryStrategyRuleValue(strategyRule);
    }

    @Override
    public StrategyAwardRuleModelVO queryStrategyAwardRuleModelVO(Long strategyId, Integer awardId) {
        StrategyAward strategyAward = new StrategyAward();
        strategyAward.setStrategyId(strategyId);
        strategyAward.setAwardId(awardId);
        String ruleModels = strategyAwardDao.queryStrategyAwardRuleModels(strategyAward);
        if (null == ruleModels) return null;
        return StrategyAwardRuleModelVO.builder().ruleModels(ruleModels).build();
    }

    @Override
    public RuleTreeVO queryRuleTreeVOByTreeId(String treeId) {
        return strategyRuleTreeSupport.queryRuleTreeVOByTreeId(treeId);
    }

    @Override
    public void cacheStrategyAwardCount(String cacheKey, Integer awardCount) {
        strategyAwardCacheSupport.cacheStrategyAwardCount(cacheKey, awardCount);
    }

    @Override
    public Boolean subtractionAwardStock(String cacheKey) {
        return strategyAwardCacheSupport.subtractionAwardStock(cacheKey);
    }

    @Override
    public Boolean subtractionAwardStock(String cacheKey, Date endDateTime) {
        return strategyAwardCacheSupport.subtractionAwardStock(cacheKey, endDateTime);
    }

    @Override
    public void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO) {
        strategyAwardCacheSupport.awardStockConsumeSendQueue(strategyAwardStockKeyVO);
    }

    @Override
    public StrategyAwardStockKeyVO reserveAwardStock(Long strategyId, Integer awardId, Date endDateTime, String reservationId) {
        return strategyAwardCacheSupport.reserveStock(strategyId, awardId, endDateTime, reservationId);
    }

    @Override
    public void confirmAwardStockReservation(StrategyAwardStockKeyVO reservation) {
        strategyAwardCacheSupport.confirmReservation(reservation);
    }

    @Override
    public void releaseAwardStockReservation(StrategyAwardStockKeyVO reservation) {
        strategyAwardCacheSupport.releaseReservation(reservation);
    }

    @Override
    public StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException {
        return strategyAwardCacheSupport.takeQueueValue();
    }

    @Override
    public StrategyAwardStockKeyVO takeQueueValue(Long strategyId, Integer awardId) throws InterruptedException {
        return strategyAwardCacheSupport.takeQueueValue(strategyId, awardId);
    }

    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        strategyAwardCacheSupport.updateStrategyAwardStock(strategyId, awardId);
    }

    @Override
    public void updateStrategyAwardStockOnce(StrategyAwardStockKeyVO stockKey) {
        strategyAwardCacheSupport.updateStrategyAwardStockOnce(stockKey);
    }

    @Override
    public void syncStrategyAwardStockFromQueue(Long strategyId, Integer awardId) {
        strategyAwardCacheSupport.syncStrategyAwardStockFromQueue(strategyId, awardId);
    }

    @Override
    public StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId) {
        return strategyAwardCacheSupport.queryStrategyAwardEntity(strategyId, awardId);
    }

    @Override
    public Long queryStrategyIdByActivityId(Long activityId) {
        return strategyActivityMappingPort.queryStrategyIdByActivityId(activityId);
    }

    @Override
    public Integer queryTodayUserRaffleCount(String userId, Long strategyId) {
        Long activityId = strategyActivityMappingPort.queryActivityIdByStrategyId(strategyId);
        return strategyActivityAccountPort.queryTodayRaffleCount(userId, activityId);
    }

    @Override
    public Map<String, Integer> queryAwardRuleLockCount(String[] treeIds) {
        return strategyRuleTreeSupport.queryAwardRuleLockCount(treeIds);
    }

    @Override
    public Integer queryActivityAccountTotalUseCount(String userId, Long strategyId) {
        Long activityId = strategyActivityMappingPort.queryActivityIdByStrategyId(strategyId);
        return strategyActivityAccountPort.queryTotalUseCount(userId, activityId);
    }

    @Override
    public List<RuleWeightVO> queryAwardRuleWeight(Long strategyId) {
        return strategyRuleTreeSupport.queryAwardRuleWeight(strategyId);
    }

    @Override
    public List<StrategyAwardStockKeyVO> queryOpenActivityStrategyAwardList() {
        // 合并 Redis 待刷新登记表，使离线活动仍能消费待刷新库存队列。
        return strategyAwardCacheSupport.queryPendingStrategyAwardStockKeys();
    }

    @Override
    public void cacheStrategyArmoryAlgorithm(String key, String beanName) {
        strategyAwardCacheSupport.cacheStrategyArmoryAlgorithm(key, beanName);
    }

    @Override
    public String queryStrategyArmoryAlgorithmFromCache(String key) {
        return strategyAwardCacheSupport.queryStrategyArmoryAlgorithmFromCache(key);
    }

}
