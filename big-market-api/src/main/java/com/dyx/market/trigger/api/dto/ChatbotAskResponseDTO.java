package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotAskResponseDTO implements Serializable {

    private String intent;

    private String toolName;

    private String answer;

    private Boolean success;

    private Object data;

    /** Credits deducted for this ask (0 if chatbot is disabled or free mode) */
    private BigDecimal creditDeducted;

    /** Latest credit balance after deduction */
    private BigDecimal creditBalance;

}
