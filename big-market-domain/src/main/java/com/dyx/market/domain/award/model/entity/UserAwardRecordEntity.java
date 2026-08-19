package com.dyx.market.domain.award.model.entity;

import com.dyx.market.domain.award.model.valobj.AwardStateVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户中奖记录实体对象
 * @create 2024-04-06 09:06
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAwardRecordEntity {

    /** 用户ID */
    private String userId;
    /** 活动ID */
    private Long activityId;
    /** 抽奖策略ID */
    private Long strategyId;
    /** 抽奖订单ID【作为幂等使用】 */
    private String orderId;
    /** 奖品ID */
    private Integer awardId;
    /** 奖品标题（名称） */
    private String awardTitle;
    /** 中奖时间 */
    private Date awardTime;
    /** 发奖状态；create-待发奖、complete-本地发奖逻辑完成、fail-发奖失败。complete 不代表积分已到账。 */
    private AwardStateVO awardState;
    /** 奖品配置 JSON；发奖时根据该配置确定实际发放内容。 */
    private String awardConfig;

}
