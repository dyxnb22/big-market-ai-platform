package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 活动配额扣减幂等账本 PO：{@code raffle_quota_decrement_ledger_{000..003}}。
 * <p>
 * 唯一键 {@code (user_id, activity_id, out_business_no)} 防止重复 RPC 导致二次扣减。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleQuotaDecrementLedger {

    private Long id;

    /** 分片键，须与用户活动账户表分片一致 */
    private String userId;

    private Long activityId;

    /** 幂等键，等于抽奖订单的 outBusinessNo */
    private String outBusinessNo;

    /** Original quota buckets; rollback must not recalculate these at retry time. */
    private String month;
    private String day;

    /** 状态：applied | rolled_back */
    private String status;

    private Date createTime;

    private Date updateTime;

}
