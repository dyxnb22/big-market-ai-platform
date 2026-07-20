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
 * 远程写入对账任务。
 *
 * <p>保存原始请求 payload 与业务幂等号，UNKNOWN/超时场景由 message-job 使用同一请求重试，
 * 不允许生成新的业务号。</p>
 */
public class PendingRemoteWriteTask {

    /** 数据库主键。 */
    private Long id;
    /** 远程写入的业务幂等号。 */
    private String outBusinessNo;
    /** credit_create、quota_update 等操作类型。 */
    private String operation;
    /** 序列化后的原始 RPC 请求。 */
    private String payload;
    /** pending、continuation_pending、done、failed 等状态。 */
    private String state;
    /** 对账重试次数。 */
    private Integer retryCount;
    private Date createTime;
    private Date updateTime;
}
