package com.dyx.market.trigger.api;

import com.dyx.market.trigger.api.dto.FulfillmentDistributeAwardRequestDTO;
import com.dyx.market.trigger.api.dto.FulfillmentSaveUserAwardRecordRequestDTO;
import com.dyx.market.trigger.api.response.Response;

/**
 * 跨服务 Dubbo 接口：奖品履约（发奖）操作。
 *
 * <p>Provider 在 big-market-fulfillment-service。各方法委托本地 IAwardService 领域 Bean，
 * 结果封装为统一 {@link Response}，错误处理约定与
 * {@link IAccountCreditService}、{@link IAccountQuotaService} 等一致。</p>
 *
 * <p>market-service、message-job-service、infrastructure 的进程内调用方
 * 仍直接调用 IAwardService——需 award-credit outbox 本地冒烟通过且远程发奖开关开启后，
 * 才会路由到本接口。</p>
 *
 * @see com.dyx.market.domain.award.service.IAwardService
 */
public interface IFulfillmentAwardService {

    /**
     * 持久化用户中奖记录并发出发奖调度事件。
     *
     * @param request userId、activityId、strategyId、orderId、awardId、
     *                awardTitle、awardTime、awardState、awardConfig
     * @return 成功/失败通过 {@code Response<Void>} 返回
     */
    Response<Void> saveUserAwardRecord(FulfillmentSaveUserAwardRecordRequestDTO request);

    /**
     * 向用户派发（交付）奖品。
     *
     * @param request userId、orderId、awardId、awardConfig
     * @return 成功/失败通过 {@code Response<Void>} 返回
     */
    Response<Void> distributeAward(FulfillmentDistributeAwardRequestDTO request);

}
