package com.dyx.market.trigger.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端动态配置写入请求对象。
 */
@Data
public class AdminConfigRequestDTO implements Serializable {

    /** 配置命名空间 */
    private String namespace;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;

    /** 乐观并发控制：填写上一次读取到的全量配置哈希。 */
    private String expectedContentHash;

}
