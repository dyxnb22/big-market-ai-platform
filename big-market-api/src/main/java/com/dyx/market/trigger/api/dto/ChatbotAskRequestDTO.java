package com.dyx.market.trigger.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 聊天机器人问答请求对象。
 */
@Data
public class ChatbotAskRequestDTO implements Serializable {

    /** 用户访问令牌 */
    private String token;

    /** 用户 ID */
    private String userId;

    /** 活动 ID */
    private Long activityId;

    /** 用户输入的问题或指令 */
    private String message;

    /** 客户端生成的幂等键，标识一次问答请求 */
    private String requestId;

}
