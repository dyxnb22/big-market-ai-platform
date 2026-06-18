package com.dyx.market.strategy.port;

import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * 账户参与次数端口：通过 Dubbo 调用 {@link IAccountQuotaService} 实现。
 * <p>
 * strategy-service 无法直接访问 activity/account 领域表；本端口委托 account-service，
 * 复用现有 API 契约，不引入新的跨域依赖。
 * <p>
 * {@code check=false}：account-service 未注册到 Nacos 时仍可启动。
 * 失败时两方法均返回 0（保守默认值），与远程策略读路径禁用前行为一致。
 * 远程策略读路径须保持 {@code strategy.service.remote-read.enabled=false}，
 * 直至确认 strategy-service 可访问 account-service。
 */
@Slf4j
@Component
public class LocalStrategyAccountParticipationPort implements IStrategyAccountParticipationPort {

    // check=false：account-service 未注册时仍可启动
    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @Override
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        try {
            Response<Integer> resp = accountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountPartakeCount success activityId:{} userId:{}", activityId, userId);
                return resp.getData() != null ? resp.getData() : 0;
            }
            log.warn("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountPartakeCount non-success code:{} activityId:{} userId:{}",
                    resp != null ? resp.getCode() : null, activityId, userId);
        } catch (Exception e) {
            log.error("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountPartakeCount failed, returning 0 activityId:{} userId:{}", activityId, userId, e);
        }
        return 0;
    }

    @Override
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        try {
            Response<Integer> resp = accountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountDayPartakeCount success activityId:{} userId:{}", activityId, userId);
                return resp.getData() != null ? resp.getData() : 0;
            }
            log.warn("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountDayPartakeCount non-success code:{} activityId:{} userId:{}",
                    resp != null ? resp.getCode() : null, activityId, userId);
        } catch (Exception e) {
            log.error("[LocalStrategyAccountParticipationPort] queryRaffleActivityAccountDayPartakeCount failed, returning 0 activityId:{} userId:{}", activityId, userId, e);
        }
        return 0;
    }

}
