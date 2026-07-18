package com.dyx.market.domain.rebate.service;

import com.dyx.market.domain.rebate.event.SendRebateMessageEvent;
import com.dyx.market.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.dyx.market.domain.rebate.model.entity.BehaviorEntity;
import com.dyx.market.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.dyx.market.domain.rebate.model.entity.TaskEntity;
import com.dyx.market.domain.rebate.model.valobj.DailyBehaviorRebateVO;
import com.dyx.market.domain.rebate.model.valobj.BehaviorTypeVO;
import com.dyx.market.domain.rebate.model.valobj.TaskStateVO;
import com.dyx.market.domain.rebate.repository.IBehaviorRebateRepository;
import com.dyx.market.types.common.Constants;
import com.dyx.market.types.common.OrderIdGenerator;
import com.dyx.market.types.event.BaseEvent;
import com.dyx.market.types.enums.ResponseCode;
import com.dyx.market.types.exception.AppException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 行为返利服务实现
 * @create 2024-04-30 15:31
 */
@Service
public class BehaviorRebateService implements IBehaviorRebateService {

    @Resource
    private IBehaviorRebateRepository behaviorRebateRepository;
    @Resource
    private SendRebateMessageEvent sendRebateMessageEvent;

    @Override
    public List<String> createOrder(BehaviorEntity behaviorEntity) {
        // 1. 查询返利配置
        List<DailyBehaviorRebateVO> dailyBehaviorRebateVOS = behaviorRebateRepository.queryDailyBehaviorRebateConfig(behaviorEntity.getBehaviorTypeVO());
        if (null == dailyBehaviorRebateVOS || dailyBehaviorRebateVOS.isEmpty()) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "行为返利未配置");
        }

        // 2. 构建聚合对象
        List<String> orderIds = new ArrayList<>();
        List<BehaviorRebateAggregate> behaviorRebateAggregates = new ArrayList<>();
        for (DailyBehaviorRebateVO dailyBehaviorRebateVO : dailyBehaviorRebateVOS) {
            validateRebateConfig(dailyBehaviorRebateVO);
            // 拼装业务ID；用户ID_返利类型_外部透彻业务ID
            String bizId = behaviorEntity.getUserId() + Constants.UNDERLINE + dailyBehaviorRebateVO.getRebateType() + Constants.UNDERLINE + behaviorEntity.getOutBusinessNo();
            BehaviorRebateOrderEntity behaviorRebateOrderEntity = BehaviorRebateOrderEntity.builder()
                    .userId(behaviorEntity.getUserId())
                    .orderId(OrderIdGenerator.generate(12))
                    .behaviorType(dailyBehaviorRebateVO.getBehaviorType())
                    .rebateDesc(dailyBehaviorRebateVO.getRebateDesc())
                    .rebateType(dailyBehaviorRebateVO.getRebateType())
                    .rebateConfig(dailyBehaviorRebateVO.getRebateConfig())
                    .outBusinessNo(behaviorEntity.getOutBusinessNo())
                    .bizId(bizId)
                    .build();
            orderIds.add(behaviorRebateOrderEntity.getOrderId());

            // MQ 消息对象
            SendRebateMessageEvent.RebateMessage rebateMessage = SendRebateMessageEvent.RebateMessage.builder()
                    .userId(behaviorEntity.getUserId())
                    .rebateType(dailyBehaviorRebateVO.getRebateType())
                    .rebateConfig(dailyBehaviorRebateVO.getRebateConfig())
                    .bizId(bizId)
                    .build();

            // 构建事件消息
            BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage> rebateMessageEventMessage = sendRebateMessageEvent.buildEventMessage(rebateMessage);

            // 组装任务对象
            TaskEntity taskEntity = new TaskEntity();
            taskEntity.setUserId(behaviorEntity.getUserId());
            taskEntity.setTopic(sendRebateMessageEvent.topic());
            taskEntity.setMessageId(rebateMessageEventMessage.getId());
            taskEntity.setMessage(rebateMessageEventMessage);
            taskEntity.setState(TaskStateVO.create);

            BehaviorRebateAggregate behaviorRebateAggregate = BehaviorRebateAggregate.builder()
                        .userId(behaviorEntity.getUserId())
                        .behaviorRebateOrderEntity(behaviorRebateOrderEntity)
                        .taskEntity(taskEntity)
                        .build();

            behaviorRebateAggregates.add(behaviorRebateAggregate);
        }

        // 3. 存储聚合对象数据
        behaviorRebateRepository.saveUserRebateRecord(behaviorEntity.getUserId(), behaviorRebateAggregates);

        // 4. 返回订单ID集合
        return orderIds;
    }

    private void validateRebateConfig(DailyBehaviorRebateVO config) {
        if (config == null || ("sku".equalsIgnoreCase(config.getRebateType())
                ? !isPositiveLong(config.getRebateConfig())
                : "integral".equalsIgnoreCase(config.getRebateType())
                ? !isPositiveDecimal(config.getRebateConfig()) : true)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "返利配置非法或返利类型不支持");
        }
    }

    private boolean isPositiveLong(String value) {
        try { return Long.parseLong(value) > 0; } catch (Exception e) { return false; }
    }

    private boolean isPositiveDecimal(String value) {
        try { return new java.math.BigDecimal(value).signum() > 0; } catch (Exception e) { return false; }
    }

    @Override
    public List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo) {
        return behaviorRebateRepository.queryOrderByOutBusinessNo(userId, outBusinessNo);
    }

    @Override
    public List<DailyBehaviorRebateVO> queryDailyBehaviorRebateConfig() {
        return behaviorRebateRepository.queryDailyBehaviorRebateConfig(BehaviorTypeVO.SIGN);
    }

}
