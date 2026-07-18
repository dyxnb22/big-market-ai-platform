package com.dyx.market.trigger.application;

import com.dyx.market.domain.activity.adapter.repository.IActivityRepository;
import com.dyx.market.domain.activity.model.entity.DeliveryOrderEntity;
import com.dyx.market.domain.activity.model.entity.SkuRechargeEntity;
import com.dyx.market.domain.activity.model.entity.UnpaidActivityOrderEntity;
import com.dyx.market.domain.activity.model.valobj.OrderTradeTypeVO;
import com.dyx.market.domain.credit.model.entity.TradeEntity;
import com.dyx.market.domain.credit.model.valobj.TradeNameVO;
import com.dyx.market.domain.credit.model.valobj.TradeTypeVO;
import com.dyx.market.trigger.adapter.IAccountCreditWriteAdapter;
import com.dyx.market.trigger.adapter.IAccountQuotaWriteAdapter;
import com.dyx.market.trigger.api.dto.SkuProductShopCartRequestDTO;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 积分兑换商品应用服务：编排下单、扣积分、发货与库存补偿。
 */
@Slf4j
@Service
public class CreditPayExchangeApplicationService {

    @Resource
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    private IActivityRepository activityRepository;

    public void creditPayExchange(SkuProductShopCartRequestDTO request) {
        Long sku = request.getSku();
        log.info("积分兑换商品开始 userId:{} sku:{}", request.getUserId(), sku);
        if (StringUtils.isBlank(request.getUserId()) || null == sku || StringUtils.isBlank(request.getRequestId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        String outBusinessNo = buildOutBusinessNo(request.getUserId(), sku, request.getRequestId());

        UnpaidActivityOrderEntity unpaidActivityOrder = accountQuotaWriteAdapter.createOrder(SkuRechargeEntity.builder()
                .userId(request.getUserId())
                .sku(sku)
                .outBusinessNo(outBusinessNo)
                .orderTradeType(OrderTradeTypeVO.credit_pay_trade)
                .build());
        log.info("积分兑换商品，创建订单完成 userId:{} sku:{} outBusinessNo:{}",
                request.getUserId(), sku, unpaidActivityOrder.getOutBusinessNo());

        payAndDeliver(unpaidActivityOrder, sku, request.getUserId());
    }

    /**
     * 远程 quota 对账成功后继续扣积分与发货（NR-006 continuation）。
     */
    public void continueAfterRemoteQuotaCreated(String userId, String outBusinessNo, Long sku) {
        UnpaidActivityOrderEntity unpaid = accountQuotaWriteAdapter.createOrder(SkuRechargeEntity.builder()
                .userId(userId)
                .sku(sku)
                .outBusinessNo(outBusinessNo)
                .orderTradeType(OrderTradeTypeVO.credit_pay_trade)
                .build());
        payAndDeliver(unpaid, sku, userId);
    }

    /** Continue a conversion after account-service confirmed the credit debit. */
    public void continueAfterRemoteCreditCreated(String userId, String outBusinessNo) {
        UnpaidActivityOrderEntity unpaid = activityRepository.queryQuotaOrderByOutBusinessNo(userId, outBusinessNo);
        if (unpaid == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "兑换订单不存在，无法继续发货: " + outBusinessNo);
        }
        accountQuotaWriteAdapter.updateOrder(DeliveryOrderEntity.builder()
                .userId(userId).outBusinessNo(outBusinessNo).build());
    }

    private void payAndDeliver(UnpaidActivityOrderEntity unpaidActivityOrder, Long sku, String userId) {
        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(unpaidActivityOrder.getUserId())
                    .tradeName(TradeNameVO.CONVERT_SKU)
                    .tradeType(TradeTypeVO.REVERSE)
                    .amount(unpaidActivityOrder.getPayAmount() != null
                            ? unpaidActivityOrder.getPayAmount().negate()
                            : null)
                    .outBusinessNo(unpaidActivityOrder.getOutBusinessNo())
                    .build());
            log.info("积分兑换商品，支付订单完成 userId:{} sku:{} orderId:{}", userId, sku, orderId);
        } catch (AppException e) {
            if (!ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                // UNKNOWN means the remote debit may already have committed. Keep
                // the reservation until the credit outbox continuation resolves it.
                if (!ResponseCode.UN_ERROR.getCode().equals(e.getCode())) {
                    log.warn("积分兑换商品，明确拒绝扣积分，恢复SKU库存 userId:{} sku:{} outBusinessNo:{}",
                            userId, sku, unpaidActivityOrder.getOutBusinessNo());
                    restoreActivitySkuStock(sku, unpaidActivityOrder.getOutBusinessNo());
                }
                throw e;
            }
            log.warn("积分兑换商品，支付订单已存在，继续补偿发货 userId:{} sku:{} outBusinessNo:{}",
                    userId, sku, unpaidActivityOrder.getOutBusinessNo());
        }

        try {
            accountQuotaWriteAdapter.updateOrder(DeliveryOrderEntity.builder()
                    .userId(unpaidActivityOrder.getUserId())
                    .outBusinessNo(unpaidActivityOrder.getOutBusinessNo())
                    .build());
            log.info("积分兑换商品，发货完成 userId:{} sku:{} outBusinessNo:{}",
                    userId, sku, unpaidActivityOrder.getOutBusinessNo());
        } catch (Exception deliveryEx) {
            log.error("积分兑换商品，发货失败，等待补偿任务重试 userId:{} sku:{} outBusinessNo:{}",
                    userId, sku, unpaidActivityOrder.getOutBusinessNo(), deliveryEx);
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "积分已扣减，发货处理中，请稍后刷新查看兑换结果");
        }
    }

    public static String buildOutBusinessNo(String userId, Long sku, String requestId) {
        return userId + "_" + sku + "_" + requestId;
    }

    private void restoreActivitySkuStock(Long sku, String reservationId) {
        try {
            activityRepository.restoreActivitySkuStock(sku, reservationId);
        } catch (Exception e) {
            log.error("恢复SKU库存失败 sku:{}", sku, e);
        }
    }
}
