package com.dyx.market.domain.activity.application;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityDrawRequestEntity {

    private String userId;

    private Long activityId;

}
