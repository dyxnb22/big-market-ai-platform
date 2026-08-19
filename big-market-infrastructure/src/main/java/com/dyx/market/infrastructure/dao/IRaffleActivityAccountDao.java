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

    /** 恢复总额度剩余次数加 1，用于回滚补偿。 */
    void addAccountTotalSurplusQuota(RaffleActivityAccount raffleActivityAccount);

    /** 恢复主账户中的月额度剩余次数镜像加 1，用于回滚补偿。 */
    void addAccountMonthSurplusQuota(RaffleActivityAccount raffleActivityAccount);

    /** 恢复主账户中的日额度剩余次数镜像加 1，用于回滚补偿。 */
    void addAccountDaySurplusQuota(RaffleActivityAccount raffleActivityAccount);

}
