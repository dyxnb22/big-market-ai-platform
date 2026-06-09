package com.dyx.market.market.config;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.AccountQuotaCreateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaUpdateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.UnpaidActivityOrderResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class AccountRemoteQuotaWriteAdapter implements IAccountQuotaWriteAdapter {

    @Value("${account.service.remote-quota-write.enabled:false}")
    private boolean remoteQuotaWriteEnabled;

    @Resource
    private IRaffleActivityAccountQuotaService raffleActivityAccountQuotaService;

    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @Override
    public UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity) {
        if (remoteQuotaWriteEnabled) {
            try {
                Response<UnpaidActivityOrderResponseDTO> resp = accountQuotaService.createOrder(AccountQuotaCreateOrderRequestDTO.builder()
                        .userId(skuRechargeEntity.getUserId())
                        .sku(skuRechargeEntity.getSku())
                        .outBusinessNo(skuRechargeEntity.getOutBusinessNo())
                        .orderTradeType(skuRechargeEntity.getOrderTradeType().getCode())
                        .build());
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[AccountRemoteQuotaWriteAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                            skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo());
                    return toEntity(resp.getData());
                }
                log.warn("[AccountRemoteQuotaWriteAdapter] createOrder non-success code:{} userId:{} outBusinessNo:{}",
                        resp != null ? resp.getCode() : null, skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo());
            } catch (Exception e) {
                log.error("[AccountRemoteQuotaWriteAdapter] createOrder remote failed, falling back to local userId:{} outBusinessNo:{}",
                        skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo(), e);
            }
        }
        return raffleActivityAccountQuotaService.createOrder(skuRechargeEntity);
    }

    @Override
    public void updateOrder(DeliveryOrderEntity deliveryOrderEntity) {
        if (remoteQuotaWriteEnabled) {
            try {
                Response<Boolean> resp = accountQuotaService.updateOrder(AccountQuotaUpdateOrderRequestDTO.builder()
                        .userId(deliveryOrderEntity.getUserId())
                        .outBusinessNo(deliveryOrderEntity.getOutBusinessNo())
                        .build());
                if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                    log.info("[AccountRemoteQuotaWriteAdapter] updateOrder remote success userId:{} outBusinessNo:{}",
                            deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo());
                    return;
                }
                log.warn("[AccountRemoteQuotaWriteAdapter] updateOrder non-success code:{} userId:{} outBusinessNo:{}",
                        resp != null ? resp.getCode() : null, deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo());
            } catch (Exception e) {
                log.error("[AccountRemoteQuotaWriteAdapter] updateOrder remote failed, falling back to local userId:{} outBusinessNo:{}",
                        deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo(), e);
            }
        }
        raffleActivityAccountQuotaService.updateOrder(deliveryOrderEntity);
    }

    private UnpaidActivityOrderEntity toEntity(UnpaidActivityOrderResponseDTO dto) {
        if (dto == null) {
            return null;
        }
        return UnpaidActivityOrderEntity.builder()
                .userId(dto.getUserId())
                .orderId(dto.getOrderId())
                .outBusinessNo(dto.getOutBusinessNo())
                .payAmount(dto.getPayAmount())
                .build();
    }

}
