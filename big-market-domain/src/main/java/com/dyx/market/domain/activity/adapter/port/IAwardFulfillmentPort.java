package com.dyx.market.domain.activity.adapter.port;

import com.dyx.market.domain.award.model.entity.UserAwardRecordEntity;

/**
 * 领域端口：持久化抽奖中奖履约记录。
 * <p>
 * 本地路径（默认）：LocalAwardFulfillmentPort 委托 IAwardService.saveUserAwardRecord，
 * 在同一本地事务中写入 user_award_record 与任务发件箱行。
 * 远程路径：履约写入可由独立 fulfillment 服务实现同一契约。
 */
public interface IAwardFulfillmentPort {

    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecord);

}
