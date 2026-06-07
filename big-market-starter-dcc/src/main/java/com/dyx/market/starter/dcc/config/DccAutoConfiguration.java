package com.dyx.market.starter.dcc.config;

import com.dyx.market.starter.dcc.support.DccValueBeanPostProcessor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Order(1)
@Configuration
@EnableConfigurationProperties(DccProperties.class)
@ConditionalOnProperty(value = "zookeeper.sdk.config.enable", havingValue = "true", matchIfMissing = false)
public class DccAutoConfiguration {

    @Bean(name = "zookeeperClient")
    public CuratorFramework zookeeperClient(DccProperties properties) {
        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(properties.getBaseSleepTimeMs(), properties.getMaxRetries());
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(properties.getConnectString())
                .retryPolicy(retryPolicy)
                .sessionTimeoutMs(properties.getSessionTimeoutMs())
                .connectionTimeoutMs(properties.getConnectionTimeoutMs())
                .build();
        client.start();
        return client;
    }

    @Bean(name = "dccValueBeanPostProcessor")
    public DccValueBeanPostProcessor dccValueBeanPostProcessor(CuratorFramework zookeeperClient) throws Exception {
        return new DccValueBeanPostProcessor(zookeeperClient);
    }
}
