package com.dyx.market.trigger.application;

import com.dyx.market.trigger.adapter.IStrategyReadAdapter;
import com.dyx.market.trigger.api.dto.RaffleAwardListRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleAwardListResponseDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyRuleWeightResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Service
public class RaffleStrategyQueryApplicationService {

    @Resource
    private IStrategyReadAdapter strategyReadAdapter;

    public List<RaffleAwardListResponseDTO> queryRaffleAwardList(RaffleAwardListRequestDTO request) {
        log.info("查询抽奖奖品列表开始 userId:{} activityId：{}", request.getUserId(), request.getActivityId());
        validateUserActivity(request.getUserId(), request.getActivityId());
        List<RaffleAwardListResponseDTO> data = strategyReadAdapter.queryRaffleAwardList(request);
        log.info("查询抽奖奖品列表完成 userId:{} activityId：{} size:{}", request.getUserId(), request.getActivityId(), data.size());
        return data;
    }

    public List<RaffleStrategyRuleWeightResponseDTO> queryRaffleStrategyRuleWeight(
            RaffleStrategyRuleWeightRequestDTO request) {
        log.info("查询抽奖策略权重规则开始 userId:{} activityId：{}", request.getUserId(), request.getActivityId());
        validateUserActivity(request.getUserId(), request.getActivityId());
        List<RaffleStrategyRuleWeightResponseDTO> data = strategyReadAdapter.queryRaffleStrategyRuleWeight(request);
        log.info("查询抽奖策略权重规则完成 userId:{} activityId：{} size:{}", request.getUserId(), request.getActivityId(), data.size());
        return data;
    }

    private static void validateUserActivity(String userId, Long activityId) {
        if (StringUtils.isBlank(userId) || null == activityId) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
    }
}
