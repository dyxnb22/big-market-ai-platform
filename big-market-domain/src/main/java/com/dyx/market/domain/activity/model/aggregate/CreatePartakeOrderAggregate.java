package com.dyx.market.domain.activity.model.aggregate;

import com.dyx.market.domain.activity.model.entity.ActivityAccountDayEntity;
import com.dyx.market.domain.activity.model.entity.ActivityAccountEntity;
import com.dyx.market.domain.activity.model.entity.ActivityAccountMonthEntity;
import com.dyx.market.domain.activity.model.entity.UserRaffleOrderEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参与活动（partake）订单聚合：在一次扣额度 + 建抽奖单时携带账户快照与待持久化抽奖单。
 * <p>由 {@code AbstractRaffleActivityPartake} 构建，经 {@code IActivityRepository} 或
 * {@code IActivityAccountPort} 持久化：</p>
 * <ul>
 *   <li>本地模式：quota 扣减与订单插入在同一事务（{@code saveCreatePartakeOrderAggregate}）</li>
 *   <li>远程模式：quota 已通过 Port 扣减，仅存订单（{@code savePartakeOrderOnly}）</li>
 * </ul>
 * <p>{@code isExistAccountMonth}/{@code isExistAccountDay} 控制保存时是否 upsert 月/日额度子账户。</p>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-04-05 08:31
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatePartakeOrderAggregate {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 账户总额度
     */
    private ActivityAccountEntity activityAccountEntity;

    /**
     * 是否存在月账户；{@code false} 时保存流程会新建月额度记录
     */
    @Builder.Default
    private boolean isExistAccountMonth = true;

    /**
     * 账户月额度
     */
    private ActivityAccountMonthEntity activityAccountMonthEntity;

    /**
     * 是否存在日账户；{@code false} 时保存流程会新建日额度记录
     */
    @Builder.Default
    private boolean isExistAccountDay = true;

    /**
     * 账户日额度
     */
    private ActivityAccountDayEntity activityAccountDayEntity;

    /**
     * 抽奖单实体
     */
    private UserRaffleOrderEntity userRaffleOrderEntity;

}
