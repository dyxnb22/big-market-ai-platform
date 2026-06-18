package com.dyx.market.infrastructure.elasticsearch;

import com.dyx.market.infrastructure.elasticsearch.po.UserRaffleOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Elasticsearch 用户抽奖订单查询 DAO（学习/演示用 ES SQL 接入）。
 */
@Mapper
public interface IElasticSearchUserRaffleOrderDao {

    List<UserRaffleOrder> queryUserRaffleOrderList();

}
