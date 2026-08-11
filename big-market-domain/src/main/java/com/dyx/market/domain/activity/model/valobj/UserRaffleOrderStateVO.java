package com.dyx.market.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户抽奖单（partake order）生命周期状态。
 * <p>典型流转：{@code create}（已扣额度、待抽奖）→ {@code used}（抽奖已消费）；
 * 异常路径可进入 {@code cancel} 或 {@code failed}。</p>
 * <p>{@code create} 状态的未使用订单可被 {@code queryNoUsedRaffleOrder} 复用，是 partake 幂等路径。</p>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-04-04 18:55
 */
@Getter
@AllArgsConstructor
public enum UserRaffleOrderStateVO {

    /** 已创建、额度已扣，等待抽奖消费 */
    create("create", "创建"),
    /** 抽奖流程已消费该订单 */
    used("used", "已使用"),
    /** 已作废（如补偿回滚） */
    cancel("cancel", "已作废"),
    /** 执行失败（如远程配额 saga 补偿后标记） */
    failed("failed", "执行失败"),
    ;

    private final String code;
    private final String desc;

}
