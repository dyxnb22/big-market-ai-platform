package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * API DTO for saveUserAwardRecord — the cross-service contract to persist a
 * user's award record after a successful raffle.
 *
 * This is the API-surface equivalent of UserAwardRecordEntity, mapped from/to
 * the domain entity inside the provider. It carries only the fields the caller
 * supplies — no domain types leak through the RPC boundary.
 *
 * @see com.dyx.market.domain.award.model.entity.UserAwardRecordEntity
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FulfillmentSaveUserAwardRecordRequestDTO implements Serializable {

    /** 用户ID */
    private String userId;
    /** 活动ID */
    private Long activityId;
    /** 抽奖策略ID */
    private Long strategyId;
    /** 抽奖订单ID【作为幂等使用】 */
    private String orderId;
    /** 奖品ID */
    private Integer awardId;
    /** 奖品标题（名称） */
    private String awardTitle;
    /** 中奖时间 */
    private Date awardTime;
    /**
     * 奖品状态；create-创建、complete-发奖完成、fail-发奖失败
     * Maps to {@code AwardStateVO.code}
     */
    private String awardState;
    /** 奖品配置信息；发奖的时候，可以根据 */
    private String awardConfig;

}
