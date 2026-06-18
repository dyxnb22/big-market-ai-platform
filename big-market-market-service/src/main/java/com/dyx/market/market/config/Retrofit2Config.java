package com.dyx.market.market.config;

import com.dyx.market.infrastructure.gateway.IOpenAIAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Retrofit2 HTTP 客户端配置，用于调用 OpenAI 等外部网关 API。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(Retrofit2ConfigProperties.class)
public class Retrofit2Config {

    @Bean
    public Retrofit retrofit(Retrofit2ConfigProperties properties) {
        return new Retrofit.Builder()
                .baseUrl(properties.getApiHost())
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
    }

    @Bean
    public IOpenAIAccountService openAIAccountService(Retrofit retrofit) {
        return retrofit.create(IOpenAIAccountService.class);
    }

}
