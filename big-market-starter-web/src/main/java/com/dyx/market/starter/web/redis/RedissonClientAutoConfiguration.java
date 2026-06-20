package com.dyx.market.starter.web.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties(RedissonSdkProperties.class)
public class RedissonClientAutoConfiguration {

    @Bean(name = "redissonClient")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedissonSdkProperties properties) {
        Config config = new Config();
        config.setCodec(JsonJacksonCodec.INSTANCE);
        RedissonSingleServerSupport.apply(config, properties);
        return Redisson.create(config);
    }
}
