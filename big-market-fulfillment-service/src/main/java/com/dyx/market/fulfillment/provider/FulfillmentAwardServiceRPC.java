package com.dyx.market.fulfillment.provider;

import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.valobj.AwardStateVO;
import com.dyx.market.domain.award.service.IAwardService;
import com.dyx.market.trigger.api.IFulfillmentAwardService;
import com.dyx.market.trigger.api.dto.FulfillmentDistributeAwardRequestDTO;
import com.dyx.market.trigger.api.dto.FulfillmentSaveUserAwardRecordRequestDTO;
import com.dyx.market.trigger.api.response.Response;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;

/**
 * Phase 2.3-A dark launch: Dubbo provider wrapping the existing IAwardService.
 *
 * Implements the API contract {@link IFulfillmentAwardService} from big-market-api.
 * Internally delegates to the domain {@link IAwardService} — no logic lives here.
 *
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
public class FulfillmentAwardServiceRPC implements IFulfillmentAwardService {

    @Resource
    private IAwardService awardService;

    @Override
    public Response<Void> saveUserAwardRecord(FulfillmentSaveUserAwardRecordRequestDTO request) {
        log.info("[FulfillmentAwardServiceRPC] saveUserAwardRecord userId:{} orderId:{}",
                request == null ? null : request.getUserId(),
                request == null ? null : request.getOrderId());
        try {
            if (request == null
                    || isBlank(request.getUserId())
                    || request.getActivityId() == null
                    || request.getStrategyId() == null
                    || isBlank(request.getOrderId())
                    || request.getAwardId() == null
                    || isBlank(request.getAwardState())) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            AwardStateVO awardState = AwardStateVO.getByCode(request.getAwardState());
            if (awardState == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            UserAwardRecordEntity entity = UserAwardRecordEntity.builder()
                    .userId(request.getUserId())
                    .activityId(request.getActivityId())
                    .strategyId(request.getStrategyId())
                    .orderId(request.getOrderId())
                    .awardId(request.getAwardId())
                    .awardTitle(request.getAwardTitle())
                    .awardTime(request.getAwardTime())
                    .awardState(awardState)
                    .awardConfig(request.getAwardConfig())
                    .build();

            awardService.saveUserAwardRecord(entity);

            return Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (AppException e) {
            log.error("[FulfillmentAwardServiceRPC] saveUserAwardRecord error userId:{} orderId:{}",
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getOrderId(), e);
            return Response.<Void>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("[FulfillmentAwardServiceRPC] saveUserAwardRecord failed userId:{} orderId:{}",
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getOrderId(), e);
            return Response.<Void>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    public Response<Void> distributeAward(FulfillmentDistributeAwardRequestDTO request) {
        log.info("[FulfillmentAwardServiceRPC] distributeAward userId:{} orderId:{} awardId:{}",
                request == null ? null : request.getUserId(),
                request == null ? null : request.getOrderId(),
                request == null ? null : request.getAwardId());
        try {
            if (request == null
                    || isBlank(request.getUserId())
                    || isBlank(request.getOrderId())
                    || request.getAwardId() == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }

            DistributeAwardEntity entity = DistributeAwardEntity.builder()
                    .userId(request.getUserId())
                    .orderId(request.getOrderId())
                    .awardId(request.getAwardId())
                    .awardConfig(request.getAwardConfig())
                    .build();

            awardService.distributeAward(entity);

            return Response.<Void>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .build();
        } catch (AppException e) {
            log.error("[FulfillmentAwardServiceRPC] distributeAward error userId:{} orderId:{} awardId:{}",
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getOrderId(),
                    request == null ? null : request.getAwardId(), e);
            return Response.<Void>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("[FulfillmentAwardServiceRPC] distributeAward failed userId:{} orderId:{} awardId:{}",
                    request == null ? null : request.getUserId(),
                    request == null ? null : request.getOrderId(),
                    request == null ? null : request.getAwardId(), e);
            return Response.<Void>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}
