package com.dyx.market.domain.strategy.service;

import com.dyx.market.domain.strategy.model.entity.RaffleAwardEntity;
import com.dyx.market.domain.strategy.model.entity.RaffleFactorEntity;
import com.dyx.market.domain.strategy.model.entity.StrategyAwardEntity;
import com.dyx.market.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.dyx.market.domain.strategy.repository.IStrategyRepository;
import com.dyx.market.domain.strategy.service.armory.IStrategyDispatch;
import com.dyx.market.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.dyx.market.domain.strategy.service.rule.tree.factory.DefaultTreeFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GOV-C02：权重/黑名单规则链接管后仍必须进入库存预占树
 *（见 {@link AbstractRaffleStrategy#performRaffle}）。
 */
public class AbstractRaffleStrategyTakeoverStockTest {

    private static final String USER_ID = "user-takeover";
    private static final Long STRATEGY_ID = 100001L;
    private static final String ORDER_ID = "order-takeover-001";
    private static final Integer TAKEOVER_AWARD_ID = 102;

    private IStrategyRepository repository;

    @Before
    public void setUp() {
        repository = mock(IStrategyRepository.class);
        when(repository.queryStrategyAwardEntity(eq(STRATEGY_ID), eq(TAKEOVER_AWARD_ID)))
                .thenReturn(StrategyAwardEntity.builder()
                        .strategyId(STRATEGY_ID)
                        .awardId(TAKEOVER_AWARD_ID)
                        .awardTitle("takeover-award")
                        .sort(1)
                        .build());
    }

    @Test
    public void weight_takeover_still_reserves_stock_via_tree() {
        assertTakeoverReservesStock(DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode());
    }

    @Test
    public void blacklist_takeover_still_reserves_stock_via_tree() {
        assertTakeoverReservesStock(DefaultChainFactory.LogicModel.RULE_BLACKLIST.getCode());
    }

    private void assertTakeoverReservesStock(String logicModel) {
        AtomicInteger treeCalls = new AtomicInteger();
        AtomicReference<Integer> treeAwardId = new AtomicReference<>();
        AtomicReference<String> treeOrderId = new AtomicReference<>();

        StrategyAwardStockKeyVO reservation = StrategyAwardStockKeyVO.builder()
                .strategyId(STRATEGY_ID)
                .awardId(TAKEOVER_AWARD_ID)
                .reservationId(ORDER_ID)
                .lockSurplus(8L)
                .build();

        AbstractRaffleStrategy strategy = new AbstractRaffleStrategy(
                repository,
                mock(IStrategyDispatch.class),
                mock(DefaultChainFactory.class),
                mock(DefaultTreeFactory.class)
        ) {
            @Override
            public DefaultChainFactory.StrategyAwardVO raffleLogicChain(String userId, Long strategyId) {
                return DefaultChainFactory.StrategyAwardVO.builder()
                        .awardId(TAKEOVER_AWARD_ID)
                        .logicModel(logicModel)
                        .build();
            }

            @Override
            public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId) {
                return raffleLogicTree(userId, strategyId, awardId, null, null);
            }

            @Override
            public DefaultTreeFactory.StrategyAwardVO raffleLogicTree(String userId, Long strategyId, Integer awardId,
                                                                     Date endDateTime, String orderId) {
                treeCalls.incrementAndGet();
                treeAwardId.set(awardId);
                treeOrderId.set(orderId);
                return DefaultTreeFactory.StrategyAwardVO.builder()
                        .awardId(awardId)
                        .awardRuleValue("rule_stock")
                        .stockReserved(true)
                        .stockReservation(reservation)
                        .build();
            }
        };

        RaffleAwardEntity result = strategy.performRaffle(RaffleFactorEntity.builder()
                .userId(USER_ID)
                .strategyId(STRATEGY_ID)
                .orderId(ORDER_ID)
                .endDateTime(new Date(System.currentTimeMillis() + 86_400_000L))
                .build());

        assertEquals("chain takeover must still invoke raffleLogicTree", 1, treeCalls.get());
        assertEquals(TAKEOVER_AWARD_ID, treeAwardId.get());
        assertEquals(ORDER_ID, treeOrderId.get());
        assertEquals(TAKEOVER_AWARD_ID, result.getAwardId());
        assertTrue(Boolean.TRUE.equals(result.getStockReserved()));
        assertEquals(reservation, result.getStockReservation());
    }
}
