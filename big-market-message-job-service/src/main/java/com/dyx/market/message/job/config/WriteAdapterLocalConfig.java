package com.dyx.market.message.job.config;

import com.dyx.market.trigger.adapter.IAwardDispatchAdapter;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
import com.dyx.market.trigger.adapter.LocalAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.LocalAccountQuotaWriteAdapter;
import com.dyx.market.trigger.adapter.LocalAwardDispatchAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 本地写适配器 Bean 提供者（message-job-service）。
 * <p>
 * 使用 {@code @Bean} + {@code @ConditionalOnMissingBean} 的 {@code @Configuration} 类
 * 管理 message-job 的本地/远程写适配器，具体实现由 Spring Profile 选择。
 * <p>
 * {@code dev/local/test} 使用本地服务委托，{@code docker} 使用 account-service Dubbo。
 */
@Configuration
public class WriteAdapterLocalConfig {

    @Bean
    @Profile({"dev", "local", "test"})
    @ConditionalOnMissingBean(IAccountQuotaWriteAdapter.class)
    public LocalAccountQuotaWriteAdapter localAccountQuotaWriteAdapter() {
        return new LocalAccountQuotaWriteAdapter();
    }

    @Bean
    @Profile({"dev", "local", "test"})
    @ConditionalOnMissingBean(IAccountCreditWriteAdapter.class)
    public LocalAccountCreditWriteAdapter localAccountCreditWriteAdapter() {
        return new LocalAccountCreditWriteAdapter();
    }

    @Bean
    @Profile("docker")
    public AccountRemoteCreditWriteAdapter accountRemoteCreditWriteAdapter() {
        return new AccountRemoteCreditWriteAdapter();
    }

    @Bean
    @Profile("docker")
    public AccountRemoteQuotaWriteAdapter accountRemoteQuotaWriteAdapter() {
        return new AccountRemoteQuotaWriteAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(IAwardDispatchAdapter.class)
    public LocalAwardDispatchAdapter localAwardDispatchAdapter() {
        return new LocalAwardDispatchAdapter();
    }

}
