package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户端活动展示配置（公开只读，不含敏感信息）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityDisplayConfigResponseDTO implements Serializable {

    private Long activityId;
    /** 活动展示标题 */
    private String title;
    /** 活动展示文案 */
    private String copy;
    /** 活动展示状态文案 */
    private String state;
    /** AI 对话是否开启 */
    private Boolean chatbotEnabled;
}
