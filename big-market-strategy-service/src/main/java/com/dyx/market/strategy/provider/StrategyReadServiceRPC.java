package com.dyx.market.strategy.provider;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.valobj.RuleWeightVO;
import com.dyx.market.domain.strategy.service.IRaffleAward;
import com.dyx.market.domain.strategy.service.IRaffleRule;
import com.dyx.market.strategy.port.IStrategyAccountParticipationPort;
import com.dyx.market.trigger.api.IStrategyReadService;
import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 策略读服务 Dubbo Provider：由 big-market-strategy-service 托管。
 * <p>
 * 只读接口，提供策略奖品列表与规则权重查询；不含抽奖执行、库存变更或活动/账户跨域调用。
 * <p>
 * 解锁状态 enrichment（isAwardUnlock、waitUnLockCount）需要账户参与次数，
 * 通过 {@link IStrategyAccountParticipationPort} 从 account-service 获取，失败时保守回退为 0。
 */
@Slf4j
@DubboService(version = "1.0")
public class StrategyReadServiceRPC implements IStrategyReadService {

    @Resource
    private IRaffleAward raffleAward;

    @Resource
    private IRaffleRule raffleRule;

    @Resource
    private IStrategyAccountParticipationPort strategyAccountParticipationPort;

    @Override
    public Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        log.info("策略读服务-查询奖品列表开始 userId:{} activityId:{}", request == null ? null : request.getUserId(), request == null ? null : request.getActivityId());
        try {
            if (request == null || StringUtils.isBlank(request.getUserId()) || request.getActivityId() == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            List<StrategyAwardEntity> strategyAwardEntities = raffleAward.queryRaffleStrategyAwardListByActivityId(request.getActivityId());

            String[] treeIds = strategyAwardEntities.stream()
                    .map(StrategyAwardEntity::getRuleModels)
                    .filter(ruleModel -> ruleModel != null && !ruleModel.isEmpty())
                    .toArray(String[]::new);

            Map<String, Integer> ruleLockCountMap = raffleRule.queryAwardRuleLockCount(treeIds);

            // 经 IStrategyAccountParticipationPort 从 account-service 获取当日参与次数；
            // 不可达时回退为 0（保守策略——所有锁定奖品保持锁定）
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
                        .waitUnLockCount(null == awardRuleLockCount || awardRuleLockCount <= dayPartakeCount ? 0 : awardRuleLockCount - dayPartakeCount)
                        .build());
            }

            Response<List<RaffleAwardListResponseDTO>> response = Response.<List<RaffleAwardListResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
            log.info("策略读服务-查询奖品列表完成 userId:{} activityId:{} response:{}", request.getUserId(), request.getActivityId(), JSON.toJSONString(response));
            return response;
        } catch (AppException e) {
            log.error("策略读服务-查询奖品列表异常 userId:{}", request == null ? null : request.getUserId(), e);
            return Response.<List<RaffleAwardListResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("策略读服务-查询奖品列表失败 userId:{}", request == null ? null : request.getUserId(), e);
            return Response.<List<RaffleAwardListResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<List<RaffleStrategyRuleWeightResponseDTO>> queryRaffleStrategyRuleWeight(RaffleStrategyRuleWeightRequestDTO request) {
        log.info("策略读服务-查询规则权重开始 userId:{} activityId:{}", request == null ? null : request.getUserId(), request == null ? null : request.getActivityId());
        try {
            if (request == null || StringUtils.isBlank(request.getUserId()) || request.getActivityId() == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            // 经 IStrategyAccountParticipationPort 从 account-service 获取累计使用次数；
            // 不可达时回退为 0（保守策略——解锁阈值视为未满足）
            int userActivityAccountTotalUseCount = strategyAccountParticipationPort
                    .queryRaffleActivityAccountPartakeCount(request.getActivityId(), request.getUserId());

            List<RuleWeightVO> ruleWeightVOList = raffleRule.queryAwardRuleWeightByActivityId(request.getActivityId());
            List<RaffleStrategyRuleWeightResponseDTO> result = new ArrayList<>();
            for (RuleWeightVO ruleWeightVO : ruleWeightVOList) {
                List<RaffleStrategyRuleWeightResponseDTO.StrategyAward> strategyAwards = new ArrayList<>();
                for (RuleWeightVO.Award award : ruleWeightVO.getAwardList()) {
                    RaffleStrategyRuleWeightResponseDTO.StrategyAward strategyAward = new RaffleStrategyRuleWeightResponseDTO.StrategyAward();
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

            Response<List<RaffleStrategyRuleWeightResponseDTO>> response = Response.<List<RaffleStrategyRuleWeightResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
            log.info("策略读服务-查询规则权重完成 userId:{} activityId:{} response:{}", request.getUserId(), request.getActivityId(), JSON.toJSONString(response));
            return response;
        } catch (AppException e) {
            log.error("策略读服务-查询规则权重异常 userId:{}", request == null ? null : request.getUserId(), e);
            return Response.<List<RaffleStrategyRuleWeightResponseDTO>>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("策略读服务-查询规则权重失败 userId:{}", request == null ? null : request.getUserId(), e);
            return Response.<List<RaffleStrategyRuleWeightResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
