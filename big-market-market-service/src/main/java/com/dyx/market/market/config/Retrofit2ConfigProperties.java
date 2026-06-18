package com.dyx.market.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外部网关 API 参数，绑定配置前缀 {@code gateway.config}。
 */
@Data
@ConfigurationProperties(prefix = "gateway.config", ignoreInvalidFields = true)
public class Retrofit2ConfigProperties {

    private boolean enable;
    private String apiHost;

}
