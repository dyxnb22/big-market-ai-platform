package com.dyx.market.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt", ignoreInvalidFields = true)
public class JwtConfigProperties {

    private String secret = "change-me-in-dev-only";

}
