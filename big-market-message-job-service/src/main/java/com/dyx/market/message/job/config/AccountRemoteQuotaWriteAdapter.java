package com.dyx.market.message.job.config;

import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.infrastructure.adapter.repository.PendingRemoteWriteSupport;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
import com.dyx.market.trigger.api.IAccountQuotaService;
import com.dyx.market.trigger.api.dto.AccountQuotaCreateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.AccountQuotaUpdateOrderRequestDTO;
import com.dyx.market.trigger.api.dto.UnpaidActivityOrderResponseDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.common.RemoteWriteOperations;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;

import javax.annotation.Resource;

/**
 * 远程活动额度写适配器（message-job-service）：失败写 pending，不回退本地。
 */
@Slf4j
public class AccountRemoteQuotaWriteAdapter implements IAccountQuotaWriteAdapter {

    @Resource
    private PendingRemoteWriteSupport pendingRemoteWriteSupport;

    @DubboReference(version = "1.0", check = false)
    private IAccountQuotaService accountQuotaService;

    @Override
    public UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity) {
        AccountQuotaCreateOrderRequestDTO request = AccountQuotaCreateOrderRequestDTO.builder()
                .userId(skuRechargeEntity.getUserId())
                .sku(skuRechargeEntity.getSku())
                .outBusinessNo(skuRechargeEntity.getOutBusinessNo())
                .orderTradeType(skuRechargeEntity.getOrderTradeType().getCode())
                .build();
        try {
            Response<UnpaidActivityOrderResponseDTO> resp = accountQuotaService.createOrder(request);
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[AccountRemoteQuotaWriteAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                        skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo());
                return toEntity(resp.getData());
            }
            log.warn("[AccountRemoteQuotaWriteAdapter] createOrder non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null, skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo());
        } catch (Exception e) {
            log.error("[AccountRemoteQuotaWriteAdapter] createOrder remote failed userId:{} outBusinessNo:{}",
                    skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo(), e);
        }
        if (!pendingRemoteWriteSupport.enqueue(skuRechargeEntity.getOutBusinessNo(), RemoteWriteOperations.QUOTA_CREATE, request)) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程额度订单写入失败，补偿任务参数无效");
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程额度订单写入失败，已记录待对账任务");
    }

    @Override
    public void updateOrder(DeliveryOrderEntity deliveryOrderEntity) {
        AccountQuotaUpdateOrderRequestDTO request = AccountQuotaUpdateOrderRequestDTO.builder()
                .userId(deliveryOrderEntity.getUserId())
                .outBusinessNo(deliveryOrderEntity.getOutBusinessNo())
                .build();
        try {
            Response<Boolean> resp = accountQuotaService.updateOrder(request);
            if (resp != null && ResponseCode.SUCCESS.getCode().equals(resp.getCode())) {
                log.info("[AccountRemoteQuotaWriteAdapter] updateOrder remote success userId:{} outBusinessNo:{}",
                        deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo());
                return;
            }
            log.warn("[AccountRemoteQuotaWriteAdapter] updateOrder non-success code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null, deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo());
        } catch (Exception e) {
            log.error("[AccountRemoteQuotaWriteAdapter] updateOrder remote failed userId:{} outBusinessNo:{}",
                    deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo(), e);
        }
        if (!pendingRemoteWriteSupport.enqueue(deliveryOrderEntity.getOutBusinessNo(), RemoteWriteOperations.QUOTA_UPDATE, request)) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程额度发货写入失败，补偿任务参数无效");
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程额度发货写入失败，已记录待对账任务");
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
