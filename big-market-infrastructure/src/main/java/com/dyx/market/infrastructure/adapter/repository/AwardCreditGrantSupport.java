package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.award.adapter.port.IAwardCreditWritePort;
import com.dyx.market.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.entity.UserCreditAwardEntity;
import com.dyx.market.infrastructure.dao.IUserAwardRecordDao;
import com.dyx.market.infrastructure.dao.po.UserAwardRecord;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 积分奖品发放，从 {@link AwardRepository} 拆分以降低单类复杂度。
 */
@Slf4j
@Component
public class AwardCreditGrantSupport {

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private IAwardCreditWritePort awardCreditWritePort;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private IRedisService redisService;

    public void saveGiveOutPrizesAggregate(GiveOutPrizesAggregate giveOutPrizesAggregate) {
        String userId = giveOutPrizesAggregate.getUserId();
        UserCreditAwardEntity userCreditAwardEntity = giveOutPrizesAggregate.getUserCreditAwardEntity();
        UserAwardRecordEntity userAwardRecordEntity = giveOutPrizesAggregate.getUserAwardRecordEntity();

        UserAwardRecord userAwardRecordReq = UserAwardRecord.builder()
                .userId(userId)
                .orderId(userAwardRecordEntity.getOrderId())
                .awardState(userAwardRecordEntity.getAwardState().getCode())
                .build();

        RLock lock = redisService.getLock(Constants.RedisKey.ACTIVITY_ACCOUNT_LOCK + userId);
        // Use watchdog (no lease time) so the lock auto-renews while the transaction
        // is in progress and cannot expire before the credit write completes.
        lock.lock();
        try {
            dbRouter.doRouter(giveOutPrizesAggregate.getUserId());
            saveWithCreditOutbox(userId, userCreditAwardEntity, userAwardRecordEntity, userAwardRecordReq);
        } finally {
            dbRouter.clear();
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void saveWithCreditOutbox(String userId, UserCreditAwardEntity userCreditAwardEntity,
                                      UserAwardRecordEntity userAwardRecordEntity,
                                      UserAwardRecord userAwardRecordReq) {
        transactionTemplate.execute(status -> {
            try {
                int updateAwardCount = userAwardRecordDao.updateAwardRecordCompletedState(userAwardRecordReq);
                if (0 == updateAwardCount) {
                    log.warn("更新中奖记录，重复更新拦截(outbox) userId:{}", userId);
                    status.setRollbackOnly();
                    return 1;
                }
                awardCreditWritePort.insertCreditAwardTask(
                        userId,
                        userAwardRecordEntity.getOrderId(),
                        userCreditAwardEntity.getCreditAmount());
                return 1;
            } catch (DuplicateKeyException e) {
                status.setRollbackOnly();
                log.error("更新中奖记录，outbox唯一索引冲突(已处理) userId:{}", userId, e);
                throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
            }
        });
    }

}
