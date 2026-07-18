package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.valobj.RuleWeightVO;
import com.dyx.market.domain.strategy.service.IRaffleAward;
import com.dyx.market.domain.strategy.service.IRaffleRule;
import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 抽奖策略读查询的本地进程内实现。
 * <p>
 * 直接在 market-service 进程内执行策略只读查询。
 * 保持 RaffleStrategyController 重构前的读语义：奖品锁定状态来自 IRaffleAward + IRaffleRule，
 * 日/总参与次数来自 IAccountReadAdapter。不经 DubboReference、不依赖功能开关。
 */
@Slf4j
@Component
public class LocalStrategyReadAdapter implements IStrategyReadAdapter {

    @Resource
    private IRaffleAward raffleAward;

    @Resource
    private IRaffleRule raffleRule;

    @Resource
    private IAccountReadAdapter accountReadAdapter;

    @Override
    public List<RaffleAwardListResponseDTO> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        List<StrategyAwardEntity> strategyAwardEntities =
                raffleAward.queryRaffleStrategyAwardListByActivityId(request.getActivityId());

        String[] treeIds = strategyAwardEntities.stream()
                .map(StrategyAwardEntity::getRuleModels)
                .filter(ruleModel -> ruleModel != null && !ruleModel.isEmpty())
                .toArray(String[]::new);

        Map<String, Integer> ruleLockCountMap = raffleRule.queryAwardRuleLockCount(treeIds);

        Integer dayPartakeCount = accountReadAdapter.queryRaffleActivityAccountDayPartakeCount(
                request.getActivityId(), request.getUserId());

        List<RaffleAwardListResponseDTO> result = new ArrayList<>(strategyAwardEntities.size());
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardRuleLockCount = ruleLockCountMap.get(strategyAward.getRuleModels());
            result.add(RaffleAwardListResponseDTO.builder()
                    .awardId(strategyAward.getAwardId())
                    .awardTitle(strategyAward.getAwardTitle())
                    .awardSubtitle(strategyAward.getAwardSubtitle())
                    .sort(strategyAward.getSort())
                    .awardRuleLockCount(awardRuleLockCount)
                    .isAwardUnlock(null == awardRuleLockCount || dayPartakeCount >= awardRuleLockCount)
                    .waitUnLockCount(null == awardRuleLockCount || awardRuleLockCount <= dayPartakeCount
                            ? 0 : awardRuleLockCount - dayPartakeCount)
                    .build());
        }
        return result;
    }

    @Override
    public List<RaffleStrategyRuleWeightResponseDTO> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request) {
        Integer userActivityAccountTotalUseCount = accountReadAdapter.queryRaffleActivityAccountPartakeCount(
                request.getActivityId(), request.getUserId());

        List<RuleWeightVO> ruleWeightVOList = raffleRule.queryAwardRuleWeightByActivityId(request.getActivityId());

        List<RaffleStrategyRuleWeightResponseDTO> result = new ArrayList<>();
        for (RuleWeightVO ruleWeightVO : ruleWeightVOList) {
            List<RaffleStrategyRuleWeightResponseDTO.StrategyAward> strategyAwards = new ArrayList<>();
            for (RuleWeightVO.Award award : ruleWeightVO.getAwardList()) {
                RaffleStrategyRuleWeightResponseDTO.StrategyAward strategyAward =
                        new RaffleStrategyRuleWeightResponseDTO.StrategyAward();
                strategyAward.setAwardId(award.getAwardId());
                strategyAward.setAwardTitle(award.getAwardTitle());
                strategyAwards.add(strategyAward);
            }
            RaffleStrategyRuleWeightResponseDTO dto = new RaffleStrategyRuleWeightResponseDTO();
            dto.setRuleWeightCount(ruleWeightVO.getWeight());
            dto.setStrategyAwards(strategyAwards);
            dto.setUserActivityAccountTotalUseCount(userActivityAccountTotalUseCount);
            result.add(dto);
        }
        return result;
    }

}
