package com.dyx.market.domain.activity.application;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityDrawResponseEntity {

    private Integer awardId;

    private String awardTitle;

    private Integer awardIndex;

}
