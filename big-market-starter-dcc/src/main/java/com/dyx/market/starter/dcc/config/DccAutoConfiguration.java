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

/**
 * 动态配置中心（DCC）自动配置。
 *
 * <p>当 {@code zookeeper.sdk.config.enable=true} 时，
 * 注册 Zookeeper 客户端及 {@link DccValueBeanPostProcessor}。</p>
 */
@Order(1)
@Configuration
@EnableConfigurationProperties(DccProperties.class)
@ConditionalOnProperty(value = "zookeeper.sdk.config.enable", havingValue = "true", matchIfMissing = false)
public class DccAutoConfiguration {

    /** 创建并启动 Curator Zookeeper 客户端。 */
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

    /** 注册 DCC 字段注入与热更新处理器。 */
    @Bean(name = "dccValueBeanPostProcessor")
    public DccValueBeanPostProcessor dccValueBeanPostProcessor(CuratorFramework zookeeperClient) throws Exception {
        return new DccValueBeanPostProcessor(zookeeperClient);
    }
}
