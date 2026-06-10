package com.dyx.market.fulfillment.provider;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.service.IAwardService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
 * Phase 2.3-A dark launch: Dubbo provider wrapping the existing IAwardService.
 *
 * Delegates all calls to AwardService unchanged — no logic lives here.
 * No caller is wired to this provider yet; traffic cutover deferred to Phase 2.3-B+
 * after the credit-award outbox is staging-validated.
 *
 * Safety constraint: UserCreditRandomAward writes user_credit_account directly in a
 * shared local transaction with user_award_record. This must be routed through
 * account-service via the outbox BEFORE any traffic cutover to fulfillment-service.
 * See docs/microservices-split-phase-2-3-fulfillment-service.md.
 */
@Slf4j
@DubboService(version = "1.0")
public class FulfillmentAwardServiceRPC implements IAwardService {

    @Resource
    private IAwardService awardService;

    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        log.info("[FulfillmentAwardServiceRPC] saveUserAwardRecord userId:{} orderId:{}",
                userAwardRecordEntity.getUserId(), userAwardRecordEntity.getOrderId());
        awardService.saveUserAwardRecord(userAwardRecordEntity);
    }

    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception {
        log.info("[FulfillmentAwardServiceRPC] distributeAward userId:{} orderId:{} awardId:{}",
                distributeAwardEntity.getUserId(), distributeAwardEntity.getOrderId(),
                distributeAwardEntity.getAwardId());
        awardService.distributeAward(distributeAwardEntity);
    }

}
