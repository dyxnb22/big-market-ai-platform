package com.dyx.market.domain.award.service;

import com.dyx.market.domain.award.adapter.event.SendAwardMessageEvent;
import com.dyx.market.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.dyx.market.domain.award.model.entity.DistributeAwardEntity;
import com.dyx.market.domain.award.model.entity.TaskEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;
import com.dyx.market.domain.award.model.entity.UserAwardRecordLogEntity;
import com.dyx.market.domain.award.model.valobj.TaskStateVO;
import com.dyx.market.domain.award.adapter.repository.IAwardRepository;
import com.dyx.market.domain.award.service.distribute.IDistributeAward;
import com.dyx.market.types.event.BaseEvent;
import com.dyx.market.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 发奖领域服务：两阶段分离「记录中奖」与「实际发奖」。
 * <ul>
 *   <li>{@link #saveUserAwardRecord} — 同步阶段：中奖记录 + outbox task 同事务（幂等键 {@code orderId}）</li>
 *   <li>{@link #distributeAward} — 异步阶段：按奖品 {@code awardKey} 路由到 {@link IDistributeAward} 实现</li>
 * </ul>
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @create 2024-04-06 09:39
 */
@Slf4j
@Service
public class AwardService implements IAwardService {

    private final IAwardRepository awardRepository;
    private final SendAwardMessageEvent sendAwardMessageEvent;
    private final Map<String, IDistributeAward> distributeAwardMap;

    public AwardService(IAwardRepository awardRepository, SendAwardMessageEvent sendAwardMessageEvent, Map<String, IDistributeAward> distributeAwardMap) {
        this.awardRepository = awardRepository;
        this.sendAwardMessageEvent = sendAwardMessageEvent;
        this.distributeAwardMap = distributeAwardMap;
    }

    /**
     * 阶段一：持久化中奖记录并写入 send_award outbox task（同事务）。
     * 由抽奖流程同步调用；实际发奖由 MQ 消费者异步触发 {@link #distributeAward}。
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        // 构建消息对象
        SendAwardMessageEvent.SendAwardMessage sendAwardMessage = new SendAwardMessageEvent.SendAwardMessage();
        sendAwardMessage.setUserId(userAwardRecordEntity.getUserId());
        sendAwardMessage.setOrderId(userAwardRecordEntity.getOrderId());
        sendAwardMessage.setAwardId(userAwardRecordEntity.getAwardId());
        sendAwardMessage.setAwardTitle(userAwardRecordEntity.getAwardTitle());
        sendAwardMessage.setAwardConfig(userAwardRecordEntity.getAwardConfig());

        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> sendAwardMessageEventMessage = sendAwardMessageEvent.buildEventMessage(sendAwardMessage);

        // 构建任务对象
        TaskEntity taskEntity = new TaskEntity();
        taskEntity.setUserId(userAwardRecordEntity.getUserId());
        taskEntity.setTopic(sendAwardMessageEvent.topic());
        taskEntity.setMessageId(sendAwardMessageEventMessage.getId());
        taskEntity.setMessage(sendAwardMessageEventMessage);
        taskEntity.setState(TaskStateVO.create);

        // 构建聚合对象
        UserAwardRecordAggregate userAwardRecordAggregate = UserAwardRecordAggregate.builder()
                .taskEntity(taskEntity)
                .userAwardRecordEntity(userAwardRecordEntity)
                .build();

        // 存储聚合对象 - 一个事务下，用户的中奖记录
        awardRepository.saveUserAwardRecord(userAwardRecordAggregate);

        log.info("中奖记录保存完成 userId:{} orderId:{}", userAwardRecordEntity.getUserId(), userAwardRecordEntity.getOrderId());
    }

    /**
     * 阶段二：按奖品配置 {@code awardKey} 路由到对应 {@link IDistributeAward} Bean 执行发奖。
     * 由 {@code SendAwardConsumer} 调用；{@code orderId} 为幂等键。
     */
    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception {
        // 奖品Key
        String awardKey = awardRepository.queryAwardKey(distributeAwardEntity.getAwardId());
        if (null == awardKey) {
            log.error("分发奖品，奖品ID不存在 awardId:{}", distributeAwardEntity.getAwardId());
            throw new AppException(com.dyx.market.types.enums.ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "奖品配置不存在，awardId=" + distributeAwardEntity.getAwardId());
        }

        // 奖品服务
        IDistributeAward distributeAward = distributeAwardMap.get(awardKey);

        if (null == distributeAward) {
            log.error("分发奖品，对应的服务不存在。awardKey:{}", awardKey);
            if ("openai_model".equals(awardKey)) {
                throw new AppException(com.dyx.market.types.enums.ResponseCode.UN_ERROR.getCode(),
                        "openai_model 奖品类型在演示栈未启用，请使用 user_credit_random 等已支持类型");
            }
            throw new RuntimeException("分发奖品，奖品" + awardKey + "对应的服务不存在");
        }

        // 发放奖品
        distributeAward.giveOutPrizes(distributeAwardEntity);
    }

    @Override
    public List<UserAwardRecordLogEntity> queryUserAwardRecords(String userId, int limit) {
        return awardRepository.queryUserAwardRecords(userId, limit);
    }

}
