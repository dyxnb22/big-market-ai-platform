package com.dyx.market.trigger.api.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端动态配置查询应答对象。
 */
@Data
@Builder
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

}
