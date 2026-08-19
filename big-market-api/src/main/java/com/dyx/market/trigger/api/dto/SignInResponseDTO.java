package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 日历签到应答对象：今日是否已签、本次奖励积分、最新余额与提示文案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignInResponseDTO implements Serializable {

    /** 用户今天是否已经签到。 */
    private Boolean signedToday;

    /** 本次签到奖励的积分；今日已签到时为 0。 */
    private BigDecimal rewardCredit;

    /** 签到完成后的最新积分余额。 */
    private BigDecimal creditBalance;

    /** 面向用户的提示文案，例如“签到成功，+10 积分”或“今日已签到”。 */
    private String message;

}
