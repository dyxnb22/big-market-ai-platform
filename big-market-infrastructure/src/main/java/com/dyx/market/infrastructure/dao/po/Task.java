package com.dyx.market.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 任务表，发送MQ
 * @create 2024-04-03 15:30
 */
@Data
public class Task {

    /** 自增ID */
    private String id;
    /** 活动ID */
    private String userId;
    /** 消息主题 */
    private String topic;
    /** 消息编号 */
    private String messageId;
    /** 消息主体 */
    private String message;
    /** 任务状态；create-创建、completed-完成、fail-可重试、manual_pending-人工处理 */
    private String state;
    /** 已经失败的发送次数 */
    private Integer retryCount;
    /** 下一次允许重试的时间 */
    private Date nextRetryTime;
    /** 最近一次失败原因（不保存完整消息体） */
    private String lastError;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;

}
