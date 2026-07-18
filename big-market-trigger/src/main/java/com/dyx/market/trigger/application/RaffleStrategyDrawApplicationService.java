package com.dyx.market.trigger.application;

import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.domain.strategy.service.IRaffleStrategy;
import com.dyx.market.trigger.api.dto.RaffleStrategyRequestDTO;
import com.dyx.market.trigger.api.dto.RaffleStrategyResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 抽奖策略执行应用服务：校验参数并委托领域层执行随机抽奖。
 */
@Slf4j
@Service
public class RaffleStrategyDrawApplicationService {

    @Resource
    private IRaffleStrategy raffleStrategy;

    public RaffleStrategyResponseDTO randomRaffle(RaffleStrategyRequestDTO requestDTO) {
        log.info("随机抽奖开始 strategyId: {}", requestDTO.getStrategyId());
        String userId = requestDTO.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(RaffleFactorEntity.builder()
                .userId(userId)
                .strategyId(requestDTO.getStrategyId())
                .build());
        log.info("随机抽奖完成 strategyId: {} awardId:{}", requestDTO.getStrategyId(), raffleAwardEntity.getAwardId());
        return RaffleStrategyResponseDTO.builder()
                .awardId(raffleAwardEntity.getAwardId())
                .awardIndex(raffleAwardEntity.getSort())
                .build();
    }
}
