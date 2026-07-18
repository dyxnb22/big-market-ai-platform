package com.dyx.market.trigger.application;

import com.alibaba.fastjson.JSON;
import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.model.valobj.BehaviorTypeVO;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.adapter.IRebateOrderAdapter;
import com.dyx.market.trigger.adapter.IRebateReadAdapter;
import com.dyx.market.domain.rebate.model.valobj.DailyBehaviorRebateVO;
import com.dyx.market.trigger.api.dto.SignInResponseDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 日历签到返利应用服务。
 */
@Slf4j
@Service
public class CalendarSignApplicationService {

    private static final DateTimeFormatter DATE_FORMAT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private IRebateOrderAdapter rebateOrderAdapter;
    @Resource
    private IRebateReadAdapter rebateReadAdapter;
    @Resource
    private IAccountReadAdapter accountRemoteReadAdapter;

    public SignInResponseDTO sign(String userId) {
        log.info("日历签到返利开始 userId:{}", userId);
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        String outBusinessNo = LocalDate.now().format(DATE_FORMAT_DAY);
        if (rebateReadAdapter.isCalendarSignRebate(userId, outBusinessNo)) {
            log.info("日历签到返利-今日已签到 userId:{}", userId);
            return alreadySignedResponse(userId);
        }
        try {
            BehaviorEntity behaviorEntity = new BehaviorEntity();
            behaviorEntity.setUserId(userId);
            behaviorEntity.setBehaviorTypeVO(BehaviorTypeVO.SIGN);
            behaviorEntity.setOutBusinessNo(outBusinessNo);
            List<DailyBehaviorRebateVO> configs = rebateReadAdapter.queryCalendarSignRebateConfig();
            if (configs == null || configs.isEmpty()) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "签到返利未配置");
            }
            BigDecimal rewardCredit = configs.stream()
                    .filter(config -> "integral".equalsIgnoreCase(config.getRebateType()))
                    .map(config -> parseReward(config.getRebateConfig()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<String> orderIds = rebateOrderAdapter.createOrder(behaviorEntity);
            if (orderIds == null || orderIds.isEmpty()) {
                throw new AppException(ResponseCode.UN_ERROR.getCode(), "签到返利未生成任何有效权益");
            }
            log.info("日历签到返利完成 userId:{} orderIds: {}", userId, JSON.toJSONString(orderIds));
            return SignInResponseDTO.builder()
                    .signedToday(true)
                    .rewardCredit(rewardCredit)
                    .creditBalance(queryCreditBalanceSafe(userId))
                    .message("签到成功，+" + rewardCredit.stripTrailingZeros().toPlainString() + " 积分")
                    .build();
        } catch (AppException e) {
            if (ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("日历签到返利-并发重复签到 userId:{}", userId);
                return alreadySignedResponse(userId);
            }
            throw e;
        }
    }

    private BigDecimal parseReward(String value) {
        try {
            BigDecimal reward = new BigDecimal(value);
            if (reward.signum() <= 0) {
                throw new NumberFormatException("reward must be positive");
            }
            return reward;
        } catch (Exception e) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "签到积分返利配置非法");
        }
    }

    public boolean isSignedToday(String userId) {
        log.info("查询用户是否完成日历签到返利开始 userId:{}", userId);
        if (StringUtils.isBlank(userId)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        boolean signed = rebateReadAdapter.isCalendarSignRebate(userId, LocalDate.now().format(DATE_FORMAT_DAY));
        log.info("查询用户是否完成日历签到返利完成 userId:{} signed:{}", userId, signed);
        return signed;
    }

    private SignInResponseDTO alreadySignedResponse(String userId) {
        return SignInResponseDTO.builder()
                .signedToday(true)
                .rewardCredit(BigDecimal.ZERO)
                .creditBalance(queryCreditBalanceSafe(userId))
                .message("今日已签到，明天再来")
                .build();
    }

    private BigDecimal queryCreditBalanceSafe(String userId) {
        try {
            return accountRemoteReadAdapter.queryUserCreditAccount(userId);
        } catch (Exception e) {
            log.warn("查询用户积分失败 userId:{}", userId, e);
            return BigDecimal.ZERO;
        }
    }
}
