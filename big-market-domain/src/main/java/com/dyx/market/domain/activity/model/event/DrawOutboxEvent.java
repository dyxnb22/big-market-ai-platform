package com.dyx.market.domain.activity.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 抽奖 Saga Outbox 关联载荷：在配置的服务边界间传递抽奖流程所需的最小标识集。
 * <p>
 * {@code orderId} 为幂等键，贯穿建单 → 执行抽奖 → 保存中奖记录；
 * 任意远程步骤必须回传该键，以便编排器识别重复投递。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DrawOutboxEvent {

    private String userId;
    private Long activityId;
    private Long strategyId;
    private String orderId;
    private Integer awardId;
    private String awardTitle;
    private Date awardTime;
    private String awardConfig;
    private DrawSagaStep sagaStep;

    /** 抽奖 Saga 步骤 */
    public enum DrawSagaStep {
        /** 创建参与订单 */
        CREATE_ORDER,
        /** 执行策略抽奖 */
        PERFORM_RAFFLE,
        /** 保存用户中奖记录 */
        SAVE_AWARD_RECORD,
        /** 流程完成 */
        COMPLETE,
        /** 补偿回滚 */
        COMPENSATE
    }
}
