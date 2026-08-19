package com.dyx.market.types.common;

/**
 * 平台通用常量：字符串分隔符及 Redis 缓存 key 前缀。
 *
 * <p>{@link RedisKey} 供各领域仓储、策略装配与分布式锁命名使用。</p>
 */
public class Constants {

    public final static String SPLIT = ",";
    public final static String COLON = ":";
    public final static String SPACE = " ";
    public final static String UNDERLINE = "_";

    /**
     * 定义出缓存key的前缀标识，
     */
    public static class RedisKey {
        public static String ACTIVITY_KEY = "big_market_activity_key_";
        public static String ACTIVITY_SKU_KEY = "big_market_activity_sku_key_";
        public static String ACTIVITY_COUNT_KEY = "big_market_activity_count_key_";
        public static String STRATEGY_KEY = "big_market_strategy_key_";
        public static String STRATEGY_AWARD_KEY = "big_market_strategy_award_key_";
        public static String STRATEGY_AWARD_LIST_KEY = "big_market_strategy_award_list_key_";
        public static String STRATEGY_RATE_TABLE_KEY = "big_market_strategy_rate_table_key_";
        public static String STRATEGY_RATE_RANGE_KEY = "big_market_strategy_rate_range_key_";
        public static String RULE_TREE_VO_KEY = "rule_tree_vo_key_";
        public static String STRATEGY_AWARD_COUNT_KEY = "strategy_award_count_key_";
        public static String STRATEGY_AWARD_COUNT_QUERY_KEY = "strategy_award_count_query_key";
        public static String STRATEGY_RULE_WEIGHT_KEY = "strategy_rule_weight_key_";
        public static String ACTIVITY_SKU_COUNT_QUERY_KEY = "activity_sku_count_query_key";
        public static String ACTIVITY_SKU_STOCK_COUNT_KEY = "activity_sku_stock_count_key_";
        public static String ACTIVITY_SKU_COUNT_CLEAR_KEY = "activity_sku_count_clear_key_";
        public static String ACTIVITY_ACCOUNT_LOCK = "activity_account_lock_";
        public static String ACTIVITY_ACCOUNT_UPDATE_LOCK = "activity_account_update_lock_";
        public static String USER_CREDIT_ACCOUNT_LOCK = "user_credit_account_lock_";
        public static String STRATEGY_ARMORY_ALGORITHM_KEY = "strategy_armory_algorithm_key_";
        /** 待刷新策略奖品库存键集合；成员格式为 {@code {strategyId}_{awardId}}。 */
        public static String STRATEGY_AWARD_STOCK_PENDING_SET = "strategy_award_stock_pending_set";
        /** 待刷新活动 SKU 库存键集合；成员为字符串形式的 SKU。 */
        public static String ACTIVITY_SKU_STOCK_PENDING_SET = "activity_sku_stock_pending_set";

    }

}
