package com.dyx.market.domain.award.service.distribute;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;

/**
 * 奖品发放 SPI：每种 {@code awardKey} 对应一个 Spring Bean 实现。
 * <p>由 {@code AwardService#distributeAward} 按配置路由调用；
 * 调用方通常为 {@code SendAwardConsumer}（MQ 异步）。</p>
 * <p>实现须以 {@code orderId} 保证幂等；重复执行应抛 {@code INDEX_DUP} 或静默成功。</p>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-05-18 08:22
 */
public interface IDistributeAward {

    /**
     * 执行奖品发放。
     *
     * @param distributeAwardEntity 含 userId、orderId（幂等键）、awardId、awardConfig
     * @throws Exception 不可恢复错误向上抛出；幂等冲突抛 {@code AppException(INDEX_DUP)}
     */
    void giveOutPrizes(DistributeAwardEntity distributeAwardEntity) throws Exception;

}
