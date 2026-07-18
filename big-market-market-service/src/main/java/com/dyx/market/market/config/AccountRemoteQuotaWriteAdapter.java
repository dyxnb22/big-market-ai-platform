package com.dyx.market.market.config;

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
import com.dyx.market.types.enums.RemoteWriteOutcome;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;

import jakarta.annotation.Resource;

/**
 * 活动配额写路径：REJECTED 不入 pending；UNKNOWN 先 exists 探测再入 pending。
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
            RemoteWriteOutcome outcome = classify(resp);
            if (outcome == RemoteWriteOutcome.SUCCESS) {
                log.info("[AccountRemoteQuotaWriteAdapter] createOrder remote success userId:{} outBusinessNo:{}",
                        skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo());
                return toEntity(resp.getData());
            }
            if (outcome == RemoteWriteOutcome.REJECTED) {
                throw new AppException(resp.getCode(), resp.getInfo() != null ? resp.getInfo() : "远程额度订单被拒绝");
            }
            log.warn("[AccountRemoteQuotaWriteAdapter] createOrder unknown code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null, skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AccountRemoteQuotaWriteAdapter] createOrder remote failed userId:{} outBusinessNo:{}",
                    skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo(), e);
            UnpaidActivityOrderEntity recovered = recoverCreateIfExists(skuRechargeEntity);
            if (recovered != null) {
                return recovered;
            }
        }
        UnpaidActivityOrderEntity recovered = recoverCreateIfExists(skuRechargeEntity);
        if (recovered != null) {
            return recovered;
        }
        if (!pendingRemoteWriteSupport.enqueue(skuRechargeEntity.getOutBusinessNo(), RemoteWriteOperations.QUOTA_CREATE, request, skuRechargeEntity.getUserId())) {
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
            RemoteWriteOutcome outcome = classify(resp);
            if (outcome == RemoteWriteOutcome.SUCCESS) {
                log.info("[AccountRemoteQuotaWriteAdapter] updateOrder remote success userId:{} outBusinessNo:{}",
                        deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo());
                return;
            }
            if (outcome == RemoteWriteOutcome.REJECTED) {
                throw new AppException(resp.getCode(), resp.getInfo() != null ? resp.getInfo() : "远程额度发货被拒绝");
            }
            log.warn("[AccountRemoteQuotaWriteAdapter] updateOrder unknown code:{} userId:{} outBusinessNo:{}",
                    resp != null ? resp.getCode() : null, deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AccountRemoteQuotaWriteAdapter] updateOrder remote failed userId:{} outBusinessNo:{}",
                    deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo(), e);
            if (remoteUpdateAlreadySucceeded(deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo())) {
                return;
            }
        }
        if (remoteUpdateAlreadySucceeded(deliveryOrderEntity.getUserId(), deliveryOrderEntity.getOutBusinessNo())) {
            return;
        }
        if (!pendingRemoteWriteSupport.enqueue(deliveryOrderEntity.getOutBusinessNo(), RemoteWriteOperations.QUOTA_UPDATE, request, deliveryOrderEntity.getUserId())) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程额度发货写入失败，补偿任务参数无效");
        }
        throw new AppException(ResponseCode.UN_ERROR.getCode(), "远程额度发货写入失败，已记录待对账任务");
    }

    private UnpaidActivityOrderEntity recoverCreateIfExists(SkuRechargeEntity skuRechargeEntity) {
        try {
            Response<UnpaidActivityOrderResponseDTO> response = accountQuotaService.queryActivityOrder(
                    skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo());
            if (response != null && ResponseCode.SUCCESS.getCode().equals(response.getCode())
                    && response.getData() != null) {
                return toEntity(response.getData());
            }
        } catch (Exception probeEx) {
            log.warn("[AccountRemoteQuotaWriteAdapter] existsActivityOrder probe failed userId:{} outBusinessNo:{}",
                    skuRechargeEntity.getUserId(), skuRechargeEntity.getOutBusinessNo(), probeEx);
        }
        return null;
    }

    private boolean remoteUpdateAlreadySucceeded(String userId, String outBusinessNo) {
        try {
            Response<Boolean> done = accountQuotaService.isActivityOrderCompleted(userId, outBusinessNo);
            return done != null
                    && ResponseCode.SUCCESS.getCode().equals(done.getCode())
                    && Boolean.TRUE.equals(done.getData());
        } catch (Exception probeEx) {
            log.warn("[AccountRemoteQuotaWriteAdapter] isActivityOrderCompleted probe failed userId:{} outBusinessNo:{}",
                    userId, outBusinessNo, probeEx);
            return false;
        }
    }

    static RemoteWriteOutcome classify(Response<?> resp) {
        if (resp == null) {
            return RemoteWriteOutcome.UNKNOWN;
        }
        if (ResponseCode.SUCCESS.getCode().equals(resp.getCode())
                || ResponseCode.INDEX_DUP.getCode().equals(resp.getCode())) {
            return RemoteWriteOutcome.SUCCESS;
        }
        if (ResponseCode.ILLEGAL_PARAMETER.getCode().equals(resp.getCode())
                || ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode().equals(resp.getCode())) {
            return RemoteWriteOutcome.REJECTED;
        }
        return RemoteWriteOutcome.UNKNOWN;
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
