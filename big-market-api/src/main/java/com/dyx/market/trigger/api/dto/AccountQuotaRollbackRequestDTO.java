package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 额度回滚（Saga 补偿）RPC 请求对象。
 *
 * <p>启用服务化额度扣减模式时，需 account-service 幂等台账 DDL 及冒烟验证。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountQuotaRollbackRequestDTO implements Serializable {

    /** 用户 ID */
    private String userId;

    /** 活动 ID */
    private Long activityId;

    /** 幂等键，与对应 decrementQuota 调用使用的值相同 */
    private String outBusinessNo;

}
