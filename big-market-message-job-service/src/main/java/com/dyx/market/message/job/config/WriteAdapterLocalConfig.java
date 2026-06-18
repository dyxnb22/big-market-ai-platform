package com.dyx.market.message.job.config;

import com.dyx.market.trigger.adapter.IAwardDispatchAdapter;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
import com.dyx.market.trigger.adapter.LocalAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.LocalAccountQuotaWriteAdapter;
import com.dyx.market.trigger.adapter.LocalAwardDispatchAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地写适配器 Bean 提供者（message-job-service）。
 * <p>
 * 使用 {@code @Bean} + {@code @ConditionalOnMissingBean} 的 {@code @Configuration} 类
 * （而非在适配器类上加 {@code @Component}），确保评估顺序可靠：
 * 带 {@code @ConditionalOnProperty} 的远程适配器在组件扫描阶段评估；
 * 本配置类的 {@code @Bean} 方法执行时，远程 Bean 已注册（flag=true）或不存在（flag=false）。
 * <p>
 * 远程适配器不存在时（默认 flag=false），本类方法注册本地服务委托适配器；
 * 远程适配器存在时（flag=true），{@code @ConditionalOnMissingBean} 抑制本类方法。
 */
@Configuration
public class WriteAdapterLocalConfig {

    @Bean
    @ConditionalOnMissingBean(IAccountQuotaWriteAdapter.class)
    public LocalAccountQuotaWriteAdapter localAccountQuotaWriteAdapter() {
        return new LocalAccountQuotaWriteAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(IAccountCreditWriteAdapter.class)
    public LocalAccountCreditWriteAdapter localAccountCreditWriteAdapter() {
        return new LocalAccountCreditWriteAdapter();
    }

    @Bean
    @ConditionalOnProperty(name = "account.fulfillment.remote-award.enabled", havingValue = "true")
    public RemoteAwardDispatchAdapter remoteAwardDispatchAdapter() {
        return new RemoteAwardDispatchAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(IAwardDispatchAdapter.class)
    public LocalAwardDispatchAdapter localAwardDispatchAdapter() {
        return new LocalAwardDispatchAdapter();
    }

}
