package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 延迟扣减额度 RPC 请求对象。
 *
 * <p>对应 Provider 方法在 RaffleActivityPartakeService 额度扣减链路
 * 完全验证并推广前返回 UN_ERROR。当前阶段尚无调用方接入。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountQuotaDecrementRequestDTO implements Serializable {

    /** 用户 ID */
    private String userId;

    /** 活动 ID */
    private Long activityId;

    /** 幂等键，与抽奖订单的 outBusinessNo 一致 */
    private String outBusinessNo;

}
