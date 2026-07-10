package com.dyx.market.starter.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResponseHttpStatusAutoConfiguration {

    @Bean
    public ResponseHttpStatusAdvice responseHttpStatusAdvice() {
        return new ResponseHttpStatusAdvice();
    }
}
