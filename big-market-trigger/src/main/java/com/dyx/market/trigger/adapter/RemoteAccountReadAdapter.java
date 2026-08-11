package com.dyx.market.trigger.adapter;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.CreditOrderResponseDTO;
import com.dyx.market.trigger.api.dto.UserActivityAccountResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * account-service 的只读 Dubbo 适配器。
 *
 * <p>该类由 market-service 与 message-job-service 的 Docker Profile 配置为 Bean，
 * 两个服务共用同一份远程读取实现，避免读取失败时悄悄切回各自本地数据库。</p>
 */
@Slf4j
public class RemoteAccountReadAdapter implements IAccountReadAdapter {

    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;

    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @Override
    public BigDecimal queryUserCreditAccount(String userId) {
        Response<BigDecimal> response = accountCreditService.queryUserCreditAccount(userId);
        if (isSuccess(response)) {
            return response.getData() == null ? BigDecimal.ZERO : response.getData();
        }
        throw readFailure("queryUserCreditAccount", userId, response);
    }

    @Override
    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        Response<UserActivityAccountResponseDTO> response = accountQuotaService
                .queryActivityAccountEntity(activityId, userId);
        if (!isSuccess(response)) {
            throw readFailure("queryActivityAccountEntity", userId, response);
        }
        UserActivityAccountResponseDTO dto = response.getData();
        if (dto == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "account-service 返回空活动账户");
        }
        return ActivityAccountEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .totalCount(dto.getTotalCount())
                .totalCountSurplus(dto.getTotalCountSurplus())
                .dayCount(dto.getDayCount())
                .dayCountSurplus(dto.getDayCountSurplus())
                .monthCount(dto.getMonthCount())
                .monthCountSurplus(dto.getMonthCountSurplus())
                .build();
    }

    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        Response<Integer> response = accountQuotaService
                .queryRaffleActivityAccountPartakeCount(activityId, userId);
        if (isSuccess(response)) {
            return response.getData() == null ? 0 : response.getData();
        }
        throw readFailure("queryRaffleActivityAccountPartakeCount", userId, response);
    }

    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        Response<Integer> response = accountQuotaService
                .queryRaffleActivityAccountDayPartakeCount(activityId, userId);
        if (isSuccess(response)) {
            return response.getData() == null ? 0 : response.getData();
        }
        throw readFailure("queryRaffleActivityAccountDayPartakeCount", userId, response);
    }

    @Override
    public List<CreditOrderResponseDTO> queryUserCreditOrders(String userId, int limit) {
        Response<List<CreditOrderResponseDTO>> response = accountCreditService.queryUserCreditOrders(userId, limit);
        if (isSuccess(response)) {
            return response.getData() == null ? Collections.emptyList() : response.getData();
        }
        throw readFailure("queryUserCreditOrders", userId, response);
    }

    private static boolean isSuccess(Response<?> response) {
        return response != null && ResponseCode.SUCCESS.getCode().equals(response.getCode());
    }

    private static AppException readFailure(String operation, String userId, Response<?> response) {
        String code = response == null ? "null" : response.getCode();
        log.warn("[RemoteAccountReadAdapter] {} failed userId:{} code:{}", operation, userId, code);
        return new AppException(ResponseCode.UN_ERROR.getCode(),
                "account-service 账户读取失败，请稍后重试");
    }
}
