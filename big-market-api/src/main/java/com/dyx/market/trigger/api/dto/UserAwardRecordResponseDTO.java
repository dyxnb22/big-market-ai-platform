package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户中奖记录应答对象（服务端抽奖历史）。
 *
 * <p>{@code awardState} 透传库内字符串（create-待发奖、completed-发奖完成、fail-发奖失败），
 * 前端据此展示“发放中/已到账”等异步发奖状态。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecordResponseDTO implements Serializable {

    /** 活动ID */
    private Long activityId;
    /** 抽奖订单ID（幂等键，用于问题排查展示） */
    private String orderId;
    /** 奖品ID */
    private Integer awardId;
    /** 奖品标题 */
    private String awardTitle;
    /** 发奖状态；create-待发奖、completed-发奖完成、fail-发奖失败 */
    private String awardState;
    /** 中奖时间 */
    private Date awardTime;

}
