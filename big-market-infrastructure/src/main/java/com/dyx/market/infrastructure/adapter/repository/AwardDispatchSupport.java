package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.award.adapter.port.IAwardActivityOrderPort;
import com.dyx.market.domain.award.adapter.port.IAwardDispatchTaskOutboxPort;
import com.dyx.market.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.dyx.market.domain.award.model.entity.TaskEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.infrastructure.dao.IUserAwardRecordDao;
import com.dyx.market.infrastructure.dao.po.UserAwardRecord;
import com.dyx.market.infrastructure.event.EventPublisher;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;

/**
 * 中奖记录写入与发奖任务派发，从 {@link AwardRepository} 拆分以降低单类复杂度。
 */
@Slf4j
@Component
public class AwardDispatchSupport {

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private IAwardActivityOrderPort awardActivityOrderPort;
    @Resource
    private IAwardDispatchTaskOutboxPort awardDispatchTaskOutboxPort;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;

    public void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate) {

        UserAwardRecordEntity userAwardRecordEntity = userAwardRecordAggregate.getUserAwardRecordEntity();
        TaskEntity taskEntity = userAwardRecordAggregate.getTaskEntity();
        String userId = userAwardRecordEntity.getUserId();
        Long activityId = userAwardRecordEntity.getActivityId();
        Integer awardId = userAwardRecordEntity.getAwardId();

        UserAwardRecord userAwardRecord = UserAwardRecord.builder()
                .userId(userAwardRecordEntity.getUserId())
                .activityId(userAwardRecordEntity.getActivityId())
                .strategyId(userAwardRecordEntity.getStrategyId())
                .orderId(userAwardRecordEntity.getOrderId())
                .awardId(userAwardRecordEntity.getAwardId())
                .awardTitle(userAwardRecordEntity.getAwardTitle())
                .awardTime(userAwardRecordEntity.getAwardTime())
                .awardState(userAwardRecordEntity.getAwardState().getCode())
                .build();

        try {
            dbRouter.doRouter(userId);
            transactionTemplate.execute(status -> {
                try {
                    // 写入记录
                    userAwardRecordDao.insert(userAwardRecord);
                    // 写入任务
                    awardDispatchTaskOutboxPort.insert(taskEntity);
                    // 更新抽奖单
                    int count = awardActivityOrderPort.markUserRaffleOrderUsed(
                            userAwardRecordEntity.getUserId(),
                            userAwardRecordEntity.getOrderId());
                    if (1 != count) {
                        status.setRollbackOnly();
                        log.error("写入中奖记录，用户抽奖单已使用过，不可重复抽奖 userId: {} activityId: {} awardId: {}", userId, activityId, awardId);
                        throw new AppException(ResponseCode.ACTIVITY_ORDER_ERROR.getCode(), ResponseCode.ACTIVITY_ORDER_ERROR.getInfo());
                    }
                    return 1;
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.error("写入中奖记录，唯一索引冲突 userId: {} activityId: {} awardId: {}", userId, activityId, awardId, e);
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
                }
            });
        } finally {
            dbRouter.clear();
        }

        dbRouter.doRouter(userId);
        try {
            // 发送消息【在事务外执行，如果失败还有任务补偿】
            eventPublisher.publish(taskEntity.getTopic(), taskEntity.getMessage());
            awardDispatchTaskOutboxPort.markSendMessageCompleted(taskEntity);
            log.info("写入中奖记录，发送MQ消息完成 userId: {} orderId:{} topic: {}", userId, userAwardRecordEntity.getOrderId(), taskEntity.getTopic());
        } catch (Exception e) {
            log.error("写入中奖记录，发送MQ消息失败 userId: {} topic: {}", userId, taskEntity.getTopic());
            awardDispatchTaskOutboxPort.markSendMessageFail(taskEntity);
        } finally {
            dbRouter.clear();
        }

    }

}
