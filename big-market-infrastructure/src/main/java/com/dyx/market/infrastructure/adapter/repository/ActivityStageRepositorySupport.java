package com.dyx.market.infrastructure.adapter.repository;

import com.dyx.market.domain.activity.model.entity.RaffleActivityStageEntity;
import com.dyx.market.infrastructure.dao.IRaffleActivityStageDao;
import com.dyx.market.infrastructure.dao.po.RaffleActivityStage;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动 stage 上架数据访问，从 {@link ActivityRepository} 拆分。
 */
@Component
public class ActivityStageRepositorySupport {

    @Resource
    private IRaffleActivityStageDao raffleActivityStageDao;

    public void appendStageActivity(String channel, String source, Long activityId) {
        raffleActivityStageDao.insert(RaffleActivityStage.builder()
                .channel(channel).source(source).activityId(activityId).build());
    }

    public void updateStageActivity2Active(Long id) {
        raffleActivityStageDao.updateStageActivity2ActiveById(id);
    }

    public void updateStageActivity2Expire(Long id) {
        raffleActivityStageDao.updateStageActivity2ExpireById(id);
    }

    public Long queryStageActiveBySC(String channel, String source) {
        return raffleActivityStageDao.queryStageActiveBySC(
                RaffleActivityStage.builder().channel(channel).source(source).build());
    }

    public List<RaffleActivityStageEntity> queryStageActivityList() {
        List<RaffleActivityStageEntity> result = new ArrayList<>();
        List<RaffleActivityStage> list = raffleActivityStageDao.queryStageActivityList();
        for (RaffleActivityStage stage : list) {
            result.add(RaffleActivityStageEntity.builder()
                    .id(stage.getId())
                    .channel(stage.getChannel())
                    .source(stage.getSource())
                    .activityId(stage.getActivityId())
                    .state(stage.getState())
                    .build());
        }
        return result;
    }

    public Long queryStageActivity2ActiveById(Long id) {
        return raffleActivityStageDao.queryStageActivity2ActiveById(id);
    }
}
