package com.dyx.market.domain.activity.service;

import com.dyx.market.domain.activity.model.entity.*;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动账户额度服务
 * @create 2024-03-16 08:38
 */
public interface IRaffleActivityAccountQuotaService {

    /**
     * 创建 sku 账户充值订单，给用户增加抽奖次数
     * <p>
     * 1. 在【打卡、签到、分享、对话、积分兑换】等行为动作下，创建出活动订单，给用户的活动账户【日、月】充值可用的抽奖次数。
     * 2. 对于用户可获得的抽奖次数，比如首次进来就有一次，则是依赖于运营配置的动作，在前端页面上。用户点击后，可以获得一次抽奖次数。
     *
     * @param skuRechargeEntity 活动商品充值实体对象
     * @return 未支付订单
     */
    UnpaidActivityOrderEntity createOrder(SkuRechargeEntity skuRechargeEntity);

    /**
     * 订单出货 - 积分充值
     * @param deliveryOrderEntity 出货单实体对象
     */
    void updateOrder(DeliveryOrderEntity deliveryOrderEntity);

    /**
     * 查询活动账户 - 总，参与次数
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 参与次数
     */
    Integer queryRaffleActivityAccountPartakeCount(Long activityId, String userId);

    /**
     * 查询活动账户 - 日，参与次数
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 参与次数
     */
    Integer queryRaffleActivityAccountDayPartakeCount(Long activityId, String userId);

    /**
     * 查询活动账户额度「总、月、日」
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 账户实体
     */
    ActivityAccountEntity queryActivityAccountEntity(Long activityId, String userId);

    /**
     * 在幂等账本保护下同步扣减总/月/日三类活动额度。
     *
     * <p>由 account-service 的 {@code AccountQuotaServiceRPC.decrementQuota} 调用；
     * 账本表使 RPC 重试不会重复扣减。</p>
     *
     * @param userId 用户 ID，也是分库键
     * @param activityId 活动 ID
     * @param outBusinessNo 幂等键，通常为抽奖订单的业务幂等号
     * @return 本次扣减成功或已扣减返回 true；额度不足返回 false
     */
    boolean decrementQuota(String userId, Long activityId, String outBusinessNo);

    /**
     * 回滚先前扣减的一次活动额度（Saga 补偿）。
     *
     * <p>由幂等账本保护；即使原扣减从未真正执行也可以安全调用。相同幂等键重复回滚时
     * 返回 true，不会重复恢复额度。</p>
     *
     * @param userId 用户 ID，也是分库键
     * @param activityId 活动 ID
     * @param outBusinessNo 幂等键，必须与 decrementQuota 使用相同的值
     * @return 已回滚、已回滚过或没有账本记录返回 true；基础设施失败返回 false
     */
    boolean rollbackQuota(String userId, Long activityId, String outBusinessNo);

}
