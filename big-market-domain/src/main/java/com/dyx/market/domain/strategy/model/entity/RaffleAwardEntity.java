package com.dyx.market.domain.strategy.model.entity;

import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖奖品实体
 * @create 2024-01-06 09:20
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RaffleAwardEntity {

    /** 奖品ID */
    private Integer awardId;
    /** 抽奖奖品标题 */
    private String awardTitle;
    /** 奖品配置信息 */
    private String awardConfig;
    /** 奖品顺序号 */
    private Integer sort;
    /** 是否已在 Redis 预占奖品库存 */
    private Boolean stockReserved;
    /** 奖品库存预占信息（确认后入队落库，失败时释放） */
    private StrategyAwardStockKeyVO stockReservation;

}
