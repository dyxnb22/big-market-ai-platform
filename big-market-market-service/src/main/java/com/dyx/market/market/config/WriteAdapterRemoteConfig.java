package com.dyx.market.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 远程写适配器 Bean 提供者（market-service）。
 * <p>
 * Docker Profile 下统一注册 account-service Dubbo 写适配器；本地 Profile 由 trigger
 * 模块的 Local*Adapter 提供本地实现。
 */
@Configuration
@Profile("docker")
public class WriteAdapterRemoteConfig {

    @Bean
    public AccountRemoteCreditWriteAdapter accountRemoteCreditWriteAdapter() {
        return new AccountRemoteCreditWriteAdapter();
    }

    @Bean
    public AccountRemoteQuotaWriteAdapter accountRemoteQuotaWriteAdapter() {
        return new AccountRemoteQuotaWriteAdapter();
    }

}
