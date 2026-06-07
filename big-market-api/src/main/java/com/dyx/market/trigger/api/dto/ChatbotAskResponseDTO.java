package com.dyx.market.trigger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class ChatbotAskResponseDTO implements Serializable {

    private String intent;

    private String toolName;

    private String answer;

    private Boolean success;

    private Object data;

}
