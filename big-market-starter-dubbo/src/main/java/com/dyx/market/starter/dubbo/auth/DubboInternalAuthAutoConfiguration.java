package com.dyx.market.starter.dubbo.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

/**
 * Dubbo 内部 RPC 鉴权自动配置，将 {@link InternalRpcAuthProperties} 注入消费端与提供端过滤器。
 */
@Configuration
@ConditionalOnClass(name = "org.apache.dubbo.rpc.Filter")
@EnableConfigurationProperties(InternalRpcAuthProperties.class)
public class DubboInternalAuthAutoConfiguration {

    @Resource
    private InternalRpcAuthProperties properties;

    @PostConstruct
    public void wireFilters() {
        DubboInternalAuthProviderFilter.configure(properties);
        DubboInternalAuthConsumerFilter.configure(properties);
    }
}
