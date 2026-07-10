package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.StrategyAwardStockConfirmTask;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IStrategyAwardStockConfirmTaskDao {

    @DBRouter
    void insert(StrategyAwardStockConfirmTask task);

    List<StrategyAwardStockConfirmTask> queryPendingTasks(@Param("limit") int limit);

    @DBRouter
    int claimProcessing(StrategyAwardStockConfirmTask task);

    @DBRouter
    int updateConfirmed(StrategyAwardStockConfirmTask task);

    @DBRouter
    int updateRetryFailed(StrategyAwardStockConfirmTask task);

    int revertStaleProcessing(@Param("staleBefore") java.util.Date staleBefore, @Param("limit") int limit);
}
