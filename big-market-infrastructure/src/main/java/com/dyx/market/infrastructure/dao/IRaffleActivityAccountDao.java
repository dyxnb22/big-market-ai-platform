package com.dyx.market.infrastructure.dao;

import com.dyx.market.infrastructure.dao.po.RaffleActivityAccount;
import com.dyx.market.middleware.db.router.annotation.DBRouter;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动账户表
 * @create 2024-03-09 10:05
 */
@Mapper
public interface IRaffleActivityAccountDao {

    void insert(RaffleActivityAccount raffleActivityAccount);

    int updateAccountQuota(RaffleActivityAccount raffleActivityAccount);

    @DBRouter
    RaffleActivityAccount queryActivityAccountByUserId(RaffleActivityAccount raffleActivityAccountReq);

    int updateActivityAccountSubtractionQuota(RaffleActivityAccount raffleActivityAccount);

    int updateActivityAccountMonthSubtractionQuota(RaffleActivityAccount raffleActivityAccount);

    int updateActivityAccountDaySubtractionQuota(RaffleActivityAccount raffleActivityAccount);

    void updateActivityAccountMonthSurplusImageQuota(RaffleActivityAccount raffleActivityAccount);

    void updateActivityAccountDaySurplusImageQuota(RaffleActivityAccount raffleActivityAccount);

    RaffleActivityAccount queryAccountByUserId(RaffleActivityAccount raffleActivityAccount);

    /** Restore total_count_surplus + 1 (rollback compensation). */
    void addAccountTotalSurplusQuota(RaffleActivityAccount raffleActivityAccount);

    /** Restore month_count_surplus mirror + 1 in main account (rollback compensation). */
    void addAccountMonthSurplusQuota(RaffleActivityAccount raffleActivityAccount);

    /** Restore day_count_surplus mirror + 1 in main account (rollback compensation). */
    void addAccountDaySurplusQuota(RaffleActivityAccount raffleActivityAccount);

}
