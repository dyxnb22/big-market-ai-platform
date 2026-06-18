package com.dyx.market.fulfillment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 / 外部 API 配置，绑定前缀 {@code gateway.config}。
 */
@Data
@ConfigurationProperties(prefix = "gateway.config", ignoreInvalidFields = true)
public class Retrofit2ConfigProperties {

    private boolean enable;
    private String apiHost;

}
