package com.dyx.market.trigger.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ChatbotAskRequestDTO implements Serializable {

    private String token;

    private String userId;

    private Long activityId;

    private String message;

    /**
     * Client generated idempotency key for one ask request.
     */
    private String requestId;

}
