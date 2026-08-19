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
 * <p>发奖路径：CAS 更新中奖记录为 complete → 插入 credit_award_task outbox（同事务）。</p>
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
        // 不指定租约时间，使用 watchdog 在事务执行期间自动续期，避免积分写入完成前锁过期。
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

    /**
     * 同事务内：先 CAS 将中奖记录标为 complete，再插入 credit_award_task。
     * <ul>
     *   <li>update 行数为 0 → 已处理，回滚（幂等）</li>
     *   <li>{@code DuplicateKeyException} on outbox → {@code INDEX_DUP}，表示任务已存在</li>
     * </ul>
     * 外层 Redisson 锁（watchdog 续期）防止并发重复发奖。
     */
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
