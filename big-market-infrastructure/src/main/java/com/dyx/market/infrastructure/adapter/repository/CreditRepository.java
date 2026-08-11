package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.award.model.valobj.AccountStatusVO;
import com.dyx.market.domain.credit.adapter.port.ICreditTradeTaskOutboxPort;
import com.dyx.market.domain.credit.model.aggregate.TradeAggregate;
import com.dyx.market.domain.credit.model.entity.CreditAccountEntity;
import com.dyx.market.domain.credit.model.entity.CreditOrderEntity;
import com.dyx.market.domain.credit.model.entity.CreditOrderLogEntity;
import com.dyx.market.domain.credit.model.entity.TaskEntity;
import com.dyx.market.domain.credit.repository.ICreditRepository;
import com.dyx.market.infrastructure.event.EventPublisher;
import com.dyx.market.infrastructure.dao.IUserCreditAccountDao;
import com.dyx.market.infrastructure.dao.IUserCreditOrderDao;
import com.dyx.market.infrastructure.dao.po.UserCreditAccount;
import com.dyx.market.infrastructure.dao.po.UserCreditOrder;
import com.dyx.market.infrastructure.redis.IRedisService;
import com.dyx.market.middleware.db.router.strategy.IDBRouterStrategy;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户积分仓储
 * @create 2024-06-01 10:07
 */
@Slf4j
@Repository
public class CreditRepository implements ICreditRepository {

    @Resource
    private IRedisService redisService;
    @Resource
    private IUserCreditAccountDao userCreditAccountDao;
    @Resource
    private IUserCreditOrderDao userCreditOrderDao;
    @Resource
    private ICreditTradeTaskOutboxPort creditTradeTaskOutboxPort;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;

