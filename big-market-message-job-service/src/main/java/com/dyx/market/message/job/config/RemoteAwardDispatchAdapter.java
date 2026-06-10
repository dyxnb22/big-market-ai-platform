package com.dyx.market.message.job.config;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.trigger.adapter.IAwardDispatchAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;

/**
 * Phase 2.3-B: remote award dispatch adapter for message-job-service.
 * Active only when account.fulfillment.remote-award.enabled=true.
 * Registered as @Bean by WriteAdapterLocalConfig — do NOT add @Component.
 *
 * RpcException is logged and re-thrown; award dispatch failure must surface.
 * No local fallback — unlike read adapters, a silent swallow here would lose award delivery.
 *
 * Enabling requires: Phase 2.2 staging GO + award credit outbox staging-validated.
 */
@Slf4j
public class RemoteAwardDispatchAdapter implements IAwardDispatchAdapter {

    @DubboReference(interfaceClass = IAwardService.class, version = "1.0", check = false)
    private IAwardService fulfillmentAwardService;

    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception {
        try {
            fulfillmentAwardService.distributeAward(distributeAwardEntity);
            log.info("[RemoteAwardDispatchAdapter] distributeAward remote success userId:{} orderId:{} awardId:{}",
                    distributeAwardEntity.getUserId(), distributeAwardEntity.getOrderId(),
                    distributeAwardEntity.getAwardId());
        } catch (RpcException e) {
            log.error("[RemoteAwardDispatchAdapter] distributeAward RPC failed userId:{} orderId:{} awardId:{}",
                    distributeAwardEntity.getUserId(), distributeAwardEntity.getOrderId(),
                    distributeAwardEntity.getAwardId(), e);
            throw e;
        }
    }

}
