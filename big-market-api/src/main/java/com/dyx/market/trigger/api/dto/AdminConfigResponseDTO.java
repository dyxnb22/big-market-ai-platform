package com.dyx.market.trigger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端动态配置查询应答对象。
 */
@Data
@Builder(toBuilder = true)
public class AdminConfigResponseDTO implements Serializable {

    /** 配置命名空间 */
    private String namespace;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;

    /** 最后更新时间戳（毫秒） */
    private Long updateTime;

    /** 全量配置内容哈希（SHA-256 前 16 位 hex），便于对账 */
    private String contentHash;

    /** 本次保存是否已成功发布到 Nacos（未启用 sync 时为 false） */
    private Boolean nacosPublished;

    /** Nacos 已提交但 Redis fan-out 尚未确认时为 true；Nacos listener 是持久化兜底。 */
    private Boolean notificationPending;

    /** 权威来源：local | nacos */
    private String source;

}
