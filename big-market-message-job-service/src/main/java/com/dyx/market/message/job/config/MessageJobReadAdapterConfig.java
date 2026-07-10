package com.dyx.market.message.job.config;

import com.dyx.market.domain.rebate.service.IBehaviorRebateService;
import com.dyx.market.trigger.adapter.IRebateOrderAdapter;
import com.dyx.market.trigger.adapter.IRebateReadAdapter;
import com.dyx.market.trigger.adapter.LocalStrategyReadAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * message-job 本地读适配器（BM-002）：供 application 层签到/策略查询等使用。
 */
@Configuration
@Import(LocalStrategyReadAdapter.class)
public class MessageJobReadAdapterConfig {

    @Bean
    @ConditionalOnMissingBean(IRebateOrderAdapter.class)
    public IRebateOrderAdapter rebateOrderAdapter(IBehaviorRebateService behaviorRebateService) {
        return behaviorEntity -> behaviorRebateService.createOrder(behaviorEntity);
    }

    @Bean
    @ConditionalOnMissingBean(IRebateReadAdapter.class)
    public IRebateReadAdapter rebateReadAdapter(IBehaviorRebateService behaviorRebateService) {
        return (userId, outBusinessNo) ->
                !behaviorRebateService.queryOrderByOutBusinessNo(userId, outBusinessNo).isEmpty();
    }
}
