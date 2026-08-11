package com.dyx.market.domain.award.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用户中奖记录读模型（抽奖历史查询）。
 *
 * <p>与写模型 {@link UserAwardRecordEntity} 分离：{@code awardState} 保留库内原始字符串，
 * 因为历史数据同时存在 {@code complete} 枚举编码与 {@code completed} SQL 字面量，
 * 读路径不做枚举反解，避免对存量数据抛异常。</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecordLogEntity {

    /** 活动ID */
    private Long activityId;
    /** 抽奖订单ID（幂等键） */
    private String orderId;
    /** 奖品ID */
    private Integer awardId;
    /** 奖品标题 */
    private String awardTitle;
    /** 发奖状态；库内字符串：create / completed / fail */
    private String awardState;
    /** 中奖时间 */
    private Date awardTime;

}
