package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动账户表-日次数
 * @create 2024-04-03 15:28
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleActivityAccountDay {

    private static final DateTimeFormatter DATE_FORMAT_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 自增ID
     */
    private Long id;
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 活动ID
     */
    private Long activityId;
    /**
     * 日期（yyyy-mm-dd）
     */
    private String day;
    /**
     * 日次数
     */
    private Integer dayCount;
    /**
     * 日次数-剩余
     */
    private Integer dayCountSurplus;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;

    public static String currentDay() {
        return LocalDate.now().format(DATE_FORMAT_DAY);
    }

}
