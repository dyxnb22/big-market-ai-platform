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
 * Phase 2.2-B2: provides local write adapter beans for message-job-service.
 *
 * Using @Bean + @ConditionalOnMissingBean in a @Configuration class (rather than
 * @Component on the adapter classes themselves) ensures reliable evaluation order:
 * the @ConditionalOnProperty-guarded remote adapters are evaluated during component
 * scanning; by the time this @Configuration's @Bean methods are processed, the
 * remote adapter beans are either registered (flag=true) or absent (flag=false).
 *
 * When remote adapters are absent (default, flag=false), these @Bean methods fire
 * and register the local service-delegate adapters. When remote adapters are present
 * (flag=true), @ConditionalOnMissingBean suppresses these methods.
 *
 * Spring's CommonAnnotationBeanPostProcessor processes @Resource fields on beans
 * created here exactly as it would for @Component-registered beans.
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
