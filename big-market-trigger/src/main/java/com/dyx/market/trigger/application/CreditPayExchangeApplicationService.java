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

import jakarta.annotation.Resource;

/**
 * 积分兑换商品应用服务：编排下单、扣积分、发货与库存补偿。
 */
@Slf4j
@Service
public class CreditPayExchangeApplicationService {

    @Resource
    /** 账户额度服务适配器，负责创建兑换订单并完成发货/履约状态推进。 */
    private IAccountQuotaWriteAdapter accountQuotaWriteAdapter;
    @Resource
    /** 账户积分写适配器，负责兑换场景的扣积分交易。 */
    private IAccountCreditWriteAdapter accountCreditWriteAdapter;
    @Resource
    /** 活动仓储，用于恢复 SKU 库存并查询已创建的兑换订单。 */
    private IActivityRepository activityRepository;

    /**
     * 编排一次积分兑换：创建额度订单 → 扣减积分 → 推进发货状态。
     *
     * <p>业务幂等号由用户、SKU 和请求 ID 组成。明确拒绝扣积分时恢复已预占的 SKU；
     * 远程调用返回未知结果时保留预占，等待账户出账续作或补偿任务确认，避免重复扣积分
     * 或错误释放库存。</p>
     *
     * @param request 兑换请求，包含用户 ID、SKU 和请求幂等 ID
     */
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

    /**
     * 账户服务确认扣积分后继续推进兑换发货。
     *
     * @param userId 用户 ID
     * @param outBusinessNo 兑换交易业务幂等号
     */
    public void continueAfterRemoteCreditCreated(String userId, String outBusinessNo) {
        UnpaidActivityOrderEntity unpaid = activityRepository.queryQuotaOrderByOutBusinessNo(userId, outBusinessNo);
        if (unpaid == null) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(),
                    "兑换订单不存在，无法继续发货: " + outBusinessNo);
        }
        accountQuotaWriteAdapter.updateOrder(DeliveryOrderEntity.builder()
                .userId(userId).outBusinessNo(outBusinessNo).build());
    }

    /**
     * 执行扣积分与发货状态推进。
     *
     * <p>扣积分重复时直接进入发货补偿；扣积分明确失败时恢复 SKU 预占；扣积分结果未知
     * 时保留订单和库存预占，交由后续 outbox 续作。发货失败不回滚已扣积分，而是抛出可由
     * 补偿任务继续处理的异常。</p>
     */
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
                // UNKNOWN 表示远程扣积分可能已经提交；保留库存预占，直到积分 outbox 续作确认结果。
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

    /** 按用户、SKU 和请求幂等 ID 构造兑换交易号。 */
    public static String buildOutBusinessNo(String userId, Long sku, String requestId) {
        return userId + "_" + sku + "_" + requestId;
    }

    /** 尝试恢复 SKU 库存；恢复失败交由后续补偿流程处理，不覆盖原始业务异常。 */
    private void restoreActivitySkuStock(Long sku, String reservationId) {
        try {
            activityRepository.restoreActivitySkuStock(sku, reservationId);
        } catch (Exception e) {
            log.error("恢复SKU库存失败 sku:{}", sku, e);
        }
    }
}
