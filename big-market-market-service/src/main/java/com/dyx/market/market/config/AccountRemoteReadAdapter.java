package com.dyx.market.market.config;

import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.service.ICreditAdjustService;
import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.api.IAccountCreditService;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.UserActivityAccountResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

/**
 * 账户读路径路由：按 {@code account.service.remote-read.enabled} 在 account-service（Dubbo）与本地领域服务间切换。
 * <p>
 * 仅负责只读查询（积分余额、活动配额、参与次数）；写路径不走此适配器。
 * 远程失败时透明回退本地实现。
 */
@Slf4j
@Component
public class AccountRemoteReadAdapter implements IAccountReadAdapter {

    @Value("${account.service.remote-read.enabled:false}")
    private boolean remoteReadEnabled;

    @Resource
    private ICreditAdjustService creditAdjustService;

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    // check=false：account-service 未注册到 Nacos 时仍可启动
    @DubboReference(version = "1.0", check = false)
    private IAccountCreditService accountCreditService;

    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    /**
     * 查询用户当前积分余额。
     * 远程：{@code IAccountCreditService.queryUserCreditAccount}；本地：{@code ICreditAdjustService}。
     */
    public BigDecimal queryUserCreditAccount(String userId) {
        if (remoteReadEnabled) {
            try {
                Response<BigDecimal> resp = accountCreditService.queryUserCreditAccount(userId);
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[AccountRemoteReadAdapter] queryUserCreditAccount remote success userId:{}", userId);
                    return resp.getData() != null ? resp.getData() : BigDecimal.ZERO;
                }
                log.warn("[AccountRemoteReadAdapter] queryUserCreditAccount non-success code:{} userId:{}",
                        resp != null ? resp.getCode() : null, userId);
            } catch (Exception e) {
                log.error("[AccountRemoteReadAdapter] queryUserCreditAccount remote failed, falling back to local userId:{}", userId, e);
            }
        }
        CreditAccountEntity entity = creditAdjustService.queryUserCreditAccount(userId);
        return entity != null ? entity.getAdjustAmount() : BigDecimal.ZERO;
    }

    /**
     * 查询用户在活动下的总/日/月配额账户。
     */
    public ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId) {
        if (remoteReadEnabled) {
            try {
                Response<UserActivityAccountResponseDTO> resp = accountQuotaService.queryActivityAccountEntity(activityId, userId);
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[AccountRemoteReadAdapter] queryActivityAccountEntity remote success activityId:{} userId:{}", activityId, userId);
                    UserActivityAccountResponseDTO dto = resp.getData();
                    if (dto == null) return null;
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
                log.warn("[AccountRemoteReadAdapter] queryActivityAccountEntity non-success code:{} activityId:{} userId:{}",
                        resp != null ? resp.getCode() : null, activityId, userId);
            } catch (Exception e) {
                log.error("[AccountRemoteReadAdapter] queryActivityAccountEntity remote failed, falling back to local activityId:{} userId:{}", activityId, userId, e);
            }
        }
        return raffleActivityAccountQuotaService.queryActivityAccountEntity(activityId, userId);
    }

    /**
     * 查询用户在活动下的累计参与次数。
     */
    public Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId) {
        if (remoteReadEnabled) {
            try {
                Response<Integer> resp = accountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[AccountRemoteReadAdapter] queryRaffleActivityAccountPartakeCount remote success activityId:{} userId:{}", activityId, userId);
                    return resp.getData() != null ? resp.getData() : 0;
                }
                log.warn("[AccountRemoteReadAdapter] queryRaffleActivityAccountPartakeCount non-success code:{} activityId:{} userId:{}",
                        resp != null ? resp.getCode() : null, activityId, userId);
            } catch (Exception e) {
                log.error("[AccountRemoteReadAdapter] queryRaffleActivityAccountPartakeCount remote failed, falling back to local activityId:{} userId:{}", activityId, userId, e);
            }
        }
        return raffleActivityAccountQuotaService.queryRaffleActivityAccountPartakeCount(activityId, userId);
    }

    /**
     * 查询用户在活动下的当日参与次数。
     */
    public Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId) {
        if (remoteReadEnabled) {
            try {
                Response<Integer> resp = accountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[AccountRemoteReadAdapter] queryRaffleActivityAccountDayPartakeCount remote success activityId:{} userId:{}", activityId, userId);
                    return resp.getData() != null ? resp.getData() : 0;
                }
                log.warn("[AccountRemoteReadAdapter] queryRaffleActivityAccountDayPartakeCount non-success code:{} activityId:{} userId:{}",
                        resp != null ? resp.getCode() : null, activityId, userId);
            } catch (Exception e) {
                log.error("[AccountRemoteReadAdapter] queryRaffleActivityAccountDayPartakeCount remote failed, falling back to local activityId:{} userId:{}", activityId, userId, e);
            }
        }
        return raffleActivityAccountQuotaService.queryRaffleActivityAccountDayPartakeCount(activityId, userId);
    }

}
