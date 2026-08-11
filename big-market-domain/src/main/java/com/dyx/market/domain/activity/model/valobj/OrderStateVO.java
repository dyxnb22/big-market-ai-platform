package com.dyx.market.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 活动额度订单（积分兑换等）的支付/履约状态。
 * <p>典型流转：</p>
 * <pre>
 *   wait_pay ──(积分扣减 MQ 成功)──► completed
 *       │
 *       └──(扣减失败/超时对账)──► compensating ──► failed
 * </pre>
 * <p>补偿路径由 {@code CreditPayDeliveryReconcileJob} 驱动：退款 → 释放库存 → 标记 failed。</p>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-03-16 10:34
 */
@Getter
@AllArgsConstructor
public enum OrderStateVO {

    /** 订单已创建，等待异步积分扣减 */
    wait_pay("wait_pay","待支付"),
    /** 扣减失败或超时，补偿流程进行中 */
    compensating("compensating", "补偿中"),
    /** 积分扣减成功，额度已发放 */
    completed("completed", "完成"),
    /** 补偿完成，订单关闭 */
    failed("failed", "失败"),
    ;

    private final String code;
    private final String desc;

}