    /**
     * 积分交易落库 + outbox 发 MQ（锁粒度：{@code userId_outBusinessNo}）。
     * <ol>
     *   <li>Redisson 锁 → 事务内 upsert 账户、插入订单、写入 task outbox</li>
     *   <li>事务外 publish MQ；失败标记 task fail，由 job 补偿</li>
     * </ol>
     * {@code DuplicateKeyException} → {@code INDEX_DUP}，表示同 {@code outBusinessNo} 已处理。
     */
    @Override
    public void saveUserCreditTradeOrder(TradeAggregate tradeAggregate) {
        String userId = tradeAggregate.getUserId();
        CreditAccountEntity creditAccountEntity = tradeAggregate.getCreditAccountEntity();
        CreditOrderEntity creditOrderEntity = tradeAggregate.getCreditOrderEntity();
        TaskEntity taskEntity = tradeAggregate.getTaskEntity();

        // 积分账户
        UserCreditAccount userCreditAccountReq = new UserCreditAccount();
        userCreditAccountReq.setUserId(userId);
        userCreditAccountReq.setTotalAmount(creditAccountEntity.getAdjustAmount());
        // 知识；仓储往上有业务语义，仓储往下到 dao 操作是没有业务语义的。所以不用在乎这块使用的字段名称，直接用持久化对象即可。
        userCreditAccountReq.setAvailableAmount(creditAccountEntity.getAdjustAmount());
        userCreditAccountReq.setAccountStatus(AccountStatusVO.open.getCode());

        // 积分订单
        UserCreditOrder userCreditOrderReq = new UserCreditOrder();
        userCreditOrderReq.setUserId(creditOrderEntity.getUserId());
        userCreditOrderReq.setOrderId(creditOrderEntity.getOrderId());
        userCreditOrderReq.setTradeName(creditOrderEntity.getTradeName().getName());
        userCreditOrderReq.setTradeType(creditOrderEntity.getTradeType().getCode());
        userCreditOrderReq.setTradeAmount(creditOrderEntity.getTradeAmount());
        userCreditOrderReq.setOutBusinessNo(creditOrderEntity.getOutBusinessNo());

        RLock lock = redisService.getLock(Constants.RedisKey.USER_CREDIT_ACCOUNT_LOCK + userId + Constants.UNDERLINE + creditOrderEntity.getOutBusinessNo());
        try {
            lock.lock();
            dbRouter.doRouter(userId);
            // 编程式事务
            transactionTemplate.execute(status -> {
                try {
                    // 1. 保存账户积分
                    UserCreditAccount userCreditAccount = userCreditAccountDao.queryUserCreditAccount(userCreditAccountReq);
                    if (null == userCreditAccount) {
                        if (userCreditAccountReq.getAvailableAmount().signum() < 0) {
                            status.setRollbackOnly();
                            throw new AppException(ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode(), "积分账户不存在，不能直接扣减");
                        }
                        userCreditAccountDao.insert(userCreditAccountReq);
                    } else {
                        if (!AccountStatusVO.open.getCode().equals(userCreditAccount.getAccountStatus())) {
                            status.setRollbackOnly();
                            throw new AppException(ResponseCode.UN_ERROR.getCode(), "积分账户已冻结");
                        }
                        BigDecimal availableAmount = userCreditAccountReq.getAvailableAmount();
                        if (availableAmount.compareTo(BigDecimal.ZERO) >= 0) {
                            int addCount = userCreditAccountDao.updateAddAmount(userCreditAccountReq);
                            if (addCount != 1) {
                                status.setRollbackOnly();
                                throw new AppException(ResponseCode.UN_ERROR.getCode(), "积分账户状态已变更");
                            }
                        } else {
                            int subtractionCount = userCreditAccountDao.updateSubtractionAmount(userCreditAccountReq);
                            if (1 != subtractionCount) {
                                status.setRollbackOnly();
                                throw new AppException(ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode(), ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getInfo());
                            }
                        }
                    }
                    // 2. 保存账户订单
                    userCreditOrderDao.insert(userCreditOrderReq);
                    // 3. 写入任务
                    creditTradeTaskOutboxPort.insert(taskEntity);
                } catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.warn("调整账户积分额度唯一索引冲突，幂等返回 userId:{} orderId:{} outBusinessNo:{}", userId, creditOrderEntity.getOrderId(), creditOrderEntity.getOutBusinessNo());
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), ResponseCode.INDEX_DUP.getInfo());
                } catch (Exception e) {
                    status.setRollbackOnly();
                    log.error("调整账户积分额度失败 userId:{} orderId:{}", userId, creditOrderEntity.getOrderId(), e);
                    throw e;
                }
                return 1;
            });
        } finally {
            dbRouter.clear();
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        try {
            // 发送消息【在事务外执行，如果失败还有任务补偿】
            eventPublisher.publish(taskEntity.getTopic(), taskEntity.getMessage());
            // 更新数据库记录，task 任务表
            dbRouter.doRouter(userId);
            creditTradeTaskOutboxPort.markSendMessageCompleted(taskEntity);
            log.info("调整账户积分记录，发送MQ消息完成 userId: {} orderId:{} topic: {}", userId, creditOrderEntity.getOrderId(), taskEntity.getTopic());
        } catch (Exception e) {
            log.error("调整账户积分记录，发送MQ消息失败 userId: {} topic: {}", userId, taskEntity.getTopic());
            dbRouter.doRouter(userId);
            creditTradeTaskOutboxPort.markSendMessageFail(taskEntity);
        } finally {
            dbRouter.clear();
        }

    }

    @Override
    public CreditAccountEntity queryUserCreditAccount(String userId) {
        UserCreditAccount userCreditAccountReq = new UserCreditAccount();
        userCreditAccountReq.setUserId(userId);
        try {
            dbRouter.doRouter(userId);
            UserCreditAccount userCreditAccount = userCreditAccountDao.queryUserCreditAccount(userCreditAccountReq);
            if (userCreditAccount == null) {
                return null;
            }
            return CreditAccountEntity.builder().userId(userId).adjustAmount(userCreditAccount.getAvailableAmount()).build();
        } finally {
            dbRouter.clear();
        }

    }

    /**
     * 查询用户积分流水读模型。user_credit_order 按 userId 分库分表，
     * 需显式路由到用户分片；trade_name 落库为中文展示值，读路径原样透传。
     */
    @Override
    public List<CreditOrderLogEntity> queryUserCreditOrders(String userId, int limit) {
        UserCreditOrder query = new UserCreditOrder();
        query.setUserId(userId);
        List<UserCreditOrder> orders;
        try {
            dbRouter.doRouter(userId);
            orders = userCreditOrderDao.queryUserCreditOrderListByUserId(query);
        } finally {
            dbRouter.clear();
        }
        List<CreditOrderLogEntity> result = new ArrayList<>();
        if (orders == null) {
            return result;
        }
        for (UserCreditOrder order : orders) {
            if (result.size() >= limit) {
                break;
            }
            result.add(CreditOrderLogEntity.builder()
                    .orderId(order.getOrderId())
                    .tradeName(order.getTradeName())
                    .tradeType(order.getTradeType())
                    .tradeAmount(order.getTradeAmount())
                    .createTime(order.getCreateTime())
                    .build());
        }
        return result;
    }

}
