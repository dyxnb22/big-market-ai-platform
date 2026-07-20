package com.dyx.market.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
/**
 * MQ 死信持久化记录。
 *
 * <p>state 由 pending、reviewed、replayed、manual_pending 等状态驱动；
 * money-effect 消息默认只允许人工审核后的 replay。</p>
 */
public class MqDeadLetter {

    /** 数据库主键。 */
    private Long id;
    /** 消息摘要，用于识别同一队列中的重复消息。 */
    private String messageId;
    /** 业务消息号，重入 DLQ 时用于关联历史记录。 */
    private String businessMessageId;
    /** 原始 RabbitMQ 队列名。 */
    private String queue;
    /** 原始消息内容；日志不得直接输出其完整内容。 */
    private String payload;
    /** 死信审核/重放状态。 */
    private String state;
    /** replay 任务重试次数。 */
    private Integer retryCount;
    /** 消费端失败次数，用于避免无限重入。 */
    private Integer consumeFailCount;
    private Date createTime;
    private Date updateTime;
}
