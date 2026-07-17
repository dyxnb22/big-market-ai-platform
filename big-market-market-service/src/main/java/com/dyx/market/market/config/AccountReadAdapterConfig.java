package com.dyx.market.market.config;

import com.dyx.market.trigger.adapter.IAccountReadAdapter;
import com.dyx.market.trigger.adapter.RemoteAccountReadAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** Docker Profile 下统一使用 account-service 的远程账户读取实现。 */
@Configuration
@Profile("docker")
public class AccountReadAdapterConfig {

    @Bean
    @Primary
    public IAccountReadAdapter remoteAccountReadAdapter() {
        return new RemoteAccountReadAdapter();
    }
}
