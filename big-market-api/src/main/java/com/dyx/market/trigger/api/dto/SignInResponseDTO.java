package com.dyx.market.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignInResponseDTO implements Serializable {

    /** Whether the user has already signed in today */
    private Boolean signedToday;

    /** Credit amount rewarded for this sign-in (0 if already signed) */
    private BigDecimal rewardCredit;

    /** Latest credit balance */
    private BigDecimal creditBalance;

    /** Human-readable message, e.g. "签到成功，+10 积分" or "今日已签到" */
    private String message;

}
