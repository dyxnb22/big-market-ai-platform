package com.dyx.market.domain.activity.application;

import lombok.Builder;
import lombok.Data;

/**
 * 活动抽奖应用层响应：中奖奖品信息。
 */
@Data
@Builder
public class ActivityDrawResponseEntity {

    private Integer awardId;

    private String awardTitle;

    private Integer awardIndex;

}
