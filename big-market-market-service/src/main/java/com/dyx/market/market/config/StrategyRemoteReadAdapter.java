package com.dyx.market.market.config;

import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.valobj.RuleWeightVO;
import com.dyx.market.domain.strategy.service.IRaffleAward;
import com.dyx.market.domain.strategy.service.IRaffleRule;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.adapter.IStrategyReadAdapter;
import com.dyx.market.trigger.api.IStrategyReadService;
import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routes strategy read queries to big-market-strategy-service via Dubbo when enabled.
 *
 * Flag: strategy.service.remote-read.enabled (default false).
 * This bean overrides LocalStrategyReadAdapter (@ConditionalOnMissingBean) in market-service.
 *
 * When flag=false: falls through to local IRaffleAward / IRaffleRule / IAccountReadAdapter path.
 * When flag=true and remote call succeeds: returns data from IStrategyReadService.
 * When flag=true and remote call fails or returns non-success: logs and falls back to local.
 *
 * Do NOT enable until:
 *   - big-market-strategy-service is registered  Nacos
 *   - IStrategyAccountParticipationPort in strategy-service returns real counts from account-service
 *   - end-to-end local validation passes
 *   - DBA + Ops + Engineering + Oncall sign off (approval gate)
 */
@Slf4j
@Component
public class StrategyRemoteReadAdapter implements IStrategyReadAdapter {

    @Value("${strategy.service.remote-read.enabled:false}")
    private boolean remoteReadEnabled;

    @Resource
    private IRaffleAward raffleAward;

    @Resource
    private IRaffleRule raffleRule;

    @Resource
    private IAccountReadAdapter accountReadAdapter;

    // check=false: startup succeeds even when strategy-service is not registered in Nacos.
    @DubboReference(version = "1.0", check = false)
    private IStrategyReadService strategyReadService;

    @Override
    public List<RaffleAwardListResponseDTO> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        if (remoteReadEnabled) {
            try {
                Response<List<RaffleAwardListResponseDTO>> resp = strategyReadService.queryRaffleAwardList(request);
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[StrategyRemoteReadAdapter] queryRaffleAwardList remote success userId:{} activityId:{}",
                            request.getUserId(), request.getActivityId());
                    return resp.getData() != null ? resp.getData() : new ArrayList<>();
                }
                log.warn("[StrategyRemoteReadAdapter] queryRaffleAwardList remote non-success code:{} userId:{} activityId:{}",
                        resp != null ? resp.getCode() : null, request.getUserId(), request.getActivityId());
            } catch (Exception e) {
                log.error("[StrategyRemoteReadAdapter] queryRaffleAwardList remote failed, falling back to local userId:{} activityId:{}",
                        request.getUserId(), request.getActivityId(), e);
            }
        }
        return localQueryRaffleAwardList(request);
    }

    @Override
    public List<RaffleStrategyRuleWeightResponseDTO> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request) {
        if (remoteReadEnabled) {
            try {
                Response<List<RaffleStrategyRuleWeightResponseDTO>> resp = strategyReadService.queryRaffleStrategyRuleWeight(request);
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[StrategyRemoteReadAdapter] queryRaffleStrategyRuleWeight remote success userId:{} activityId:{}",
                            request.getUserId(), request.getActivityId());
                    return resp.getData() != null ? resp.getData() : new ArrayList<>();
                }
                log.warn("[StrategyRemoteReadAdapter] queryRaffleStrategyRuleWeight remote non-success code:{} userId:{} activityId:{}",
                        resp != null ? resp.getCode() : null, request.getUserId(), request.getActivityId());
            } catch (Exception e) {
                log.error("[StrategyRemoteReadAdapter] queryRaffleStrategyRuleWeight remote failed, falling back to local userId:{} activityId:{}",
                        request.getUserId(), request.getActivityId(), e);
            }
        }
        return localQueryRaffleStrategyRuleWeight(request);
    }

    private List<RaffleAwardListResponseDTO> localQueryRaffleAwardList(RaffleAwardListRequestDTO request) {
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

    private List<RaffleStrategyRuleWeightResponseDTO> localQueryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request) {
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
