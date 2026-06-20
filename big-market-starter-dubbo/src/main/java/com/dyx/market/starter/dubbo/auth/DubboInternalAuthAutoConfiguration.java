package com.dyx.market.starter.dubbo.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

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
