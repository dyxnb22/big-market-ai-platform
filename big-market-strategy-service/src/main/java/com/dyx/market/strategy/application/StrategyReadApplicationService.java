package com.dyx.market.strategy.application;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.valobj.RuleWeightVO;
import com.dyx.market.domain.strategy.service.IRaffleAward;
import com.dyx.market.domain.strategy.service.IRaffleRule;
import com.dyx.market.strategy.port.IStrategyAccountParticipationPort;
import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StrategyReadApplicationService {

    @Resource
    private IRaffleAward raffleAward;
    @Resource
    private IRaffleRule raffleRule;
    @Resource
    private IStrategyAccountParticipationPort strategyAccountParticipationPort;

    public List<RaffleAwardListResponseDTO> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId()) || request.getActivityId() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        List<StrategyAwardEntity> strategyAwardEntities =
                raffleAward.queryRaffleStrategyAwardListByActivityId(request.getActivityId());

        String[] treeIds = strategyAwardEntities.stream()
                .map(StrategyAwardEntity::getRuleModels)
                .filter(ruleModel -> ruleModel != null && !ruleModel.isEmpty())
                .toArray(String[]::new);

        Map<String, Integer> ruleLockCountMap = raffleRule.queryAwardRuleLockCount(treeIds);

        int dayPartakeCount = strategyAccountParticipationPort
                .queryRaffleActivityAccountDayPartakeCount(request.getActivityId(), request.getUserId());

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

    public List<RaffleStrategyRuleWeightResponseDTO> queryRaffleStrategyRuleWeight(
            RaffleStrategyRuleWeightRequestDTO request) {
        if (request == null || StringUtils.isBlank(request.getUserId()) || request.getActivityId() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }

        int userActivityAccountTotalUseCount = strategyAccountParticipationPort
                .queryRaffleActivityAccountPartakeCount(request.getActivityId(), request.getUserId());

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
