package com.dyx.market.message.job.config;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.trigger.adapter.IAwardDispatchAdapter;
import com.dyx.market.trigger.api.IFulfillmentAwardService;
import com.dyx.market.trigger.api.dto.FulfillmentDistributeAwardRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;

/**
 * remote award dispatch adapter for message-job-service.
 * Active only when account.fulfillment.remote-award.enabled=true.
 * Registered as @Bean by WriteAdapterLocalConfig — do NOT add @Component.
 *
 * Calls the fulfillment-service Dubbo provider via the API contract
 * {@link IFulfillmentAwardService} from big-market-api.
 *
 * RpcException is logged and re-thrown; award dispatch failure must surface.
 * No local fallback — unlike read adapters, a silent swallow here would lose award delivery.
 */
@Slf4j
public class RemoteAwardDispatchAdapter implements IAwardDispatchAdapter {

    @DubboReference(interfaceClass = IFulfillmentAwardService.class, version = "1.0", check = false)
    private IFulfillmentAwardService fulfillmentAwardService;

    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception {
        FulfillmentDistributeAwardRequestDTO request = FulfillmentDistributeAwardRequestDTO.builder()
                .userId(distributeAwardEntity.getUserId())
                .orderId(distributeAwardEntity.getOrderId())
                .awardId(distributeAwardEntity.getAwardId())
                .awardConfig(distributeAwardEntity.getAwardConfig())
                .build();
        try {
            Response<Void> response = fulfillmentAwardService.distributeAward(request);
            if (response == null || !ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                String code = response == null ? "NULL" : response.getCode();
                String info = response == null ? "null response" : response.getInfo();
                log.error("[RemoteAwardDispatchAdapter] distributeAward remote failed userId:{} orderId:{} awardId:{} code:{} info:{}",
                        distributeAwardEntity.getUserId(), distributeAwardEntity.getOrderId(),
                        distributeAwardEntity.getAwardId(), code, info);
                throw new RpcException("distributeAward failed: code=" + code + " info=" + info);
            }
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
