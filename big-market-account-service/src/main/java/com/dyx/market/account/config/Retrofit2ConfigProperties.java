package com.dyx.market.account.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrofit 外部 API 开关与地址，绑定前缀 {@code gateway.config}。
 */
@Data
@ConfigurationProperties(prefix = "gateway.config", ignoreInvalidFields = true)
public class Retrofit2ConfigProperties {

    private boolean enable;
    private String apiHost;

}
