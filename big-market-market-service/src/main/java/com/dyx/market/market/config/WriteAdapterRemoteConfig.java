package com.dyx.market.market.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 远程写适配器 Bean 提供者（market-service）。
 * <p>
 * 对齐 message-job {@link com.dyx.market.message.job.config.WriteAdapterLocalConfig}：
 * 带 {@code @ConditionalOnProperty} 的远程 Bean 在本配置类注册；
 * trigger 模块 {@code Local*Adapter} 在远程 Bean 不存在时通过 {@code @ConditionalOnMissingBean} 回退。
 */
@Configuration
public class WriteAdapterRemoteConfig {

    @Bean
    @ConditionalOnProperty(name = "account.service.remote-credit-write.enabled", havingValue = "true")
    public AccountRemoteCreditWriteAdapter accountRemoteCreditWriteAdapter() {
        return new AccountRemoteCreditWriteAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "account.service.remote-quota-write.enabled", havingValue = "true")
    public AccountRemoteQuotaWriteAdapter accountRemoteQuotaWriteAdapter() {
        return new AccountRemoteQuotaWriteAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "rebate.service.remote-create-order.enabled", havingValue = "true")
    public RebateRemoteCreateOrderAdapter rebateRemoteCreateOrderAdapter() {
        return new RebateRemoteCreateOrderAdapter();
    }
}
