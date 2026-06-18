package com.dyx.market.domain.activity.application;

import lombok.Builder;
import lombok.Data;

/**
 * 活动抽奖应用层请求：用户与活动标识。
 */
@Data
@Builder
public class ActivityDrawRequestEntity {

    private String userId;

    private Long activityId;

}
