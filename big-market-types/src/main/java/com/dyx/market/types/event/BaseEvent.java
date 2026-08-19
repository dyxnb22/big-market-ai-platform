package com.dyx.market.types.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 基础事件消息
 * @create 2024-03-30 12:42
 */
@Data
public abstract class BaseEvent<T> {

    /** 根据业务数据构造待发布的事件消息。 */
    public abstract EventMessage<T> buildEventMessage(T data);

    /** 返回事件对应的消息主题。 */
    public abstract String topic();

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventMessage<T> {
        /** 事件消息唯一 ID。 */
        private String id;
        /** 事件创建时间。 */
        private Date timestamp;
        /** 事件业务载荷。 */
        private T data;
    }

}
