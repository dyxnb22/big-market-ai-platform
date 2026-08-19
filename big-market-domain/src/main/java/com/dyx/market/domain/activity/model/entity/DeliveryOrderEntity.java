package com.dyx.market.domain.activity.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 出货单实体对象
 * @create 2024-06-08 20:02
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryOrderEntity {

    /**
     * 用户ID
     */
    private String userId;
    /**
     * 业务幂等 ID，由外部调用方透传，用于保证发货状态推进幂等。
     */
    private String outBusinessNo;

}
