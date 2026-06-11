package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * API DTO for distributeAward — the cross-service contract to trigger prize
 * delivery after a raffle win.
 *
 * This is the API-surface equivalent of DistributeAwardEntity, mapped from/to
 * the domain entity inside the provider. No domain types leak through the RPC
 * boundary.
 *
 * @see com.dyx.market.domain.award.model.entity.DistributeAwardEntity
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FulfillmentDistributeAwardRequestDTO implements Serializable {

    /** 用户ID */
    private String userId;
    /** 订单ID */
    private String orderId;
    /** 奖品ID */
    private Integer awardId;
    /** 奖品配置信息 */
    private String awardConfig;

}
