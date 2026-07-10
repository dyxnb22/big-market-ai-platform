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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 积分兑换商品应用服务：编排下单、扣积分、发货与库存补偿。
 */
@Slf4j
@Service
public class CreditPayExchangeApplicationService {

    private static final DateTimeFormatter DATE_FORMAT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;
    @Resource
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    private IActivityRepository activityRepository;

    public void creditPayExchange(SkuProductShopCartRequestDTO request) {
        Long sku = request.getSku();
        log.info("积分兑换商品开始 userId:{} sku:{}", request.getUserId(), sku);
        if (StringUtils.isBlank(request.getUserId()) || null == sku) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
        }
        String outBusinessNo = request.getUserId() + "_" + sku + "_"
                + LocalDate.now().format(DATE_FORMAT_DAY) + "_" + System.currentTimeMillis();

        UnpaidActivityOrderEntity unpaidActivityOrder = accountQuotaWriteAdapter.createOrder(SkuRechargeEntity.builder()
                .userId(request.getUserId())
                .sku(sku)
                .outBusinessNo(outBusinessNo)
                .orderTradeType(OrderTradeTypeVO.credit_pay_trade)
                .build());
        log.info("积分兑换商品，创建订单完成 userId:{} sku:{} outBusinessNo:{}",
                request.getUserId(), sku, unpaidActivityOrder.getOutBusinessNo());

        try {
            String orderId = accountCreditWriteAdapter.createOrder(TradeEntity.builder()
                    .userId(unpaidActivityOrder.getUserId())
                    .tradeName(TradeNameVO.CONVERT_SKU)
                    .tradeType(TradeTypeVO.REVERSE)
                    .amount(unpaidActivityOrder.getPayAmount().negate())
                    .outBusinessNo(unpaidActivityOrder.getOutBusinessNo())
                    .build());
            log.info("积分兑换商品，支付订单完成 userId:{} sku:{} orderId:{}", request.getUserId(), sku, orderId);
        } catch (AppException e) {
            if (!ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) {
                log.warn("积分兑换商品，支付扣积分失败，恢复SKU库存 userId:{} sku:{} outBusinessNo:{}",
                        request.getUserId(), sku, unpaidActivityOrder.getOutBusinessNo());
                restoreActivitySkuStock(sku);
                throw e;
            }
            log.warn("积分兑换商品，支付订单已存在，继续补偿发货 userId:{} sku:{} outBusinessNo:{}",
                    request.getUserId(), sku, unpaidActivityOrder.getOutBusinessNo());
        }

        try {
            accountQuotaWriteAdapter.updateOrder(DeliveryOrderEntity.builder()
                    .userId(unpaidActivityOrder.getUserId())
                    .outBusinessNo(unpaidActivityOrder.getOutBusinessNo())
                    .build());
            log.info("积分兑换商品，发货完成 userId:{} sku:{} outBusinessNo:{}",
                    request.getUserId(), sku, unpaidActivityOrder.getOutBusinessNo());
        } catch (Exception deliveryEx) {
            log.error("积分兑换商品，发货失败，等待补偿任务重试 userId:{} sku:{} outBusinessNo:{}",
                    request.getUserId(), sku, unpaidActivityOrder.getOutBusinessNo(), deliveryEx);
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "积分已扣减，发货处理中，请稍后刷新查看兑换结果");
        }
    }

    private void restoreActivitySkuStock(Long sku) {
        try {
            activityRepository.restoreActivitySkuStock(sku);
        } catch (Exception e) {
            log.error("恢复SKU库存失败 sku:{}", sku, e);
        }
    }
}
