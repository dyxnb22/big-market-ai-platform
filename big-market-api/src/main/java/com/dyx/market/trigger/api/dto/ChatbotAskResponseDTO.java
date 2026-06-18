package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 聊天机器人问答应答对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotAskResponseDTO implements Serializable {

    /** 识别出的用户意图 */
    private String intent;

    /** 调用的工具名称 */
    private String toolName;

    /** 返回给用户的自然语言回答 */
    private String answer;

    /** 工具调用是否成功 */
    private Boolean success;

    /** 工具返回的结构化数据 */
    private Object data;

    /** 本次问答扣除的积分（聊天机器人关闭或免费模式时为 0） */
    private BigDecimal creditDeducted;

    /** 扣费后的最新积分余额 */
    private BigDecimal creditBalance;

}
